package com.poc.svc.bank.controller;

import com.poc.svc.bank.logging.TraceContext;
import com.poc.svc.bank.service.BankAssetService;
import com.poc.svc.bank.service.BankAssetService.BankAssetResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bank/customers/{customerId}/assets")
public class BankAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final BankAssetService bankAssetService;

    public BankAssetController(BankAssetService bankAssetService) {
        this.bankAssetService = bankAssetService;
    }

    @GetMapping
    public ResponseEntity<BankAssetResponse> getCustomerAssets(
            @PathVariable String customerId,
            @RequestHeader(value = TRACE_ID_HEADER, required = false) String traceIdHeader) {

        String determinedTraceId = TraceContext.ensureTraceId(traceIdHeader);
        BankAssetResponse response = bankAssetService.getCustomerAssets(customerId);
        BankAssetResponse enrichedResponse = ensureTraceId(response, determinedTraceId);

        return ResponseEntity.ok()
                .header(TRACE_ID_HEADER, enrichedResponse.traceId())
                .body(enrichedResponse);
    }

    private BankAssetResponse ensureTraceId(BankAssetResponse response, String traceId) {
        return traceId.equals(response.traceId()) ? response : response.withTraceId(traceId);
    }
}
