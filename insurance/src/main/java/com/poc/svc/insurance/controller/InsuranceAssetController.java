package com.poc.svc.insurance.controller;

import com.poc.svc.insurance.logging.TraceContext;
import com.poc.svc.insurance.service.InsuranceAssetService;
import com.poc.svc.insurance.service.InsuranceAssetService.InsuranceAssetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "insurance-asset-controller")
@RequestMapping("/insurance/customers/{customerId}/assets")
public class InsuranceAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final InsuranceAssetService insuranceAssetService;

    public InsuranceAssetController(InsuranceAssetService insuranceAssetService) {
        this.insuranceAssetService = insuranceAssetService;
    }

    @GetMapping
    @Operation(
            operationId = "getInsuranceCustomerAssets",
            summary = "查詢保險資產",
            description = "組合多種保單資訊並提供總保障金額，支援追蹤用 Trace ID。",
            parameters = {
                    @Parameter(
                            name = "customerId",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "保險系統的客戶識別碼"),
                    @Parameter(
                            name = TRACE_ID_HEADER,
                            in = ParameterIn.HEADER,
                            required = false,
                            description = "追蹤請求用的 Trace ID，如未提供則自動產生")
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查詢成功",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = InsuranceAssetResponse.class))),
                    @ApiResponse(responseCode = "400", description = "customerId 無效"),
                    @ApiResponse(responseCode = "404", description = "查無客戶資料"),
                    @ApiResponse(responseCode = "504", description = "下游保險服務逾時")
            })
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
