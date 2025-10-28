package com.poc.svc.assets.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AssetsAsyncProperties.class)
public class AsyncConfig {

    @Bean(name = "assetAsyncExecutor")
    public Executor assetAsyncExecutor(AssetsAsyncProperties properties) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        int poolSize = properties.getThreadPoolSize();
        taskExecutor.setCorePoolSize(poolSize);
        taskExecutor.setMaxPoolSize(poolSize);
        taskExecutor.setQueueCapacity(poolSize * 2);
        taskExecutor.setThreadNamePrefix("asset-async-");
        taskExecutor.setAllowCoreThreadTimeOut(true);
        taskExecutor.initialize();
        return taskExecutor;
    }
}
