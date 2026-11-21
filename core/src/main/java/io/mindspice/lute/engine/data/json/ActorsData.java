package io.mindspice.lute.engine.data.json;

import java.util.Map;

public record ActorsData(Map<String, MapActorData.Npc> npc, Map<String, MapActorData.Enemy> enemies) {
    public ActorsData {
        npc = validate("npc", npc);
        enemies = validate("enemies", enemies);
    }

    private static <T> Map<String, T> validate(String group, Map<String, T> definitions) {
        if (definitions == null) { throw new IllegalArgumentException("actors.json requires a " + group + " map"); }
        definitions.forEach((id, definition) -> {
            if (id.isBlank() || definition == null) {
                throw new IllegalArgumentException("actors.json " + group + " requires nonblank IDs and nonnull definitions");
            }
        });
        return Map.copyOf(definitions);
    }
}
