package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

public final class ActorAi implements Component {
    public static final ComponentMapper<ActorAi> MAPPER = ComponentMapper.getFor(ActorAi.class);

    private final String graphKey;
    // Start idle briefly so the authored spawn facing is visible.
    private float wanderTimeLeft = .25f;

    public ActorAi(String graphKey) { this.graphKey = graphKey; }

    public String graphKey() { return graphKey; }

    public boolean advanceWander(float deltaTime) {
        wanderTimeLeft -= deltaTime;
        return wanderTimeLeft <= 0f;
    }

    public void resetWander(float duration) { wanderTimeLeft = duration; }
}
