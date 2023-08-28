package org.iris_events.router.model.sub;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;

@RegisterForReflection
@Message(name = "session-closed", scope = Scope.INTERNAL)
public record SessionClosed(@JsonProperty("user_id") String userId, @JsonProperty("session_id") String sessionId) {
}
