package com.tujang.studio.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.support.StorageService;

/**
 * 默认的结果整理：把 {@code seed/results/{templateId}.jpg} 复制成
 * {@code data/results/{taskId}.jpg}，并读出尺寸。
 *
 * <p>为什么要"复制一份"而不是让 result_url 直接指向 seed：
 * <ul>
 *   <li>结果必须按**任务**唯一，否则两个任务的历史卡片会指向同一张图；</li>
 *   <li>seed 是只读的种子目录，产物必须落在可写的 data 卷；</li>
 *   <li>将来真做图像合成时，只需往同一路径写，接口和表结构都不用改。</li>
 * </ul>
 */
@Component
public class SeedResultProducer implements ResultProducer {

    private static final Logger log = LoggerFactory.getLogger(SeedResultProducer.class);

    private final StorageService storageService;
    private final Path seedDir;

    public SeedResultProducer(
            StorageService storageService,
            @Value("${app.storage.seed-dir}") String seedDir) {
        this.storageService = storageService;
        this.seedDir = Paths.get(seedDir).toAbsolutePath().normalize();
    }

    @Override
    public ProducedResult produce(TaskRow task) {
        Path source = seedDir.resolve("results").resolve(task.templateId() + ".jpg").normalize();
        if (!source.startsWith(seedDir) || !Files.isRegularFile(source)) {
            throw new StepFailure(TaskStep.FINALIZE, "SEED_RESULT_MISSING",
                    "该模板缺少预置结果图（seed/results/" + task.templateId() + ".jpg）");
        }

        Path target = storageService.dataDir()
                .resolve("results")
                .resolve(task.id() + ".jpg")
                .normalize();

        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("write result failed: task={} target={}", task.id(), target, ex);
            throw new StepFailure(TaskStep.FINALIZE, "RESULT_WRITE_FAILED", "结果写入失败", ex);
        }

        int width;
        int height;
        try (InputStream in = Files.newInputStream(target)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new StepFailure(TaskStep.FINALIZE, "RESULT_WRITE_FAILED", "结果图无法读取");
            }
            width = image.getWidth();
            height = image.getHeight();
        } catch (IOException ex) {
            throw new StepFailure(TaskStep.FINALIZE, "RESULT_WRITE_FAILED", "结果图无法读取", ex);
        }

        return new ProducedResult("/files/results/" + task.id() + ".jpg", width, height);
    }
}
