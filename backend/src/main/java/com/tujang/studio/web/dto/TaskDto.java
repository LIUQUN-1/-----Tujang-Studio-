package com.tujang.studio.web.dto;

import java.time.Instant;
import java.util.List;

/**
 * 任务视图（docs/api.md §4.2）。提交接口返回的也是这个形状，
 * 前端因此可以对"刚受理的任务"和"查询到的任务"用同一套渲染逻辑。
 */
public record TaskDto(
        String id,
        String status,
        String currentStep,
        String failedStep,
        String errorCode,
        String errorMessage,
        String templateId,
        String templateTitle,
        String templateCoverUrl,
        TaskResultDto result,
        List<TaskInputDto> inputs,
        SimulationDto simulation,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Instant updatedAt) {
}
