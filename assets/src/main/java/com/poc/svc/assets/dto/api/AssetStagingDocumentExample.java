package com.poc.svc.assets.dto.api;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "asset_staging 文件範例，提供 Swagger 顯示完整資料結構。")
public record AssetStagingDocumentExample(
        @Schema(description = "Mongo ObjectId 字串表示。", example = "6916821d13e65e95974ea5e0")
        String _id,
        @Schema(example = "00614374-480e-4507-9f94-0756966b1253")
        String traceId,
        @Schema(example = "com.poc.svc.assets.entity.AssetStagingDocument")
        String _class,
        @Schema(description = "聚合完成時間（ISO 8601）。", example = "2025-11-14T01:13:00.971Z")
        String aggregatedAt,
        @Schema(example = "COMPLETED")
        String aggregationStatus,
        @ArraySchema(
                arraySchema = @Schema(description = "下游來源回寫的細項資產。"),
                schema = @Schema(implementation = AssetEntryExample.class)
        )
        List<AssetEntryExample> assets,
        @Schema(example = "TWD")
        String baseCurrency,
        @ArraySchema(
                arraySchema = @Schema(description = "各來源整體回應狀態與金額摘要。"),
                schema = @Schema(implementation = ComponentExample.class)
        )
        List<ComponentExample> components,
        @Schema(example = "J12****789")
        String customerId,
        @Schema(example = "35736511.1")
        String totalAssetValue
) {

    @Schema(description = "單筆資產來源的聚合紀錄，用於說明 assets 陣列內容。")
    public record AssetEntryExample(
            @Schema(example = "J12****789") String customerId,
            @Schema(example = "BANK") String source,
            @Schema(example = "SUCCESS") String sourceStatus,
            @Schema(example = "高收益儲蓄 1") String assetName,
            @Schema(example = "account") String assetType,
            @Schema(example = "EUR") String currency,
            @Schema(example = "EUR") String sourceCurrency,
            @Schema(example = "35") String exchangeRate,
            @Schema(example = "TWD") String baseCurrency,
            @Schema(example = "10510647.7") String amountInBase,
            @Schema(example = "300304.22") String balance,
            @Schema(example = "311765.39") String marketValue,
            @Schema(example = "263817.03") String coverage,
            @Schema(example = "PRO-1000") String policyNumber,
            @Schema(example = "property") String policyType,
            @Schema(example = "paid") String premiumStatus,
            @Schema(example = "MEDIUM") String riskLevel,
            @Schema(example = "BND-10") String symbol,
            @Schema(example = "251") String holdings,
            @Schema(example = "ACC-1000-************") String accountId,
            @Schema(example = "2025-11-14T01:13:00.971Z") String fetchedAt,
            @Schema(example = "2025-11-14T01:13:00.971Z") String aggregatedAt,
            @Schema(example = "00614374-480e-4507-9f94-0756966b1253") String traceId,
            @Schema(example = "6916821c702a4581eb6169c5") String payloadRefId,
            @Schema(example = "638854.87") String totalAssetValue,
            @Schema(example = "COMPLETED") String aggregationStatus
    ) {
    }

    @Schema(description = "聚合流程中每個來源回填的狀態與匯率資訊，用於說明 components 陣列。")
    public record ComponentExample(
            @Schema(example = "BANK") String source,
            @Schema(example = "SUCCESS") String status,
            @Schema(example = "638854.87") String amountInBase,
            @Schema(example = "TWD") String sourceCurrency,
            @Schema(example = "1.0000") String exchangeRate,
            @Schema(example = "00614374-480e-4507-9f94-0756966b1253") String rawTraceId,
            @Schema(example = "2025-11-14T01:13:00.971Z") String fetchedAt,
            @ArraySchema(
                    arraySchema = @Schema(description = "來源回傳的詳細清單，欄位會依來源略有不同。"),
                    schema = @Schema(implementation = ComponentAssetDetailExample.class)
            )
            List<ComponentAssetDetailExample> assetDetails,
            @Schema(example = "6916821c702a4581eb6169c5") String payloadRefId
    ) {
    }

    @Schema(description = "下游資產系統回傳的原始細節範例。")
    public record ComponentAssetDetailExample(
            @Schema(example = "ACC-1000-************") String accountId,
            @Schema(example = "高收益儲蓄 1") String assetName,
            @Schema(example = "account") String assetType,
            @Schema(example = "300304.22") String balance,
            @Schema(example = "USD") String currency,
            @Schema(example = "bond") String securityType,
            @Schema(example = "BND-10") String symbol,
            @Schema(example = "251") String holdings,
            @Schema(example = "311765.39") String marketValue,
            @Schema(example = "MEDIUM") String riskLevel,
            @Schema(example = "property") String policyType,
            @Schema(example = "paid") String premiumStatus,
            @Schema(example = "263817.03") String coverage,
            @Schema(example = "2025-11-14T01:13:00.938198Z") String lastUpdated
    ) {
    }
}
