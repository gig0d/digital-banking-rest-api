package com.gig0d.banking.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("banking.demo")
public record DemoSettings(boolean enabled, String adminEmail, String adminPassword) {}
