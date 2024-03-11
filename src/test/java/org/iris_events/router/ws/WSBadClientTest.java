package org.iris_events.router.ws;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.websocket.CloseReason;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@QuarkusTest
public class WSBadClientTest {
    private final static Logger log = LoggerFactory.getLogger(WSBadClientTest.class);

    @TestHTTPResource("/v0/websocket")
    URI uri;

    @ParameterizedTest
    @ValueSource(strings = {"GlobaliD-iOS/4.8.0", "GlobaliD-Android/4.8.0"})
    public void testBadUserAgentOKHttp(String userAgent) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        var client = new OkHttpClient.Builder()
                .build();
        try {
            client.newWebSocket(new Request.Builder()
                            .header("User-Agent", userAgent)
                            .url(uri.toString().replace("http:", "ws:"))
                            .build()
                    , new WebSocketListener() {
                        @Override
                        public void onClosed(@NotNull okhttp3.WebSocket webSocket, int code, @NotNull String reason) {
                            log.info("onClose: {}, {}", code, reason);
                            onClosing(webSocket, code, reason);
                        }

                        @Override
                        public void onClosing(@NotNull okhttp3.WebSocket webSocket, int code, @NotNull String reason) {
                            log.info("onClosing: {}, {}", code, reason);
                            if (code == CloseReason.CloseCodes.CANNOT_ACCEPT.getCode()) {
                                latch.countDown();
                                log.info("Socket closed as expected");
                            }
                        }

                        @Override
                        public void onOpen(@NotNull okhttp3.WebSocket webSocket, @NotNull Response response) {
                            log.info("Connected");
                        }
                    }
            );
            Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "Socket should have failed to open");
        } finally {
            client.dispatcher().executorService().close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GlobaliD-iOS/4.9.0", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"})
    public void testGoodUserAgent(String userAgent) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try (var client = HttpClient.newHttpClient()) {
            var socket = client
                    .newWebSocketBuilder()
                    .header("User-Agent", userAgent)
                    .buildAsync(URI.create(uri.toString().replace("http:", "ws:")), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            log.info("Connected");
                            latch.countDown();
                            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Good client");
                        }
                    })
                    .join();
            Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS), "Socket should have connected successfully");
            client.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "4.2.0",
            "4.3.0"
    })
    public void testBadClientVersion(String clientVersion) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        HttpClient client = HttpClient.newHttpClient();
        try {
            var ws = client
                    .newWebSocketBuilder()
                    .header("x-client-version", clientVersion)
                    .buildAsync(URI.create(uri.toString().replace("http:", "ws:")), new WebSocket.Listener() {

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.warn("onError: {}", error.getMessage());
                            latch.countDown();
                            WebSocket.Listener.super.onError(webSocket, error);
                        }

                    })/*.exceptionally(throwable -> {
                        log.warn("Could not open ws connection: {}", throwable.getMessage());
                        latch.countDown();
                        return null;
                    })*/
                    .exceptionally(throwable -> {
                        log.warn("Could not open ws connection: {}", throwable.getMessage());
                        latch.countDown();
                        return null;
                    }).get(1, TimeUnit.SECONDS);


        } catch (Exception e) {
            latch.countDown();
            log.info("Exception: {}", e.getMessage());
        } finally {
            client.shutdownNow();
            client.close();
        }
        Assertions.assertTrue(latch.await(5, TimeUnit.SECONDS), "Socket should have failed to open");
    }


}
