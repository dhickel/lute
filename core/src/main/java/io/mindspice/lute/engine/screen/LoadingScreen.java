package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.AtlasAsset;

public class LoadingScreen extends ScreenAdapter {
    private final GameCore gameCore;
    private final AssetService assetService;

    public LoadingScreen(GameCore gameCore, AssetService assetService) {
        this.gameCore = gameCore;
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
            createScreens();
            gameCore.removeScreen(this);
            this.dispose();
            gameCore.setScreen(GameScreen.class);
        }
    }



    private void createScreens() {
        gameCore.addScreen(new GameScreen(this.gameCore));
    }
}
