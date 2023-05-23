package id.global.core.router.ws.message.handler;

import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;
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
