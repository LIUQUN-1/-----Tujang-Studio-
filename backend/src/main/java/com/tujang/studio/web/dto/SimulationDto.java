package com.tujang.studio.web.dto;

import java.util.Map;

/**
 * 本任务生效的模拟配置（仅 dev 下回显，便于演示时确认"这次跑的是哪个剧本"）。
 * 生产 profile 下为 null。
 */
public record SimulationDto(String failStep, Map<String, Long> perStepDurations) {
}
