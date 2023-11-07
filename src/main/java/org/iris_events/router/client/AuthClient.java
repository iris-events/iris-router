package org.iris_events.router.client;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@ApplicationScoped
public class AuthClient {
    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    @Inject
    JWTParser jwtParser;

    public JsonWebToken checkToken(String jwtToken) {
        MDC.put("token", jwtToken);
        try {
            return jwtParser.parse(jwtToken);
        } catch (ParseException e) {

            log.info("token: {}", jwtToken);
            if (e.getCause() instanceof InvalidJwtException ije) {
                log.info("invalid jwt, context: {}, details: {}", ije.getJwtContext(),ije.getErrorDetails());
            }
            log.warn("Could not parse token", e);

        } finally {
            MDC.remove("token");
        }
        return null;
    }

}
