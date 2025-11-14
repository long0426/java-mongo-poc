package com.poc.svc.assets.dto.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "Mongo asset_staging document example used in Swagger responses")
public record AssetStagingDocumentExample(
        @Schema(example = "{\"$oid\":\"6916821d13e65e95974ea5e0\"}")
        Map<String, Object> _id,
        @Schema(example = "00614374-480e-4507-9f94-0756966b1253")
        String traceId,
        @Schema(example = "com.poc.svc.assets.entity.AssetStagingDocument")
        String _class,
        @Schema(example = "{\"$date\":\"2025-11-14T01:13:00.971Z\"}")
        Map<String, Object> aggregatedAt,
        @Schema(example = "COMPLETED")
        String aggregationStatus,
        List<Map<String, Object>> assets,
        @Schema(example = "TWD")
        String baseCurrency,
        List<Map<String, Object>> components,
        @Schema(example = "J12****789")
        String customerId,
        @Schema(example = "35736511.1")
        String totalAssetValue
) {
}
