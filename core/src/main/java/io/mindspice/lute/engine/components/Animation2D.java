package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.Map;

public class Animation2D implements Component {
    public static final ComponentMapper<Animation2D> MAPPER = ComponentMapper.getFor(Animation2D.class);

    private final String atlasId;
    private final String atlasKey;
    private AnimationType type;
    private Facing.Direction fdirection;
    private Animation.PlayMode mode;
    private float speed;
    private float previousSpeed;
    private final float baseFrameDuration;
    private float frameDuration;
    private float stateTime;
    private double deathSeconds;
    private boolean bowReleased;
    private float openingHold;
    private Animation<TextureRegion> animation;
    boolean dirty;
    private Attack attack;
    private Attack queuedAttack;
    private Map<String, Long> requiredAttackFrames = Map.of();
    private boolean attackSoundEmitted;

    public record Attack(String action, float speed, float frameInterval, int soundFrame, String soundId) { }

    public void configureAttack(Attack attack) { this.attack = attack; }

    // Keep the rendered pose and its metadata in sync until the next animation pass.
    public void queueAttack(Attack attack) { queuedAttack = attack; openingHold = 0f; }

    /** Show the authored first pose without advancing hit frames during a short AI windup. */
    public void holdOpeningPose(float seconds) { openingHold = seconds; }

    public void restartQueuedAttack() {
        if (queuedAttack == null) { return; }
        attack = queuedAttack;
        queuedAttack = null;
        resetAttackPlayback();
    }

    public void setRequiredAttackFrames(Map<String, Long> frames) { requiredAttackFrames = Map.copyOf(frames); }

    public Map<String, Long> getRequiredAttackFrames() { return requiredAttackFrames; }

    public String getAction() {
        return type == AnimationType.ATTACK && attack != null ? attack.action() : type.atlasKey;
    }

    public String regionKey(String action, Facing.Direction direction) {
        return (atlasKey.isEmpty() ? "" : atlasKey + "/") + action + "_" + direction.atlasKey;
    }

    public float getStateTime() { return stateTime; }

    public void setStateTime(float stateTime) { this.stateTime = stateTime; }

    public int getFrameIndex() { return animation.getKeyFrameIndex(stateTime); }

    public boolean isAttackReady() {
        return attack != null && type == AnimationType.ATTACK && !dirty && animation != null;
    }

    public boolean hasReachedFrame(int frame) {
        return isAttackReady() && openingHold <= 0f && stateTime >= frame * frameDuration;
    }

    public String consumeAttackSound() {
        if (!isAttackReady() || attackSoundEmitted || stateTime < attack.soundFrame() * frameDuration) { return null; }
        attackSoundEmitted = true;
        return attack.soundId();
    }

    public Animation2D(String atlasId,
                       String atlasKey,
                       AnimationType type,
                       Animation.PlayMode mode,
                       float speed,
                       float frameInterval
    ) {
        this.atlasId = atlasId;
        this.atlasKey = atlasKey;
        this.type = type;
        this.fdirection = Facing.Direction.DOWN;
        this.mode = mode;
        this.speed = speed;
        this.baseFrameDuration = frameInterval;
        this.frameDuration = frameInterval;
        this.stateTime = 0f;
        this.animation = null;
        this.dirty = true;
    }

    public void setAnimation(Animation<TextureRegion> animation, Facing.Direction direction) {
        this.animation = animation;
        this.fdirection = direction;
        stateTime = 0f;
        dirty = false;
    }

    public Facing.Direction getFdirection() {
        return fdirection;
    }

    public Animation<TextureRegion> getAnimation() {
        return animation;
    }

    public String getAtlasId() {
        return atlasId;
    }

    public String getAtlasKey() {
        return atlasKey;
    }


    public AnimationType getType() {
        return type;
    }

    public Animation.PlayMode getPlayMode() {
        return mode;
    }

    public float getSpeed() {
        return speed;
    }

    public float getFrameDuration() {
        return frameDuration;
    }

    public void setType(AnimationType type) {
        this.type = type;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setMode(Animation.PlayMode mode) {
        this.mode = mode;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public void beginAttack() {
        previousSpeed = speed;
        if (queuedAttack != null) { restartQueuedAttack(); }
        else { resetAttackPlayback(); }
    }

    private void resetAttackPlayback() {
        speed = attack.speed();
        frameDuration = attack.frameInterval();
        bowReleased = false;
        attackSoundEmitted = false;
        dirty = true;
    }

    public void releaseBow() {
        bowReleased = true;
    }

    public float advancePlayback(float delta, boolean drawingBow) {
        if (type == AnimationType.DEATH) {
            deathSeconds += delta;
            stateTime = (float) Math.min(deathSeconds * speed, animation.getAnimationDuration());
        } else if (bowReleased) {
            stateTime = (BowAim.FULL_DRAW_FRAME + 1) * frameDuration;
            bowReleased = false;
        } else {
            if (type == AnimationType.ATTACK && openingHold > 0f) {
                float held = Math.min(delta, openingHold);
                openingHold -= held;
                delta -= held;
            }
            incAndGetStateTime(delta);
            if (drawingBow) { stateTime = Math.min(stateTime, BowAim.FULL_DRAW_FRAME * frameDuration); }
        }
        return stateTime;
    }

    public void endAttack() {
        bowReleased = false;
        speed = previousSpeed;
        frameDuration = baseFrameDuration;
    }

    public double getDeathSeconds() { return deathSeconds; }

    public void setDeathSeconds(double seconds) { deathSeconds = seconds; }

    public double getPlaybackDuration() { return (double) animation.getAnimationDuration() / speed; }

    public boolean isFinished() {
        return animation.isAnimationFinished(stateTime);
    }

    public float incAndGetStateTime(float delta) {
        stateTime += delta * speed;
        return stateTime;
    }

    public enum AnimationType {
        IDLE("idle"), WALK("walk"), ATTACK("attack"), DEATH("death"), DEFAULT("");

        public final String atlasKey;


        AnimationType(String s) {
            this.atlasKey = s;
        }
    }
}
