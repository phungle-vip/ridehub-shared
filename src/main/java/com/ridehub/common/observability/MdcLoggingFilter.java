package com.ridehub.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that extracts or creates distributed tracing identifiers and user context,
 * populating the SLF4J MDC for logging consistency across microservices.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_USER_ID = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. Correlation ID
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = request.getHeader(REQUEST_ID_HEADER);
            }
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            MDC.put(MDC_CORRELATION_ID, correlationId);
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // 2. W3C Traceparent (version-traceid-spanid-traceflags)
            String traceparent = request.getHeader(TRACEPARENT_HEADER);
            if (traceparent != null && !traceparent.isBlank()) {
                MDC.put(TRACEPARENT_HEADER, traceparent);
                String[] parts = traceparent.split("-");
                if (parts.length >= 4) {
                    MDC.put(MDC_TRACE_ID, parts[1]);
                    MDC.put(MDC_SPAN_ID, parts[2]);
                }
            } else {
                MDC.put(MDC_TRACE_ID, correlationId);
            }

            // 3. User Identifier from Security Context if present
            extractUserContext();

            filterChain.doFilter(request, response);

        } finally {
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_SPAN_ID);
            MDC.remove(MDC_USER_ID);
            MDC.remove(TRACEPARENT_HEADER);
        }
    }

    private void extractUserContext() {
        try {
            Class<?> sch = Class.forName("org.springframework.security.core.context.SecurityContextHolder");
            Object context = sch.getMethod("getContext").invoke(null);
            if (context != null) {
                Object auth = context.getClass().getMethod("getAuthentication").invoke(context);
                if (auth != null) {
                    Object principal = auth.getClass().getMethod("getPrincipal").invoke(auth);
                    if (principal != null) {
                        String name = (String) auth.getClass().getMethod("getName").invoke(auth);
                        if (name != null && !name.isBlank() && !"anonymousUser".equalsIgnoreCase(name)) {
                            MDC.put(MDC_USER_ID, name);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Spring Security may not be present or no user logged in
        }
    }
}
