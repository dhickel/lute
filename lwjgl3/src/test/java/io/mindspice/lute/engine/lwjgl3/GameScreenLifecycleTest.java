package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.systems.PhysicSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.systems.WorldTimeSystem;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GameScreenLifecycleTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;
    private static int checks;
    private boolean initialized;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Game screen lifecycle regression");
        config.setWindowedMode(320, 240);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new GameScreenLifecycleTest(), config);
        if (failure != null) { throw new AssertionError("Game screen lifecycle regression failed", failure); }
        System.out.println("GameScreenLifecycleTest: " + checks + " real GL checks passed");
    }

    @Override
    public void create() {
        try { runTests(); }
        catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void runTests() throws ReflectiveOperationException {
        GAME.create();
        initialized = true;
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        var engine = GAME.getEngine();
        var screens = (ScreenManager) field(GameCore.class, "screenManager").get(GAME);
        var loaded = (Map<?, ?>) field(ScreenManager.class, "loadedScreens").get(screens);
        var original = (GameScreen) loaded.get("GameScreen");
        var assets = ManifestLoader.parseMapManifest("test_tiled");
        // LoadingScreen loads one asset per render; keep this finite even if startup breaks.
        for (int i = 0; i < 200 && engine.getSystem(TiledSystem.class).getCurrentMap() == null; i++) {
            GAME.render();
        }
        require(playerCount(engine) == 1, "Real initial loading creates one player");
        var foreign = new ForeignSystem();
        engine.addSystem(foreign);
        var owned = snapshot(engine, foreign);
        var physics = engine.getSystem(PhysicSystem.class);
        var time = engine.getSystem(WorldTimeSystem.class);
        require(time.getDayLengthMinutes() == GameConsts.DAY_LENGTH_MINUTES,
            "GameScreen supplies the configured day length to world time");
        double clampedWorldSeconds = (24 * 60 / GameConsts.DAY_LENGTH_MINUTES) / 30;
        var beforeVisibleUpdate = time.getTotalInGameSeconds();
        original.render(1f);
        near(time.getTotalInGameSeconds(), beforeVisibleUpdate + clampedWorldSeconds,
            "Visible game rendering advances world time with the clamped simulation delta");
        original.pause();
        var pausedAt = time.getTotalInGameSeconds();
        original.render(1f);
        near(time.getTotalInGameSeconds(), pausedAt, "Paused game rendering does not advance world time");
        original.resume();
        original.render(1f);
        near(time.getTotalInGameSeconds(), pausedAt + clampedWorldSeconds, "Resumed game rendering advances world time");

        var player = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
        var loadout = io.mindspice.lute.engine.components.Loadout.MAPPER.get(player);
        var originalWeapon = loadout.getWeaponId();
        loadout.setWeapon("basic_bow");
        var aim = io.mindspice.lute.engine.components.BowAim.MAPPER.get(player);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SPACE);
        engine.update(.01f);
        require(aim.isAiming() && !Combat.MAPPER.get(player).isAttacking(), "Real bow draws the aiming overlay");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SPACE);
        engine.update(.01f);
        require(!aim.isAiming() && Combat.MAPPER.get(player).isAttacking(), "Release renders the atlas arrow instead");
        for (int i = 0; i < 120; i++) { engine.update(1f / 60); }
        loadout.setWeapon(originalWeapon);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.W);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SHIFT_LEFT);
        engine.update(0.02f);
        require(Combat.MAPPER.get(player).isAttacking(), "Teardown starts with a live attack");
        var beforeHide = time.getTotalInGameSeconds();
        screens.loadScreen("away", new ScreenAdapter());
        screens.switchScreens("away");
        original.render(1f);
        near(time.getTotalInGameSeconds(), beforeHide, "Hidden game rendering does not advance world time");
        screens.switchScreens("GameScreen");
        original.render(1f);
        near(time.getTotalInGameSeconds(), beforeHide + clampedWorldSeconds, "Shown game rendering resumes world time");
        require(playerCount(engine) == 1, "Hide/show retains the live player while checking world time");
        screens.switchScreens("away");
        require(playerCount(engine) == 0, "Hide removes the old player and its physics body");
        require(engine.getSystem(PhysicSystem.class) == physics, "Hide retains screen systems for reuse");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.W);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SHIFT_LEFT);
        screens.switchScreens("GameScreen");
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SHIFT_LEFT);
        engine.update(0.02f);
        require(playerCount(engine) == 1, "Showing the same screen reloads one player");
        require(engine.getSystem(PhysicSystem.class) == physics, "Show reuses the same physics system");
        var resumed = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
        require(Combat.MAPPER.get(resumed).isAttacking(), "Key-up while hidden allows a fresh press after show");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SHIFT_LEFT);

        screens.unloadScreen("GameScreen");
        verifyDetached(engine, foreign, owned);
        require(physics.getEngine() == null, "Disposed screen's physics system cannot receive engine updates");
        int updates = foreign.updates;
        engine.update(0.02f); // Separate JavaExec process isolates a native regression from Gradle.
        require(foreign.updates == updates + 1, "The surviving engine safely updates foreign systems after World disposal");
        original.dispose();
        verifyDetached(engine, foreign, owned);
        try {
            original.show();
            throw new AssertionError("Disposed screen must not be shown again");
        } catch (IllegalStateException expected) {
            require(true, "Disposed screen cannot enqueue a map load into a later screen");
        }

        for (int i = 0; i < 2; i++) {
            var replacement = new GameScreen(assets);
            screens.loadScreen("GameScreen", replacement);
            screens.switchScreens("GameScreen");
            engine.update(0.02f);
            require(playerCount(engine) == 1, "Recreation installs exactly one live player: " + i);
            require(engine.getSystem(PhysicSystem.class) != physics, "Recreation uses fresh physics");
            require(engine.getSystem(EventSystem.class) != owned.getFirst(), "Recreation uses a fresh event bus");
            var replacements = snapshot(engine, foreign);
            screens.unloadScreen("GameScreen");
            verifyDetached(engine, foreign, replacements);
            replacement.dispose();
            engine.update(0.02f);
        }

        var neverShown = new GameScreen(assets);
        var hiddenSystems = snapshot(engine, foreign);
        screens.loadScreen("hidden", neverShown);
        screens.unloadScreen("hidden");
        neverShown.dispose();
        verifyDetached(engine, foreign, hiddenSystems);
        engine.update(0.02f);
        verifyPartialSetup(assets);

        var shutdownScreen = new GameScreen(assets);
        screens.loadScreen("shutdown", shutdownScreen);
        screens.switchScreens("shutdown");
        engine.update(0.02f);
        require(playerCount(engine) == 1, "Final screen is active before normal application shutdown");
        var shutdownSystems = snapshot(engine, foreign);
        GAME.dispose();
        initialized = false;
        verifyDetached(engine, foreign, shutdownSystems);
        shutdownScreen.dispose();
        engine.update(0.02f);
    }

    private static void verifyPartialSetup(MapAssets assets) throws ReflectiveOperationException {
        var originalEngine = GAME.getEngine();
        var partial = new FailingEngine();
        var foreign = new ForeignSystem();
        partial.addSystem(foreign);
        var engineField = field(GameCore.class, "engine");
        engineField.set(GAME, partial);
        try {
            try {
                new GameScreen(assets);
                throw new AssertionError("Injected installation failure must propagate");
            } catch (IllegalStateException expected) {
                require(expected == partial.failure, "Constructor preserves the original installation failure");
            }
            require(partial.installed.stream().anyMatch(TiledSystem.class::isInstance),
                "Failure occurs after the map owner was installed");
            verifyDetached(partial, foreign, partial.installed);
            partial.update(0.02f);
            require(foreign.updates == 1, "Partial setup leaves the shared engine usable");
        } finally {
            engineField.set(GAME, originalEngine);
        }
    }

    private static List<EntitySystem> snapshot(Engine engine, ForeignSystem foreign) {
        var systems = new ArrayList<EntitySystem>();
        for (var system : engine.getSystems()) {
            if (system != foreign) { systems.add(system); }
        }
        return systems;
    }

    private static void verifyDetached(Engine engine, ForeignSystem foreign, List<EntitySystem> owned) {
        require(engine.getSystems().size() == 1 && engine.getSystems().first() == foreign,
            "Only the foreign system remains installed");
        require(foreign.disposals == 0 && foreign.removals == 0, "Screen neither removes nor disposes a foreign system");
        for (var system : owned) {
            require(system.getEngine() == null, "Owned system detached before engine reuse: " + system.getClass().getSimpleName());
        }
        require(engine.getEntities().size() == 0, "Disposed screen leaves no entities referencing its World");
    }

    private static int playerCount(Engine engine) {
        return engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).size();
    }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void near(double actual, double expected, String message) {
        checks++;
        if (Math.abs(actual - expected) > 0.000001) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    @Override
    public void dispose() {
        if (initialized) {
            try { GAME.dispose(); }
            catch (Throwable error) {
                if (failure == null) { failure = error; }
                else { failure.addSuppressed(error); }
            }
        }
    }

    private static final class ForeignSystem extends EntitySystem implements Disposable {
        private int updates;
        private int disposals;
        private int removals;

        @Override public void update(float deltaTime) { updates++; }
        @Override public void removedFromEngine(Engine engine) { removals++; }
        @Override public void dispose() { disposals++; }
    }

    private static final class FailingEngine extends Engine {
        private final IllegalStateException failure = new IllegalStateException("Injected system installation failure");
        private final List<EntitySystem> installed = new ArrayList<>();

        @Override
        public void addSystem(EntitySystem system) {
            super.addSystem(system);
            if (!(system instanceof ForeignSystem)) { installed.add(system); }
            if (system instanceof TiledSystem) { throw failure; }
        }
    }
}
