package com.tujang.studio.domain;

/**
 * 分类及其模板数。{@code count} 由 SQL 聚合得到，前端「全部」Tab 自行求和。
 */
public record CategoryCount(String code, String name, int count) {
}
