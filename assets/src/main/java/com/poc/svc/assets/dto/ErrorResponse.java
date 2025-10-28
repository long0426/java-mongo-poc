package com.poc.svc.assets.dto;

import java.util.Map;

/**
 * 標準化錯誤回應結構，與 REST API 回傳格式一致。
 * `details` 欄位預設為不可變的空集合以避免 NullPointerException。
 */
public record ErrorResponse(
        String code,
        String message,
        Map<String, Object> details,
        String traceId
) {

    public ErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ErrorResponse of(String code, String message, Map<String, Object> details, String traceId) {
        return new ErrorResponse(code, message, details, traceId);
    }
}
