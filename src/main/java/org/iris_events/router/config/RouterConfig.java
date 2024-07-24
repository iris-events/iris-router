package org.iris_events.router.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;

@ConfigMapping(prefix = "iris.router")
public interface RouterConfig {

    @WithDefault("[]")
    List<String> bannedUserAgents();

    @WithDefault("[]")
    List<String> bannedClientVersions();


    @WithDefault("[]")
    List<String> nonRpcEvents();


}
