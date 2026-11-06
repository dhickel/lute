package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.assets.MapAsset;
import io.mindspice.lute.engine.assets.tiled.TiledAshleyConfigurator;
import io.mindspice.lute.engine.assets.tiled.TiledService;
import io.mindspice.lute.engine.systems.*;

import java.util.function.Consumer;

public class GameScreen extends ScreenAdapter {

    private final Engine engine;
    private final TiledService tiledService;
    private final TiledAshleyConfigurator tiledAshleyConfigurator;
    private final InputManager inputManager;
    private final GameCore game;


    public GameScreen(GameCore gameCore) {
        this.game = gameCore;
        this.tiledService = new TiledService(gameCore.getAssetService());
        this.engine = new Engine();
        this.inputManager = gameCore.getInputManager();
        this.tiledAshleyConfigurator = new TiledAshleyConfigurator(engine, gameCore.getAssetService(), inputManager);

        this.engine.addSystem(new ControllerSystem());
        this.engine.addSystem(new MoveSystem());
        this.engine.addSystem(new AnimationPlayerSystem());
        this.engine.addSystem(new FacingSystem());
        this.engine.addSystem(new AnimationSystem(gameCore.getAssetService()));
        this.engine.addSystem(new RenderSystem(gameCore.getBatch(), gameCore.getViewport(), gameCore.getCamera()));


    }

    @Override
    public void show() {
        inputManager.releaseHeldInputs();
        game.setInputProcessors(inputManager);
        Consumer<TiledMap> renderConsumer = engine.getSystem(RenderSystem.class)::setMap;
        tiledService.setMapChangeConsumer(renderConsumer);
        tiledService.setLoadObjectConsumer(tiledAshleyConfigurator::onLoadObject);

        var tiledMap = this.tiledService.loadMap(MapAsset.MAIN);
        tiledService.setMap(tiledMap);
    }

    @Override
    public void hide() {
        inputManager.releaseHeldInputs();
        engine.getSystem(ControllerSystem.class).update(0f);
        for (var entity : engine.getEntitiesFor(Family.all(Controller.class).get())) {
            inputManager.unRegKeyListener(Controller.MAPPER.get(entity).getInputListener());
        }
        engine.removeAllEntities();
    }

    @Override
    public void pause() {
        inputManager.releaseHeldInputs();
        engine.getSystem(ControllerSystem.class).update(0f);
    }

    @Override
    public void resume() {
        inputManager.releaseHeldInputs();
        engine.getSystem(ControllerSystem.class).update(0f);
    }

    @Override
    public void render(float delta) {
        engine.update(Math.min(delta, 1 / 30f));
    }

    @Override
    public void dispose() {
        hide();
        for (var sys : this.engine.getSystems()) {
            if (sys instanceof Disposable dis) {
                dis.dispose();
            }
        }
    }
}

