package com.zhixue.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 测试用的假 Ollama：用 JDK 自带 HttpServer 起一个本地端口，记录收到的请求体并按路径返回预设响应。
 * 让测试覆盖真实的 HTTP 序列化与解析路径，而不是只测 stub 分支。
 */
public final class FakeOllama implements AutoCloseable {

    public record Reply(int status, String body) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    private final Map<String, Function<JsonNode, Reply>> handlers = new ConcurrentHashMap<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    public FakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] raw = exchange.getRequestBody().readAllBytes();
            JsonNode body = raw.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
            paths.add(path);
            requests.add(body);
            Function<JsonNode, Reply> handler = handlers.get(path);
            Reply reply = handler == null ? new Reply(404, "{\"error\":\"not found\"}") : handler.apply(body);
            byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public FakeOllama on(String path, Function<JsonNode, Reply> handler) {
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
    }
}
