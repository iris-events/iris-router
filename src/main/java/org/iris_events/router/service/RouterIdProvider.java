package org.iris_events.router.service;

import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.UUID;

@ApplicationScoped
@Unremovable
@Startup
public class RouterIdProvider {


    private static String routerId;

    public RouterIdProvider(@ConfigProperty(name = "quarkus.uuid") UUID routerId) {
        RouterIdProvider.routerId = routerId.toString();
        Log.warn("router id: "+routerId);
    }

    public static String routerId() {
        return routerId;
    }
}
