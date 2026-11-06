package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.mindspice.lute.engine.assets.AtlasAsset;

public class Animation2D implements Component {
    public static final ComponentMapper<Animation2D> MAPPER = ComponentMapper.getFor(Animation2D.class);

    private final AtlasAsset atlasAsset;
    private final String atlasKey;
    private AnimationType type;
    private Facing.Direction fdirection;
    private Animation.PlayMode mode;
    private float speed;
    private float stateTime;
    private Animation<TextureRegion> animation;
    boolean dirty;

    public Animation2D(AtlasAsset atlasAsset,
                       String atlasKey,
                       AnimationType type,
                       Animation.PlayMode mode,
                       float speed
    ) {
        this.atlasAsset = atlasAsset;
        this.atlasKey = atlasKey;
        this.type = type;
        this.fdirection = Facing.Direction.DOWN;
        this.mode = mode;
        this.speed = speed;
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

    public AtlasAsset getAtlasAsset() {
        return atlasAsset;
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

    public boolean isFinished() {
        return animation.isAnimationFinished(stateTime);
    }

    public float incAndGetStateTime(float delta) {
        stateTime += delta * speed;
        return stateTime;
    }

    public enum AnimationType {
        IDLE, WALK;

        public final String atlasKey;


        AnimationType() {
            this.atlasKey = name().toLowerCase();
        }
    }
}
