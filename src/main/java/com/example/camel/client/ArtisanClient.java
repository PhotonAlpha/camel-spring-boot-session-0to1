package com.example.camel.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;
import java.util.Map;

/**
 * A Spring HTTP interface: the declarative alternative to hand-writing an HTTP call.
 *
 * <p>Compared with the {@code http://} Camel component used by API-3, the contract here is
 * <b>compile-time checked</b> - the URL, the verb, the request type and the response type all
 * live in one place and the compiler enforces them at every call site.
 *
 * <p>The base URL comes from a property rather than being hard-coded, which works because
 * {@code HttpServiceClientConfig} wires Spring's embedded value resolver into the proxy factory.
 *
 * <p>No implementation is written: {@code HttpServiceProxyFactory} generates the proxy, and the
 * {@code httpexchange:} Camel component invokes it.
 */
@HttpExchange("${demo.remote.base-url}/buziVa/artisan")
public interface ArtisanClient {

    @PostExchange("list")
    ResponseEntity<List<MyAccountDetailsVO>> listUser(@RequestBody MyAccountDetailsVO user,
                                                      @RequestHeader HttpHeaders httpHeaders,
                                                      @RequestParam Map<String, String> params);
}
