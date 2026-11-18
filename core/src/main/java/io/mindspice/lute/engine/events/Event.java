package io.mindspice.lute.engine.events;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.tiled.MapTriggers.SensorType;

public sealed interface Event {

    sealed interface Map extends Event {
        record Changed(String mapId) implements Map { }
    }

    sealed interface Music extends Event {
        record Changed(String musicId) implements Music { }
        record Volume(float level) implements Music { }
    }

    sealed interface Sound extends Event {
        record Play(String soundId) implements Sound { }
        record PlayAtVolume(String soundId, float volume) implements Sound { }
    }

    sealed interface Sensor extends Event {
        record Enter(String name, SensorType type, Entity player) implements Sensor { }
        record Exit(String name, SensorType type, Entity player) implements Sensor { }
    }

    sealed interface Player extends Event {
        record Moved() implements Player { }
    }
}
