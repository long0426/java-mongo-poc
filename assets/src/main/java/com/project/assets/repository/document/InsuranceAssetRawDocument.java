package com.project.assets.repository.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Document(collection = "insurance_raw")
public record InsuranceAssetRawDocument(
        @Id String id,
        String customerId,
        Map<String, Object> payload,
        BigDecimal totalCoverage,
        Integer policiesCount,
        Instant fetchedAt,
        String traceId
) {
}
