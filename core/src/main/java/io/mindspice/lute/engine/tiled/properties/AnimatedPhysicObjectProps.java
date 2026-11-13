package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;

import java.util.Objects;

public record AnimatedPhysicObjectProps(
    String animation,
    float animationSpeed,
    int keyFrames,
    String atlasAsset,
    String atlasKey
) {

    public AnimatedPhysicObjectProps(String animation, float animationSpeed, int keyFrames, String atlasAsset, String atlasKey) {
        this.animation = animation;
        this.animationSpeed = animationSpeed;
        this.keyFrames = keyFrames;
        this.atlasAsset = atlasAsset;
        this.atlasKey = atlasKey;
    }

    public static AnimatedPhysicObjectProps fromMapObj(TiledMapTileMapObject obj) {
        var props = new MapProperties();
        props.putAll(obj.getTile().getProperties());
        props.putAll(obj.getProperties());
        String animation = Objects.requireNonNull(props.get("animation", String.class), "animation");
        float animationSpeed = Objects.requireNonNull(props.get("animationSpeed", Number.class), "animationSpeed").floatValue();
        int keyFrames = Objects.requireNonNull(props.get("keyFrames", Integer.class), "keyFrames");
        String atlasAsset = Objects.requireNonNull(props.get("atlasAsset", String.class), "atlasAsset");
        String atlasKey = Objects.requireNonNull(props.get("atlasKey", String.class), "atlasKey");

        return new AnimatedPhysicObjectProps(animation, animationSpeed, keyFrames, atlasAsset, atlasKey);
    }
}
