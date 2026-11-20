package io.mindspice.lute.engine.data.json;

import java.util.Map;

public record GameData(
    PlayerData playerData,
    WeaponsData weaponsData,
    ProjectilesData projectilesData,
    Map<String, MapActorData.Npc> charactersData,
    Map<String, MapActorData.Enemy> enemiesData,
    Map<String, ObjectData> objectsData
) {
    public GameData {
        charactersData = Map.copyOf(charactersData);
        enemiesData = Map.copyOf(enemiesData);
        objectsData = Map.copyOf(objectsData);
    }
}
