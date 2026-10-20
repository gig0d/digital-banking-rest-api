package com.gig0d.banking.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {
    @Test
    void throttlesAuthenticationRequestsAndIgnoresSpoofedForwardingHeaders() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 2);
        AtomicInteger reached = new AtomicInteger();
        MockHttpServletResponse last = null;
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setServletPath("/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", "192.0.2." + i);
            last = new MockHttpServletResponse();
            filter.doFilter(request, last, (req, res) -> reached.incrementAndGet());
        }
        assertThat(reached).hasValue(2);
        assertThat(last.getStatus()).isEqualTo(429);
        assertThat(last.getHeader("Retry-After")).isEqualTo("60");
        assertThat(last.getContentType()).startsWith("application/problem+json");
    }
}
