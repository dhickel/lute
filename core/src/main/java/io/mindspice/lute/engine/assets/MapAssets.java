package io.mindspice.lute.engine.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MapAssets(
    Asset.Map mapAsset,
    String startMusic,
    Map<String, Asset.Atlas> atlasAssets,
    Map<String, Asset.Music> musicAssets,
    Map<String, Asset.Sound> soundAssets
) {
    public MapAssets {
        if (mapAsset == null) { throw new IllegalArgumentException("Map asset must not be null"); }
        atlasAssets = checkedAssets(atlasAssets, "atlas");
        musicAssets = checkedAssets(musicAssets, "music");
        soundAssets = checkedAssets(soundAssets, "sound");
    }

    private static <T extends Asset<?>> Map<String, T> checkedAssets(Map<String, T> assets, String kind) {
        if (assets == null) { throw new IllegalArgumentException("Missing " + kind + " assets"); }
        assets.forEach((id, asset) -> {
            if (asset == null || !asset.id().equals(id)) {
                throw new IllegalArgumentException("Manifest " + kind + " key must match asset ID: " + id);
            }
        });
        return Map.copyOf(assets);
    }

    public List<Asset<?>> all() {
        List<Asset<?>> assets = new ArrayList<>();
        assets.add(mapAsset);
        assets.addAll(atlasAssets.values());
        assets.addAll(musicAssets.values());
        assets.addAll(soundAssets.values());
        return assets;
    }

}
