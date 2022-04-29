package id.global.core.router.ws.message.handler;

import static id.global.core.router.ws.message.handler.SubscribeMessageHandler.EVENT_NAME;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.core.router.events.ErrorCode;
import id.global.core.router.events.ErrorEvent;
import id.global.core.router.events.ErrorType;
import id.global.core.router.events.UserAuthenticated;
import id.global.core.router.events.UserAuthenticatedEvent;
import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.Subscribe;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
import id.global.core.router.service.WebsocketRegistry;

@ApplicationScoped
@EventType(EVENT_NAME)
public class SubscribeMessageHandler implements MessageHandler {

    public static final String EVENT_NAME = "subscribe";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    WebsocketRegistry websocketRegistry;

    @Inject
    BackendService backendService;

    @Override
    public void handle(UserSession userSession, RequestWrapper requestWrapper) {
        final var payload = requestWrapper.payload();
        final var subscribe = objectMapper.convertValue(payload, Subscribe.class);
        final var clientTraceId = requestWrapper.clientTraceId();
        subscribe(userSession, subscribe, clientTraceId);
    }

    private void subscribe(final UserSession userSession, final Subscribe subscribe, final String clientTraceId) {
        if (subscribe.getToken() != null) {
            final var loginSucceeded = websocketRegistry.login(userSession, subscribe.getToken());
            if (loginSucceeded) {
                final var userAuthenticatedEvent = new UserAuthenticatedEvent();
                userSession.sendEvent(userAuthenticatedEvent, clientTraceId);
                final var userAuthenticated = new UserAuthenticated(userSession.getUserId());
                // TODO: do not emit yet, we need to declare queue first
                // sendIrisEventToBackend(userSession, clientTraceId, userAuthenticated);
            } else {
                final var errorEvent = new ErrorEvent(ErrorType.AUTHORIZATION_FAILED, ErrorCode.AUTHORIZATION_FAILED,
                        "authorization failed");
                userSession.sendEvent(errorEvent, clientTraceId);
                // when token is present, login must succeed
                return;
            }
        }
        if (subscribe.getHeartbeat() != null) {
            userSession.setSendHeartbeat(subscribe.getHeartbeat());
        }

        subscribeResources(userSession, subscribe, clientTraceId);
    }

    private void subscribeResources(final UserSession userSession, final Subscribe subscribe, final String clientTraceId) {
        final var resourceSubscriptions = subscribe.getResources();
        if (resourceSubscriptions == null) {
            return;
        }

        // create new subscription service specific event to omit token
        final var subscribeResources = new id.global.iris.irissubscription.Subscribe(subscribe.getResources());
        backendService.sendIrisEventToBackend(userSession, clientTraceId, subscribeResources);
    }
}
