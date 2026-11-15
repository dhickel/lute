package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;

public sealed interface Asset<T> {
    String id();
    String path();
    Class<T> type();

    private static void validate(String id, String path) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Asset logical ID must not be null or blank");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Asset path must not be null or blank for ID: " + id);
        }
    }

    private static String path(String prefix, String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalArgumentException("Asset file must not be null or blank");
        }
        return prefix + file;
    }

    record Map(String id, String path) implements Asset<TiledMap> {
        public Map { validate(id, path); }
        @Override public Class<TiledMap> type() { return TiledMap.class; }
        public static Map of(String id, String file) {
            return new Map(id, Asset.path("maps/", file));
        }
    }

    record Atlas(String id, String path) implements Asset<TextureAtlas> {
        public Atlas { validate(id, path); }
        @Override public Class<TextureAtlas> type() { return TextureAtlas.class; }
        public static Atlas of(String id, String file) {
            return new Atlas(id, Asset.path("objects/packed/", file));
        }
    }

    record Music(String id, String path) implements Asset<com.badlogic.gdx.audio.Music> {
        public Music { validate(id, path); }
        @Override public Class<com.badlogic.gdx.audio.Music> type() { return com.badlogic.gdx.audio.Music.class; }
        public static Music of(String id, String file) {
            return new Music(id, Asset.path("audio/", file));
        }
    }

    record Sound(String id, String path) implements Asset<com.badlogic.gdx.audio.Sound> {
        public Sound { validate(id, path); }
        @Override public Class<com.badlogic.gdx.audio.Sound> type() { return com.badlogic.gdx.audio.Sound.class; }
        public static Sound of(String id, String file) {
            return new Sound(id, Asset.path("audio/", file));
        }
    }
}
