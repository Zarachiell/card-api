package com.example.api.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestContextLoggingFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger("http.audit");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String reqId = firstNonBlank(req.getHeader("X-Request-ID"), UUID.randomUUID().toString());
        long start = System.nanoTime();
        MDC.put("requestId", reqId);
        MDC.put("method", req.getMethod());
        MDC.put("path", req.getRequestURI());
        MDC.put("remote", firstNonBlank(req.getHeader("X-Forwarded-For"), req.getRemoteAddr()));
        MDC.put("userAgent", firstNonBlank(req.getHeader("User-Agent"), "-"));

        try {
            chain.doFilter(req, res);
        } finally {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a instanceof JwtAuthenticationToken jwt) {
                String clientId = jwt.getToken().getClaimAsString("azp");
                if (clientId == null) clientId = jwt.getToken().getClaimAsString("client_id");
                if (clientId != null) MDC.put("clientId", clientId);
                MDC.put("sub", jwt.getName());
            }
            long ms = (System.nanoTime() - start) / 1_000_000;
            LOG.info("http completed status={} durationMs={}", res.getStatus(), ms);
            MDC.clear();
        }
    }

    private static String firstNonBlank(String... vs) {
        for (String v : vs) if (v != null && !v.isBlank()) return v;
        return null;
    }
}

