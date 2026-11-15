package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapObject;

public record PlayerCharProps(String animation, String atlasAsset) {
    public static PlayerCharProps fromMapObj(MapObject obj) {
        var props = obj.getProperties();
        return new PlayerCharProps(props.get("animation", "IDLE", String.class),
            props.get("atlasAsset", "OBJECTS", String.class));
    }
}
