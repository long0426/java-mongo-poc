package com.project.assets.integration;

import com.project.assets.service.BankAssetWriter;
import com.project.assets.service.InsuranceAssetWriter;
import com.project.assets.service.SecuritiesAssetWriter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface AssetSourceClient {

    CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId);

    CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId);

    CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId);

    record BankAssetResult(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalBalance,
            String currency,
            List<BankAssetWriter.BankAssetWriteRequest.CurrencyAmount> currencySummary,
            Instant fetchedAt,
            String traceId
    ) {
    }

    record SecuritiesAssetResult(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalMarketValue,
            String currency,
            int holdingsCount,
            Instant fetchedAt,
            String traceId
    ) {
    }

    record InsuranceAssetResult(
            String customerId,
            Map<String, Object> payload,
            BigDecimal totalCoverage,
            String currency,
            int policiesCount,
            Instant fetchedAt,
            String traceId
    ) {
    }
}
