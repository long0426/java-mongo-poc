package com.project.bank.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public interface BankAssetService {

    BankAssetResponse getCustomerAssets(String customerId);

    record BankAssetResponse(
            String customerId,
            List<BankAssetItem> bankAssets,
            BigDecimal totalBalance,
            String currency,
            String traceId
    ) {
        public BankAssetResponse {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(bankAssets, "bankAssets must not be null");
            Objects.requireNonNull(totalBalance, "totalBalance must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
        }

        public BankAssetResponse withTraceId(String newTraceId) {
            return new BankAssetResponse(customerId, bankAssets, totalBalance, currency, newTraceId);
        }

        public record BankAssetItem(String accountId, BigDecimal balance, String currency) {
        }
    }
}
