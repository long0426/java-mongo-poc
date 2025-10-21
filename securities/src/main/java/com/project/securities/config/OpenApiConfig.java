package com.project.securities.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI securitiesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Securities Asset API")
                        .version("v1")
                        .description("證券資產查詢 REST API"));
    }
}
