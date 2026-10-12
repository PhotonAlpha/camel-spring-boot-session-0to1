package com.example.camel.config;

import org.apache.camel.component.http.HttpClientConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.httpclient5.LogbookHttpExecHandler;

/**
 * Hooks Logbook into Camel's {@code http://} component, which API-3 calls through its ProducerTemplate.
 *
 * <p>camel-http builds its own Apache HttpClient 5, so the RestClient interceptor that covers API-7
 * never sees these calls. Camel offers {@link HttpClientConfigurer} as the hook into that builder;
 * the component picks this bean up through
 * {@code camel.component.http.http-client-configurer} in application.yml, so every
 * {@code http://} endpoint is logged without touching a URI.
 *
 * <p>{@link LogbookHttpExecHandler} rather than Logbook's request/response interceptor pair: on the
 * classic (blocking) client the exec handler sits around the whole exchange and can buffer the
 * response body for logging while still handing it on to Camel unread.
 *
 * <p>Registered <i>first</i>, i.e. outermost: it logs one entry per logical call (not per retry or
 * redirect) and sees the response after decompression. The price is that the logged request has
 * no Content-Type / Content-Length - HttpClient keeps those on the entity and only turns them into
 * headers further down the chain.
 */
@Configuration
public class LogbookHttpClientConfig {

    @Bean
    public HttpClientConfigurer logbookHttpClientConfigurer(Logbook logbook) {
        return builder -> builder.addExecInterceptorFirst("logbook", new LogbookHttpExecHandler(logbook));
    }
}
