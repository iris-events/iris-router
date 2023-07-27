package org.iris_events.router.ws.message.handler;

import org.iris_events.router.model.RequestWrapper;
import org.iris_events.router.model.UserSession;

public interface MessageHandler {
    void handle(UserSession userSession, RequestWrapper requestWrapper);
}
