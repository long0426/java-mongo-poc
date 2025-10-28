package com.poc.svc.assets.config;

import com.poc.svc.assets.util.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 於每次請求產生或沿用 TRACE ID，串接 MDC 與 Response Header。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String existingTraceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        String traceId = TraceContext.ensureTraceId(existingTraceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceContext.clear();
        }
    }
}
