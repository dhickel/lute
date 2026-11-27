package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Graphic;

import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

import java.util.HashMap;
import java.util.Map;

public class AnimationSystem extends IteratingSystem implements EventEmitter {
    private final AssetService assetService;
    private final Map<String, AtlasFrames> characterFrames = new HashMap<>();

    private record AtlasFrames(TextureAtlas atlas, Map<String, Array<TextureAtlas.AtlasRegion>> regions) { }

    public AnimationSystem(AssetService assetService) {
        super(Family.all(Animation2D.class, Graphic.class, Facing.class).get());
        this.assetService = assetService;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!CombatSystem.usesFixedAttackPlayback(Combat.MAPPER.get(entity))) {
            advancePlayback(entity, deltaTime);
        }
    }

    void advancePlayback(Entity entity, float deltaTime) {
        Animation2D anim2d = Animation2D.MAPPER.get(entity);
        Facing.Direction fDirection = Facing.MAPPER.get(entity).getDirection();

        var aim = BowAim.MAPPER.get(entity);
        boolean drawing = aim != null && aim.isAiming();
        if (anim2d.isDirty() || fDirection != anim2d.getFdirection()) {
            boolean turningAttack = anim2d.isAttackReady();
            float previousTime = anim2d.getStateTime();
            updateAnimation(anim2d, fDirection);
            // A new direction changes the pose, not the clock or the attack cues.
            if (turningAttack) { anim2d.setStateTime(previousTime); }
            else { deltaTime = 0f; }
        }
        float stateTime = anim2d.advancePlayback(deltaTime, drawing);

        Animation<TextureRegion> animation = anim2d.getAnimation();
        animation.setPlayMode(anim2d.getPlayMode());

        String soundId = drawing ? null : anim2d.consumeAttackSound();
        if (soundId != null) {
            eventEmitter.accept(new Event.Sound.Play(soundId));
        }

        TextureRegion keyFrame = animation.getKeyFrame(stateTime);
        Graphic.MAPPER.get(entity).setRegion(keyFrame);
    }

    // The normal frame pass has already advanced playback at walk speed. Add only
    // the time actually spent running, without replaying attack cues or other poses.
    void advanceSprintWalk(Entity entity, float runningTime) {
        var anim = Animation2D.MAPPER.get(entity);
        if (anim.getType() != Animation2D.AnimationType.WALK || anim.isDirty()) { return; }
        float stateTime = anim.incAndGetStateTime(runningTime);
        Graphic.MAPPER.get(entity).setRegion(anim.getAnimation().getKeyFrame(stateTime));
    }

    static void validateCanvas(TextureAtlas.AtlasRegion frame, int width, int height, String key) {
        if (width <= 0 || height <= 0 || frame.rotate || frame.offsetX != 0 || frame.offsetY != 0
            || frame.getRegionWidth() != width || frame.getRegionHeight() != height
            || frame.originalWidth != width || frame.originalHeight != height) {
            throw new IllegalArgumentException("Character layers require aligned untrimmed canvases: " + key);
        }
    }

    public void updateAnimation(Animation2D anim2d, Facing.Direction fDir) {
        String atlasId = anim2d.getAtlasId();
        var atlas = assetService.get(TextureAtlas.class, atlasId);
        String key = anim2d.getType() == Animation2D.AnimationType.DEFAULT
            ? anim2d.getAtlasKey()
            : anim2d.regionKey(anim2d.getAction(), fDir);

        Array<TextureAtlas.AtlasRegion> frames;
        // Environmental frames belong to their entities, not to a system that survives map travel.
        if (atlasId.equals(CharacterAssets.CHARACTER.id()) || atlasId.equals(CharacterAssets.ENEMIES.id())) {
            var cache = characterFrames.get(atlasId);
            if (cache == null || cache.atlas() != atlas) {
                cache = new AtlasFrames(atlas, new HashMap<>());
                characterFrames.put(atlasId, cache);
            }
            frames = cache.regions().computeIfAbsent(key, name -> loadFrames(atlas, name));
        } else {
            frames = loadFrames(atlas, key);
        }
        anim2d.setAnimation(new Animation<>(anim2d.getFrameDuration(), frames), fDir);
    }

    private static Array<TextureAtlas.AtlasRegion> loadFrames(TextureAtlas atlas, String key) {
        var frames = atlas.findRegions(key);
        if (frames.isEmpty()) { throw new GdxRuntimeException("No atlas regions found for key: " + key); }
        return frames;
    }
}
