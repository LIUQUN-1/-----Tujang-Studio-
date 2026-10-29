package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.domain.TaskStep;

/**
 * 模拟配置的"快照 + 一次性"语义。
 *
 * <p>重点是 NEXT 用完即清、ALL 持续生效——这条如果写反，
 * 演示时会出现"失败一次之后就全都失败"，非常难排查。
 */
class SimulationRegistryTest {

    private SimulationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimulationRegistry(new ObjectMapper());
    }

    @Test
    @DisplayName("没配置时 take() 返回 null（生产环境也就是这个状态）")
    void takeReturnsNullWhenUnset() {
        assertThat(registry.take()).isNull();
    }

    @Test
    @DisplayName("NEXT：只对下一个任务生效，取走即清")
    void nextAppliesOnce() {
        registry.update(TaskStep.GENERATE, Map.of(TaskStep.GENERATE, 3000L), false);

        SimulationRegistry.Snapshot first = registry.take();
        assertThat(first).isNotNull();
        assertThat(first.failStep()).isEqualTo("GENERATE");
        assertThat(first.durationsJson()).isEqualTo("{\"GENERATE\":3000}");

        assertThat(registry.take()).isNull();
    }

    @Test
    @DisplayName("ALL：持续生效，可以反复取到同一份配置")
    void allStaysSticky() {
        registry.update(TaskStep.FINALIZE, Map.of(), true);

        assertThat(registry.take().failStep()).isEqualTo("FINALIZE");
        assertThat(registry.take().failStep()).isEqualTo("FINALIZE");
    }

    @Test
    @DisplayName("只配耗时（不注入失败）也是有效配置")
    void durationsOnlyIsValid() {
        registry.update(null, Map.of(TaskStep.ASSET_CHECK, 200L, TaskStep.GENERATE, 8000L), true);

        SimulationRegistry.Snapshot snapshot = registry.take();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.failStep()).isNull();
        // 按执行顺序输出，JSON 稳定
        assertThat(snapshot.durationsJson()).isEqualTo("{\"ASSET_CHECK\":200,\"GENERATE\":8000}");
    }

    @Test
    @DisplayName("state() 反映当前配置与 applyTo")
    void stateReflectsCurrentConfig() {
        registry.update(TaskStep.ASSET_CHECK, Map.of(TaskStep.ASSET_CHECK, 100L), true);

        SimulationRegistry.State state = registry.state();
        assertThat(state.failStep()).isEqualTo(TaskStep.ASSET_CHECK);
        assertThat(state.perStepDurations()).containsEntry(TaskStep.ASSET_CHECK, 100L);
        assertThat(state.sticky()).isTrue();
    }

    @Test
    @DisplayName("clear() 之后彻底失效")
    void clearResetsEverything() {
        registry.update(TaskStep.GENERATE, Map.of(TaskStep.GENERATE, 1L), true);
        registry.clear();

        assertThat(registry.take()).isNull();
        assertThat(registry.state().failStep()).isNull();
        assertThat(registry.state().perStepDurations()).isEmpty();
    }
}
