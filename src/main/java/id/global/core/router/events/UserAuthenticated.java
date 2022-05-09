package id.global.core.router.events;

import id.global.common.auth.jwt.Role;
import id.global.common.iris.annotations.Message;

@Message(name = "user-authenticated", rolesAllowed = { Role.AUTHENTICATED })
public record UserAuthenticated(String userId) {
}
