package com.tujang.studio.service;

import static com.tujang.studio.support.ImageFixtures.png;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStatus;
import com.tujang.studio.support.StorageService;

/**
 * 「结果整理」把预置产物复制成**这次任务专属**的结果文件。
 * 重点是：URL 按任务唯一、路径不越界、缺预置图时给出明确失败。
 */
class SeedResultProducerTest {

    @TempDir
    private Path tempDir;

    private Path dataDir;
    private Path seedDir;
    private SeedResultProducer producer;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tempDir.resolve("data");
        seedDir = tempDir.resolve("seed");
        Files.createDirectories(seedDir.resolve("results"));
        Files.write(seedDir.resolve("results/demo-postcard.jpg"), png(120, 90));

        producer = new SeedResultProducer(new StorageService(dataDir.toString()), seedDir.toString());
    }

    private static TaskRow task(String id, String templateId) {
        return new TaskRow(id, "user-1", templateId, "旅行明信片", "/c.jpg",
                TaskStatus.PROCESSING, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null, LocalDateTime.now());
    }

    @Test
    @DisplayName("复制到 data/results/{taskId}.jpg 并读出尺寸")
    void copiesSeedResultPerTask() {
        var result = producer.produce(task("task-1", "demo-postcard"));

        assertThat(result.url()).isEqualTo("/files/results/task-1.jpg");
        assertThat(result.width()).isEqualTo(120);
        assertThat(result.height()).isEqualTo(90);
        assertThat(Files.exists(dataDir.resolve("results/task-1.jpg"))).isTrue();
    }

    @Test
    @DisplayName("两个任务的结果互不覆盖（URL 按任务唯一）")
    void differentTasksGetDifferentFiles() {
        var first = producer.produce(task("task-1", "demo-postcard"));
        var second = producer.produce(task("task-2", "demo-postcard"));

        assertThat(first.url()).isNotEqualTo(second.url());
        assertThat(Files.exists(dataDir.resolve("results/task-1.jpg"))).isTrue();
        assertThat(Files.exists(dataDir.resolve("results/task-2.jpg"))).isTrue();
    }

    @Test
    @DisplayName("模板缺预置结果图时明确失败在 FINALIZE")
    void failsWhenSeedResultMissing() {
        assertThatThrownBy(() -> producer.produce(task("task-3", "no-such-template")))
                .isInstanceOf(StepFailure.class)
                .satisfies(ex -> {
                    StepFailure failure = (StepFailure) ex;
                    assertThat(failure.code()).isEqualTo("SEED_RESULT_MISSING");
                    assertThat(failure.step().name()).isEqualTo("FINALIZE");
                });
    }
}
