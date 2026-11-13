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
        String filePath = GameConsts.MAPS_PATH + mapName + ".manifest.json";

        AssetManifest manifest = null;
        try (var file = Gdx.files.internal(filePath).read()) {
            manifest = mapper.readValue(file, AssetManifest.class);
        } catch (IOException e) {
            throw new GdxRuntimeException("Failed to read file at path: " + filePath, e);
        }

        Asset.Map mapAsset = Asset.Map.of(manifest.mapAsset());

        Map<String, Asset.Atlas> atlasAssets = manifest.atlasAssets().entrySet().stream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry -> Asset.Atlas.of(entry.getValue())));

        Map<String, Asset.Music> musicAssets = manifest.musicAssets().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Asset.Music.of(entry.getValue())));

        Map<String, Asset.Sound> soundAsset = manifest.soundAssets().entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> Asset.Sound.of(entry.getValue())));

        return new MapAssets(mapAsset, manifest.startMusic(), atlasAssets, musicAssets, soundAsset);
    }
}
