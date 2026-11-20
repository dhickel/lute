package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActorsData(Definition npc, Definition enemy) {
    /** Legacy packs without actors.json use three tiles for NPCs and five for enemies (32 px tiles). */
    public static final ActorsData DEFAULTS = new ActorsData(new Definition(96), new Definition(160));

    public ActorsData {
        if (npc == null || enemy == null) {
            throw new IllegalArgumentException("actors.json requires npc and enemy definitions");
        }
    }

    public record Definition(@JsonProperty("awareness_radius") float awarenessRadius) {
        public Definition {
            if (!Float.isFinite(awarenessRadius) || awarenessRadius <= 0f) {
                throw new IllegalArgumentException("awareness_radius must be finite and positive (pixels)");
            }
        }
    }
}
