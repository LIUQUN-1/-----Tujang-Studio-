package com.tujang.studio.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.LinearGradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.StorageService;

/**
 * 结果整理：把**模板封面**与**用户上传的素材**合成一张成品图。
 *
 * <p>不是 AI，也没有任何外部依赖，就是 {@link Graphics2D} 拼图：
 * <ol>
 *   <li>背景：模板封面缩到很小再放大（廉价模糊），铺满画布，压一层深色；</li>
 *   <li>前景：用户素材按数量排版（1 张居中 / 2 张并排 / 3 张一大两小），
 *       每张按"填满裁切"放进圆角框，描一圈细边；</li>
 *   <li>收尾：暗角 + 右下角水印（ASCII，避免容器里没中文字体）。</li>
 * </ol>
 *
 * <p>为什么用模板封面当背景：封面本身就是按模板主题生成的抽象图，
 * 拿它做底比纯色更像"这个模板的产物"，又不需要给模板表加版式字段。
 *
 * <p>确定性：同样的输入得到同样的输出。这一点很重要——测试可以据此断言
 * "换一张素材，结果就不同"，而这正是旧实现（复制预置图）做不到的。
 */
@Component
public class CompositeResultProducer implements ResultProducer {

    private static final Logger log = LoggerFactory.getLogger(CompositeResultProducer.class);

    private static final int CANVAS_WIDTH = 1200;
    private static final int CANVAS_HEIGHT = 900;
    private static final int MARGIN = 60;
    /** 底部留给水印的空白，避免水印压到素材上。 */
    private static final int BOTTOM_BAND = 96;
    private static final int GAP = 30;
    private static final int CORNER_RADIUS = 18;
    private static final float JPEG_QUALITY = 0.92f;

    /** 背景模糊：先缩到这么大再放大，等价于一次廉价的盒式模糊。 */
    private static final int BLUR_WIDTH = 48;
    private static final int BLUR_HEIGHT = 36;

    private final StorageService storageService;
    private final TaskMapper taskMapper;
    private final Path seedDir;

    public CompositeResultProducer(
            StorageService storageService,
            TaskMapper taskMapper,
            @Value("${app.storage.seed-dir}") String seedDir) {
        this.storageService = storageService;
        this.taskMapper = taskMapper;
        this.seedDir = Paths.get(seedDir).toAbsolutePath().normalize();
    }

    @Override
    public ProducedResult produce(TaskRow task) {
        List<TaskAssetRef> inputs = taskMapper.findInputs(task.id());
        if (inputs.isEmpty()) {
            throw new StepFailure(TaskStep.FINALIZE, "NO_INPUT", "任务没有可用的素材，无法合成");
        }

        BufferedImage canvas = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            drawBackdrop(g, task);
            drawPhotos(g, task, inputs);
            drawVignette(g);
            drawBottomBand(g);
            drawWatermark(g, task);
        } finally {
            g.dispose();
        }

        Path target = storageService.dataDir()
                .resolve("results")
                .resolve(task.id() + ".jpg")
                .normalize();
        writeJpeg(canvas, target, task.id());

