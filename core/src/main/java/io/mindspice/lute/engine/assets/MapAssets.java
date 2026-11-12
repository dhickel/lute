package io.mindspice.lute.engine.assets;

import java.util.Map;

public record MapAssets(
    Asset.Map mapAsset,
    Map<String, Asset.Atlas> atlasAssets,
    Map<String, Asset.Music> musicAssets,
    Map<String, Asset.Sound> soundAssets
) { }
