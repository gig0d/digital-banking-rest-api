# ADR 003: Supported Spring stack and approved JUnit 6 adjustment

Status: Accepted by the owner. Date: 2026-10-19.

## Context

The original specification requested Java 21, a supported stable Spring Boot release, and JUnit 5. Official compatibility documentation was checked before implementation. Spring Boot 4.0.8 supports Java 21 and uses Spring Framework 7; springdoc's compatibility matrix pairs Boot 4.0.x with springdoc 3.0.x.

Running Spring Boot integration tests with JUnit 5.13.4 failed with `NoSuchMethodError` for `ExtensionContext.Store.computeIfAbsent`. The latest inspected JUnit 5.14.4 API also lacked that method. Spring Framework 7's testing baseline is JUnit 6.

## Decision

Use Java 21, Spring Boot 4.0.8, springdoc 3.0.3, and the JUnit Jupiter 6 release managed by Spring Boot. The owner explicitly approved JUnit 6 after the compatibility failure was explained. Preserve Jupiter-style test code and use supported Spring testing components instead of a compatibility shim or an older framework override.

## Consequences

The delivered test stack differs from the initial JUnit 5 requirement by explicit agreement. Most test authoring concepts remain the same. Dependency versions should continue to be reviewed; pinning a supported version today does not guarantee indefinite support.

Sources: [Boot 4.0 requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html), [Spring Framework 7 release notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes), [springdoc compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
