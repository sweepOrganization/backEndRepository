package com.sweep.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadConfig {


    @Bean
    public Executor asyncExecutor(){
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setQueueCapacity(100);
        threadPoolTaskExecutor.setMaxPoolSize(20);
        threadPoolTaskExecutor.setCorePoolSize(5);
        threadPoolTaskExecutor.setThreadNamePrefix("async-thread");
        threadPoolTaskExecutor.initialize();
        return threadPoolTaskExecutor;
    }
}
