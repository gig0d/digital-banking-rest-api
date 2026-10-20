package com.gig0d.banking.demo;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration
@Profile("demo")
@ConditionalOnProperty(name = "banking.demo.enabled", havingValue = "true")
@EnableConfigurationProperties(DemoSettings.class)
public class DemoConfiguration {
    @Bean
    ApplicationRunner bootstrapDemo(DemoBootstrap bootstrap) { return args -> bootstrap.bootstrap(); }
}
