package io.mindspice.lute.engine.assets;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public record AssetManifest(
    @JsonAlias("map_asset") String mapAsset,
    @JsonAlias("start_music") String startMusic,
    @JsonAlias("atlas_assets") Map<String, String> atlasAssets,
    @JsonAlias("music_assets") Map<String, String> musicAssets,
    @JsonAlias("sound_assets") Map<String, String> soundAssets
) {
}
