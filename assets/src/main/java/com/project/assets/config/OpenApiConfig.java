package com.project.assets.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI assetsOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Aggregated Asset API")
                        .version("v1")
                        .description("整合資產查詢 REST API"));
    }
}
