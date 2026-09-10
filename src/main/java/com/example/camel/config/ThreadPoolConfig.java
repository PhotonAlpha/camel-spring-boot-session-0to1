package com.example.camel.config;

import brave.baggage.*;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.CompositeTaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    @Resource
    @Lazy
    Tracer tracer;

    @Bean
    public TaskDecorator taskDecorator() {
        return new CompositeTaskDecorator(Arrays.asList( new SpanTaskDecorator(tracer),
                new ContextPropagatingTaskDecorator())); //也可以自己实现，这边使用框架解决
    }

//    @Bean(destroyMethod = "shutdown", name = "threadPoolTaskExecutor")
//    ThreadPoolTaskExecutor threadPoolTaskExecutor(TaskDecorator taskDecorator) {
//        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
//        taskExecutor.setCorePoolSize(8);
//        taskExecutor.setMaxPoolSize(8);
//        taskExecutor.setQueueCapacity(200);
//        taskExecutor.setKeepAliveSeconds(0);
//        taskExecutor.setThreadNamePrefix("my-pool-");
//
//        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
//        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
//        taskExecutor.setAwaitTerminationSeconds(60);
//        taskExecutor.setTaskDecorator(taskDecorator);
//        taskExecutor.initialize();
//        return taskExecutor;
//    }

//    @Bean
//    ExecutorService myTaskPool(@Qualifier("threadPoolTaskExecutor") ThreadPoolTaskExecutor threadPoolTaskExecutor) {
//        return threadPoolTaskExecutor.getThreadPoolExecutor();
//    }

    @Bean(destroyMethod = "shutdown", name = "myTaskPool")
    ExecutorService myTaskPool(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(8);
        taskExecutor.setMaxPoolSize(8);
        taskExecutor.setQueueCapacity(200);
        taskExecutor.setKeepAliveSeconds(0);
        taskExecutor.setThreadNamePrefix("my-pool-");

        taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(60);
        taskExecutor.setTaskDecorator(taskDecorator);
        taskExecutor.initialize();
        return taskExecutor.getThreadPoolExecutor();
    }

    @Bean
    CorrelationScopeDecorator.Builder mdcCorrelationScopeDecoratorBuilder(
            ObjectProvider<CorrelationScopeCustomizer> correlationScopeCustomizers) {
        CorrelationScopeDecorator.Builder builder = MyMDCScopeDecorator.newBuilder();
        builder.add(CorrelationScopeConfig.SingleCorrelationField.create(MyMDCScopeDecorator.CORRELATION_FIELD));
        correlationScopeCustomizers.orderedStream().forEach((customizer) ->
                customizer
                        .customize(builder));

        return builder;
    }
    @Bean
    public BaggagePropagationCustomizer baggagePropagationCustomizer() {
        return builder -> builder.add(BaggagePropagationConfig.SingleBaggageField.local(MyMDCScopeDecorator.CORRELATION_FIELD));

    }
}