        return new ProducedResult("/files/results/" + task.id() + ".jpg", CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    // ---------------------------------------------------------------- 背景

    private void drawBackdrop(Graphics2D g, TaskRow task) {
        Path cover = seedDir.resolve("covers").resolve(task.templateId() + ".jpg").normalize();

        if (cover.startsWith(seedDir) && Files.isRegularFile(cover)) {
            try {
                BufferedImage image = ImageIO.read(cover.toFile());
                if (image != null) {
                    drawBlurred(g, image);
                    return;
                }
            } catch (IOException ex) {
                log.warn("read template cover failed, fallback to gradient: task={} cover={}", task.id(), cover, ex);
            }
        } else {
            log.warn("template cover missing, fallback to gradient: task={} cover={}", task.id(), cover);
        }

        // 封面缺失也要出结果：宁可背景是纯色，也不要让用户的任务失败
        g.setColor(new Color(28, 30, 34));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 缩到 48×36 再放大铺满：得到一层柔和的色块，不抢前景素材的注意力。 */
    private void drawBlurred(Graphics2D g, BufferedImage cover) {
        BufferedImage tiny = new BufferedImage(BLUR_WIDTH, BLUR_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D tg = tiny.createGraphics();
        try {
            tg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            drawCoverInto(tg, cover, 0, 0, BLUR_WIDTH, BLUR_HEIGHT);
        } finally {
            tg.dispose();
        }

        g.drawImage(tiny, 0, 0, CANVAS_WIDTH, CANVAS_HEIGHT, null);
        // 压暗，保证白色素材边框和水印有对比
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    // ---------------------------------------------------------------- 前景

    private void drawPhotos(Graphics2D g, TaskRow task, List<TaskAssetRef> inputs) {
        List<Cell> cells = layout(inputs.size());

        for (int i = 0; i < cells.size() && i < inputs.size(); i++) {
            TaskAssetRef input = inputs.get(i);
            Path file = storageService.dataDir().resolve(input.storedPath()).normalize();

            if (!file.startsWith(storageService.dataDir()) || !Files.isRegularFile(file)) {
                throw new StepFailure(TaskStep.FINALIZE, "ASSET_MISSING",
                        "第 " + (input.position() + 1) + " 张素材文件已丢失，无法合成");
            }

            BufferedImage photo;
            try {
                photo = ImageIO.read(file.toFile());
            } catch (IOException ex) {
                throw new StepFailure(TaskStep.FINALIZE, "ASSET_UNREADABLE",
                        "第 " + (input.position() + 1) + " 张素材读取失败", ex);
            }
            if (photo == null) {
                throw new StepFailure(TaskStep.FINALIZE, "ASSET_UNREADABLE",
                        "第 " + (input.position() + 1) + " 张素材不是有效图片");
            }

            Cell cell = cells.get(i);
            RoundRectangle2D frame = new RoundRectangle2D.Float(
                    cell.x(), cell.y(), cell.w(), cell.h(), CORNER_RADIUS, CORNER_RADIUS);

            g.setClip(frame);
            drawCoverInto(g, photo, cell.x(), cell.y(), cell.w(), cell.h());
            g.setClip(null);

            // 细边：让素材从背景里"浮"出来
            g.setColor(new Color(255, 255, 255, 60));
            g.setStroke(new BasicStroke(2f));
            g.draw(frame);
        }

        log.debug("composited task {} with {} photo(s)", task.id(), Math.min(inputs.size(), cells.size()));
    }

    /**
     * 按素材数量选版式。上限就是模板要求的 1~3 张；
     * 万一将来模板允许更多，用两列网格兜底，不会崩。
     */
    private static List<Cell> layout(int count) {
        int usableW = CANVAS_WIDTH - 2 * MARGIN;
        int usableH = CANVAS_HEIGHT - MARGIN - BOTTOM_BAND;

        if (count <= 1) {
            return List.of(new Cell(MARGIN, MARGIN, usableW, usableH));
        }
        if (count == 2) {
            int w = (usableW - GAP) / 2;
            return List.of(
                    new Cell(MARGIN, MARGIN, w, usableH),
                    new Cell(MARGIN + w + GAP, MARGIN, w, usableH));
        }
        if (count == 3) {
            int bigW = (int) (usableW * 0.62);
            int smallW = usableW - bigW - GAP;
            int smallH = (usableH - GAP) / 2;
            return List.of(
                    new Cell(MARGIN, MARGIN, bigW, usableH),
                    new Cell(MARGIN + bigW + GAP, MARGIN, smallW, smallH),
                    new Cell(MARGIN + bigW + GAP, MARGIN + smallH + GAP, smallW, smallH));
        }

        int cols = 2;
        int rows = (count + cols - 1) / cols;
        int w = (usableW - (cols - 1) * GAP) / cols;
        int h = (usableH - (rows - 1) * GAP) / rows;
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new Cell(
                        MARGIN + (i % cols) * (w + GAP),
                        MARGIN + (i / cols) * (h + GAP),
                        w, h))
                .toList();
    }

    private record Cell(int x, int y, int w, int h) {
    }

    // ---------------------------------------------------------------- 收尾

    /** 暗角：四角稍暗，视线自然落到中间的素材上。 */
    private void drawVignette(Graphics2D g) {
        float radius = Math.max(CANVAS_WIDTH, CANVAS_HEIGHT) * 0.75f;
        RadialGradientPaint paint = new RadialGradientPaint(
                new Point2D.Float(CANVAS_WIDTH / 2f, CANVAS_HEIGHT / 2f),
                radius,
                new float[] {0.55f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 110)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE);
        g.setPaint(paint);
        g.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 底部压一层从透明到黑的渐变，水印放在亮背景上也不会看不清。 */
    private void drawBottomBand(Graphics2D g) {
        // 只压最底部那条留白，别把素材的下沿也一起压暗
        int top = CANVAS_HEIGHT - BOTTOM_BAND;
        LinearGradientPaint paint = new LinearGradientPaint(
                new Point2D.Float(0, top),
                new Point2D.Float(0, CANVAS_HEIGHT),
                new float[] {0f, 1f},
                new Color[] {new Color(0, 0, 0, 0), new Color(0, 0, 0, 150)});
        g.setPaint(paint);
        g.fillRect(0, top, CANVAS_WIDTH, BOTTOM_BAND);
    }

    /**
     * 右下角水印。
     *
     * <p><b>只用 ASCII</b>：运行镜像是 alpine JRE，不一定装了中文字体，
     * 画中文会变成方块。字体不可用时（{@link Font#canDisplay} 为 false）直接跳过，
     * 不因为装饰性的东西让任务失败。
     */
    private void drawWatermark(Graphics2D g, TaskRow task) {
        try {
            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 20);
            if (!font.canDisplay('T')) {
                return;
            }
            g.setFont(font);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String label = "TUJANG STUDIO   #" + task.id().substring(Math.max(0, task.id().length() - 6));
            FontMetrics metrics = g.getFontMetrics();
            int x = CANVAS_WIDTH - MARGIN - metrics.stringWidth(label);
            int y = CANVAS_HEIGHT - MARGIN + 4;

            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(label, x + 1, y + 1);
            g.setColor(new Color(255, 255, 255, 170));
            g.drawString(label, x, y);
        } catch (RuntimeException ex) {
            // 字体子系统在最小化 JRE 上可能抛错；装饰失败不影响结果
            log.debug("watermark skipped: {}", ex.toString());
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 把图片按"填满目标框并居中裁切"画进去（等价 CSS {@code object-fit: cover}）。
     * 直接拉伸会把人脸压扁，这是最容易看出"假"的地方。
     */
    private static void drawCoverInto(Graphics2D g, BufferedImage image, int x, int y, int w, int h) {
        double scale = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
        int drawW = (int) Math.ceil(image.getWidth() * scale);
        int drawH = (int) Math.ceil(image.getHeight() * scale);
        int drawX = x + (w - drawW) / 2;
        int drawY = y + (h - drawH) / 2;
        g.drawImage(image, drawX, drawY, drawW, drawH, null);
    }

    private static void writeJpeg(BufferedImage image, Path target, String taskId) {
        try {
            Files.createDirectories(target.getParent());
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);

            try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
                writer.setOutput(out);
                writer.write(null, new IIOImage(image, null, null), params);
            } finally {
                writer.dispose();
            }
        } catch (IOException ex) {
            log.error("write result failed: task={} target={}", taskId, target, ex);
            throw new StepFailure(TaskStep.FINALIZE, "RESULT_WRITE_FAILED", "结果写入失败", ex);
        }
    }
}


