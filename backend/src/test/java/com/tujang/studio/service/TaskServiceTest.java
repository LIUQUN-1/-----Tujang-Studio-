package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.auth.CurrentUserProvider;
import com.tujang.studio.domain.AssetRow;
import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStatus;
import com.tujang.studio.mapper.AssetMapper;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.StorageService;
import com.tujang.studio.web.ApiException;
import com.tujang.studio.web.dto.SubmitTaskRequest;
import com.tujang.studio.web.dto.TemplateDetailDto;

/**
 * 受理校验：数量、重复、归属、类型、大小、文件存在；
 * 以及"事务提交后才投递"这一步不能被绕过。
 */
@ExtendWith(MockitoExtension.class)
// 这里刻意放宽：不少用例断言"在校验早期就抛错，因此后续 stub 根本用不上"，
// 严格 stub 会把这种**正确行为**报成失败。
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    private static final String USER = "user-1";
    private static final String TEMPLATE = "demo-postcard";

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private AssetMapper assetMapper;

    @Mock
    private TemplateService templateService;

    @Mock
    private StorageService storageService;

    @Mock
    private CurrentUserProvider currentUser;

    @Mock
    private TaskPipeline pipeline;

    @Mock
    private SimulationRegistry simulationRegistry;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskMapper, assetMapper, templateService,
                storageService, currentUser, pipeline, simulationRegistry, new ObjectMapper());
    }

    private static TemplateDetailDto template() {
        return new TemplateDetailDto(TEMPLATE, "旅行明信片", "creative", "创意图片",
                "/files/seed/covers/demo-postcard.jpg", "描述", 2,
                List.of("image/jpeg", "image/png"), 5L * 1024 * 1024);
    }

    private static AssetRow asset(String id, String owner, String mime, long size) {
        return new AssetRow(id, owner, TEMPLATE, "assets/" + id + ".png", id + ".png",
                mime, size, 100, 100, LocalDateTime.now());
    }

    private static ApiException failureOf(Runnable action) {
        try {
            action.run();
            throw new AssertionError("预期抛出 ApiException，但没有");
        } catch (ApiException ex) {
            return ex;
        }
    }

    private void givenTemplateAndUser() {
        when(templateService.detail(TEMPLATE)).thenReturn(template());
        when(currentUser.currentUserId()).thenReturn(USER);
    }

    private void givenUsableAssets() {
        when(assetMapper.findById("a1")).thenReturn(Optional.of(asset("a1", USER, "image/png", 1024)));
        when(assetMapper.findById("a2")).thenReturn(Optional.of(asset("a2", USER, "image/jpeg", 2048)));
        when(storageService.exists("assets/a1.png")).thenReturn(true);
        when(storageService.exists("assets/a2.png")).thenReturn(true);
    }

    @Test
    @DisplayName("数量与模板要求不符：400，错误挂在 assetIds 字段上")
    void rejectsWrongAssetCount() {
        givenTemplateAndUser();

        var ex = failureOf(() -> taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1"))));

        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(ex.fields()).containsKey("assetIds");
        assertThat(ex.fields().get("assetIds")).contains("需要 2 张").contains("当前 1 张");
        verify(taskMapper, never()).insert(any());
    }

    @Test
    @DisplayName("同一张素材用两次：400")
    void rejectsDuplicateAssets() {
        givenTemplateAndUser();

        var ex = failureOf(() -> taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1", "a1"))));

        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("重复");
    }

    @Test
    @DisplayName("用了别人的素材：403 ASSET_FORBIDDEN")
    void rejectsForeignAsset() {
        givenTemplateAndUser();
        when(assetMapper.findById("a1")).thenReturn(Optional.of(asset("a1", "someone-else", "image/png", 1024)));

        var ex = failureOf(() -> taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1", "a2"))));

        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.code()).isEqualTo("ASSET_FORBIDDEN");
        assertThat(ex.getMessage()).contains("第 1 张");
    }

    @Test
    @DisplayName("素材文件已从磁盘消失：400，提示重新上传")
    void rejectsMissingFileOnDisk() {
        givenTemplateAndUser();
        givenUsableAssets();
        when(storageService.exists("assets/a2.png")).thenReturn(false);

        var ex = failureOf(() -> taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1", "a2"))));

        assertThat(ex.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getMessage()).contains("已丢失");
    }

    @Test
    @DisplayName("正常提交：建任务 + 按顺序建关联 + 投递执行，返回 PENDING")
    void acceptsValidSubmission() {
        givenTemplateAndUser();
        givenUsableAssets();

        var pending = new TaskRow("task-1", USER, TEMPLATE, "旅行明信片",
                "/files/seed/covers/demo-postcard.jpg", TaskStatus.PENDING,
                null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), null, null, LocalDateTime.now());
        when(taskMapper.findById(anyString())).thenReturn(Optional.of(pending));
        when(taskMapper.findInputs(anyString())).thenReturn(List.of(
                new TaskAssetRef("a1", "assets/a1.png", 0),
                new TaskAssetRef("a2", "assets/a2.png", 1)));

        var dto = taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1", "a2")));

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.currentStep()).isNull();
        assertThat(dto.result()).isNull();
        assertThat(dto.inputs()).hasSize(2);
        assertThat(dto.inputs().get(0).url()).isEqualTo("/files/assets/a1.png");

        ArgumentCaptor<TaskRow> inserted = ArgumentCaptor.forClass(TaskRow.class);
        verify(taskMapper).insert(inserted.capture());
        assertThat(inserted.getValue().userId()).isEqualTo(USER);
        assertThat(inserted.getValue().status()).isEqualTo(TaskStatus.PENDING);

        verify(taskMapper).insertTaskAsset(anyString(), eq("a1"), eq(0));
        verify(taskMapper).insertTaskAsset(anyString(), eq("a2"), eq(1));
        verify(pipeline).submit(anyString());
    }

    @Test
    @DisplayName("受理时把模拟配置快照写进任务行（P5）")
    void writesSimulationSnapshot() {
        givenTemplateAndUser();
        givenUsableAssets();
        when(simulationRegistry.take())
                .thenReturn(new SimulationRegistry.Snapshot("GENERATE", "{\"GENERATE\":3000}"));
        when(taskMapper.findById(anyString())).thenReturn(Optional.of(new TaskRow(
                "task-1", USER, TEMPLATE, "旅行明信片", "/c.jpg", TaskStatus.PENDING,
                null, null, null, null, null, null, null, "GENERATE", "{\"GENERATE\":3000}",
                LocalDateTime.now(), null, null, LocalDateTime.now())));
        when(taskMapper.findInputs(anyString())).thenReturn(List.of());

        var dto = taskService.submit(new SubmitTaskRequest(TEMPLATE, List.of("a1", "a2")));

        ArgumentCaptor<TaskRow> inserted = ArgumentCaptor.forClass(TaskRow.class);
        verify(taskMapper).insert(inserted.capture());
        assertThat(inserted.getValue().simFailStep()).isEqualTo("GENERATE");
        assertThat(inserted.getValue().simDurations()).isEqualTo("{\"GENERATE\":3000}");
        // 快照同时回显给前端，便于确认"这次跑的是哪个剧本"
        assertThat(dto.simulation()).isNotNull();
        assertThat(dto.simulation().failStep()).isEqualTo("GENERATE");
    }

    @Test
    @DisplayName("查询他人任务：403 TASK_FORBIDDEN")    void rejectsReadingOtherUsersTask() {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(taskMapper.findById("task-9")).thenReturn(Optional.of(new TaskRow(
                "task-9", "someone-else", TEMPLATE, "旅行明信片", "/c.jpg",
                TaskStatus.PROCESSING, null, null, null, null, null, null, null, null, null,
                LocalDateTime.now(), LocalDateTime.now(), null, LocalDateTime.now())));

        var ex = failureOf(() -> taskService.get("task-9"));

        assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.code()).isEqualTo("TASK_FORBIDDEN");
    }

    @Test
    @DisplayName("任务不存在：404 TASK_NOT_FOUND")
    void rejectsUnknownTask() {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(taskMapper.findById("nope")).thenReturn(Optional.empty());

        var ex = failureOf(() -> taskService.get("nope"));

        assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.code()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    @DisplayName("历史接口把 limit 收进 [1,200]")
    void clampsHistoryLimit() {
        when(currentUser.currentUserId()).thenReturn(USER);
        when(taskMapper.findByUser(USER, 200)).thenReturn(List.of());
        when(taskMapper.findByUser(USER, 1)).thenReturn(List.of());

        taskService.history(10_000);
        taskService.history(0);

        verify(taskMapper).findByUser(USER, 200);
        verify(taskMapper).findByUser(USER, 1);
    }
}
