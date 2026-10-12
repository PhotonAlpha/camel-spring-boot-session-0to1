package com.example.camel.client;

import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Builds the {@code @HttpExchange} proxies.
 *
 * <p>Each client is declared as a bean by hand. Framework 7 / Boot 4 can register HTTP interfaces
 * automatically ({@code @ImportHttpServices}), but the explicit wiring is kept on purpose so the
 * teaching code shows how the proxy is actually built.
 *
 * <p>{@code embeddedValueResolver} is what makes {@code @HttpExchange("${demo.remote.base-url}/...")}
 * resolve against the environment; without it the annotation value is taken literally.
 */
@Configuration
public class HttpServiceClientConfig {

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder,
                                                           ConfigurableBeanFactory beanFactory) {
        RestClient restClient = builder.build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .embeddedValueResolver(new EmbeddedValueResolver(beanFactory))
                .build();
    }

    /**
     * The bean name {@code artisanClient} is what the route refers to in
     * {@code httpexchange:artisanClient/listUser}.
     */
    @Bean
    public ArtisanClient artisanClient(HttpServiceProxyFactory factory) {
        return factory.createClient(ArtisanClient.class);
    }
}
