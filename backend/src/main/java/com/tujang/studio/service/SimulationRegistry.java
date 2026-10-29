package com.tujang.studio.service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.domain.TaskStep;

/**
 * 模拟参数的**内存态注册表**。只有 dev profile 的接口能写入它，
 * 生产环境里没有入口，{@link #take()} 永远返回 null——业务代码不必到处判 profile。
 *
 * <p>写入时机是"受理任务"，值是当时的**快照**并存进 task 行：
 * 这样并发任务各按自己的剧本跑，不会出现"改了全局配置把在跑的任务也改了"。
 *
 * <p>进程重启后配置丢失——这是开发工具，不是业务状态，可接受且已在文档里写明。
 */
@Component
public class SimulationRegistry {

    private static final Logger log = LoggerFactory.getLogger(SimulationRegistry.class);

    private final ObjectMapper objectMapper;

    private TaskStep failStep;
    private Map<TaskStep, Long> perStepDurations = Map.of();
    /** true = 一直生效（applyTo=ALL）；false = 只对下一个任务生效（applyTo=NEXT） */
    private boolean sticky;

    public SimulationRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 受理新任务时调用：取走当前配置的快照。没有配置返回 null。 */
    public synchronized Snapshot take() {
        if (failStep == null && perStepDurations.isEmpty()) {
            return null;
        }
        Snapshot snapshot = new Snapshot(
                failStep == null ? null : failStep.name(),
                toJson(perStepDurations));
        if (!sticky) {
            // 一次性配置用完即清，避免"设了一次失败，后面全失败"的意外
            clear();
        }
        return snapshot;
    }

    public synchronized void update(TaskStep failStep, Map<TaskStep, Long> durations, boolean sticky) {
        this.failStep = failStep;
        // EnumMap(Map) 无法从空 map 推断键类型，空的时候直接用 Map.of()
        this.perStepDurations = (durations == null || durations.isEmpty())
                ? Map.of()
                : Collections.unmodifiableMap(new EnumMap<>(durations));
        this.sticky = sticky;
        log.info("simulation updated: failStep={} durations={} applyTo={}",
                failStep, this.perStepDurations, sticky ? "ALL" : "NEXT");
    }

    public synchronized void clear() {
        this.failStep = null;
        this.perStepDurations = Map.of();
    }

    public synchronized State state() {
        return new State(failStep, perStepDurations, sticky);
    }

    private String toJson(Map<TaskStep, Long> durations) {
        if (durations.isEmpty()) {
            return null;
        }
        // 按执行顺序输出，JSON 稳定，便于日志与测试断言
        var ordered = new LinkedHashMap<String, Long>();
        for (TaskStep step : TaskStep.ORDER) {
            Long value = durations.get(step);
            if (value != null) {
                ordered.put(step.name(), value);
            }
        }
        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (JsonProcessingException ex) {
            log.warn("serialize sim durations failed", ex);
            return null;
        }
    }

    /** 受理时写进 task 行的快照。 */
    public record Snapshot(String failStep, String durationsJson) {
    }

    /** GET 回显。 */
    public record State(TaskStep failStep, Map<TaskStep, Long> perStepDurations, boolean sticky) {
    }
}
