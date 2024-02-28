package org.iris_events.router.config;

import io.quarkus.runtime.annotations.ConfigItem;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithDefaults;

import java.util.List;

@ConfigMapping(prefix = "iris.router")
public interface RouterConfig {

    List<String> bannedUserAgents();

    List<String> bannedClientVersions();


}
