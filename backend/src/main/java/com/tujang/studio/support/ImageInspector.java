package com.tujang.studio.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * 图片探测：只信任**文件内容**，不信任客户端声明的 Content-Type 与扩展名。
 *
 * <p>先用魔数判断"这到底是不是图片、是哪种"，再用 {@link ImageIO} 真正解码一次
 * 拿到宽高——解码失败说明文件损坏或只是改了扩展名的假图，这正是 R-03 要挡的情况。
 */
public final class ImageInspector {

    /** 探测结果。{@code mimeType} 由魔数推导，不是客户端声明的值。 */
    public record ImageInfo(String mimeType, String extension, int width, int height) {
    }

    private ImageInspector() {
    }

    public static Optional<ImageInfo> inspect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return Optional.empty();
        }

        String mime;
        String ext;
        if (isJpeg(bytes)) {
            mime = "image/jpeg";
            ext = "jpg";
        } else if (isPng(bytes)) {
            mime = "image/png";
            ext = "png";
        } else {
            return Optional.empty();
        }

        // 魔数对了才能走到这里；解码失败说明内容损坏
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return Optional.empty();
            }
            return Optional.of(new ImageInfo(mime, ext, image.getWidth(), image.getHeight()));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** 魔数是否为可识别的图片格式（用于区分"类型不支持"和"图片损坏"）。 */
    public static boolean hasKnownImageSignature(byte[] bytes) {
        return bytes != null && (isJpeg(bytes) || isPng(bytes));
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }
}
