package org.iris_events.router.events;


import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.annotation.security.RolesAllowed;
import org.iris_events.annotations.Message;

@RegisterForReflection
@Message(name = "user-authenticated", rolesAllowed = @RolesAllowed("**"))
public record UserAuthenticated(String userId) {
}
