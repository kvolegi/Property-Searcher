package com.propertyseacher.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * High-throughput asynchronous concurrency architecture powered by Java 21 Virtual Threads (Project Loom).
 * Eliminates OS thread starvation during high-concurrency external portal scraping and alert dispatching.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConcurrencyConfig {

    @Bean(name = "applicationTaskExecutor")
    public AsyncTaskExecutor applicationTaskExecutor() {
        log.info("Initializing Java 21 Project Loom Virtual Thread TaskExecutor for non-blocking I/O operations");
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return new TaskExecutorAdapter(virtualExecutor);
    }

    @Bean(name = "virtualThreadExecutor")
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
