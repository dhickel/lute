package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.assets.tiled.ManifestLoader;
import io.mindspice.lute.engine.assets.tiled.TiledAshleyLoader;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.*;

public class GameScreen extends ScreenAdapter implements EventEmitter {


    private final TiledAshleyLoader tiledAshleyLoader;
    private final InputManager inputManager;
    private final GameCore game;
    private final World physicWorld;
    private final Engine engine;
    private final MapAssets mapAssets;
    private boolean visible;


    public GameScreen(MapAssets mapAssets) {
        this.game = GameCore.INSTANCE;
        this.inputManager = this.game.getInputManager();
        this.engine = this.game.getEngine();
        this.mapAssets = mapAssets;

        this.physicWorld = new World(Vector2.Zero, true);
        this.physicWorld.setAutoClearForces(false);
        this.tiledAshleyLoader = new TiledAshleyLoader(engine, this.game.getAssetService(), inputManager, this.physicWorld);


        this.engine.addSystem(new EventSystem());
        this.engine.addSystem(new ControllerSystem());
        this.engine.addSystem(new PhysicMoveSystem());
        this.engine.addSystem(new AnimationPlayerSystem());
        this.engine.addSystem(new FacingSystem());
        this.engine.addSystem(new PhysicSystem(physicWorld, GameConsts.PHYSIC_INTERVAL));
        this.engine.addSystem(new AnimationSystem(this.game.getAssetService()));
        this.engine.addSystem(new CameraSystem(game.getCamera()));
        this.engine.addSystem(new RenderSystem(this.game.getBatch(), this.game.getViewport(), this.game.getCamera()));
        this.engine.addSystem(new TiledSystem(game.getAssetService(), tiledAshleyLoader, physicWorld));
        this.engine.addSystem(new PhysicDebugRenderSystem(physicWorld, game.getCamera()));

        //game.getCamera().zoom = 2;


    }

    @Override
    public void show() {
        visible = true;
        inputManager.releaseHeldInputs();
        game.setInputProcessors(inputManager);


        eventEmitter.accept(new Event.Map.Load(mapAssets));
    }

    @Override
    public void hide() {
        if (!visible) { return; }
        visible = false;
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
        this.physicWorld.dispose();
    }
}

