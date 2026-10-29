package com.tujang.studio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tujang.studio.domain.TaskAssetRef;
import com.tujang.studio.domain.TaskRow;
import com.tujang.studio.domain.TaskStatus;
import com.tujang.studio.domain.TaskStep;
import com.tujang.studio.mapper.TaskMapper;
import com.tujang.studio.support.Sleeper;

/**
 * 执行器行为：步骤顺序、失败中断、幂等短路、队列满的处理。
 *
 * <p>{@link Sleeper} 用空实现，所以测试不会真的等；断言重点在**调用顺序**与
 * **失败后哪些东西没有被调用**——后者才是"失败即中断"的真正证据。
 */
@ExtendWith(MockitoExtension.class)
class TaskPipelineTest {

    private static final String TASK_ID = "task-1";

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private AssetChecker assetChecker;

    @Mock
    private ResultProducer resultProducer;

    @Mock
    private Sleeper sleeper;

    @Mock
    private Executor executor;

    private TaskPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new TaskPipeline(
                taskMapper, assetChecker, resultProducer, sleeper, executor,
                new ObjectMapper(), 10, 20, 30);
    }

    private static TaskRow task(TaskStatus status, String simFailStep) {
        return new TaskRow(TASK_ID, "user-1", "demo-postcard", "旅行明信片", "/c.jpg",
                status, null, null, null, null,
                null, null, null, simFailStep, null,
                LocalDateTime.now(), null, null, LocalDateTime.now());
    }

    private void givenPendingTask(String simFailStep) {
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.PENDING, simFailStep)));
        lenient().when(taskMapper.beginProcessing(anyString(), anyString())).thenReturn(1);
        lenient().when(taskMapper.advanceStep(anyString(), anyString())).thenReturn(1);
        lenient().when(taskMapper.markSucceeded(anyString(), anyString(), anyInt(), anyInt())).thenReturn(1);
        lenient().when(taskMapper.findInputs(TASK_ID))
                .thenReturn(List.of(new TaskAssetRef("asset-1", "assets/a.jpg", 0)));
    }

    @Test
    @DisplayName("三步按顺序执行：素材检查 → 模拟生成 → 结果整理 → 置成功")
    void runsStepsInOrder() throws InterruptedException {
        givenPendingTask(null);
        when(resultProducer.produce(any())).thenReturn(new ResultProducer.ProducedResult("/files/results/x.jpg", 1200, 900));

        pipeline.execute(TASK_ID);

        InOrder order = inOrder(taskMapper, assetChecker, resultProducer, sleeper);
        order.verify(taskMapper).beginProcessing(TASK_ID, "ASSET_CHECK");
        order.verify(sleeper).sleep(10L);
        order.verify(assetChecker).check(any(), any());
        order.verify(taskMapper).advanceStep(TASK_ID, "GENERATE");
        order.verify(sleeper).sleep(20L);
        order.verify(taskMapper).advanceStep(TASK_ID, "FINALIZE");
        order.verify(sleeper).sleep(30L);
        order.verify(resultProducer).produce(any());
        order.verify(taskMapper).markSucceeded(TASK_ID, "/files/results/x.jpg", 1200, 900);
        verify(taskMapper, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("素材检查失败：立刻落 FAILED(ASSET_CHECK)，后续步骤一次都不执行")
    void stopsWhenAssetCheckFails() {
        givenPendingTask(null);
        doThrow(new StepFailure(TaskStep.ASSET_CHECK, "ASSET_MISSING", "第 1 张素材文件已丢失"))
                .when(assetChecker).check(any(), any());

        pipeline.execute(TASK_ID);

        verify(taskMapper).markFailed(TASK_ID, "ASSET_CHECK", "ASSET_MISSING", "第 1 张素材文件已丢失");
        verify(taskMapper, never()).advanceStep(anyString(), eq("GENERATE"));
        verify(taskMapper, never()).advanceStep(anyString(), eq("FINALIZE"));
        verify(resultProducer, never()).produce(any());
        verify(taskMapper, never()).markSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("模拟生成失败：不进入结果整理，也没有结果地址")
    void stopsWhenGenerateFails() {
        givenPendingTask("GENERATE");

        pipeline.execute(TASK_ID);

        verify(taskMapper).markFailed(eq(TASK_ID), eq("GENERATE"), eq("GENERATE_TIMEOUT"), anyString());
        verify(assetChecker).check(any(), any());
        verify(taskMapper, never()).advanceStep(anyString(), eq("FINALIZE"));
        verify(resultProducer, never()).produce(any());
        verify(taskMapper, never()).markSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("结果整理失败：任务失败且不写结果")
    void stopsWhenFinalizeFails() {
        givenPendingTask(null);
        when(resultProducer.produce(any()))
                .thenThrow(new StepFailure(TaskStep.FINALIZE, "RESULT_WRITE_FAILED", "结果写入失败"));

        pipeline.execute(TASK_ID);

        verify(taskMapper).markFailed(eq(TASK_ID), eq("FINALIZE"), eq("RESULT_WRITE_FAILED"), anyString());
        verify(taskMapper, never()).markSucceeded(any(), any(), any(), any());
    }

    @Test
    @DisplayName("已终态的任务直接跳过，不重新执行（终态不可回退）")
    void skipsTerminalTask() {
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.SUCCEEDED, null)));

        pipeline.execute(TASK_ID);

        verify(taskMapper, never()).beginProcessing(anyString(), anyString());
        verifyNoInteractions(assetChecker, resultProducer);
    }

    @Test
    @DisplayName("抢不到 PENDING→PROCESSING 就安静退出（重复投递不会跑两遍）")
    void exitsWhenClaimLost() {
        when(taskMapper.findById(TASK_ID)).thenReturn(Optional.of(task(TaskStatus.PENDING, null)));
        when(taskMapper.beginProcessing(anyString(), anyString())).thenReturn(0);

        pipeline.execute(TASK_ID);

        verifyNoInteractions(assetChecker, resultProducer);
        verify(taskMapper, never()).markFailed(any(), any(), any(), any());
    }

    @Test
    @DisplayName("队列满：直接把任务标成 FAILED(QUEUE_FULL)，不阻塞调用方")
    void marksFailedWhenQueueIsFull() {
        String id = TASK_ID;
        doThrow(new RejectedExecutionException("full")).when(executor).execute(any());

        pipeline.submit(id);

        verify(taskMapper).markFailed(eq(id), eq("ASSET_CHECK"), eq("QUEUE_FULL"), anyString());
    }

    @Test
    @DisplayName("提交成功后由线程池执行，提交方法本身立即返回")
    void submitDelegatesToExecutor() {
        pipeline.submit(TASK_ID);

        verify(executor).execute(any());
        assertThat(TASK_ID).isNotBlank();
    }
}
