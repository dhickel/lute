package io.mindspice.lute.engine.events;

import com.badlogic.gdx.maps.tiled.TiledMap;
import io.mindspice.lute.engine.assets.MapAsset;

public sealed interface Event {

    sealed interface Map extends Event {
        record Load(MapAsset mapAsset) implements Map { }

        record Changed(TiledMap map) implements Map { }


    }
}
