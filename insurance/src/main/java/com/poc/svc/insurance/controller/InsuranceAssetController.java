package com.poc.svc.insurance.controller;

import com.poc.svc.insurance.logging.TraceContext;
import com.poc.svc.insurance.service.InsuranceAssetService;
import com.poc.svc.insurance.service.InsuranceAssetService.InsuranceAssetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insurance/customers/{customerId}/assets")
public class InsuranceAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final InsuranceAssetService insuranceAssetService;

    public InsuranceAssetController(InsuranceAssetService insuranceAssetService) {
        this.insuranceAssetService = insuranceAssetService;
    }

    @GetMapping
    public ResponseEntity<InsuranceAssetResponse> getCustomerAssets(
            @PathVariable String customerId,
            @RequestHeader(value = TRACE_ID_HEADER, required = false) String traceIdHeader
    ) {
        String traceId = TraceContext.ensureTraceId(traceIdHeader);
        InsuranceAssetResponse response = insuranceAssetService.getCustomerAssets(customerId);
        InsuranceAssetResponse enriched = ensureTraceId(response, traceId);
        return ResponseEntity.ok()
                .header(TRACE_ID_HEADER, enriched.traceId())
                .body(enriched);
    }

    private InsuranceAssetResponse ensureTraceId(InsuranceAssetResponse response, String traceId) {
        if (StringUtils.hasText(response.traceId()) && response.traceId().equals(traceId)) {
            return response;
        }
        return response.withTraceId(traceId);
    }
}
