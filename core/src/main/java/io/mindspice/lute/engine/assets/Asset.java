package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import io.mindspice.lute.engine.singletons.GameConsts;

public sealed interface Asset<T> {
    AssetDescriptor<T> descriptor();

    String file();

    record Map(
        AssetDescriptor<TiledMap> descriptor,
        String file
    ) implements Asset<TiledMap> {

        public static Map of(String mapFile) {
            var params = new BaseTiledMapLoader.Parameters();
            params.projectFilePath = GameConsts.PROJECT_PATH;

            return new Map(
                new AssetDescriptor<>("maps/" + mapFile, TiledMap.class, params),
                mapFile
            );
        }
    }

    record Atlas(
        AssetDescriptor<TextureAtlas> descriptor,
        String file
    ) implements Asset<TextureAtlas> {

        public static Atlas of(String atlasFile) {
            return new Atlas(
                new AssetDescriptor<>("objects/packed/" + atlasFile, TextureAtlas.class),
                atlasFile
            );
        }
    }

    record Music(
        AssetDescriptor<com.badlogic.gdx.audio.Music> descriptor,
        String file
    ) implements Asset<com.badlogic.gdx.audio.Music> {

        public static Music of(String musicFile) {
            return new Music(
                new AssetDescriptor<>("audio/" + musicFile, com.badlogic.gdx.audio.Music.class),
                musicFile
            );
        }
    }

    record Sound(
        AssetDescriptor<com.badlogic.gdx.audio.Sound> descriptor,
        String file
    ) implements Asset<com.badlogic.gdx.audio.Sound> {

        public static Sound of(String soundFile) {
            return new Sound(
                new AssetDescriptor<>("audio/" + soundFile, com.badlogic.gdx.audio.Sound.class),
                soundFile
            );
        }
    }
}



