package io.mindspice.lute.engine.events;

import com.badlogic.gdx.maps.tiled.TiledMap;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.MapAssets;

public sealed interface Event {

    sealed interface Map extends Event {
        record Load(MapAssets mapAssets) implements Map { }

        record Changed(TiledMap map) implements Map { }

    }

    sealed interface Music extends Event {
        record Changed(Asset.Music musicAsset) implements Music {}
    }
}
