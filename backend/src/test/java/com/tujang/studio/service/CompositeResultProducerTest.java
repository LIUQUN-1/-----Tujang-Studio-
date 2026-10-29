package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.StorageService;

/**
 * 结果合成：模板封面 + 用户素材。
 *
 * <p>最要紧的一条断言是「不同素材 → 不同结果图」——旧实现（复制预置图）做不到，
 * 而这正是"素材有没有真的参与制作"的判断标准。
 */
@ExtendWith(MockitoExtension.class)
class CompositeResultProducerTest {

    private static final String TEMPLATE = "demo-postcard";

    @TempDir
    Path tmp;

    @Mock
    private TaskMapper taskMapper;

    private Path dataDir;
    private Path seedDir;
    private CompositeResultProducer producer;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = tmp.resolve("data");
        seedDir = tmp.resolve("seed");
        Files.createDirectories(dataDir.resolve("assets"));
        Files.createDirectories(dataDir.resolve("results"));
        Files.createDirectories(seedDir.resolve("covers"));

        StorageService storage = new StorageService(dataDir.toString());
        producer = new CompositeResultProducer(storage, taskMapper, seedDir.toString());
    }

    @Test
    @DisplayName("合成成功：返回可访问地址与画布尺寸，文件真实落盘")
    void producesCanvas() throws IOException {
        writeCover(new Color(30, 60, 90));
        List<TaskAssetRef> inputs = List.of(asset("a1", new Color(200, 80, 60), 0));
        when(taskMapper.findInputs("task-1")).thenReturn(inputs);

        ResultProducer.ProducedResult result = producer.produce(task("task-1"));

        assertThat(result.url()).isEqualTo("/files/results/task-1.jpg");
        assertThat(result.width()).isEqualTo(1200);
        assertThat(result.height()).isEqualTo(900);

        BufferedImage written = ImageIO.read(dataDir.resolve("results/task-1.jpg").toFile());
        assertThat(written).isNotNull();
        assertThat(written.getWidth()).isEqualTo(1200);
        assertThat(written.getHeight()).isEqualTo(900);
    }

    @Test
    @DisplayName("换一张素材，结果图就不同（素材真的参与了合成）")
    void differentInputsProduceDifferentImages() throws IOException {
        writeCover(new Color(30, 60, 90));
        when(taskMapper.findInputs("task-1"))
                .thenReturn(List.of(asset("a1", new Color(220, 60, 60), 0)));
        when(taskMapper.findInputs("task-2"))
                .thenReturn(List.of(asset("a2", new Color(60, 220, 60), 0)));

        producer.produce(task("task-1"));
        producer.produce(task("task-2"));

        byte[] first = Files.readAllBytes(dataDir.resolve("results/task-1.jpg"));
        byte[] second = Files.readAllBytes(dataDir.resolve("results/task-2.jpg"));
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("同样的输入得到同样的结果（确定性，便于断言与排查）")
    void sameInputsProduceSameImage() throws IOException {
        writeCover(new Color(30, 60, 90));
        when(taskMapper.findInputs("task-1")).thenReturn(List.of(asset("a1", new Color(200, 80, 60), 0)));

        producer.produce(task("task-1"));
        byte[] first = Files.readAllBytes(dataDir.resolve("results/task-1.jpg"));
        producer.produce(task("task-1"));
        byte[] again = Files.readAllBytes(dataDir.resolve("results/task-1.jpg"));

        assertThat(first).isEqualTo(again);
    }

    @Test
    @DisplayName("2 张与 3 张素材都能出图（版式不同，但不失败）")
    void handlesMultipleInputs() throws IOException {
        writeCover(new Color(30, 60, 90));
        when(taskMapper.findInputs("task-2"))
                .thenReturn(List.of(asset("a1", Color.RED, 0), asset("a2", Color.BLUE, 1)));
        when(taskMapper.findInputs("task-3"))
                .thenReturn(List.of(asset("a1", Color.RED, 0), asset("a2", Color.BLUE, 1), asset("a3", Color.GREEN, 2)));

        producer.produce(task("task-2"));
        producer.produce(task("task-3"));

        assertThat(dataDir.resolve("results/task-2.jpg")).exists();
        assertThat(dataDir.resolve("results/task-3.jpg")).exists();
    }

    @Test
    @DisplayName("素材文件在编排前被删掉：失败在 FINALIZE，原因是可读的")
    void missingAssetFailsWithReason() throws IOException {
        writeCover(new Color(30, 60, 90));
        TaskAssetRef ref = new TaskAssetRef("a1", "assets/gone.jpg", 0);
        when(taskMapper.findInputs("task-1")).thenReturn(List.of(ref));

        assertThatThrownBy(() -> producer.produce(task("task-1")))
                .isInstanceOfSatisfying(StepFailure.class, failure -> {
                    assertThat(failure.step()).isEqualTo(TaskStep.FINALIZE);
                    assertThat(failure.code()).isEqualTo("ASSET_MISSING");
                    assertThat(failure.getMessage()).contains("第 1 张素材");
                });
        assertThat(dataDir.resolve("results/task-1.jpg")).doesNotExist();
    }

    @Test
    @DisplayName("任务没有素材：直接失败，不生成空图")
    void noInputsFails() {
        when(taskMapper.findInputs("task-1")).thenReturn(List.of());

        assertThatThrownBy(() -> producer.produce(task("task-1")))
                .isInstanceOfSatisfying(StepFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo("NO_INPUT"));
    }

    @Test
    @DisplayName("模板封面缺失：退化成纯色背景，任务照常成功")
    void missingCoverStillSucceeds() throws IOException {
        // 不写 covers/ 下的文件
        when(taskMapper.findInputs("task-1")).thenReturn(List.of(asset("a1", Color.ORANGE, 0)));

        ResultProducer.ProducedResult result = producer.produce(task("task-1"));

        assertThat(result.url()).isEqualTo("/files/results/task-1.jpg");
        assertThat(dataDir.resolve("results/task-1.jpg")).exists();
    }

    // ---------------------------------------------------------------- 夹具

    private TaskRow task(String id) {
        return TaskRow.pending(id, "user-1", TEMPLATE, null, null);
    }

    private TaskAssetRef asset(String assetId, Color color, int position) throws IOException {
        String relative = "assets/" + assetId + ".jpg";
        Path file = dataDir.resolve(relative);
        writeJpeg(file, color, 640, 480);
        return new TaskAssetRef(assetId, relative, position);
    }

    private void writeCover(Color color) throws IOException {
        writeJpeg(seedDir.resolve("covers").resolve(TEMPLATE + ".jpg"), color, 800, 1000);
    }

    private static void writeJpeg(Path target, Color color, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(color);
            g.fillRect(0, 0, width, height);
            // 画一条对角线，避免纯色图在 JPEG 压缩后差异过小
            g.setColor(color.darker());
            g.drawLine(0, 0, width, height);
        } finally {
            g.dispose();
        }
        Files.createDirectories(target.getParent());
        ImageIO.write(image, "jpg", target.toFile());
    }

}


