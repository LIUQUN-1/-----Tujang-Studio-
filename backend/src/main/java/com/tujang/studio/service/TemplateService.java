package com.tujang.studio.service;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tujang.studio.domain.CategoryCount;
import com.tujang.studio.domain.TemplateRow;
import com.tujang.studio.mapper.TemplateMapper;
import com.tujang.studio.web.ApiException;
import com.tujang.studio.web.dto.CategoryDto;
import com.tujang.studio.web.dto.TemplateDetailDto;
import com.tujang.studio.web.dto.TemplateSummaryDto;

/**
 * 模板读取。只读事务：模板是种子数据，P1 不提供写接口。
 */
@Service
@Transactional(readOnly = true)
public class TemplateService {

    private final TemplateMapper templateMapper;

    public TemplateService(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public List<CategoryDto> categories() {
        return templateMapper.findCategoryCounts().stream()
                .map(TemplateService::toCategoryDto)
                .toList();
    }

    /**
     * @param category 分类 code；{@code null} 或空串表示全部。
     *                 传了不存在的 code 返回空列表，而不是报错——筛选器不该让页面崩。
     */
    public List<TemplateSummaryDto> list(String category) {
        List<TemplateRow> rows = (category == null || category.isBlank())
                ? templateMapper.findAll()
                : templateMapper.findByCategory(category.trim());
        return rows.stream().map(TemplateService::toSummaryDto).toList();
    }

    public TemplateDetailDto detail(String id) {
        return templateMapper.findById(id)
                .map(TemplateService::toDetailDto)
                .orElseThrow(() -> ApiException.notFound("TEMPLATE_NOT_FOUND", "模板不存在或已下线"));
    }

    private static CategoryDto toCategoryDto(CategoryCount row) {
        return new CategoryDto(row.code(), row.name(), row.count());
    }

    private static TemplateSummaryDto toSummaryDto(TemplateRow row) {
        return new TemplateSummaryDto(
                row.id(),
                row.title(),
                row.categoryCode(),
                row.categoryName(),
                row.coverUrl(),
                row.description(),
                row.requiredImageCount());
    }

    private static TemplateDetailDto toDetailDto(TemplateRow row) {
        return new TemplateDetailDto(
                row.id(),
                row.title(),
                row.categoryCode(),
                row.categoryName(),
                row.coverUrl(),
                row.description(),
                row.requiredImageCount(),
                splitMimeTypes(row.acceptedMimeTypes()),
                row.maxFileSizeBytes());
    }

    /** 库里存的是 "image/jpeg,image/png"，出接口时拆成数组。 */
    private static List<String> splitMimeTypes(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
