package com.tujang.studio.web.dto;

import java.time.Instant;

/** 成功后的结果（docs/api.md §4.2）。失败或进行中时为 null。 */
public record TaskResultDto(String url, Integer width, Integer height, Instant createdAt) {
}
