package com.tujang.studio.web.dto;

import java.util.Map;

/** 当前模拟配置的回显（docs/api.md §6.1）。 */
public record SimulationStateDto(
        String failStep,
        Map<String, Long> perStepDurations,
        String applyTo) {
}
