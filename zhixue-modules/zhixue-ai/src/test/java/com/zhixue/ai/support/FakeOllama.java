package com.zhixue.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 测试用的假 Ollama：用 JDK 自带 HttpServer 起一个本地端口，记录收到的请求体并按路径返回预设响应。
 * 让测试覆盖真实的 HTTP 序列化与解析路径，而不是只测 stub 分支。
 *
 * <p>{@link #streamChat} 模拟 Ollama 的流式输出（NDJSON，一行一个 JSON），可设置首块延迟与块间隔，
 * 用来验证首块超时、总时长超时与中途取消。</p>
 */
public final class FakeOllama implements AutoCloseable {

    public record Reply(int status, String body) {
    }

    /** 流式响应：逐行写出，每行之间间隔 gapMs */
    private record Stream(List<String> lines, long firstDelayMs, long gapMs) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    /** 流式处理会阻塞线程，必须用线程池，否则其他请求要排队等它写完 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Function<JsonNode, Reply>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    public FakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        JsonNode body = raw.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        paths.add(path);
        requests.add(body);
        Stream stream = streams.get(path);
        if (stream != null) {
            writeStream(exchange, stream);
            return;
        }
        Function<JsonNode, Reply> handler = handlers.get(path);
        Reply reply = handler == null ? new Reply(404, "{\"error\":\"not found\"}") : handler.apply(body);
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private static void writeStream(HttpExchange exchange, Stream stream) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0); // 0 = chunked
        try (OutputStream os = exchange.getResponseBody()) {
            sleep(stream.firstDelayMs());
            for (String line : stream.lines()) {
                os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                sleep(stream.gapMs());
            }
        } catch (IOException clientGone) {
            // 客户端取消订阅或超时后断开：正常情况
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public FakeOllama on(String path, Function<JsonNode, Reply> handler) {
        streams.remove(path);
        handlers.put(path, handler);
        return this;
    }

    /** /api/chat 固定回复 content */
    public FakeOllama chatReplies(String content) {
        return on("/api/chat", body -> new Reply(200, json(Map.of(
                "model", body.path("model").asText(),
                "message", Map.of("role", "assistant", "content", content),
                "done", true))));
    }

    /**
     * /api/chat 以流式逐块返回 pieces，最后一行 done=true。
     *
     * @param firstDelayMs 第一块之前的等待（模拟模型冷启动）
     * @param gapMs        块与块之间的间隔
     */
    public FakeOllama streamChat(long firstDelayMs, long gapMs, String... pieces) {
        List<String> lines = new ArrayList<>();
        for (String piece : pieces) {
            lines.add(json(Map.of("message", Map.of("role", "assistant", "content", piece), "done", false)));
        }
        lines.add(json(Map.of("message", Map.of("role", "assistant", "content", ""), "done", true)));
        handlers.remove("/api/chat");
        streams.put("/api/chat", new Stream(lines, firstDelayMs, gapMs));
        return this;
    }

    /** /api/embed 为每条输入返回 dims 维的向量（第 i 条的首维为 i+1，便于断言顺序） */
    public FakeOllama embedReturns(int dims) {
        return on("/api/embed", body -> {
            int n = body.path("input").isArray() ? body.path("input").size() : 1;
            float[][] vectors = new float[n][dims];
            for (int i = 0; i < n; i++) {
                vectors[i][0] = i + 1;
            }
            return new Reply(200, json(Map.of("model", body.path("model").asText(), "embeddings", vectors)));
        });
    }

    public List<JsonNode> requests() {
        return requests;
    }

    public List<String> paths() {
        return paths;
    }

    public JsonNode lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    public static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
