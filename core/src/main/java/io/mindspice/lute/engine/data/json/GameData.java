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
}
