package com.tujang.studio.domain;

import java.time.LocalDateTime;

/** 素材表的一行。文件在磁盘，这里只放元信息。 */
public record AssetRow(
        String id,
        String userId,
        String templateId,
        String storedPath,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height,
        LocalDateTime createdAt) {
}
