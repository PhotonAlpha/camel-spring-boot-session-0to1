package com.example.camel.bean;

import com.example.camel.client.MyAccountDetailsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The "artisan" external system that {@link com.example.camel.client.ArtisanClient} talks to.
 * A plain Spring MVC controller, so API-7 exercises a real HTTP round trip offline.
 *
 * <p>It echoes back what it received, which makes the headers and query parameters bound by the
 * {@code httpexchange:} component visible in the reply and in the log.
 */
@RestController
@RequestMapping("/buziVa/artisan")
public class MockArtisanController {

    private static final Logger log = LoggerFactory.getLogger(MockArtisanController.class);

    @PostMapping("/list")
    public List<MyAccountDetailsVO> list(@RequestBody MyAccountDetailsVO user,
                                         @RequestHeader HttpHeaders headers,
                                         @RequestParam Map<String, String> params) {
        log.info("[MOCK-ARTISAN] body={}", user);
        log.info("[MOCK-ARTISAN] X-Trace-Id={} X-Channel={}",
                headers.getFirst("X-Trace-Id"), headers.getFirst("X-Channel"));
        log.info("[MOCK-ARTISAN] query params={}", params);

        double base = user.amount() == null ? 0d : user.amount();
        List<MyAccountDetailsVO> result = List.of(
                new MyAccountDetailsVO(user.userId() + "-PRIMARY", user.userName(),
                        user.channel(), round2(base)),
                new MyAccountDetailsVO(user.userId() + "-SAVINGS", user.userName(),
                        user.channel(), round2(base * 0.3)),
                new MyAccountDetailsVO(user.userId() + "-CREDIT", user.userName(),
                        user.channel(), round2(base * 1.5)));
        log.info("[MOCK-ARTISAN] returning {} accounts", result.size());
        return result;
    }

    private static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
