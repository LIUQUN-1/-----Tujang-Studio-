package com.tujang.studio.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.support.ImageInspector;
import com.tujang.studio.support.StorageService;

/**
 * 第一步「素材检查」。
 *
 * <p>这一步不是摆设：提交时的校验是同步、基于数据库元数据的，
 * 而这里在后台重新确认**磁盘上的文件**还在、非空、且仍是有效图片。
 * 中间任何一刻文件被清掉（人是会手动删 data 目录的），任务会明确失败在 ASSET_CHECK，
 * 而不是带着坏数据一路跑到"生成成功"。
 */
@Component
public class AssetChecker {

    private static final Logger log = LoggerFactory.getLogger(AssetChecker.class);
    private static final int SIGNATURE_BYTES = 16;

    private final StorageService storageService;

    public AssetChecker(StorageService storageService) {
        this.storageService = storageService;
    }

    public void check(TaskRow task, List<TaskAssetRef> inputs) {
        if (inputs.isEmpty()) {
            throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_MISSING", "任务没有关联任何素材");
        }

        for (TaskAssetRef input : inputs) {
            Path file = storageService.dataDir().resolve(input.storedPath()).normalize();
            if (!file.startsWith(storageService.dataDir())) {
                throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_MISSING", "素材路径不合法");
            }
            if (!Files.isRegularFile(file)) {
                throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_MISSING",
                        "第 " + (input.position() + 1) + " 张素材文件已丢失，请重新上传");
            }
            try {
                if (Files.size(file) == 0) {
                    throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_EMPTY",
                            "第 " + (input.position() + 1) + " 张素材是空文件");
                }
                byte[] head = readHead(file);
                if (!ImageInspector.hasKnownImageSignature(head)) {
                    throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_CORRUPTED",
                            "第 " + (input.position() + 1) + " 张素材已损坏");
                }
            } catch (IOException ex) {
                log.warn("asset check io failure: task={} asset={}", task.id(), input.assetId(), ex);
                throw new StepFailure(TaskStep.ASSET_CHECK, "ASSET_CORRUPTED",
                        "第 " + (input.position() + 1) + " 张素材读取失败");
            }
        }
    }

    private static byte[] readHead(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            byte[] head = new byte[SIGNATURE_BYTES];
            int read = in.readNBytes(head, 0, SIGNATURE_BYTES);
            if (read < SIGNATURE_BYTES) {
                byte[] shorter = new byte[read];
                System.arraycopy(head, 0, shorter, 0, read);
                return shorter;
            }
            return head;
        }
    }
}
