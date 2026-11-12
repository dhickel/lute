package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.assets.tiled.FixedTmxMapLoader;

public class AssetService implements Disposable {
    private final AssetManager assetManager;

    public AssetService(FileHandleResolver resolver) {
        this.assetManager = new AssetManager(resolver);
        this.assetManager.setLoader(TiledMap.class, new FixedTmxMapLoader(resolver));
    }


    public <T> T load(Asset<T> asset) {
        this.assetManager.load(asset.descriptor());
        this.assetManager.finishLoading();
        return this.assetManager.get(asset.descriptor());
    }

    public <T> void unload(Asset<T> asset) {
        assetManager.unload(asset.descriptor().fileName);
    }

    public <T> void queue(Asset<T> asset) {
        assetManager.load(asset.descriptor());
    }

    public <T> T get(Asset<T> asset) {
        return assetManager.get(asset.descriptor());
    }

    public boolean update() {
        return assetManager.update();
    }

    public void debugDiagnostics() {
        Gdx.app.debug("AssetService", assetManager.getDiagnostics());
    }


    @Override
    public void dispose() {
        assetManager.dispose();
    }


}
