package com.tujang.studio.web;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查。前端首页在 P0 阶段用它验证"Next 代理 → Java"链路是否打通。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    public record HealthResponse(String status, String app, Instant time) {
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", "tujang-studio", Instant.now());
    }
}
