package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class DataLoader {
    public static final JsonMapper mapper = JsonMapper.builder().build();

    public static GameData loadGameData() {
        var player = parsePlayerData();
        var weapons = parseWeaponData();
        player.loadout().weaponCycle().forEach(weapons::requireWeapon);
        var characters = parseDefinitions("characters.json", MapActorData.Npc.class);
        var enemies = parseDefinitions("enemies.json", MapActorData.Enemy.class);
        characters.values().forEach(npc -> npc.loadout().weaponCycle().forEach(weapons::requireWeapon));
        for (var definitions : List.of(characters, enemies)) {
            for (var actor : definitions.values()) {
                if (actor.weapon() != null) { weapons.requireWeapon(actor.weapon()); }
            }
        }
        var objects = parseDefinitions("objects.json", ObjectData.class);
        return new GameData(player, weapons, parseProjectileData(), characters, enemies, objects);
    }

    private static <T> Map<String, T> parseDefinitions(String filename, Class<T> type) {
        var path = Gdx.files.internal(GameConsts.DATA_PATH + filename);
        var mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, type);
        try (var file = path.read()) {
            Map<String, T> definitions = mapper.readerFor(mapType)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .readValue(file);
            if (definitions == null || definitions.isEmpty()) {
                throw new IllegalArgumentException(filename + " requires at least one definition");
            }
            definitions.forEach((id, definition) -> {
                if (id.isBlank() || definition == null) {
                    throw new IllegalArgumentException(filename + " requires nonblank IDs and nonnull definitions");
                }
            });
            return definitions;
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + path, e);
        }
    }

    public static PlayerData parsePlayerData() {
        String filepath = GameConsts.DATA_PATH + "player.json";
        try (var file = Gdx.files.internal(filepath).read()) {
            return mapper.readValue(file, PlayerData.class);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filepath, e);
        }
    }

    public static ProjectilesData parseProjectileData() {
        String filepath = GameConsts.DATA_PATH + "projectiles.json";
        try (var file = Gdx.files.internal(filepath).read()) {
            return mapper.readValue(file, ProjectilesData.class);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filepath, e);
        }
    }

    public static WeaponsData parseWeaponData() {
        String filepath = GameConsts.DATA_PATH + "weapons.json";
        try (var file = Gdx.files.internal(filepath).read()) {
            return mapper.readValue(file, WeaponsData.class);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filepath, e);
        }
    }
}
