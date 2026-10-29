package com.tujang.studio.web.dto;

import java.util.Map;

/**
 * 设置模拟参数（仅 dev，docs/api.md §6.2）。
 *
 * @param failStep          在哪一步注入失败；null 或省略表示不注入
 * @param perStepDurations  覆盖单步耗时（毫秒）；缺省走 application.yml
 * @param applyTo           {@code NEXT}（默认，只影响下一个任务）或 {@code ALL}（持续生效）
 */
public record SimulationUpdateRequest(
        String failStep,
        Map<String, Long> perStepDurations,
        String applyTo) {
}
