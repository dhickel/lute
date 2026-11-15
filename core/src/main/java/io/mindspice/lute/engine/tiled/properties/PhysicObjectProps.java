package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;

import java.util.Objects;

public record PhysicObjectProps(String atlasKey) {
    public static PhysicObjectProps fromMapObj(TiledMapTileMapObject obj) {
        var props = new MapProperties();
        props.putAll(obj.getTile().getProperties());
        props.putAll(obj.getProperties());
        return new PhysicObjectProps(Objects.requireNonNull(props.get("atlasKey", String.class), "atlasKey"));
    }
}
