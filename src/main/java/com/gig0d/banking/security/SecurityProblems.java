package com.gig0d.banking.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class SecurityProblems {
    private SecurityProblems() {}
    // Inputs are constants from the security configuration; no request values are interpolated.
    static void write(HttpServletResponse response, int status, String title, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"type\":\"urn:banking:problem:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')
                + "\",\"title\":\"" + title + "\",\"status\":" + status + ",\"code\":\"" + code
                + "\",\"detail\":\"" + detail + "\"}");
    }
}
