package com.tujang.studio.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * 健康检查的独立单测：不启动 Spring 上下文、不连数据库，保证 CI 里毫秒级、零依赖。
 */
class HealthControllerTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();

    @Test
    @DisplayName("GET /api/health 返回 ok 与应用标识")
    void returnsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.app").value("tujang-studio"))
                .andExpect(jsonPath("$.time").isNotEmpty());
    }
}
