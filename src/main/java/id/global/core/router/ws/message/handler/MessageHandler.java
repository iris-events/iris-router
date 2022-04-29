package id.global.core.router.ws.message.handler;

import id.global.core.router.model.RequestWrapper;
import id.global.core.router.model.UserSession;

public interface MessageHandler {
    void handle(UserSession userSession, RequestWrapper requestWrapper);
}
