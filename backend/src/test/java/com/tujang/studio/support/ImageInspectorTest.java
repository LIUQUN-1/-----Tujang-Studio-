package com.tujang.studio.support;

import static com.tujang.studio.support.ImageFixtures.jpeg;
import static com.tujang.studio.support.ImageFixtures.png;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 图片探测：只认文件内容。
 * 这里覆盖 R-03 最容易被绕过的一条——"改了扩展名的假图"。
 */
class ImageInspectorTest {

    @Test
    @DisplayName("识别 PNG 并读出宽高")
    void detectsPng() throws IOException {
        var info = ImageInspector.inspect(png(120, 90));

        assertThat(info).isPresent();
        assertThat(info.get().mimeType()).isEqualTo("image/png");
        assertThat(info.get().extension()).isEqualTo("png");
        assertThat(info.get().width()).isEqualTo(120);
        assertThat(info.get().height()).isEqualTo(90);
    }

    @Test
    @DisplayName("识别 JPEG 并读出宽高")
    void detectsJpeg() throws IOException {
        var info = ImageInspector.inspect(jpeg(64, 32));

        assertThat(info).isPresent();
        assertThat(info.get().mimeType()).isEqualTo("image/jpeg");
        assertThat(info.get().extension()).isEqualTo("jpg");
        assertThat(info.get().width()).isEqualTo(64);
    }

    @Test
    @DisplayName("纯文本改成 .jpg：认不出类型")
    void rejectsTextPretendingToBeImage() {
        byte[] text = "not an image, just text".getBytes();

        assertThat(ImageInspector.inspect(text)).isEmpty();
        assertThat(ImageInspector.hasKnownImageSignature(text)).isFalse();
    }

    @Test
    @DisplayName("魔数正确但内容损坏：签名认得，解码失败")
    void detectsBrokenFileWithValidSignature() {
        byte[] broken = new byte[128];
        broken[0] = (byte) 0xFF;
        broken[1] = (byte) 0xD8;
        broken[2] = (byte) 0xFF;

        assertThat(ImageInspector.hasKnownImageSignature(broken)).isTrue();
        assertThat(ImageInspector.inspect(broken)).isEmpty();
    }

    @Test
    @DisplayName("空或过短的字节流直接判否，不抛异常")
    void handlesTooShortInput() {
        assertThat(ImageInspector.inspect(new byte[0])).isEmpty();
        assertThat(ImageInspector.inspect(null)).isEmpty();
    }
}