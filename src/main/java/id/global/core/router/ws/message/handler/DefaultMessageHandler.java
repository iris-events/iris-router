package id.global.core.router.ws.message.handler;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;
import id.global.core.router.service.BackendService;

@ApplicationScoped
@DefaultHandler
public class DefaultMessageHandler implements MessageHandler {

    @Inject
    BackendService backendService;

    @Override
    public void handle(UserSession userSession, RequestWrapper requestWrapper) {
        backendService.sendIrisEventToBackend(userSession, requestWrapper.clientTraceId(), requestWrapper);
    }
}
