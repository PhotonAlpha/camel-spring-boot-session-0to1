package com.example.camel.bean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A stand-in for an "external system" (a plain Spring MVC controller,
 * <b>not part of any Camel route</b>).
 *
 * <p>In API-3 the ProducerTemplate calls this over real HTTP, so the outbound-call demo
 * still works end to end on a machine with no network access.
 */
@RestController
@RequestMapping("/mock/remote")
public class MockRemoteApiController {

    private static final Logger log = LoggerFactory.getLogger(MockRemoteApiController.class);

    @PostMapping("/fx-rate")
    public Map<String, Object> fxRate(@RequestBody Map<String, Object> req) {
        log.info("[MOCK-REMOTE] external call received: {}", req);
        String currency = String.valueOf(req.getOrDefault("currency", "USD"));
        double rate = switch (currency) {
            case "USD" -> 7.24;
            case "EUR" -> 7.86;
            case "JPY" -> 0.048;
            default -> 1.0;
        };
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("provider", "mock-fx-service");
        resp.put("currency", currency);
        resp.put("rate", rate);
        log.info("[MOCK-REMOTE] returning fx rate: {}", resp);
        return resp;
    }
}
