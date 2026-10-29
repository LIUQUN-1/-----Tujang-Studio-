package com.tujang.studio.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.service.SimulationRegistry;
import com.tujang.studio.web.dto.SimulationStateDto;
import com.tujang.studio.web.dto.SimulationUpdateRequest;

/**
 * 模拟参数控制（**仅 dev profile**，docs/api.md §6）。
 *
 * <p>生产环境不会创建这个 bean，因此路径自然 404——不需要在业务代码里散落 if (dev)。
 * 配置只存在内存里，进程重启即失效；它影响的是"下一个被受理的任务"的快照，
 * 不会回头改动已经在跑的任务。
 */
@RestController
@RequestMapping("/api/dev/simulation")
@Profile("dev")
public class DevSimulationController {

    private final SimulationRegistry registry;

    public DevSimulationController(SimulationRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public SimulationStateDto current() {
        SimulationRegistry.State state = registry.state();
        Map<String, Long> durations = new LinkedHashMap<>();
        for (TaskStep step : TaskStep.ORDER) {
            Long value = state.perStepDurations().get(step);
            if (value != null) {
                durations.put(step.name(), value);
            }
        }
        return new SimulationStateDto(
                state.failStep() == null ? null : state.failStep().name(),
                durations.isEmpty() ? null : durations,
                state.sticky() ? "ALL" : "NEXT");
    }

    @PostMapping
    public SimulationStateDto update(@RequestBody(required = false) SimulationUpdateRequest request) {
        TaskStep failStep = parseStep(request == null ? null : request.failStep());
        Map<TaskStep, Long> durations = parseDurations(request == null ? null : request.perStepDurations());
        boolean sticky = parseApplyTo(request == null ? null : request.applyTo());

        if (failStep == null && durations.isEmpty()) {
            registry.clear();
        } else {
            registry.update(failStep, durations, sticky);
        }
        return current();
    }

    private static TaskStep parseStep(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TaskStep.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.invalid("无法识别的阶段", Map.of(
                    "failStep",
                    "failStep 只能是 ASSET_CHECK / GENERATE / FINALIZE 或省略"));
        }
    }

    private static Map<TaskStep, Long> parseDurations(Map<String, Long> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<TaskStep, Long> parsed = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            TaskStep step;
            try {
                step = TaskStep.valueOf(key.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw ApiException.invalid("无法识别的阶段", Map.of(
                        "perStepDurations",
                        "阶段名只能是 ASSET_CHECK / GENERATE / FINALIZE，收到：" + key));
            }
            if (value == null || value < 0) {
                throw ApiException.invalid("耗时不能为负数", Map.of(
                        "perStepDurations", step.name() + " 的耗时必须 >= 0"));
            }
            parsed.put(step, value);
        });
        return parsed;
    }

    private static boolean parseApplyTo(String raw) {
        if (raw == null || raw.isBlank()) {
            // 默认一次性：避免"设了一次失败，后面所有任务都失败"的意外
            return false;
        }
        return switch (raw.trim().toUpperCase()) {
            case "NEXT" -> false;
            case "ALL" -> true;
            default -> throw ApiException.invalid("applyTo 取值不合法", Map.of(
                    "applyTo", "applyTo 只能是 NEXT 或 ALL"));
        };
    }
}
