package com.poc.svc.assets.util;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 提供取得與設定 Trace ID 的簡易工具。
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String ensureTraceId(String candidate) {
        String traceId = StringUtils.hasText(candidate) ? candidate : UUID.randomUUID().toString();
        setTraceId(traceId);
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(TRACE_ID_MDC_KEY, traceId);
        }
    }

    public static String traceId() {
        return MDC.get(TRACE_ID_MDC_KEY);
    }

    public static void clear() {
        MDC.remove(TRACE_ID_MDC_KEY);
    }
}
