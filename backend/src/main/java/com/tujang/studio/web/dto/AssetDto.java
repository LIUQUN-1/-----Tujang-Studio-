package com.tujang.studio.web.dto;

import java.time.Instant;

/** 上传结果（docs/api.md §3.1）。{@code url} 是后端可直接访问的地址。 */
public record AssetDto(
        String assetId,
        String url,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        Instant createdAt) {
}
