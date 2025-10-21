package com.project.securities.logging;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Securities 模組的 TraceId 管理工具，與銀行/整合模組保持一致的 header/MDC key。
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String ensureTraceId(String candidate) {
        String traceId = StringUtils.hasText(candidate) ? candidate : UUID.randomUUID().toString();
        setTraceId(traceId);
        return traceId;
    }

    public static void setTraceId(String traceId) {
        if (StringUtils.hasText(traceId)) {
            MDC.put(MDC_KEY, traceId);
        }
    }

    public static String traceId() {
        return MDC.get(MDC_KEY);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
