package io.mindspice.lute.engine.data.json;

import java.util.HashMap;
import java.util.Map;

public record ActorsData(Map<String, MapActorData.Npc> npc, Map<String, MapActorData.Enemy> enemies) {
    public ActorsData {
        npc = validate("characters.json", npc);
        enemies = validate("enemies.json", enemies);
    }

    ActorsData withEnemyInfightChance(String enemyId, int chance) {
        var enemy = enemies.get(enemyId);
        if (enemy == null) { throw new IllegalArgumentException("Unknown enemy ID: " + enemyId); }
        var updated = new HashMap<>(enemies);
        updated.put(enemyId, enemy.withInfightChance(chance));
        return new ActorsData(npc, updated);
    }

    ActorsData withAllInfightChances(int chance) {
        if (chance < 0 || chance > 100) { throw new IllegalArgumentException("Infighting chance must be from 0 to 100"); }
        var updated = new HashMap<String, MapActorData.Enemy>();
        enemies.forEach((id, enemy) -> updated.put(id, enemy.withInfightChance(chance)));
        return new ActorsData(npc, updated);
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
