package com.poc.svc.assets.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "assets.mongo.write-retry")
public class MongoWriteRetryProperties {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofMillis(100);

    @Min(1)
    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @NotNull
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration backoff = DEFAULT_BACKOFF;

    public static MongoWriteRetryProperties defaults() {
        return new MongoWriteRetryProperties();
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
    }

    public Duration getBackoff() {
        return backoff;
    }

    public void setBackoff(Duration backoff) {
        Objects.requireNonNull(backoff, "backoff must not be null");
        if (backoff.isNegative()) {
            throw new IllegalArgumentException("backoff must not be negative");
        }
        this.backoff = backoff;
    }
}
