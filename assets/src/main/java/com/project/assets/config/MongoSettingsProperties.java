package com.project.assets.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.data.mongodb")
public record MongoSettingsProperties(
        @NotBlank(message = "spring.data.mongodb.uri is required")
        String uri,
        @NotBlank(message = "spring.data.mongodb.database is required")
        String database
) {
}
