package com.example.camel.client;

import com.example.camel.component.httpexchange.HttpExchangeContext;
import org.apache.camel.Exchange;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.service.invoker.HttpRequestValues;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * The single place that decides which per-request headers {@link ArtisanClient} sends.
 *
 * <p>An {@link HttpRequestValues.Processor} (Spring Framework 7) is registered once on the
 * {@code HttpServiceProxyFactory} and runs for <b>every</b> method of every proxy that factory
 * builds, after the arguments are resolved and before the request is sent. That makes it the
 * HTTP-interface counterpart of a servlet filter: methods declare their business arguments only,
 * and the header policy lives here.
 *
 * <p>Three layers, each used for what it is good at:
 * <ul>
 *   <li>constant headers - {@code @HttpExchange(headers = ...)} on the interface (X-Client-Id);</li>
 *   <li>per-request headers - this processor, reading the Camel Exchange (X-Trace-Id, X-Channel);</li>
 *   <li>a one-off header for a single method - still possible with {@code @RequestHeader} on that
 *       method, which the {@code httpexchange:} component keeps binding from Exchange headers.</li>
 * </ul>
 * The trace context itself (W3C {@code traceparent}) is not set here: the observation-instrumented
 * RestClient adds it on its own.
 */
@Component("artisanHeaders")
public class ArtisanHeaders implements HttpRequestValues.Processor {

    @Override
    public void process(Method method, MethodParameter[] parameters, Object[] arguments,
                        HttpRequestValues.Builder builder) {
        // The factory is shared; only apply the artisan policy to the artisan client
        if (method.getDeclaringClass() != ArtisanClient.class) {
            return;
        }
        Exchange exchange = HttpExchangeContext.current();
        if (exchange == null) {
            // Called from plain Java rather than through the httpexchange: component
            return;
        }
        builder.addHeader("X-Trace-Id", exchange.getExchangeId());
        builder.addHeader("X-Channel", channelOf(exchange));
    }

    /** An explicit X-Channel header wins, otherwise the channel field of the order, otherwise WEB. */
    private static String channelOf(Exchange exchange) {
        String header = exchange.getIn().getHeader("X-Channel", String.class);
        if (header != null) {
            return header;
        }
        if (exchange.getIn().getBody() instanceof Map<?, ?> order && order.get("channel") != null) {
            return String.valueOf(order.get("channel"));
        }
        return "WEB";
    }
}
