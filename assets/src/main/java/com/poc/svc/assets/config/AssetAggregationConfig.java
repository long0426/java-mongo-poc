package com.poc.svc.assets.config;

import com.poc.svc.assets.service.AssetAggregationService;
import com.poc.svc.assets.service.CurrencyConversionService;
import com.poc.svc.assets.service.impl.DefaultCurrencyConversionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({AssetAggregationProperties.class, CurrencyConversionProperties.class})
public class AssetAggregationConfig {

    @Bean
    public AssetAggregationService.AggregationProperties aggregationProperties(AssetAggregationProperties properties) {
        return new AssetAggregationService.AggregationProperties(properties.getBaseCurrency(), properties.getTimeout());
    }

    @Bean
    public CurrencyConversionService currencyConversionService(CurrencyConversionProperties properties) {
        return new DefaultCurrencyConversionService(properties);
    }

    @Bean
    public RestTemplate assetRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(2))
                .build();
    }
}
