package com.poc.svc.assets.controller;

import com.poc.svc.assets.util.TraceContext;
import com.poc.svc.assets.dto.AggregatedAssetResponse;
import com.poc.svc.assets.service.AssetAggregationService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/assets/customers/{customerId}")
public class AssetIntegrationController {

    private final AssetAggregationService assetAggregationService;

    public AssetIntegrationController(AssetAggregationService assetAggregationService) {
        this.assetAggregationService = assetAggregationService;
    }

    @GetMapping
    public ResponseEntity<AggregatedAssetResponse> aggregateCustomerAssets(
            @PathVariable String customerId,
            @RequestHeader(value = TraceContext.TRACE_ID_HEADER, required = false) String traceIdHeader
    ) {
        String traceId = TraceContext.ensureTraceId(traceIdHeader);
        AggregatedAssetResponse response = assetAggregationService.aggregateCustomerAssets(customerId);
        HttpHeaders headers = new HttpHeaders();
        headers.add(TraceContext.TRACE_ID_HEADER, traceId);
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }
}
