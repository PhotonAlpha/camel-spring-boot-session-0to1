package com.example.camel.client;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import org.zalando.logbook.spring.LogbookClientHttpRequestInterceptor;

/**
 * Builds the {@code @HttpExchange} proxies.
 *
 * <p>Each client is declared as a bean by hand. Framework 7 / Boot 4 can register HTTP interfaces
 * automatically ({@code @ImportHttpServices}), but the explicit wiring is kept on purpose so the
 * teaching code shows how the proxy is actually built.
 *
 * <p>What is plugged in, and at which level:
 * <ul>
 *   <li>{@code embeddedValueResolver} - makes {@code ${...}} in {@code @HttpExchange} url and
 *       headers resolve against the environment; without it they are taken literally;</li>
 *   <li>{@code httpRequestValuesProcessor} - {@link ArtisanHeaders}, the per-request header policy,
 *       applied to every interface method;</li>
 *   <li>{@code requestInterceptor} on the RestClient - Logbook, which logs every request and
 *       response on the wire (after the headers above have been added).</li>
 * </ul>
 */
@Configuration
public class HttpServiceClientConfig {

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder,
                                                           ConfigurableBeanFactory beanFactory,
                                                           ArtisanHeaders artisanHeaders,
                                                           LogbookClientHttpRequestInterceptor logbookInterceptor) {
        RestClient restClient = builder
                .requestInterceptor(logbookInterceptor)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .embeddedValueResolver(new EmbeddedValueResolver(beanFactory))
                .httpRequestValuesProcessor(artisanHeaders)
                .build();
    }

    /**
     * The bean name {@code artisanClient} is what the routes refer to in
     * {@code httpexchange:artisanClient/<method>}.
     */
    @Bean
    public ArtisanClient artisanClient(HttpServiceProxyFactory factory) {
        return factory.createClient(ArtisanClient.class);
    }
}
