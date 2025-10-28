package com.poc.svc.assets.service;

import com.poc.svc.assets.repository.InsuranceAssetRawRepository;
import com.poc.svc.assets.entity.InsuranceAssetRawDocument;
import com.poc.svc.assets.service.impl.support.MongoWriteRetrier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
public class InsuranceAssetWriter {

    private final InsuranceAssetRawRepository repository;
    private final MongoWriteRetrier mongoWriteRetrier;

    public InsuranceAssetWriter(InsuranceAssetRawRepository repository, MongoWriteRetrier mongoWriteRetrier) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mongoWriteRetrier = Objects.requireNonNull(mongoWriteRetrier, "mongoWriteRetrier must not be null");
    }

    public InsuranceAssetRawDocument write(InsuranceAssetWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        InsuranceAssetRawDocument document = new InsuranceAssetRawDocument(
                null,
                request.customerId(),
                Map.copyOf(request.payload()),
                request.totalCoverage(),
                request.policiesCount(),
                request.fetchedAt(),
                request.traceId()
        );
        return mongoWriteRetrier.execute(
                "Failed to persist insurance assets for customer %s".formatted(request.customerId()),
                () -> repository.save(document)
        );
    }

    public record InsuranceAssetWriteRequest(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalCoverage,
            Integer policiesCount,
            Instant fetchedAt,
            String traceId
    ) {
        public InsuranceAssetWriteRequest {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(totalCoverage, "totalCoverage must not be null");
            Objects.requireNonNull(policiesCount, "policiesCount must not be null");
            Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
            Objects.requireNonNull(traceId, "traceId must not be null");
        }
    }
}
