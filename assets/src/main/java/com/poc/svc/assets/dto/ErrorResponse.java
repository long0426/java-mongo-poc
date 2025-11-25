package com.poc.svc.assets.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 標準化錯誤回應結構，與 REST API 回傳格式一致。
 * `details` 欄位預設為不可變的空集合以避免 NullPointerException。
 */
@Schema(name = "ErrorResponse", description = "資產聚合 API 的錯誤回應格式")
public record ErrorResponse(
        @Schema(description = "錯誤代碼", example = "ASSET_SOURCE_UNAVAILABLE") String code,
        @Schema(description = "錯誤訊息", example = "Failed to aggregate assets") String message,
        @Schema(description = "額外錯誤細節", example = "{\"source\":\"bank\"}") Map<String, Object> details,
        @Schema(description = "Trace ID", example = "trace-1234") String traceId
) {

    public ErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details, String traceId) {
        return new ErrorResponse(code, message, details, traceId);
    }
}
