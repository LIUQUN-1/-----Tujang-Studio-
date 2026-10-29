package com.tujang.studio.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.tujang.studio.support.Sleeper;

/**
 * 异步执行的基础设施。
 *
 * <p>线程池是**单机、有界**的：核心线程数固定、队列有界、拒绝策略为 AbortPolicy。
 * 拒绝后由 {@code TaskPipeline.submit} 把任务标成 FAILED(QUEUE_FULL)——宁可明确失败，
 * 也不要让提交请求被 CallerRuns 拖住（那会破坏"受理即返回"）。
 *
 * <p>关闭时等待在跑的任务收尾（最多 10 秒），避免正常停机把任务腰斩成僵尸。
 * 进程被 kill -9 或崩溃时的恢复不在本题范围内，见 README「已知限制」。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor(
            @Value("${app.task.pool-size:4}") int poolSize,
            @Value("${app.task.queue-capacity:50}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("tujang-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    public Sleeper sleeper() {
        return Thread::sleep;
    }
}
