package com.dxy.homework.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    public static final String GRADING_EXECUTOR = "gradingExecutor";

    /**
     * 批改任务线程池。
     *
     * <p>拒绝策略用 AbortPolicy 而不是 CallerRuns：队列满时必须让调用方立刻感知到，
     * 才能标记任务失败并返还额度；如果让提交请求的线程自己跑批改，会把 HTTP 请求拖住几十秒。
     */
    @Bean(GRADING_EXECUTOR)
    public Executor gradingExecutor(GradingProperties properties) {
        GradingProperties.Executor config = properties.executor();

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.coreSize());
        executor.setMaxPoolSize(config.maxSize());
        executor.setQueueCapacity(config.queueCapacity());
        executor.setThreadNamePrefix("grading-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅停机：等正在跑的批改收尾，避免任务卡在"批改中"状态
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
