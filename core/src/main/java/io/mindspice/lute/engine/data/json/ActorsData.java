package io.mindspice.lute.engine.data.json;

import java.util.Map;

public record ActorsData(Map<String, MapActorData.Npc> npc, Map<String, MapActorData.Enemy> enemies) {
    public ActorsData {
        npc = validate("characters.json", npc);
        enemies = validate("enemies.json", enemies);
    }

    private static <T> Map<String, T> validate(String filename, Map<String, T> definitions) {
        if (definitions == null) { throw new IllegalArgumentException(filename + " requires a map of actor definitions"); }
        definitions.forEach((id, definition) -> {
            if (id.isBlank() || definition == null) {
                throw new IllegalArgumentException(filename + " requires nonblank IDs and nonnull definitions");
            }
        });
        return Map.copyOf(definitions);
    }
}
