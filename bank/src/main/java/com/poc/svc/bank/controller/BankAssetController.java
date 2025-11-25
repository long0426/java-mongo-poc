package com.poc.svc.bank.controller;

import com.poc.svc.bank.logging.TraceContext;
import com.poc.svc.bank.service.BankAssetService;
import com.poc.svc.bank.service.BankAssetService.BankAssetResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "bank-asset-controller")
@RequestMapping("/bank/customers/{customerId}/assets")
public class BankAssetController {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final BankAssetService bankAssetService;

    public BankAssetController(BankAssetService bankAssetService) {
        this.bankAssetService = bankAssetService;
    }

    @GetMapping
    @Operation(
            operationId = "getCustomerAssets",
            summary = "查詢客戶銀行資產",
            description = "根據 customerId 取得銀行持有的資產明細與總額。",
            parameters = {
                    @Parameter(
                            name = "customerId",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "銀行核心系統的客戶識別碼"),
                    @Parameter(
                            name = TRACE_ID_HEADER,
                            in = ParameterIn.HEADER,
                            required = false,
                            description = "追蹤請求用的 Trace ID，如未提供則由服務自動產生")
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "查詢成功，回傳客戶資產明細",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = BankAssetResponse.class))),
                    @ApiResponse(responseCode = "404", description = "找不到客戶資料"),
                    @ApiResponse(responseCode = "500", description = "系統內部錯誤")
            })
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
