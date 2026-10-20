package com.gig0d.banking.integration;

import com.gig0d.banking.demo.DemoFundingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
class DefaultConfigurationIT extends PostgresSupport {
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Test
    void fundingRequiresDemoProfileEvenWhenEnabledPropertyIsTrue() throws Exception {
        assertThat(context.getBeansOfType(DemoFundingService.class)).isEmpty();
        mvc.perform(post("/api/v1/admin/demo-deposits")
                .with(jwt().jwt(claims -> claims.subject(UUID.randomUUID().toString())).authorities(createAuthorityList("ROLE_ADMIN")))
                .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
    @Test
    void securityHeadersAndDefaultCorsRejectUntrustedOrigins() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(options("/api/v1/accounts").header("Origin", "https://untrusted.example.test")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
}
