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
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Graphic;

import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class AnimationSystem extends IteratingSystem implements EventEmitter {
    private final AssetService assetService;
    private final Map<CacheKey, Array<TextureAtlas.AtlasRegion>> animationCache;
    private final Map<Animation2D, TextureAtlas> currentAtlases = new WeakHashMap<>();

    public AnimationSystem(AssetService assetService) {
        super(Family.all(Animation2D.class, Graphic.class, Facing.class).get());
        this.assetService = assetService;
        this.animationCache = new HashMap<>();
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        Animation2D anim2d = Animation2D.MAPPER.get(entity);
        Facing.Direction fDirection = Facing.MAPPER.get(entity).getDirection();

        TextureAtlas atlas = assetService.get(TextureAtlas.class, anim2d.getAtlasId());
        animationCache.keySet().removeIf(key
            -> key.atlasId().equals(anim2d.getAtlasId()) && key.atlas() != atlas
        );

        TextureAtlas previousAtlas = currentAtlases.get(anim2d);
        boolean atlasChanged = previousAtlas != atlas;
        if (previousAtlas != null && atlasChanged) { validateAttackFrames(anim2d, atlas); }

        var aim = BowAim.MAPPER.get(entity);
        boolean drawing = aim != null && aim.isAiming();
        if (anim2d.isDirty() || fDirection != anim2d.getFdirection() || atlasChanged) {
            updateAnimation(anim2d, fDirection);
            currentAtlases.put(anim2d, atlas);
            deltaTime = 0f;
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

    public static void validateAttackFrames(Animation2D anim2d, TextureAtlas atlas) {
        if (anim2d.getRequiredAttackFrames().isEmpty()) { return; }
        var reference = atlas.findRegion(anim2d.regionKey("idle", Facing.Direction.RIGHT));
        if (reference == null) { throw new IllegalArgumentException("Missing character reference canvas"); }
        for (var requirement : anim2d.getRequiredAttackFrames().entrySet()) {
            for (var direction : Facing.Direction.values()) {
                var key = anim2d.regionKey(requirement.getKey(), direction);
                var frames = atlas.findRegions(key);
                if (frames.size < requirement.getValue()) {
                    throw new IllegalArgumentException("Attack animation has too few frames for " + key);
                }
                for (var frame : frames) {
                    validateCanvas(frame, reference.getRegionWidth(), reference.getRegionHeight(), key);
                }
            }
        }
    }

    static void validateCanvas(TextureAtlas.AtlasRegion frame, int width, int height, String key) {
        if (width <= 0 || height <= 0 || frame.rotate || frame.offsetX != 0 || frame.offsetY != 0
            || frame.getRegionWidth() != width || frame.getRegionHeight() != height
            || frame.originalWidth != width || frame.originalHeight != height) {
            throw new IllegalArgumentException("Character layers require aligned untrimmed canvases: " + key);
        }
    }

    public void updateAnimation(Animation2D anim2d, Facing.Direction fDir) {
        TextureAtlas atlas = assetService.get(TextureAtlas.class, anim2d.getAtlasId());
        String atlasKey = anim2d.getAtlasKey();
        Animation2D.AnimationType type = anim2d.getType();
        String action = anim2d.getAction();
        var cacheKey = new CacheKey(atlas, anim2d.getAtlasId(), atlasKey, action, fDir);

        Array<TextureAtlas.AtlasRegion> frames = animationCache.computeIfAbsent(cacheKey, k -> {
            String combinedKey = type == Animation2D.AnimationType.DEFAULT
                ? atlasKey
                : anim2d.regionKey(action, fDir);
            Array<TextureAtlas.AtlasRegion> regions = atlas.findRegions(combinedKey);


            if (regions.isEmpty()) {
                throw new GdxRuntimeException("No atlas regions found for key: " + combinedKey);
            }
            return regions;
        });
        anim2d.setAnimation(new Animation<>(anim2d.getFrameDuration(), frames), fDir);
    }


    public record CacheKey(
        TextureAtlas atlas,
        String atlasId,
        String atlasKey,
        String action,
        Facing.Direction fDirection
    ) {
    }


}
