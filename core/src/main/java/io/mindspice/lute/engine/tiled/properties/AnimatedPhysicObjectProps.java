package io.mindspice.lute.engine.tiled.properties;

import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import io.mindspice.lute.engine.data.json.AnimationData;

import java.util.Objects;

public record AnimatedPhysicObjectProps(AnimationData animation) {
    public static AnimatedPhysicObjectProps fromMapObj(TiledMapTileMapObject obj) {
        var props = new MapProperties();
        props.putAll(obj.getTile().getProperties());
        props.putAll(obj.getProperties());
        var settings = Objects.requireNonNull(props.get("animation", MapProperties.class), "animation");
        var animation = new AnimationData(
            Objects.requireNonNull(settings.get("speed", Number.class), "speed").floatValue(),
            Objects.requireNonNull(settings.get("atlasKey", String.class), "atlasKey"),
            Objects.requireNonNull(settings.get("soundFrame", Number.class), "soundFrame").intValue(),
            Objects.requireNonNull(settings.get("frameInterval", Number.class), "frameInterval").floatValue());
        return new AnimatedPhysicObjectProps(animation);
    }
}
