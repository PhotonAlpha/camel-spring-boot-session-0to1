package com.example.camel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

/**
 * Application entry point.
 *
 * <p>This project deliberately describes every route with the <b>Spring XML DSL</b>:
 * {@code @ImportResource} hands {@code classpath:camel-context.xml} to the Spring container,
 * and the {@code <camelContext>} inside it is registered as a CamelContext bean.
 *
 * <p>camel-spring-boot's auto-configuration is {@code @ConditionalOnMissingBean} on
 * CamelContext, so the context declared in XML takes over the whole application while its
 * lifecycle (startup and graceful shutdown) is still managed by Spring Boot.
 */
@SpringBootApplication
@ImportResource("classpath:camel-context.xml")
public class CamelSession0to1Application {

    public static void main(String[] args) {
        SpringApplication.run(CamelSession0to1Application.class, args);
    }
}
