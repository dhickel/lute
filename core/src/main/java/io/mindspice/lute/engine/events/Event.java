package io.mindspice.lute.engine.events;

import com.badlogic.gdx.maps.tiled.TiledMap;

public sealed interface Event {
    sealed interface Map extends Event {
        record Loaded(TiledMap map) implements Map {
            public static Loaded of(TiledMap map) {
                return new Loaded(map);
            }
        }

    }
}
