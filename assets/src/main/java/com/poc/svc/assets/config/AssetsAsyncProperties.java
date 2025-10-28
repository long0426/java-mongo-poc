package com.poc.svc.assets.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assets.async")
public class AssetsAsyncProperties {

    @Min(value = 1, message = "assets.async.thread-pool-size must be >= 1")
    private int threadPoolSize = 8;

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }
}
