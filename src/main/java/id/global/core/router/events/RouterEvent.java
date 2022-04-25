package id.global.core.router.events;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface RouterEvent {
    @JsonIgnore
    String getName();
}
