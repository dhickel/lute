package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.physics.box2d.Body;
import io.mindspice.lute.engine.singletons.GameConsts;

/** Mounted on Hoard's Dredge guards. Distances are in logical pixels. */
public final class DredgeLunge implements Component {
    public static final float COMMIT_RANGE = 80f;
    private static final float START_SPEED = 420f, END_SPEED = 180f;
    private long swing = -1;
    private float elapsed;

    public boolean beginStep(long attackId) {
        if (swing == attackId) { return true; }
        // The first fixed boundary accepts the commitment; frame zero is sampled on the next tick.
        swing = attackId;
        elapsed = 0;
        return false;
    }

    public void driveStep(Body body, Combat combat, Animation2D animation, float interval) {
        if (combat.getSensor() == null) {
            // A second tick in the same frame can inherit a contact impulse from the first.
            body.setLinearVelocity(0f, 0f);
            return;
        }
        float frame = animation.getFrameDuration() / animation.getSpeed();
        float window = (float) animation.getPlaybackDuration() - combat.getActiveAttack().hitFrame() * frame;
        float t = Math.min(1f, elapsed / window);
        float speed = (START_SPEED + (END_SPEED - START_SPEED) * t) * GameConsts.UNIT_SCALE;
        switch (combat.getAttackFacing()) {
            case LEFT -> body.setLinearVelocity(-speed, 0);
            case RIGHT -> body.setLinearVelocity(speed, 0);
            case UP -> body.setLinearVelocity(0, speed);
            case DOWN -> body.setLinearVelocity(0, -speed);
        }
        elapsed += interval;
    }
}
