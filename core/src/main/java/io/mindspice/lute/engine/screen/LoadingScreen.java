package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.Asset;

import java.util.List;

public class LoadingScreen extends ScreenAdapter {
    private final Runnable afterLoadScreen;
    private final AssetService assetService;
    private final List<Asset<?>> assetsToLoad;
    private boolean completed;
    private boolean failed;

    public LoadingScreen(AssetService assetService, Runnable afterLoadScreen, List<Asset<?>> assetsToLoad) {
        this.afterLoadScreen = afterLoadScreen;
        this.assetService = assetService;
        this.assetsToLoad = List.copyOf(assetsToLoad);
    }

    @Override
    public void show() {
        completed = false;
        failed = false;
        assetsToLoad.forEach(assetService::queue);
    }

    @Override
    public void render(float delta) {
        if (completed || failed) { return; }
        boolean ready;
        try {
            ready = assetService.update();
        } catch (RuntimeException failure) {
            failed = true;
            throw failure;
        }
        if (ready) {
            completed = true;
            Gdx.app.debug("LoadingScreen", "Finished loading assets");
            afterLoadScreen.run();
        }
    }



}
