package io.mindspice.lute.engine.data.json;

import java.util.Map;

public record GameData(
    PlayerData playerData,
    WeaponsData weaponsData,
    ProjectilesData projectilesData,
    ActorsData actorsData,
    Map<String, ObjectData> objectsData
) {
    public GameData {
        objectsData = Map.copyOf(objectsData);
    }

    /** Set the chance on every enemy definition in a new startup data snapshot. */
    public GameData withAllInfightChances(int chance) {
        return withActors(actorsData.withAllInfightChances(chance));
    }

    /** Set the chance for one enemy definition without changing the loaded catalog. */
    public GameData withEnemyInfightChance(String enemyId, int chance) {
        return withActors(actorsData.withEnemyInfightChance(enemyId, chance));
    }

    private GameData withActors(ActorsData actors) {
        return new GameData(playerData, weaponsData, projectilesData, actors, objectsData);
    }
}
