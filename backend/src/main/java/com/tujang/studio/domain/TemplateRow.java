package com.tujang.studio.domain;

/**
 * 模板查询行：一个 SQL 结果对应一行，列表与详情共用。
 *
 * <p>用 record 映射（MyBatis 的 argNameBasedConstructorAutoMapping + 编译期 -parameters），
 * 省掉一层 setter 样板。列名经 mapUnderscoreToCamelCase 转成驼峰后与构造参数名匹配。
 *
 * <p>列表接口不需要 {@code acceptedMimeTypes} / {@code maxFileSizeBytes}，
 * 两个查询仍然都查全字段——几个短列，换来一份 SQL 和一种行类型，更不容易出错。
 */
public record TemplateRow(
        String id,
        String title,
        String categoryCode,
        String categoryName,
        String coverUrl,
        String description,
        int requiredImageCount,
        String acceptedMimeTypes,
        long maxFileSizeBytes) {
}
