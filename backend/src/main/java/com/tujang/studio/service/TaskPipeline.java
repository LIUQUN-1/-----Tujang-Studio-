package com.tujang.studio.service;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.Sleeper;

/**
 * 三步执行器：素材检查 → 模拟生成 → 结果整理。
 *
 * <p>几条刻意的设计：
 * <ul>
 *   <li><b>状态迁移全靠条件 UPDATE</b>：抢不到就安静退出。重复投递、并发执行都不会把任务推进两次。</li>
 *   <li><b>失败即中断</b>：任何一步抛 {@link StepFailure} 就落 FAILED 并带着 failed_step 收工，后续步骤不再执行。</li>
 *   <li><b>查询不参与推进</b>：这里不读任何"查询请求"，只有本执行器写状态。</li>
 *   <li><b>时长可注入</b>：{@link Sleeper} 让单测不真的等待。</li>
 * </ul>
 */
@Component
public class TaskPipeline {

    private static final Logger log = LoggerFactory.getLogger(TaskPipeline.class);

    private final TaskMapper taskMapper;
    private final AssetChecker assetChecker;
    private final ResultProducer resultProducer;
    private final Sleeper sleeper;
    private final Executor executor;
    private final ObjectMapper objectMapper;

    private final long assetCheckMs;
    private final long generateMs;
    private final long finalizeMs;

    public TaskPipeline(
            TaskMapper taskMapper,
            AssetChecker assetChecker,
            ResultProducer resultProducer,
            Sleeper sleeper,
            @Qualifier("taskExecutor") Executor executor,
            ObjectMapper objectMapper,
            @Value("${app.simulation.default-durations-ms.ASSET_CHECK:800}") long assetCheckMs,
            @Value("${app.simulation.default-durations-ms.GENERATE:2500}") long generateMs,
            @Value("${app.simulation.default-durations-ms.FINALIZE:600}") long finalizeMs) {
        this.taskMapper = taskMapper;
        this.assetChecker = assetChecker;
        this.resultProducer = resultProducer;
        this.sleeper = sleeper;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.assetCheckMs = assetCheckMs;
        this.generateMs = generateMs;
        this.finalizeMs = finalizeMs;
    }

    /**
     * 投递到线程池。**不在提交请求里执行**，所以提交接口能立刻返回。
     *
     * <p>队列满时立刻把任务标成 FAILED(QUEUE_FULL)，而不是阻塞提交请求或静默丢任务——
     * 前者违背"受理即返回"，后者会让任务永远停在 PENDING。
     */
    public void submit(String taskId) {
        try {
            executor.execute(() -> execute(taskId));
        } catch (RejectedExecutionException ex) {
            log.warn("task queue full, rejecting task {}", taskId);
            taskMapper.markFailed(taskId, TaskStep.ASSET_CHECK.name(), "QUEUE_FULL",
                    "任务队列已满，请稍后重新提交");
        }
    }

    /** 同步执行。测试直接调用它，不经过线程池。 */
    public void execute(String taskId) {
        TaskRow task = taskMapper.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("pipeline got unknown task {}", taskId);
            return;
        }
        if (task.status().isTerminal()) {
            // 幂等短路：已经结束的任务不再执行
            log.debug("task {} already terminal ({}), skip", taskId, task.status());
            return;
        }
        if (taskMapper.beginProcessing(taskId, TaskStep.ASSET_CHECK.name()) == 0) {
            log.debug("task {} was claimed by another worker, skip", taskId);
            return;
        }

        SimulationProfile simulation = SimulationProfile.from(task, objectMapper);
        TaskStep current = TaskStep.ASSET_CHECK;

        try {
            List<TaskAssetRef> inputs = taskMapper.findInputs(taskId);

            pause(simulation, TaskStep.ASSET_CHECK);
            failIfInjected(simulation, TaskStep.ASSET_CHECK);
            assetChecker.check(task, inputs);

            current = TaskStep.GENERATE;
            if (!moveTo(taskId, current)) {
                return;
            }
            pause(simulation, current);
            failIfInjected(simulation, current);
            // 模拟生成：不调用任何真实模型，耗时本身就是这一步的"工作"

            current = TaskStep.FINALIZE;
            if (!moveTo(taskId, current)) {
                return;
            }
            pause(simulation, current);
            failIfInjected(simulation, current);
            ResultProducer.ProducedResult result = resultProducer.produce(task);

            if (taskMapper.markSucceeded(taskId, result.url(), result.width(), result.height()) == 0) {
                log.warn("task {} turned terminal before success was recorded", taskId);
            }
        } catch (StepFailure failure) {
            log.info("task {} failed at {}: {} {}", taskId, failure.step(), failure.code(), failure.getMessage());
            taskMapper.markFailed(taskId, failure.step().name(), failure.code(), failure.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            taskMapper.markFailed(taskId, current.name(), "INTERRUPTED", "服务关闭，任务被中断");
        } catch (RuntimeException ex) {
            log.error("task {} crashed at step {}", taskId, current, ex);
            taskMapper.markFailed(taskId, current.name(), "INTERNAL_ERROR", "处理过程中出现异常，请重新提交");
        }
    }

    /** 推进阶段；返回 false 表示任务已不在 PROCESSING（被外部终结），应立即收工。 */
    private boolean moveTo(String taskId, TaskStep step) {
        return taskMapper.advanceStep(taskId, step.name()) > 0;
    }

    private void pause(SimulationProfile simulation, TaskStep step) throws InterruptedException {
        long millis = simulation.durationOr(step, defaultDuration(step));
        if (millis > 0) {
            sleeper.sleep(millis);
        }
    }

    private void failIfInjected(SimulationProfile simulation, TaskStep step) {
        if (!simulation.shouldFail(step)) {
            return;
        }
        throw switch (step) {
            case ASSET_CHECK -> new StepFailure(step, "SIMULATED_ASSET_FAILURE", "素材检查未通过（模拟注入）");
            case GENERATE -> new StepFailure(step, "GENERATE_TIMEOUT", "生成服务响应超时（模拟注入）");
            case FINALIZE -> new StepFailure(step, "SIMULATED_FINALIZE_FAILURE", "结果整理失败（模拟注入）");
        };
    }

    private long defaultDuration(TaskStep step) {
        return switch (step) {
            case ASSET_CHECK -> assetCheckMs;
            case GENERATE -> generateMs;
            case FINALIZE -> finalizeMs;
        };
    }
}
