package com.tujang.studio.web.dto;

/** 列表卡片所需的最小字段（docs/api.md §2.2）。 */
public record TemplateSummaryDto(
        String id,
        String title,
        String categoryCode,
        String categoryName,
        String coverUrl,
        String description,
        int requiredImageCount) {
}
