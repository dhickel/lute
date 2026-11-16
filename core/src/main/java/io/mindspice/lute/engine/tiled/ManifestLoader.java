package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetManifest;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.singletons.GameConsts;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class ManifestLoader {
    public static final JsonMapper mapper = JsonMapper.builder().build();

    public static MapAssets parseMapManifest(String mapName) {
        if (mapName == null || mapName.isBlank() || mapName.contains("/") || mapName.contains("\\")
                || mapName.equals(".") || mapName.equals("..")) {
            throw new IllegalArgumentException("Map manifest name must be a non-blank directory name, not a path");
        }
        String filePath = GameConsts.MAPS_PATH + mapName + ".manifest.json";

        AssetManifest manifest = null;
        try (var file = Gdx.files.internal(filePath).read()) {
            manifest = mapper.readValue(file, AssetManifest.class);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filePath, e);
        }

        if (manifest == null || manifest.musicAssets() == null || manifest.soundAssets() == null) {
            throw new IllegalArgumentException("Map manifest must include music_assets and sound_assets (empty objects are allowed): " + filePath);
        }
        Asset.Map mapAsset = Asset.Map.of(mapName, manifest.mapAsset());

        Asset.Atlas atlasAsset = Asset.Atlas.of(mapName, manifest.atlasAsset());

        Map<String, Asset.Music> musicAssets = manifest.musicAssets().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Asset.Music.of(entry.getKey(), entry.getValue())));

        Map<String, Asset.Sound> soundAsset = manifest.soundAssets().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Asset.Sound.of(entry.getKey(), entry.getValue())));

        return new MapAssets(mapAsset, manifest.startMusic(), atlasAsset, musicAssets, soundAsset);
    }
}
