package com.poc.svc.assets.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    public static final String ASSET_FETCH_LATENCY = "asset.fetch.latency";
    public static final String ASSET_AGGREGATION_LATENCY = "asset.aggregation.latency";
    public static final String ASSET_AGGREGATION_STAGING_WRITE_LATENCY = "asset.aggregation.staging.write.latency";
    public static final String ASSET_AGGREGATION_SUCCESS = "asset.aggregation.success";
    public static final String ASSET_AGGREGATION_FAILURE = "asset.aggregation.failure";

    @Bean
    public Timer assetFetchLatencyTimer(MeterRegistry registry) {
        return Timer.builder(ASSET_FETCH_LATENCY)
                .description("下游資產 API 呼叫耗時 (milliseconds)")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer assetAggregationLatencyTimer(MeterRegistry registry) {
        return Timer.builder(ASSET_AGGREGATION_LATENCY)
                .description("整合資產流程總耗時 (milliseconds)")
                .publishPercentileHistogram()
                .register(registry);
    }

    @Bean
    public Timer assetAggregationStagingWriteTimer(MeterRegistry registry) {
        return Timer.builder(ASSET_AGGREGATION_STAGING_WRITE_LATENCY)
                .description("Mongo staging 寫入耗時 (milliseconds)")
                .publishPercentileHistogram()
                .register(registry);
    }
}
