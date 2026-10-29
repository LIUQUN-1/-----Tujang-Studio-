package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.tujang.studio.auth.CurrentUserProvider;
import com.tujang.studio.domain.AssetRow;
import com.tujang.studio.mapper.AssetMapper;
import com.tujang.studio.support.ImageFixtures;
import com.tujang.studio.support.StorageService;
import com.tujang.studio.web.ApiException;
import com.tujang.studio.web.dto.TemplateDetailDto;

/**
 * 上传校验的顺序与错误码，以及删除时的归属/引用保护。
 *
 * <p>不启动 Spring、不连数据库：StorageService 指向临时目录，图片字节现场生成。
 */
@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    private static final String USER = "user-1";
    private static final long TEMPLATE_MAX = 5L * 1024 * 1024;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private TemplateService templateService;

    @Mock
    private CurrentUserProvider currentUser;

    @TempDir
    private Path tempDir;

    private AssetService assetService;

    @BeforeEach
    void setUp() {
        assetService = new AssetService(
                assetMapper,
                templateService,
                new StorageService(tempDir.toString()),
                currentUser,
                "image/jpeg,image/png",
                10L * 1024 * 1024);
    }

    private static TemplateDetailDto template(List<String> mimes, long maxBytes) {
        return new TemplateDetailDto(
                "demo-postcard", "旅行明信片", "creative", "创意图片",
                "/files/seed/covers/demo-postcard.jpg", "描述", 2, mimes, maxBytes);
    }

    private static ApiException failureOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("预期抛出 ApiException，但没有");
        } catch (ApiException ex) {
            return ex;
        }
    }

    @Test
    @DisplayName("合法图片：落盘 + 入库 + 返回可访问 URL")
    void uploadsValidImage() throws IOException {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(templateService.detail("demo-postcard"))
                .thenReturn(template(List.of("image/jpeg", "image/png"), TEMPLATE_MAX));

        var file = new MockMultipartFile("file", "beach.png", "image/png",
                ImageFixtures.png(80, 60));

        var dto = assetService.upload(file, "demo-postcard");

        assertThat(dto.mimeType()).isEqualTo("image/png");
        assertThat(dto.width()).isEqualTo(80);
        assertThat(dto.height()).isEqualTo(60);
        assertThat(dto.url()).isEqualTo("/files/assets/" + dto.assetId() + ".png");

        ArgumentCaptor<AssetRow> captor = ArgumentCaptor.forClass(AssetRow.class);
        verify(assetMapper).insert(captor.capture());
        AssetRow saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(USER);
        assertThat(saved.storedPath()).isEqualTo("assets/" + dto.assetId() + ".png");
        assertThat(Files.exists(tempDir.resolve(saved.storedPath()))).isTrue();
    }

    @Test
    @DisplayName("超过模板上限：413 FILE_TOO_LARGE，且不读文件内容")
    void rejectsOversizedFile() {
        when(templateService.detail("demo-postcard"))
                .thenReturn(template(List.of("image/png"), 1024));

        var file = new MockMultipartFile("file", "big.png", "image/png", new byte[4096]);

        var ex = failureOf(() -> assetService.upload(file, "demo-postcard"));

        assertThat(ex.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(ex.code()).isEqualTo("FILE_TOO_LARGE");
        assertThat(ex.getMessage()).contains("超过上限 1 KB");
        verify(assetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("改了扩展名的文本：415 UNSUPPORTED_MEDIA_TYPE，与'图片损坏'区分开")
    void rejectsFakeImageExtension() {
        when(templateService.detail("demo-postcard"))
                .thenReturn(template(List.of("image/jpeg", "image/png"), TEMPLATE_MAX));

        var file = new MockMultipartFile("file", "fake.jpg", "image/jpeg",
                "definitely not an image".getBytes());

        var ex = failureOf(() -> assetService.upload(file, "demo-postcard"));

        assertThat(ex.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ex.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("魔数正确但内容损坏：400 FILE_NOT_DECODABLE")
    void rejectsCorruptedImage() {
        when(templateService.detail("demo-postcard"))
                .thenReturn(template(List.of("image/jpeg", "image/png"), TEMPLATE_MAX));

        byte[] broken = new byte[256];
        broken[0] = (byte) 0xFF;
        broken[1] = (byte) 0xD8;
        broken[2] = (byte) 0xFF;
        var file = new MockMultipartFile("file", "broken.jpg", "image/jpeg", broken);

        var ex = failureOf(() -> assetService.upload(file, "demo-postcard"));

        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.code()).isEqualTo("FILE_NOT_DECODABLE");
    }

    @Test
    @DisplayName("类型不符合模板：提示里带上模板允许的类型")
    void rejectsMimeNotAllowedByTemplate() throws IOException {
        when(templateService.detail("pet-sticker"))
                .thenReturn(template(List.of("image/jpeg"), TEMPLATE_MAX));

        var file = new MockMultipartFile("file", "pic.png", "image/png",
                ImageFixtures.png(20, 20));

        var ex = failureOf(() -> assetService.upload(file, "pet-sticker"));

        assertThat(ex.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ex.getMessage()).contains("image/jpeg").contains("image/png");
    }

    @Test
    @DisplayName("空文件：400 VALIDATION_FAILED")
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        var ex = failureOf(() -> assetService.upload(file, null));

        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.code()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("删除他人素材：403 ASSET_FORBIDDEN，且不动文件")
    void rejectsDeletingOthersAsset() {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(assetMapper.findById("a1")).thenReturn(Optional.of(new AssetRow(
                "a1", "someone-else", null, "assets/a1.png", "a1.png",
                "image/png", 10, 1, 1, LocalDateTime.now())));

        var ex = failureOf(() -> assetService.delete("a1"));

        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.code()).isEqualTo("ASSET_FORBIDDEN");
        verify(assetMapper, never()).deleteByIdAndUser(anyString(), anyString());
    }

    @Test
    @DisplayName("素材已被任务引用：409 ASSET_IN_USE")
    void rejectsDeletingAssetInUse() {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(assetMapper.findById("a2")).thenReturn(Optional.of(new AssetRow(
                "a2", USER, null, "assets/a2.png", "a2.png",
                "image/png", 10, 1, 1, LocalDateTime.now())));
        when(assetMapper.countTaskUsages("a2")).thenReturn(1);

        var ex = failureOf(() -> assetService.delete("a2"));

        assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.code()).isEqualTo("ASSET_IN_USE");
    }

    @Test
    @DisplayName("正常删除：删库 + 删文件")
    void deletesOwnUnusedAsset() throws IOException {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(assetMapper.findById("a3")).thenReturn(Optional.of(new AssetRow(
                "a3", USER, null, "assets/a3.png", "a3.png",
                "image/png", 10, 1, 1, LocalDateTime.now())));
        when(assetMapper.countTaskUsages("a3")).thenReturn(0);

        Path stored = tempDir.resolve("assets/a3.png");
        Files.createDirectories(stored.getParent());
        Files.write(stored, new byte[] {1, 2, 3});

        assetService.delete("a3");

        verify(assetMapper).deleteByIdAndUser("a3", USER);
        assertThat(Files.exists(stored)).isFalse();
    }
}
