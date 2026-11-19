package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

public class DataLoader {
    public static final JsonMapper mapper = JsonMapper.builder().build();

    public static GameData loadGameData() {
        return new GameData(
            parsePlayerData(),
            parseWeaponData(),
            parseProjectileData()
        );
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
