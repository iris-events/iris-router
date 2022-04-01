package id.global.core.router.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.auth.jwt.Role;

@Message(name = "user-authenticated", rolesAllowed = { Role.AUTHENTICATED })
public record UserAuthenticated(String userId) {
}
