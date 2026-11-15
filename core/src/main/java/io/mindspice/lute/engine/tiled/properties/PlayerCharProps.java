package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapObject;

public record PlayerCharProps(String animationType) {
    public static PlayerCharProps fromMapObj(MapObject obj) {
        return new PlayerCharProps(obj.getProperties().get("animationType", "IDLE", String.class));
    }
}
