package com.example.camel.client;

/**
 * The payload type of the external "artisan" service, both on the way in and on the way out.
 *
 * <p>A record is fine here because the Freemarker templates never touch it directly - the
 * {@code httpexchange:} component normalises the reply into plain Maps first (records have no
 * JavaBean getters, so a template would otherwise not see the fields).
 */
public record MyAccountDetailsVO(String userId,
                                 String userName,
                                 String channel,
                                 Double amount) {
}
