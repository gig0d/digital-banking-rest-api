package com.gig0d.banking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

public class AuthRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start, int count) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int limit;
    public AuthRateLimitFilter() { this(Clock.systemUTC(), 20); }
    public AuthRateLimitFilter(Clock clock, int limit) { this.clock = clock; this.limit = limit; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !(request.getServletPath().equals("/api/v1/auth/login")
                || request.getServletPath().equals("/api/v1/auth/register"));
    }

    private synchronized boolean allow(String address) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= 60_000);
        Window window = windows.get(address);
        if (window == null) {
            if (windows.size() >= 10_000) return false;
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (window.count() >= limit) return false;
        windows.put(address, new Window(window.start(), window.count() + 1));
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(request.getRemoteAddr())) {
            response.setHeader("Retry-After", "60");
            SecurityProblems.write(response, 429, "Too Many Requests", "RATE_LIMITED", "Too many authentication requests. Retry later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
