package io.mindspice.lute.engine.data.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

public class DataLoader {
    public static final JsonMapper mapper = JsonMapper.builder().build();

    public static GameData loadGameData() {
        return new GameData(
            parsePlayerData(),
            parseWeaponData()
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

    public static WeaponsData parseWeaponData() {
        String filepath = GameConsts.DATA_PATH + "weapons.json";
        try (var file = Gdx.files.internal(filepath).read()) {
            Map<String, WeaponsData.WeaponData> weapons = mapper.readValue(file, new TypeReference<>() {});
            return new WeaponsData(weapons);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filepath, e);
        }
    }
}
