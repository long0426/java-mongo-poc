package com.poc.svc.assets.controller;

import com.poc.svc.assets.dto.api.AssetStagingDocumentExample;
import com.poc.svc.assets.service.AssetAggregationService;
import com.poc.svc.assets.util.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.bson.Document;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/assets/customers/{customerId}")
public class AssetIntegrationController {

    private final AssetAggregationService assetAggregationService;

    public AssetIntegrationController(AssetAggregationService assetAggregationService) {
        this.assetAggregationService = assetAggregationService;
    }

    @GetMapping
    @Operation(
            summary = "Aggregate all assets for a customer",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Aggregated asset staging documents",
                            content = @Content(
                                    mediaType = "application/json",
                                    array = @ArraySchema(schema = @Schema(implementation = AssetStagingDocumentExample.class)),
                                    examples = @ExampleObject(
                                            name = "assetStagingExample",
                                            summary = "完整 asset_staging 文件範例",
                                            value = """
                                                    [
                                                      {
                                                        "_id": { "$oid": "6916821d13e65e95974ea5e0" },
                                                        "traceId": "00614374-480e-4507-9f94-0756966b1253",
                                                        "_class": "com.poc.svc.assets.entity.AssetStagingDocument",
                                                        "aggregatedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                        "aggregationStatus": "COMPLETED",
                                                        "assets": [
                                                          {
                                                            "customerId": "J12****789",
                                                            "source": "BANK",
                                                            "sourceStatus": "SUCCESS",
                                                            "assetName": "ACC-1000-************",
                                                            "assetType": "account",
                                                            "currency": "EUR",
                                                            "sourceCurrency": "EUR",
                                                            "exchangeRate": 35,
                                                            "baseCurrency": "TWD",
                                                            "amountInBase": "10510647.7",
                                                            "balance": "300304.22",
                                                            "accountId": "ACC-1000-************",
                                                            "fetchedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                            "aggregatedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                            "traceId": "00614374-480e-4507-9f94-0756966b1253",
                                                            "payloadRefId": { "$oid": "6916821c702a4581eb6169c5" },
                                                            "totalAssetValue": "638854.87",
                                                            "aggregationStatus": "COMPLETED"
                                                          },
                                                          {
                                                            "customerId": "J12****789",
                                                            "source": "BANK",
                                                            "sourceStatus": "SUCCESS",
                                                            "assetName": "ACC-1001-************",
                                                            "assetType": "account",
                                                            "currency": "USD",
                                                            "sourceCurrency": "USD",
                                                            "exchangeRate": 32,
                                                            "baseCurrency": "TWD",
                                                            "amountInBase": "10833620.8",
                                                            "balance": "338550.65",
                                                            "accountId": "ACC-1001-************",
                                                            "fetchedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                            "aggregatedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                            "traceId": "00614374-480e-4507-9f94-0756966b1253",
                                                            "payloadRefId": { "$oid": "6916821c702a4581eb6169c5" },
                                                            "totalAssetValue": "638854.87",
                                                            "aggregationStatus": "COMPLETED"
                                                          }
                                                        ],
                                                        "baseCurrency": "TWD",
                                                        "components": [
                                                          {
                                                            "source": "BANK",
                                                            "status": "SUCCESS",
                                                            "amountInBase": "638854.87",
                                                            "sourceCurrency": "TWD",
                                                            "exchangeRate": "1.0000",
                                                            "rawTraceId": "00614374-480e-4507-9f94-0756966b1253",
                                                            "fetchedAt": { "$date": "2025-11-14T01:13:00.971Z" },
                                                            "assetDetails": [
                                                              { "accountId": "ACC-1000-************", "assetName": "高收益儲蓄 1", "balance": "300304.22", "currency": "EUR" }
                                                            ],
                                                            "payloadRefId": { "$oid": "6916821c702a4581eb6169c5" }
                                                          }
                                                        ],
                                                        "customerId": "J12****789",
                                                        "totalAssetValue": 35736511.1
                                                      }
                                                    ]
                                                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<List<Document>> aggregateCustomerAssets(
            @PathVariable String customerId,
            @RequestHeader(value = TraceContext.TRACE_ID_HEADER, required = false) String traceIdHeader
    ) {
        String traceId = TraceContext.ensureTraceId(traceIdHeader);
        List<Document> response = assetAggregationService.aggregateCustomerAssets(customerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(TraceContext.TRACE_ID_HEADER, traceId);
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }
}
