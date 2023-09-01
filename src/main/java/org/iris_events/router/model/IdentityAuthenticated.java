package org.iris_events.router.model;

import java.util.UUID;

import org.iris_events.annotations.Message;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@Message(name = "identity/authenticated")
public record IdentityAuthenticated(@JsonProperty("identity_id") UUID identityId) {
}
