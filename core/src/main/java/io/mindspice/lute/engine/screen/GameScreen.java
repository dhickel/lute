package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.*;

import java.util.ArrayList;
import java.util.List;

public class GameScreen extends ScreenAdapter {
    private final InputManager inputManager;
    private final GameCore game;
    private final World physicWorld;
    private final Engine engine;
    private final MapAssets mapAssets;
    private final TiledSystem tiled;
    private final List<EntitySystem> ownedSystems = new ArrayList<>();
    private boolean visible;
    private boolean paused;
    private boolean disposed;


    public GameScreen(MapAssets mapAssets) {
        this.game = GameCore.INSTANCE;
        this.inputManager = this.game.getInputManager();
        this.engine = this.game.getEngine();
        this.mapAssets = mapAssets;

        this.physicWorld = new World(Vector2.Zero, true);
        this.physicWorld.setAutoClearForces(false);

        try {
            addSystem(new WorldTimeSystem(GameConsts.DAY_LENGTH_MINUTES));
            addSystem(new EventSystem());
            addSystem(new AttackCooldownSystem());
            addSystem(new ControllerSystem());
            addSystem(new FacingSystem());
            // Read the last completed physics/teleport snapshot before resolving this frame's animation.
            addSystem(new AwarenessSystem(physicWorld));
            addSystem(new AnimationPlayerSystem());
            addSystem(new AnimationSystem(this.game.getAssetService()));
            addSystem(new CombatSystem(physicWorld, game.getGameData().projectilesData().ironArrow()));
            addSystem(new LoadoutSystem());
            addSystem(new MapSensorSystem(physicWorld));
            addSystem(new PhysicMoveSystem());
            addSystem(new PhysicSystem(physicWorld, GameConsts.PHYSIC_INTERVAL));
            addSystem(new AudioSystem(game.getAssetService()));
            addSystem(new CameraSystem(game.getCamera()));
            addSystem(new RenderSystem(this.game.getBatch(), this.game.getViewport(), this.game.getCamera(), game.getAssetService()));
            addSystem(new EntityFactory(physicWorld, game.getAssetService()));
            tiled = new TiledSystem(game.getAssetService(), physicWorld, inputManager);
            addSystem(tiled);
            addSystem(new PhysicDebugRenderSystem(physicWorld, game.getCamera()));
            addSystem(new TriggerDebugRenderSystem(physicWorld, game.getCamera()));
        } catch (RuntimeException | Error failure) {
            try { dispose(); }
            catch (RuntimeException | Error cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            throw failure;
        }
    }

    private void addSystem(EntitySystem system) {
        ownedSystems.add(system);
        engine.addSystem(system);
    }

    @Override
    public void show() {
        if (disposed) { throw new IllegalStateException("Game screen is disposed"); }
        if (visible) { return; }
        inputManager.releaseHeldInputs();
        tiled.showMap(mapAssets);
        game.setInputProcessors(inputManager);
        visible = true;
    }

    @Override
    public void hide() {
        if (!visible) { return; }
        visible = false;
        paused = false;
        tiled.hideMap();
    }

    @Override
    public void pause() {
        paused = true;
        inputManager.releaseHeldInputs();
        engine.getSystem(ControllerSystem.class).update(0f);
    }

    @Override
    public void resume() {
        inputManager.releaseHeldInputs();
        engine.getSystem(ControllerSystem.class).update(0f);
        paused = false;
    }

    @Override
    public void render(float delta) {
        if (!visible || paused) { return; }
        inputManager.updateGamepad();
        engine.update(Math.min(delta, 1 / 30f));
        tiled.applyPendingTravel();
    }

    @Override
    public void dispose() {
        if (disposed) { return; }
        disposed = true;

        Throwable cleanupFailure = null;
        try {
            hide();
        } catch (RuntimeException | Error failure) {
            cleanupFailure = failure;
        }

        // Dependents detach first; event helpers and native cleanup still need the bus and world.
        for (int i = ownedSystems.size() - 1; i >= 0; i--) {
            var system = ownedSystems.get(i);
            try {
                if (system.getEngine() == engine) { engine.removeSystem(system); }
                if (system instanceof Disposable disposable) { disposable.dispose(); }
            } catch (RuntimeException | Error failure) {
                if (cleanupFailure == null) { cleanupFailure = failure; }
                else { cleanupFailure.addSuppressed(failure); }
            }
        }
        ownedSystems.clear();

        try {
            physicWorld.dispose();
        } catch (RuntimeException | Error failure) {
            if (cleanupFailure == null) { cleanupFailure = failure; }
            else { cleanupFailure.addSuppressed(failure); }
        }

        if (cleanupFailure instanceof RuntimeException failure) { throw failure; }
        if (cleanupFailure instanceof Error failure) { throw failure; }
    }
}

