package com.tujang.studio.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 运行时文件的落盘与删除。
 *
 * <p>文件名一律由服务端生成（{@code {assetId}.{ext}}），扩展名来自探测结果而非用户文件名，
 * 因此不存在路径穿越与"上传 .jsp 当图片"的问题。
 */
@Component
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final Path dataDir;
    private final Path assetsDir;
    private final Path resultsDir;

    public StorageService(@Value("${app.storage.data-dir}") String dataDir) {
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
        this.assetsDir = this.dataDir.resolve("assets");
        this.resultsDir = this.dataDir.resolve("results");
    }

    @PostConstruct
    void ensureDirectories() {
        try {
            Files.createDirectories(assetsDir);
            Files.createDirectories(resultsDir);
            log.info("storage ready: assets={} results={}", assetsDir, resultsDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("无法创建存储目录：" + dataDir, ex);
        }
    }

    /**
     * 保存用户素材。
     *
     * @param assetId  服务端生成的主键，同时作为文件名
     * @param extension 探测出的扩展名（jpg / png）
     * @return 相对 data 目录的路径，写进 {@code asset.stored_path}
     */
    public String storeAsset(String assetId, String extension, byte[] bytes) {
        String relative = "assets/" + assetId + "." + extension;
        try {
            Files.createDirectories(assetsDir);
            Files.write(assetsDir.resolve(assetId + "." + extension), bytes);
            return relative;
        } catch (IOException ex) {
            throw new UncheckedIOException("写入素材文件失败：" + relative, ex);
        }
    }

    /** 删除素材文件；文件不存在视为成功（幂等）。 */
    public void deleteAsset(String relativePath) {
        Path target = dataDir.resolve(relativePath).normalize();
        // 只允许删除 data 目录下的文件
        if (!target.startsWith(dataDir)) {
            log.warn("refuse to delete outside data dir: {}", relativePath);
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("delete asset file failed: {}", relativePath, ex);
        }
    }

    public Path dataDir() {
        return dataDir;
    }
}
