package com.tujang.studio.web.dto;

import java.util.List;

/**
 * 详情页（docs/api.md §2.3）：在摘要基础上追加两个制作约束。
 *
 * <p>{@code acceptedMimeTypes} 在库里是逗号分隔字符串，出接口时拆成数组，
 * 免得前端到处 split。
 */
public record TemplateDetailDto(
        String id,
        String title,
        String categoryCode,
        String categoryName,
        String coverUrl,
        String description,
        int requiredImageCount,
        List<String> acceptedMimeTypes,
        long maxFileSizeBytes) {
}
