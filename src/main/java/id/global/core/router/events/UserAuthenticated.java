package id.global.core.router.events;

import id.global.common.annotations.iris.Message;
import id.global.common.auth.jwt.Role;

@Message(name = "user-authenticated", rolesAllowed = { Role.AUTHENTICATED })
public record UserAuthenticated(String userId) {
}
