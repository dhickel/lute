package io.mindspice.lute.engine.components;

import com.badlogic.gdx.physics.box2d.Body;
import io.mindspice.lute.engine.singletons.GameConsts;

/** Per-actor drive state for fixed-step lunge playback. */
public final class LungeMotion {
    private static final float DRIVE_DISTANCE = 48f;
    private long swing = -1;
    private float elapsed;

    public boolean beginStep(long attackId) {
        if (swing == attackId) { return true; }
        // The first fixed boundary accepts the commitment; frame zero is sampled on the next tick.
        swing = attackId;
        elapsed = 0;
        return false;
    }

    /** Returns true on the final movement tick. */
    public boolean driveStep(Body body, Combat combat, Animation2D animation, float interval) {
        if (!combat.isAttacking() || combat.getSensor() == null) {
            // A second tick in the same frame can inherit a contact impulse from the first.
            body.setLinearVelocity(0f, 0f);
            return false;
        }
        float frame = animation.getFrameDuration() / animation.getSpeed();
        float window = (float) animation.getPlaybackDuration() - combat.getActiveAttack().hitFrame() * frame;
        float from = Math.min(1f, elapsed / window);
        // The accelerated windup can cross the hit cue between ticks. Finish the drive
        // on the last live tick even when that leaves a fraction of the nominal window.
        boolean lastLiveStep = animation.getAnimation().isAnimationFinished(animation.getStateTime() + interval * animation.getSpeed());
        float to = lastLiveStep ? 1f : Math.min(1f, (elapsed + interval) / window);
        // Integrate the easing over this tick so the full hitbox window covers the configured distance.
        float fraction = (to - from) * (1.4f - .4f * (from + to));
        float speed = DRIVE_DISTANCE * GameConsts.UNIT_SCALE * fraction / interval;
        switch (combat.getAttackFacing()) {
            case LEFT -> body.setLinearVelocity(-speed, 0);
            case RIGHT -> body.setLinearVelocity(speed, 0);
            case UP -> body.setLinearVelocity(0, speed);
            case DOWN -> body.setLinearVelocity(0, -speed);
        }
        elapsed = to == 1f ? window : elapsed + interval;
        return from < 1f && to == 1f;
    }
}
