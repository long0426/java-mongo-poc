package com.poc.svc.bank.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import io.swagger.v3.oas.annotations.media.Schema;

public interface BankAssetService {

    BankAssetResponse getCustomerAssets(String customerId);

    @Schema(name = "BankAssetResponse", description = "客戶銀行資產查詢的回應")
    record BankAssetResponse(
            @Schema(
                    description = "銀行核心系統的客戶識別碼",
                    example = "cust-001",
                    requiredMode = Schema.RequiredMode.REQUIRED) String customerId,
            @Schema(
                    description = "該客戶持有的銀行資產清單",
                    requiredMode = Schema.RequiredMode.REQUIRED) List<BankAssetItem> bankAssets,
            @Schema(
                    description = "資產總額",
                    example = "125000.50",
                    requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal totalBalance,
            @Schema(
                    description = "資產總額的幣別",
                    example = "TWD",
                    requiredMode = Schema.RequiredMode.REQUIRED) String currency,
            @Schema(
                    description = "本次查詢的 Trace ID",
                    example = "trace-7c8d",
                    requiredMode = Schema.RequiredMode.REQUIRED) String traceId) {
        public BankAssetResponse {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(bankAssets, "bankAssets must not be null");
            Objects.requireNonNull(totalBalance, "totalBalance must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
        }

        public BankAssetResponse withTraceId(String newTraceId) {
            return new BankAssetResponse(customerId, bankAssets, totalBalance, currency, newTraceId);
        }

        @Schema(name = "BankAssetItem", description = "單一銀行資產明細")
        public record BankAssetItem(
                @Schema(description = "資產對應帳號", example = "ACC-001") String accountId,
                @Schema(description = "資產名稱", example = "Salary Account") String assetName,
                @Schema(description = "資產餘額", example = "1000.00") BigDecimal balance,
                @Schema(description = "資產幣別", example = "TWD") String currency) {
            public BankAssetItem {
                Objects.requireNonNull(accountId, "accountId must not be null");
                Objects.requireNonNull(assetName, "assetName must not be null");
                Objects.requireNonNull(balance, "balance must not be null");
                Objects.requireNonNull(currency, "currency must not be null");
            }
        }
    }
}
