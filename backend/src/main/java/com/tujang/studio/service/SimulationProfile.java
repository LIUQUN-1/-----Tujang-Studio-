package com.tujang.studio.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;

/**
 * 本任务生效的模拟配置。
 *
 * <p>从 {@code task.sim_fail_step} / {@code task.sim_durations} 读——也就是**受理时快照**。
 * 用快照而不是全局单例，是为了让并发任务各按自己的剧本推进：
 * 同一个模板下，一个成功、一个在"模拟生成"失败可以稳定同屏复现。
 *
 * <p>P3 阶段这两列都是 NULL（走默认耗时、不注入失败），P5 提供写入它们的开发接口。
 */
public record SimulationProfile(TaskStep failStep, Map<TaskStep, Long> durations) {

    private static final Logger log = LoggerFactory.getLogger(SimulationProfile.class);

    public static SimulationProfile none() {
        return new SimulationProfile(null, Map.of());
    }

    public static SimulationProfile from(TaskRow task, ObjectMapper objectMapper) {
        return new SimulationProfile(parseStep(task.simFailStep()), parseDurations(task.simDurations(), objectMapper));
    }

    public long durationOr(TaskStep step, long fallbackMillis) {
        Long configured = durations.get(step);
        return configured != null && configured >= 0 ? configured : fallbackMillis;
    }

    public boolean shouldFail(TaskStep step) {
        return failStep == step;
    }

    private static TaskStep parseStep(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TaskStep.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("ignoring unknown sim_fail_step: {}", raw);
            return null;
        }
    }

    /** {@code {"ASSET_CHECK":600,"GENERATE":3000}}；坏数据当作"没配"，不影响任务执行。 */
    private static Map<TaskStep, Long> parseDurations(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            Map<TaskStep, Long> parsed = new EnumMap<>(TaskStep.class);
            node.fields().forEachRemaining(entry -> {
                try {
                    parsed.put(TaskStep.valueOf(entry.getKey()), entry.getValue().asLong());
                } catch (IllegalArgumentException ignored) {
                    // 未知步骤名忽略
                }
            });
            return Collections.unmodifiableMap(parsed);
        } catch (Exception ex) {
            log.warn("ignoring malformed sim_durations: {}", raw);
            return Map.of();
        }
    }
}
