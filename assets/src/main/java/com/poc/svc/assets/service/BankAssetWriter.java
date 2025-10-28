package com.poc.svc.assets.service;

import com.poc.svc.assets.repository.BankAssetRawRepository;
import com.poc.svc.assets.entity.BankAssetRawDocument;
import com.poc.svc.assets.service.impl.support.MongoWriteRetrier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BankAssetWriter {

    private final BankAssetRawRepository repository;
    private final MongoWriteRetrier mongoWriteRetrier;

    public BankAssetWriter(BankAssetRawRepository repository, MongoWriteRetrier mongoWriteRetrier) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.mongoWriteRetrier = Objects.requireNonNull(mongoWriteRetrier, "mongoWriteRetrier must not be null");
    }

    public BankAssetRawDocument write(BankAssetWriteRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        BankAssetRawDocument document = new BankAssetRawDocument(
                null,
                request.customerId(),
                Map.copyOf(request.payload()),
                request.totalBalance(),
                request.currencySummary().stream()
                        .map(currency -> new BankAssetRawDocument.CurrencyAmount(currency.currency(), currency.amount()))
                        .toList(),
                request.fetchedAt(),
                request.traceId()
        );
        return mongoWriteRetrier.execute(
                "Failed to persist bank assets for customer %s".formatted(request.customerId()),
                () -> repository.save(document)
        );
    }

    public record BankAssetWriteRequest(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalBalance,
            List<CurrencyAmount> currencySummary,
            Instant fetchedAt,
            String traceId
    ) {
        public BankAssetWriteRequest {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(payload, "payload must not be null");
            Objects.requireNonNull(totalBalance, "totalBalance must not be null");
            Objects.requireNonNull(currencySummary, "currencySummary must not be null");
            Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
            Objects.requireNonNull(traceId, "traceId must not be null");
        }

        public record CurrencyAmount(String currency, BigDecimal amount) {
            public CurrencyAmount {
                Objects.requireNonNull(currency, "currency must not be null");
                Objects.requireNonNull(amount, "amount must not be null");
            }
        }
    }
}
