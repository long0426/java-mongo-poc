package com.poc.svc.securities.controller;

import com.poc.svc.securities.logging.TraceContext;
import com.poc.svc.securities.service.SecuritiesAssetService;
import com.poc.svc.securities.service.SecuritiesAssetService.SecuritiesAssetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/securities/customers/{customerId}/assets")
public class SecuritiesAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final SecuritiesAssetService securitiesAssetService;

    public SecuritiesAssetController(SecuritiesAssetService securitiesAssetService) {
        this.securitiesAssetService = securitiesAssetService;
    }

    @GetMapping
    public ResponseEntity<SecuritiesAssetResponse> getCustomerAssets(
            @PathVariable String customerId,
            @RequestHeader(value = TRACE_ID_HEADER, required = false) String traceIdHeader
    ) {
        String traceId = TraceContext.ensureTraceId(traceIdHeader);
        SecuritiesAssetResponse response = securitiesAssetService.getCustomerAssets(customerId);
        SecuritiesAssetResponse enriched = ensureTraceId(response, traceId);
        return ResponseEntity.ok()
                .header(TRACE_ID_HEADER, enriched.traceId())
                .body(enriched);
    }

    private SecuritiesAssetResponse ensureTraceId(SecuritiesAssetResponse response, String traceId) {
        if (StringUtils.hasText(response.traceId()) && response.traceId().equals(traceId)) {
            return response;
        }
        return response.withTraceId(traceId);
    }
}
