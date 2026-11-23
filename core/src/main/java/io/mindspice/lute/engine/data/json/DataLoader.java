package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class DataLoader {
    public static final JsonMapper mapper = JsonMapper.builder().build();

    public static GameData loadGameData() {
        var player = parsePlayerData();
        var weapons = parseWeaponData();
        player.loadout().weaponCycle().forEach(weapons::requireWeapon);
        var actors = parseActorsData();
        actors.npc().values().forEach(npc -> npc.loadout().weaponCycle().forEach(weapons::requireWeapon));
        for (var definitions : List.of(actors.npc(), actors.enemies())) {
            for (var actor : definitions.values()) {
                if (actor.weapon() != null) { weapons.requireWeapon(actor.weapon()); }
            }
        }
        var path = Gdx.files.internal(GameConsts.DATA_PATH + "objects.json");
        try (var file = path.read()) {
            var objects = parseObjectDefinitions(file);
            return new GameData(player, weapons, parseProjectileData(), actors, objects);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + path, e);
        }
    }

    private static ActorsData parseActorsData() {
        return new ActorsData(
            parseActorDefinitions("characters.json", MapActorData.Npc.class),
            parseActorDefinitions("enemies.json", MapActorData.Enemy.class));
    }

    private static <T extends MapActorData> Map<String, T> parseActorDefinitions(String filename, Class<T> type) {
        var path = Gdx.files.internal(GameConsts.DATA_PATH + filename);
        try (var file = path.read()) {
            return definitionsReader(type)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .without(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .readValue(file);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + path, e);
        }
    }

    public static Map<String, ObjectData> parseObjectDefinitions(InputStream file) {
        return readDefinitions(file, "objects.json", definitionsReader(ObjectData.class)
            .with(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS));
    }

    private static ObjectReader definitionsReader(Class<?> type) {
        var mapType = mapper.getTypeFactory().constructMapType(Map.class, String.class, type);
        return mapper.readerFor(mapType)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }

    private static <T> Map<String, T> readDefinitions(InputStream file, String filename, ObjectReader reader) {
        Map<String, T> definitions = reader.readValue(file);
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException(filename + " requires at least one definition");
        }
        definitions.forEach((id, definition) -> {
            if (id.isBlank() || definition == null) {
                throw new IllegalArgumentException(filename + " requires nonblank IDs and nonnull definitions");
            }
        });
        return definitions;
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
