package org.iris_events.router.events;


import jakarta.annotation.security.RolesAllowed;
import org.iris_events.annotations.Message;

@Message(name = "user-authenticated", rolesAllowed = @RolesAllowed("**"))
public record UserAuthenticated(String userId) {
}
