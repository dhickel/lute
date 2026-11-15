package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

public class Animation2D implements Component {
    public static final ComponentMapper<Animation2D> MAPPER = ComponentMapper.getFor(Animation2D.class);

    private final String atlasId;
    private final String atlasKey;
    private AnimationType type;
    private Facing.Direction fdirection;
    private Animation.PlayMode mode;
    private float speed;
    private float previousSpeed;
    private float frameDuration;
    private float stateTime;
    private Animation<TextureRegion> animation;
    boolean dirty;
    private Attack attack;
    private boolean attackSoundEmitted;

    public record Attack(float speed, int soundFrame, String soundId) {
        public Attack {
            if (!Float.isFinite(speed) || speed <= 0 || soundFrame < 0
                || soundId == null || soundId.isBlank()) {
                throw new IllegalArgumentException("Invalid attack animation timing or sound ID");
            }
        }
    }

    public void configureAttack(Attack attack) { this.attack = attack; }

    public void validateAttackFrames() {
        if (attack != null && type == AnimationType.ATTACK) {
            int count = animation.getKeyFrames().length;
            if (attack.soundFrame() >= count) {
                throw new IllegalArgumentException("Attack sound frame must be less than animation frame count: " + count);
            }
        }
    }

    public boolean isAttackReady() {
        return attack != null && type == AnimationType.ATTACK && !dirty && animation != null;
    }

    public boolean hasReachedFrame(int frame) {
        return isAttackReady() && stateTime >= frame * frameDuration;
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
                       int keyFrames
    ) {
        this.atlasId = atlasId;
        this.atlasKey = atlasKey;
        this.type = type;
        this.fdirection = Facing.Direction.DOWN;
        this.mode = mode;
        this.speed = speed;
        this.frameDuration = 1f / keyFrames;
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
        speed = attack.speed();
        attackSoundEmitted = false;
    }

    public void endAttack() {
        speed = previousSpeed;
    }

    public boolean isFinished() {
        return animation.isAnimationFinished(stateTime);
    }

    public float incAndGetStateTime(float delta) {
        stateTime += delta * speed;
        return stateTime;
    }

    public enum AnimationType {
        IDLE("idle"), WALK("walk"), ATTACK("attack"), DEFAULT("");

        public final String atlasKey;


        AnimationType(String s) {
            this.atlasKey = s;
        }
    }
}
