package com.tujang.studio.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.auth.CurrentUserProvider;
import com.tujang.studio.domain.AssetRow;
import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.mapper.AssetMapper;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.StorageService;
import com.tujang.studio.support.Ulid;
import com.tujang.studio.web.ApiException;
import com.tujang.studio.web.dto.SimulationDto;
import com.tujang.studio.web.dto.SubmitTaskRequest;
import com.tujang.studio.web.dto.TaskDto;
import com.tujang.studio.web.dto.TaskInputDto;
import com.tujang.studio.web.dto.TaskResultDto;
import com.tujang.studio.web.dto.TemplateDetailDto;

/**
 * 任务的外围逻辑：受理校验、建记录、投递、查询。
 *
 * <p>受理只做**同步、便宜**的检查（数量、归属、类型、大小、文件还在）；
 * 真正耗时的检查与生成本身都在 {@link TaskPipeline} 里异步做。
 */
@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final int MAX_HISTORY = 200;

    private final TaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final TemplateService templateService;
    private final StorageService storageService;
    private final CurrentUserProvider currentUser;
    private final TaskPipeline pipeline;
    private final SimulationRegistry simulationRegistry;
    private final ObjectMapper objectMapper;

    public TaskService(
            TaskMapper taskMapper,
            AssetMapper assetMapper,
            TemplateService templateService,
            StorageService storageService,
            CurrentUserProvider currentUser,
            TaskPipeline pipeline,
            SimulationRegistry simulationRegistry,
            ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.assetMapper = assetMapper;
        this.templateService = templateService;
        this.storageService = storageService;
        this.currentUser = currentUser;
        this.pipeline = pipeline;
        this.simulationRegistry = simulationRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskDto submit(SubmitTaskRequest request) {
        String templateId = request == null || request.templateId() == null ? null : request.templateId().trim();
        if (templateId == null || templateId.isEmpty()) {
            throw ApiException.invalid("缺少模板", Map.of("templateId", "请指定要使用的模板"));
        }

        List<String> assetIds = request.assetIds() == null ? List.of() : List.copyOf(request.assetIds());
        TemplateDetailDto template = templateService.detail(templateId);
        validateAssets(template, assetIds);

        String userId = currentUser.currentUserId();
        String taskId = Ulid.next();

        // 模拟配置在受理时快照：并发任务各按自己的剧本跑（P5）。生产环境 take() 恒为 null。
        SimulationRegistry.Snapshot simulation = simulationRegistry.take();

        taskMapper.insert(TaskRow.pending(
                taskId,
                userId,
                template.id(),
                simulation == null ? null : simulation.failStep(),
                simulation == null ? null : simulation.durationsJson()));
        for (int i = 0; i < assetIds.size(); i++) {
            taskMapper.insertTaskAsset(taskId, assetIds.get(i), i);
        }

        TaskRow saved = taskMapper.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("任务创建后读不到：" + taskId));

        // 必须等事务提交后再投递：否则后台线程可能查不到这条还没提交的记录
        dispatchAfterCommit(taskId);

        log.info("task accepted: id={} template={} owner={} assets={}", taskId, template.id(), userId, assetIds.size());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public TaskDto get(String taskId) {
        TaskRow task = taskMapper.findById(taskId)
                .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "任务不存在"));
        if (!task.userId().equals(currentUser.currentUserId())) {
            throw ApiException.forbidden("TASK_FORBIDDEN", "无权查看该任务");
        }
        return toDto(task);
    }

    @Transactional(readOnly = true)
    public List<TaskDto> history(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
        return taskMapper.findByUser(currentUser.currentUserId(), safeLimit).stream()
                .map(this::toDto)
                .toList();
    }

    // ------------------------------------------------------------------

    private void validateAssets(TemplateDetailDto template, List<String> assetIds) {
        int required = template.requiredImageCount();
        if (assetIds.size() != required) {
            throw ApiException.invalid("素材数量与模板要求不一致", Map.of(
                    "assetIds", "该模板需要 %d 张素材，当前 %d 张".formatted(required, assetIds.size())));
        }
        if (assetIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw ApiException.invalid("素材编号不能为空", Map.of("assetIds", "存在空的素材编号"));
        }
        if (new HashSet<>(assetIds).size() != assetIds.size()) {
            throw ApiException.invalid("同一张素材不能重复使用", Map.of("assetIds", "存在重复的素材"));
        }

        String userId = currentUser.currentUserId();
        for (int i = 0; i < assetIds.size(); i++) {
            String assetId = assetIds.get(i);
            int position = i + 1;

            AssetRow asset = assetMapper.findById(assetId).orElseThrow(() -> ApiException.invalid(
                    "素材不存在", Map.of("assetIds", "第 %d 张素材不存在或已被清理".formatted(position))));

            if (!asset.userId().equals(userId)) {
                throw ApiException.forbidden("ASSET_FORBIDDEN", "第 %d 张素材不属于当前用户".formatted(position));
            }
            if (!template.acceptedMimeTypes().isEmpty()
                    && !template.acceptedMimeTypes().contains(asset.mimeType())) {
                throw ApiException.invalid("素材类型不符合模板要求", Map.of("assetIds",
                        "第 %d 张素材类型为 %s，模板只接受 %s"
                                .formatted(position, asset.mimeType(), String.join("、", template.acceptedMimeTypes()))));
            }
            if (template.maxFileSizeBytes() > 0 && asset.sizeBytes() > template.maxFileSizeBytes()) {
                throw ApiException.invalid("素材超过模板大小上限", Map.of("assetIds",
                        "第 %d 张素材超过单张上限".formatted(position)));
            }
            if (!storageService.exists(asset.storedPath())) {
                throw ApiException.invalid("素材文件已丢失，请重新上传", Map.of("assetIds",
                        "第 %d 张素材文件已丢失".formatted(position)));
            }
        }
    }

    private void dispatchAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pipeline.submit(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pipeline.submit(taskId);
            }
        });
    }

    private TaskDto toDto(TaskRow task) {
        List<TaskInputDto> inputs = taskMapper.findInputs(task.id()).stream()
                .map(ref -> new TaskInputDto(ref.assetId(), ref.url()))
                .toList();

        TaskResultDto result = task.resultUrl() == null
                ? null
                : new TaskResultDto(task.resultUrl(), task.resultWidth(), task.resultHeight(),
                        instant(task.finishedAt()));

        return new TaskDto(
                task.id(),
                task.status().name(),
                task.currentStep() == null ? null : task.currentStep().name(),
                task.failedStep() == null ? null : task.failedStep().name(),
                task.errorCode(),
                task.errorMessage(),
                task.templateId(),
                task.templateTitle(),
                task.templateCoverUrl(),
                result,
                inputs,
                simulationOf(task),
                instant(task.createdAt()),
                instant(task.startedAt()),
                instant(task.finishedAt()),
                instant(task.updatedAt()));
    }

    private SimulationDto simulationOf(TaskRow task) {
        boolean hasFail = task.simFailStep() != null && !task.simFailStep().isBlank();
        boolean hasDurations = task.simDurations() != null && !task.simDurations().isBlank();
        if (!hasFail && !hasDurations) {
            return null;
        }
        SimulationProfile profile = SimulationProfile.from(task, objectMapper);
        Map<String, Long> durations = new LinkedHashMap<>();
        profile.durations().forEach((step, ms) -> durations.put(step.name(), ms));
        return new SimulationDto(
                profile.failStep() == null ? null : profile.failStep().name(),
                durations.isEmpty() ? null : durations);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** 供日志/调试：任务当前所处步骤的可读名。 */
    static String describeStep(TaskStep step) {
        return step == null ? "-" : step.label();
    }
}
