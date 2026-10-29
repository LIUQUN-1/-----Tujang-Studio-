package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.tujang.studio.domain.CategoryCount;
import com.tujang.studio.domain.TemplateRow;
import com.tujang.studio.mapper.TemplateMapper;
import com.tujang.studio.web.ApiException;

/**
 * 模板域单测：不启动 Spring、不连数据库，只验证"行 → DTO 映射"和"不存在的模板要报 404"。
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock
    private TemplateMapper templateMapper;

    @InjectMocks
    private TemplateService templateService;

    private static TemplateRow row(String id, String category, int required) {
        return new TemplateRow(
                id, "旅行明信片", category, "创意图片",
                "/files/seed/covers/" + id + ".jpg", "描述", required,
                "image/jpeg,image/png", 5242880L);
    }

    @Test
    @DisplayName("不带分类时查全部，按分类时走 findByCategory")
    void listSwitchesQueryByCategory() {
        when(templateMapper.findAll()).thenReturn(List.of(row("a", "creative", 2)));

        var all = templateService.list(null);
        assertThat(all).hasSize(1);
        assertThat(all.get(0).requiredImageCount()).isEqualTo(2);
        verify(templateMapper, never()).findByCategory(org.mockito.ArgumentMatchers.anyString());

        when(templateMapper.findByCategory("pet")).thenReturn(List.of(row("b", "pet", 1)));
        assertThat(templateService.list("pet")).hasSize(1);
    }

    @Test
    @DisplayName("空串 / 全空白分类等同全部，不当成筛选条件")
    void blankCategoryMeansAll() {
        when(templateMapper.findAll()).thenReturn(List.of());

        assertThat(templateService.list("   ")).isEmpty();
        assertThat(templateService.list("")).isEmpty();

        verify(templateMapper, org.mockito.Mockito.times(2)).findAll();
        verify(templateMapper, never()).findByCategory(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("详情把逗号分隔的 mime 拆成数组")
    void detailSplitsMimeTypes() {
        when(templateMapper.findById("a")).thenReturn(Optional.of(row("a", "creative", 2)));

        var detail = templateService.detail("a");

        assertThat(detail.acceptedMimeTypes()).containsExactly("image/jpeg", "image/png");
        assertThat(detail.maxFileSizeBytes()).isEqualTo(5242880L);
    }

    @Test
    @DisplayName("不存在的模板抛 404 TEMPLATE_NOT_FOUND")
    void detailThrowsNotFound() {
        when(templateMapper.findById("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.detail("nope"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.status()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.code()).isEqualTo("TEMPLATE_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("分类计数按原样透出")
    void categoriesPassThrough() {
        when(templateMapper.findCategoryCounts())
                .thenReturn(List.of(new CategoryCount("creative", "创意图片", 3)));

        var categories = templateService.categories();

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).count()).isEqualTo(3);
    }
}
