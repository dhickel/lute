package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

public final class ActorAi implements Component {
    public static final ComponentMapper<ActorAi> MAPPER = ComponentMapper.getFor(ActorAi.class);

    private static final float INVESTIGATE_SECONDS = 2f;
    private static final float ARRIVAL_PIXELS = 8f;

    public enum Mode { WANDER, ENGAGE, INVESTIGATE }

    private final String graphKey;
    private Mode mode = Mode.WANDER;
    private final Vector2 lastSeenFeet = new Vector2();
    private float investigateTimeLeft;
    // Start idle briefly so the authored spawn facing is visible.
    private float wanderTimeLeft = .25f;

    public ActorAi(String graphKey) { this.graphKey = graphKey; }

    public String graphKey() { return graphKey; }
    public Mode mode() { return mode; }

    /** Update the mode from live sight and return a steering offset, if any. */
    public Vector2 updateTarget(Vector2 feet, Vector2 visiblePlayerFeet, float deltaTime) {
        if (visiblePlayerFeet != null) {
            lastSeenFeet.set(visiblePlayerFeet);
            mode = Mode.ENGAGE;
            return visiblePlayerFeet.sub(feet);
        }
        if (mode == Mode.WANDER) { return null; }
        if (mode == Mode.ENGAGE) {
            mode = Mode.INVESTIGATE;
            investigateTimeLeft = INVESTIGATE_SECONDS;
        } else {
            investigateTimeLeft -= deltaTime;
        }
        float arrival = ARRIVAL_PIXELS * GameConsts.UNIT_SCALE;
        if (investigateTimeLeft <= 0f || lastSeenFeet.dst2(feet) <= arrival * arrival) {
            forgetTarget();
            resetWander(0f);
            return null;
        }
        return lastSeenFeet.cpy().sub(feet);
    }

    public void forgetTarget() {
        mode = Mode.WANDER;
        lastSeenFeet.setZero();
        investigateTimeLeft = 0f;
    }

    public boolean advanceWander(float deltaTime) {
        wanderTimeLeft -= deltaTime;
        return wanderTimeLeft <= 0f;
    }

    public void resetWander(float duration) { wanderTimeLeft = duration; }
}
