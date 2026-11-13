package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapObject;

public record PlayerCharProps(
    String animation,
    float animationSpeed,
    int keyframes,
    String atlasAsset,
    String atlasKey
) {

    public PlayerCharProps(String animation, float animationSpeed, int keyframes, String atlasAsset, String atlasKey) {

        this.animation = animation;
        this.animationSpeed = animationSpeed;
        this.keyframes = keyframes;
        this.atlasAsset = atlasAsset;
        this.atlasKey = atlasKey;
    }

    public static PlayerCharProps fromMapObj(MapObject obj) {
        var props = obj.getProperties();
        // The map loader supplies project defaults; these fallbacks apply only when a property is absent.
        String animation = props.get("animation", "IDLE", String.class);
        float animationSpeed = props.get("animationSpeed", 1f, Float.class);
        int keyFrames = props.get("keyFrames", 8, Integer.class);
        String atlasAsset = props.get("atlasAsset", "OBJECTS", String.class);
        String atlasKey = props.get("atlasKey", "player", String.class);

        return new PlayerCharProps(animation, animationSpeed, keyFrames, atlasAsset, atlasKey);
    }
}
