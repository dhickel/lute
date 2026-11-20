package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActorsData(Definition npc, Definition enemy) {
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
