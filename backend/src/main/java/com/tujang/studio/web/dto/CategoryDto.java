package com.tujang.studio.web.dto;

/** 分类 Tab。{@code count} 为该分类下的模板数。 */
public record CategoryDto(String code, String name, int count) {
}
