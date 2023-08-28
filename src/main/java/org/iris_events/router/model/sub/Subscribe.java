package org.iris_events.router.model.sub;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;

import java.util.List;
import java.util.Objects;

@Message(name = "subscribe", scope = Scope.FRONTEND)
@RegisterForReflection
public class Subscribe {
    private final List<Resource> resources;

    public Subscribe(List<Resource> resources) {
        this.resources = resources;
    }

    public List<Resource> getResources() {
        return resources;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Subscribe) obj;
        return Objects.equals(this.resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resources);
    }

    @Override
    public String toString() {
        return "Subscribe[" +
                "resources=" + resources + ']';
    }

}
