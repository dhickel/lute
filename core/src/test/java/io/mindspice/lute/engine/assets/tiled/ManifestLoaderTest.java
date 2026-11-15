package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.tiled.ManifestLoader;

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
            if (!assets.mapAsset().id().equals("test_tiled")) {
                throw new AssertionError("Map key must derive from the manifest name");
            }
            checkAsset(assets.atlasAsset(), "atlas");
            if (!assets.atlasAsset().id().equals("test_tiled")) {
                throw new AssertionError("Atlas logical ID must derive from the map name");
            }
            checkAssets(assets.musicAssets(), "music");
            if (!assets.soundAssets().isEmpty()) {
                throw new AssertionError("Tutorial map must not own weapon sounds");
            }
            checkAsset(assets.musicAssets().get("TOWN"), "TOWN music");
            if (!assets.all().contains(assets.atlasAsset())) { throw new AssertionError("Single atlas must be queued"); }
            try {
                new MapAssets(assets.mapAsset(), assets.startMusic(), null, assets.musicAssets(), assets.soundAssets());
                throw new AssertionError("Missing atlas must be rejected");
            } catch (IllegalArgumentException expected) { /* expected */ }

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
            if (!key.equals(asset.id())) {
                throw new AssertionError(field + " must retain manifest logical IDs");
            }
        });
    }

    private static void checkAsset(Asset<?> asset, String field) {
        if (asset == null || asset.id() == null || asset.id().isBlank()
                || asset.path() == null || asset.path().isBlank() || asset.type() == null) {
            throw new AssertionError(field + " must have a logical ID, path, and type");
        }
    }
}
