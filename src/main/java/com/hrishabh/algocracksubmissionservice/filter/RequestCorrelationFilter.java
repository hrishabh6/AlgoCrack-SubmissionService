package com.hrishabh.algocracksubmissionservice.filter;

import com.hrishabh.algocracksubmissionservice.logging.LoggingConstants;
import com.hrishabh.algocracksubmissionservice.logging.RequestContext;
import com.hrishabh.algocracksubmissionservice.logging.StructuredLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {

    private final StructuredLogger logger = new StructuredLogger(RequestCorrelationFilter.class, "SubmissionService");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestContext.REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = StructuredLogger.generateRequestId();
        }

        long startNanos = System.nanoTime();
        RequestContext.setRequestId(requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);

        logger.logRequest(
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                request.getHeader("User-Agent"),
                extractClientIp(request),
                LoggingConstants.TYPE, "REQUEST");

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logger.logResponse(
                    requestId,
                    response.getStatus(),
                    durationMs,
                    LoggingConstants.HTTP_PATH, request.getRequestURI(),
                    LoggingConstants.HTTP_METHOD, request.getMethod());
            RequestContext.clear();
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
