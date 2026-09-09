package com.ridehub.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Feign RequestInterceptor that propagates W3C traceparent and correlation identifiers
 * from incoming HTTP context or SLF4J MDC to outgoing Feign calls.
 */
public class RidehubFeignTraceInterceptor implements RequestInterceptor {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";

    @Override
    public void apply(RequestTemplate template) {
        // 1. Check Correlation ID from MDC or HTTP Request
        String correlationId = MDC.get(MDC_CORRELATION_ID);
        String traceparent = MDC.get(TRACEPARENT_HEADER);

        HttpServletRequest currentReq = getCurrentHttpRequest();
        if (currentReq != null) {
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = currentReq.getHeader(CORRELATION_ID_HEADER);
            }
            if (traceparent == null || traceparent.isBlank()) {
                traceparent = currentReq.getHeader(TRACEPARENT_HEADER);
            }
        }

        // If still null, generate a correlation ID
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        if (!template.headers().containsKey(CORRELATION_ID_HEADER)) {
            template.header(CORRELATION_ID_HEADER, correlationId);
        }
        if (!template.headers().containsKey(REQUEST_ID_HEADER)) {
            template.header(REQUEST_ID_HEADER, correlationId);
        }

        // Propagate traceparent if present
        if (traceparent != null && !traceparent.isBlank() && !template.headers().containsKey(TRACEPARENT_HEADER)) {
            template.header(TRACEPARENT_HEADER, traceparent);
        }
    }

    private HttpServletRequest getCurrentHttpRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
                return servletRequestAttributes.getRequest();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
