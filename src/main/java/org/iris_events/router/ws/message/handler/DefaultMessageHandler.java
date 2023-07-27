package org.iris_events.router.ws.message.handler;

import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;
import org.iris_events.router.service.BackendService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@DefaultHandler
public class DefaultMessageHandler implements MessageHandler {

    @Inject
    BackendService backendService;

    @Override
    public void handle(UserSession userSession, RequestWrapper requestWrapper) {
        if (!userSession.isValid()) {
            userSession.sendSessionInvalidError(requestWrapper.clientTraceId());
            return;
        }
        backendService.sendFrontendEvent(userSession, requestWrapper);
    }
}
