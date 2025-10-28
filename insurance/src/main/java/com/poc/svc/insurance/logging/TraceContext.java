package com.poc.svc.insurance.logging;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * 管理 Insurance 模組的 Trace Id，保持 header 與 MDC 的一致性。
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
