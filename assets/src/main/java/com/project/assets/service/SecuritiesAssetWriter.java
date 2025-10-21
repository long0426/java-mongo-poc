package com.project.assets.service;

import com.project.assets.repository.SecuritiesAssetRawRepository;
import com.project.assets.repository.document.SecuritiesAssetRawDocument;
import com.project.assets.service.support.MongoWriteRetrier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Service
public class SecuritiesAssetWriter {

    private final SecuritiesAssetRawRepository repository;
    private final MongoWriteRetrier mongoWriteRetrier;

    public SecuritiesAssetWriter(SecuritiesAssetRawRepository repository, MongoWriteRetrier mongoWriteRetrier) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mongoWriteRetrier = Objects.requireNonNull(mongoWriteRetrier, "mongoWriteRetrier must not be null");
    }

    public SecuritiesAssetRawDocument write(SecuritiesAssetWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        SecuritiesAssetRawDocument document = new SecuritiesAssetRawDocument(
                null,
                request.customerId(),
                Map.copyOf(request.payload()),
                request.totalMarketValue(),
                request.holdingsCount(),
                request.fetchedAt(),
                request.traceId()
        );
        return mongoWriteRetrier.execute(
                "Failed to persist securities assets for customer %s".formatted(request.customerId()),
                () -> repository.save(document)
        );
    }

    public record SecuritiesAssetWriteRequest(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalMarketValue,
            Integer holdingsCount,
            Instant fetchedAt,
            String traceId
    ) {
        public SecuritiesAssetWriteRequest {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(totalMarketValue, "totalMarketValue must not be null");
            Objects.requireNonNull(holdingsCount, "holdingsCount must not be null");
            Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
            Objects.requireNonNull(traceId, "traceId must not be null");
        }
    }
}
