package com.poc.svc.insurance.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI insuranceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Insurance Asset API")
                        .version("v1")
                        .description("保險資產查詢 REST API"));
    }
}
