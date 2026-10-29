package com.tujang.studio.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/** 测试用图片字节生成：现场造，不依赖任何外部素材文件。 */
public final class ImageFixtures {

    private ImageFixtures() {
    }

    public static byte[] png(int width, int height) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png");
    }

    public static byte[] jpeg(int width, int height) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
