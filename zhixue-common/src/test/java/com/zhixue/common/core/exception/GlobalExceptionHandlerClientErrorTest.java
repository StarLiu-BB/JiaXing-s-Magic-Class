package com.zhixue.common.core.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 客户端错误不能报成 500。
 *
 * <p>修复前（经网关实测）：/course/detail/abc、格式错误的 JSON、不支持的请求方法、不存在的路径
 * 全部返回 {"code":500,"msg":"系统异常"} 并以 ERROR 记录完整堆栈——
 * 调用方无从知道是自己传错了，真正的服务端故障也淹没在这些噪音里。</p>
 */
class GlobalExceptionHandlerClientErrorTest {

    @RestController
    static class Probe {
        @GetMapping("/items/{id}")
        public Map<String, Object> item(@PathVariable Long id) {
            return Map.of("id", id);
        }

        @GetMapping("/search")
        public Map<String, Object> search(@RequestParam Long pageNum) {
            return Map.of("pageNum", pageNum);
        }

        @PostMapping("/items")
        public Map<String, Object> create(@RequestBody Map<String, Object> body) {
            return body;
        }

        @GetMapping("/boom")
        public Map<String, Object> boom() {
            throw new IllegalStateException("真正的服务端故障");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new Probe())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void wrongPathVariableTypeIs400() throws Exception {
        mockMvc.perform(get("/items/abc")).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void missingRequiredParameterIs400() throws Exception {
        mockMvc.perform(get("/search")).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/items").contentType(MediaType.APPLICATION_JSON).content("{bad json"))
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unsupportedMethodIs405() throws Exception {
        mockMvc.perform(delete("/items/1")).andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void realServerErrorIsStill500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("系统异常，请稍后再试"));
    }
}
