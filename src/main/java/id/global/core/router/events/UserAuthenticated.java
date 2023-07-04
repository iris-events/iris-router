package id.global.core.router.events;

import id.global.common.auth.jwt.Role;
import id.global.iris.common.annotations.Message;

//@Message(name = "user-authenticated", rolesAllowed = @RolesAllowed("**"))
@Message(name = "user-authenticated", rolesAllowed = Role.AUTHENTICATED)
public record UserAuthenticated(String userId) {
}
