package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.utils.Array;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.AtlasAsset;

import java.util.Arrays;

public class LoadingScreen extends ScreenAdapter {
    private final Runnable afterLoadScreen;
    private final AssetService assetService;
    private final AtlasAsset[] assetsToLoad;

    public LoadingScreen(AssetService assetService, Runnable afterLoadScreen, AtlasAsset... assetToLoad) {
        this.afterLoadScreen = afterLoadScreen;
        this.assetService = assetService;
        this.assetsToLoad = assetToLoad;
    }

    @Override
    public void show() {
        Arrays.stream(assetsToLoad).forEach(assetService::queue);
    }

    @Override
    public void render(float delta) {
        if (assetService.update()) {
            Gdx.app.debug("LoadingScreen", "Finished loading assets");
            afterLoadScreen.run();
        }
    }



}
