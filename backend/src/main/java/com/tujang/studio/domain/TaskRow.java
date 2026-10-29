package com.tujang.studio.domain;

import java.time.LocalDateTime;

/**
 * 任务表的一行（含 join 出来的模板标题与封面，历史列表要用）。
 *
 * <p>时间一律 UTC 的 {@link LocalDateTime}，由 SQL 的 {@code UTC_TIMESTAMP(3)} 写入，
 * Java 侧不做时区换算，出接口时统一转 {@code Instant}。
 */
public record TaskRow(
        String id,
        String userId,
        String templateId,
        String templateTitle,
        String templateCoverUrl,
        TaskStatus status,
        TaskStep currentStep,
        TaskStep failedStep,
        String errorCode,
        String errorMessage,
        String resultUrl,
        Integer resultWidth,
        Integer resultHeight,
        String simFailStep,
        String simDurations,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt) {

    /**
     * 新建任务的初始行：时间与状态由 SQL 写入（{@code UTC_TIMESTAMP(3)} / 'PENDING'），
     * 这里只带出主键、归属和模拟配置快照。
     */
    public static TaskRow pending(
            String id, String userId, String templateId, String simFailStep, String simDurations) {
        return new TaskRow(id, userId, templateId, null, null,
                TaskStatus.PENDING, null, null, null, null,
                null, null, null, simFailStep, simDurations,
                null, null, null, null);
    }
}
