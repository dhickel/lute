package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.concurrent.ThreadLocalRandom;

public final class ActorAi implements Component {
    public static final ComponentMapper<ActorAi> MAPPER = ComponentMapper.getFor(ActorAi.class);

    private static final float INVESTIGATE_SECONDS = 2f;
    private static final float ARRIVAL_PIXELS = 8f;

    public enum Mode { WANDER, ENGAGE, INVESTIGATE }

    private final String graphKey;
    private Mode mode = Mode.WANDER;
    private final Vector2 lastSeenFeet = new Vector2();
    private float investigateTimeLeft;
    private float wanderTimeLeft;
    private float walkDuration;
    private float walkHeading;
    private float walkTurn;

    public ActorAi(String graphKey) { this.graphKey = graphKey; }

    public String graphKey() { return graphKey; }
    public Mode mode() { return mode; }

    /** Update the mode from live sight and return a steering offset, if any. */
    public Vector2 updateTarget(Vector2 feet, Vector2 visiblePlayerFeet, float deltaTime) {
        if (visiblePlayerFeet != null) {
            if (mode == Mode.WANDER) { resetWander(); }
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
            return null;
        }
        return lastSeenFeet.cpy().sub(feet);
    }

    public void forgetTarget() {
        mode = Mode.WANDER;
        lastSeenFeet.setZero();
        investigateTimeLeft = 0f;
        resetWander();
    }

    public void steerWander(Vector2 direction, float deltaTime) {
        var random = ThreadLocalRandom.current();
        if (wanderTimeLeft == 0f || (walkDuration > 0f && deltaTime >= wanderTimeLeft)) {
            // Start a fresh pause on the first wander tick, including after investigation.
            wanderTimeLeft = random.nextFloat(.5f, 5f);
            walkDuration = 0f;
        } else {
            wanderTimeLeft -= deltaTime;
            if (wanderTimeLeft <= 0f) {
                walkDuration = random.nextFloat(1f, 5f);
                wanderTimeLeft = walkDuration;
                walkHeading = random.nextFloat(0f, 360f);
                walkTurn = random.nextFloat(-30f, 30f);
            }
        }

        if (walkDuration == 0f) {
            direction.setZero();
            return;
        }
        float progress = 1f - wanderTimeLeft / walkDuration;
        float eased = progress * progress * (3f - 2f * progress);
        float heading = walkHeading + walkTurn * eased;
        direction.set(MathUtils.cosDeg(heading), MathUtils.sinDeg(heading));
    }

    private void resetWander() {
        wanderTimeLeft = 0f;
        walkDuration = 0f;
    }
}
