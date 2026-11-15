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

        final float stateTime;
        if (anim2d.isDirty() || fDirection != anim2d.getFdirection() || atlasChanged) {
            updateAnimation(anim2d, fDirection);
            currentAtlases.put(anim2d, atlas);
            stateTime = 0;
        } else { stateTime = anim2d.incAndGetStateTime(deltaTime); }

        Animation<TextureRegion> animation = anim2d.getAnimation();
        animation.setPlayMode(anim2d.getPlayMode());

        String soundId = anim2d.consumeAttackSound();
        if (soundId != null) {
            eventEmitter.accept(new Event.Sound.Play(soundId));
        }

        TextureRegion keyFrame = animation.getKeyFrame(stateTime);
        Graphic.MAPPER.get(entity).setRegion(keyFrame);
    }

    public static void validateAttackFrames(Animation2D anim2d, TextureAtlas atlas) {
        if (anim2d.getMinimumAttackFrames() == 0) { return; }
        for (var direction : Facing.Direction.values()) {
            var key = anim2d.getAtlasKey() + "/attack_" + direction.atlasKey;
            if (atlas.findRegions(key).size < anim2d.getMinimumAttackFrames()) {
                throw new IllegalArgumentException("Attack animation has too few frames for " + key);
            }
        }
    }

    public void updateAnimation(Animation2D anim2d, Facing.Direction fDir) {
        TextureAtlas atlas = assetService.get(TextureAtlas.class, anim2d.getAtlasId());
        String atlasKey = anim2d.getAtlasKey();
        Animation2D.AnimationType type = anim2d.getType();
        var cacheKey = new CacheKey(atlas, anim2d.getAtlasId(), atlasKey, type, fDir);

        Array<TextureAtlas.AtlasRegion> frames = animationCache.computeIfAbsent(cacheKey, k -> {
            String combinedKey = type == Animation2D.AnimationType.DEFAULT
                ? atlasKey
                : atlasKey + "/" + type.atlasKey + "_" + fDir.atlasKey;
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
        Animation2D.AnimationType type,
        Facing.Direction fDirection
    ) {
    }


}
