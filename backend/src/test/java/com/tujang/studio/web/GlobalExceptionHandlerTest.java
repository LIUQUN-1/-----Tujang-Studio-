package com.tujang.studio.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.tujang.studio.web.dto.LoginRequest;

/**
 * 统一错误出口。这里只验"客户端错误不会被算成 500"——
 * 这类问题在容器里第一次看到时表现为"服务器开小差了"，很容易被误判成后端挂了。
 */
class GlobalExceptionHandlerTest {

    @RestController
    static class Probe {
        @PostMapping(value = "/probe", consumes = MediaType.APPLICATION_JSON_VALUE)
        void body(@RequestBody LoginRequest request) {
            // 只为触发反序列化
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new Probe())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    @DisplayName("请求体不是合法 JSON：400 VALIDATION_FAILED，而不是 500")
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("请求体不是合法的 JSON"));
    }

    @Test
    @DisplayName("JSON 合法但字段类型不对：同样 400")
    void wrongFieldTypeIsBadRequest() throws Exception {
        // 注意不能用 123：Jackson 默认会把数字转成 String，那样反而会通过
        mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"username\":{\"a\":1}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
