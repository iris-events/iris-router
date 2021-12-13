package id.global.core.router.client;

import java.util.UUID;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Timestamp;

import io.envoyproxy.envoy.service.auth.v3.AttributeContext;
import io.envoyproxy.envoy.service.auth.v3.AuthorizationGrpc;
import io.envoyproxy.envoy.service.auth.v3.CheckRequest;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;

@ApplicationScoped
public class AuthClient {
    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    @Inject
    JWTParser jwtParser;

    private final AuthorizationGrpc.AuthorizationBlockingStub authorizationStub;

    @Inject
    AuthClient(@ConfigProperty(name = "id.global.core.router.auth-service") String authServer) {
        log.info("auth server: {}", authServer);
        Channel channel = ManagedChannelBuilder
                .forTarget(authServer)
                .usePlaintext()
                .build();
        authorizationStub = AuthorizationGrpc
                .newBlockingStub(channel);
    }

    @PreDestroy
    void shutdown() {

    }

    public JsonWebToken checkToken(String authToken) {
        var request = createRequest(authToken);

        var result = authorizationStub.check(request);

        log.info("result: {}", result);
        if (result.hasOkResponse()) {
            String jwtToken = null;
            String userId = null;
            log.info("ok response: {}", result.getOkResponse());
            for (var header : result.getOkResponse().getHeadersList()) {
                var key = header.getHeader().getKey();
                var value = header.getHeader().getValue();
                log.info("header: {}: {}", key, value);
                if ("Authorization".equals(key)) {
                    jwtToken = value.substring("Bearer ".length());
                } else if ("x-user-id".equals(key)) {
                    userId = value;
                }

            }
            log.info("user: {} jwt token: {}", userId, jwtToken);
            try {
                JsonWebToken jsonWebToken = jwtParser.parse(jwtToken);
                log.info("parsed token: {}", jwtToken);
                return jsonWebToken;
            } catch (ParseException e) {
                log.error("Could not parse token", e);
            }

        }
        if (result.hasDeniedResponse()) {
            log.info("denied response: {}", result.getDeniedResponse());
        }
        if (result.hasStatus()) {
            log.info("status code: {}, message: {}", result.getStatus().getCode(), result.getStatus().getMessage());
        }

        return null;
    }

    /*
     * {
     * "CheckRequest": {
     * "attributes": {
     * "source": {
     * "address": {
     * "Address": {
     * "SocketAddress": {
     * "address": "10.198.0.198",
     * "PortSpecifier": {
     * "PortValue": 55754
     * }
     * }
     * }
     * }
     * },
     * "destination": {
     * "address": {
     * "Address": {
     * "SocketAddress": {
     * "address": "100.99.13.255",
     * "PortSpecifier": {
     * "PortValue": 8080
     * }
     * }
     * }
     * }
     * },
     * "request": {
     * "time": {
     * "seconds": 1638898531,
     * "nanos": 76560000
     * },
     * "http": {
     * "id": "12952810781186521401",
     * "method": "GET",
     * "headers": {
     * ":authority": "api.globalid.dev",
     * ":method": "GET",
     * ":path": "/v1/identities/me",
     * ":scheme": "https",
     * "accept": "application/json, text/plain, ",
     * "accept-encoding": "br,gzip",
     * "accept-language": "en-US,en;q=0.9",
     * "authorization": "Bearer nnXUq1PoePmPCRytRG5wh97q52uMvHDdn61DH-DAZp4.M0g7_lOL6uGToUGe7SaFLoUPW_h-qWw2K_PP0Z0Du84",
     * "cloudfront-is-mobile-viewer": "false",
     * "if-none-match": "W/\"947-IhIwCeIupxMwwsPP06F41/wnYxE\"",
     * "referer": "https://globalid.dev/app/groups/a5db4bde-a196-4bab-adee-6a6a848628a2/messages/68530",
     * "user-agent":
     * "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/96.0.4664.45 Safari/537.36",
     * "via": "2.0 996a6dcadb486dbb9da5040a9ab13af3.cloudfront.net (CloudFront)",
     * "x-amz-cf-id": "7qiOx2h5cAedKkkBa4d5g6u2p6PWHZPs3dgfRw0chvUeg512QBkYzw==",
     * "x-amzn-trace-id": "Root=1-61af9b63-40b408ad454499be62e99b8b",
     * "x-envoy-external-address": "64.252.88.102",
     * "x-forwarded-for": "91.246.226.128, 64.252.88.102",
     * "x-forwarded-port": "443",
     * "x-forwarded-proto": "https",
     * "x-gid-origin": "webapp",
     * "x-request-id": "23f2c855-c031-4121-a23e-29f3145291ae"
     * },
     * "path": "/v1/identities/me",
     * "host": "api.globalid.dev",
     * "scheme": "https",
     * "protocol": "HTTP/1.1"
     * }
     * },
     * "context_extensions": {
     * "config_id": "gloo-system.gid-jwt",
     * "source_name": "",
     * "source_type": "route"
     * },
     * "metadata_context": {}
     * }
     * },
     * "State": null
     * }
     */

    private CheckRequest createRequest(String token) {
        return CheckRequest
                .newBuilder()
                .setAttributes(
                        AttributeContext
                                .newBuilder()
                                .setRequest(AttributeContext.Request
                                        .newBuilder()
                                        .setTime(Timestamp.getDefaultInstance())
                                        .setHttp(AttributeContext.HttpRequest
                                                .newBuilder()
                                                .setPath("/v0/websocket")
                                                .setHost("api.globalid.dev")
                                                .setMethod("GET")
                                                .setId(UUID.randomUUID().toString())
                                                .setScheme("https")
                                                .setSize(0)
                                                .putHeaders("authorization", "bearer " + token)
                                                .build())

                                )

                                .putContextExtensions("config_id", "gloo-system.gid-jwt")
                                .putContextExtensions("source_type", "route")
                                .putContextExtensions("source_name", "")
                                .build())
                .build();
    }

}
