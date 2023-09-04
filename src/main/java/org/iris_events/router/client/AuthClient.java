package org.iris_events.router.client;

import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthClient {
    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    @Inject
    JWTParser jwtParser;

    public JsonWebToken checkToken(String jwtToken) {

        try {
            return jwtParser.parse(jwtToken);
        } catch (ParseException e) {
            log.warn("Could not parse token", e);
        }
        return null;
    }

}
