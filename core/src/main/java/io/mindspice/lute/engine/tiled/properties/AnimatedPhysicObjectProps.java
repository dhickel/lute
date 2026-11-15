package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import io.mindspice.lute.engine.data.json.AnimationData;

import java.util.Objects;

public record AnimatedPhysicObjectProps(String animationType, String atlasAsset, AnimationData animation) {
    public static AnimatedPhysicObjectProps fromMapObj(TiledMapTileMapObject obj) {
        var props = new MapProperties();
        props.putAll(obj.getTile().getProperties());
        props.putAll(obj.getProperties());
        String type = Objects.requireNonNull(props.get("animation", String.class), "animation");
        String atlas = Objects.requireNonNull(props.get("atlasAsset", String.class), "atlasAsset");
        var animation = new AnimationData(
            Objects.requireNonNull(props.get("speed", Number.class), "speed").floatValue(),
            Objects.requireNonNull(props.get("atlas_key", String.class), "atlas_key"),
            Objects.requireNonNull(props.get("sound_frame", Number.class), "sound_frame").intValue(),
            Objects.requireNonNull(props.get("frame_interval", Number.class), "frame_interval").floatValue());
        return new AnimatedPhysicObjectProps(type, atlas, animation);
    }
}
