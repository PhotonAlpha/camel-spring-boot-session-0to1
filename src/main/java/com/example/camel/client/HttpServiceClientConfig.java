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
 * <p>Spring Framework 6.2 has no auto-registration for HTTP interfaces - {@code @ImportHttpServices}
 * and {@code @HttpServiceClient} only arrive in Framework 7 / Boot 4 - so each client is declared
 * as a bean here. That is the one piece of boilerplate this approach costs today.
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
