package org.iris_events.router.events;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface RouterEvent {
    @JsonIgnore
    String getName();
}
