package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.mindspice.lute.engine.assets.Asset;

import java.lang.reflect.Proxy;
import java.util.Map;

public final class ManifestLoaderTest {
    public static void main(String[] args) {
        Files previousFiles = Gdx.files;
        try {
            Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("internal")) {
                        return new FileHandle("assets/" + arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });

            var assets = ManifestLoader.parseMapManifest("test_tiled");
            checkAsset(assets.mapAsset(), "map");
            checkAssets(assets.atlasAssets(), "atlases");
            checkAssets(assets.musicAssets(), "music");
            checkAssets(assets.soundAssets(), "sounds");
            checkAsset(assets.atlasAssets().get("OBJECTS"), "OBJECTS atlas");
            checkAsset(assets.musicAssets().get("TOWN"), "TOWN music");
            checkAsset(assets.soundAssets().get("SWORD_HIT"), "SWORD_HIT sound");
            System.out.println("ManifestLoaderTest: passed");
        } finally {
            Gdx.files = previousFiles;
        }
    }

    private static void checkAssets(Map<String, ? extends Asset<?>> assets, String field) {
        if (assets == null || assets.isEmpty()) {
            throw new AssertionError(field + " must contain at least one asset");
        }
        assets.forEach((key, asset) -> {
            if (key == null || key.isBlank()) {
                throw new AssertionError(field + " must have a nonempty key");
            }
            checkAsset(asset, field);
        });
    }

    private static void checkAsset(Asset<?> asset, String field) {
        if (asset == null || asset.file() == null || asset.file().isBlank()
                || asset.descriptor() == null || asset.descriptor().fileName == null
                || asset.descriptor().fileName.isBlank()) {
            throw new AssertionError(field + " must have a file and descriptor");
        }
    }
}
