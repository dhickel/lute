package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;

import java.util.Objects;

public record PhysicObjectProps(
    String assetKey,
    String atlasAsset
) {

    public PhysicObjectProps(String assetKey, String atlasAsset) {
        this.assetKey = assetKey;
        this.atlasAsset = atlasAsset;
    }

    public static PhysicObjectProps fromMapObj(TiledMapTileMapObject obj) {
        var props = new MapProperties();
        props.putAll(obj.getTile().getProperties());
        props.putAll(obj.getProperties());
        String assetKey = Objects.requireNonNull(props.get("atlasKey", String.class), "atlasKey");
        String atlasAsset = Objects.requireNonNull(props.get("atlasAsset", String.class), "atlasAsset");

        return new PhysicObjectProps(assetKey, atlasAsset);
    }
}
