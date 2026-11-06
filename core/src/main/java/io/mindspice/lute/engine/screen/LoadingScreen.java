package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.AtlasAsset;

public class LoadingScreen extends ScreenAdapter {
    private final Runnable afterLoadScreen;
    private final AssetService assetService;
    boolean isDirty = true;

    public LoadingScreen( AssetService assetService, Runnable afterLoadScreen) {
        this.afterLoadScreen = afterLoadScreen;
        this.assetService = assetService;
    }

    @Override
    public void show() {
        for (var atlas : AtlasAsset.values()) {
            assetService.queue(atlas);
        }
    }

    @Override
    public void render(float delta) {
        if (assetService.update()) {
            Gdx.app.debug("LoadingScreen", "Finished loading assets");
            afterLoadScreen.run();
        }
    }



    private void createScreens() {
    }
}
