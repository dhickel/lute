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
        record Changed(Asset.Music musicAsset) implements Music { }
        record Volume(float level) implements Music { }
    }

    sealed interface Sound extends Event {
        record Play(Asset.Sound sound) implements Sound { }
        record PlayAtVolume(Asset.Sound sound, float volume) implements Sound { }
    }

    sealed interface Player extends Event {
        record Moved() implements Player { }
    }
}
