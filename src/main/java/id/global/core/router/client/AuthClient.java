package id.global.core.router.client;

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
            JsonWebToken jsonWebToken = jwtParser.parse(jwtToken);
            log.info("parsed token: {}", jwtToken);
            return jsonWebToken;
        } catch (ParseException e) {
            log.error("Could not parse token", e);
        }

        return null;
    }

}
