package com.poc.svc.securities.controller;

import com.poc.svc.securities.logging.TraceContext;
import com.poc.svc.securities.service.SecuritiesAssetService;
import com.poc.svc.securities.service.SecuritiesAssetService.SecuritiesAssetResponse;
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
@Tag(name = "securities-asset-controller")
@RequestMapping("/securities/customers/{customerId}/assets")
public class SecuritiesAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final SecuritiesAssetService securitiesAssetService;

    public SecuritiesAssetController(SecuritiesAssetService securitiesAssetService) {
        this.securitiesAssetService = securitiesAssetService;
    }

    @GetMapping
    @Operation(
            operationId = "getSecuritiesCustomerAssets",
            summary = "查詢證券資產",
            description = "回傳客戶持有的股票、債券、基金等資產清單以及市值總額。",
            parameters = {
                    @Parameter(
                            name = "customerId",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "證券系統的客戶識別碼"),
                    @Parameter(
                            name = TRACE_ID_HEADER,
                            in = ParameterIn.HEADER,
                            required = false,
                            description = "追蹤請求所使用的 Trace ID")
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查詢成功",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = SecuritiesAssetResponse.class))),
                    @ApiResponse(responseCode = "400", description = "customerId 無效"),
                    @ApiResponse(responseCode = "404", description = "查無客戶資料"),
                    @ApiResponse(responseCode = "504", description = "下游證券來源逾時")
            })
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
