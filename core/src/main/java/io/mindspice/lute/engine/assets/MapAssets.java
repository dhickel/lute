package io.mindspice.lute.engine.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record MapAssets(
    Asset.Map mapAsset,
    Map<String, Asset.Atlas> atlasAssets,
    Map<String, Asset.Music> musicAssets,
    Map<String, Asset.Sound> soundAssets
) {
    public List<Asset<?>> all() {
        List<Asset<?>> assets = new ArrayList<>();
        assets.add(mapAsset);
        assets.addAll(atlasAssets.values());
        assets.addAll(musicAssets.values());
        assets.addAll(soundAssets.values());
        return assets;
    }

}
