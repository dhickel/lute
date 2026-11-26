package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.singletons.GameConsts.DebugLayers;
import io.mindspice.lute.engine.systems.AwarenessSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.systems.PhysicDebugRenderSystem;
import io.mindspice.lute.engine.systems.PhysicSystem;
import io.mindspice.lute.engine.systems.RenderSystem;
import io.mindspice.lute.engine.systems.TriggerDebugRenderSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.systems.WorldTimeSystem;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.BitSet;
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
        var input = Gdx.input.getInputProcessor();
        input.keyDown(Input.Keys.N); // The shared manager observes this before the gameplay listener is attached.
        // LoadingScreen loads one asset per render; keep this finite even if startup breaks.
        for (int i = 0; i < 200 && engine.getSystem(TiledSystem.class).getCurrentMap() == null; i++) {
            GAME.render();
        }
        require(playerCount(engine) == 1, "Real initial loading creates one player");
        var map = engine.getSystem(TiledSystem.class).getCurrentMap();
        var ground = (TiledMapTileLayer) map.getLayers().get("Ground");
        var groundTile = ground.getCell(0, 0).getTile();
        var mapAtlas = GAME.getAssetService().get(TextureAtlas.class, assets.mapAsset().id());
        int firstGid = 0;
        for (var set : map.getTileSets()) {
            if (set.getTile(groundTile.getId()) == groundTile) {
                firstGid = set.getProperties().get("firstgid", Integer.class);
                break;
            }
        }
        require(firstGid > 0 && groundTile.getTextureRegion() == mapAtlas.findRegion("__lute_tile/" + firstGid, groundTile.getId() - firstGid),
            "The real GameScreen ground layer must render from the generated map atlas");
        System.out.println("GameScreen entered: Ground[0,0] GID " + groundTile.getId()
            + " uses atlas GL texture " + groundTile.getTextureRegion().getTexture().getTextureObjectHandle());
        var foreign = new ForeignSystem();
        engine.addSystem(foreign);
        var owned = snapshot(engine, foreign);
        var physics = engine.getSystem(PhysicSystem.class);
        var time = engine.getSystem(WorldTimeSystem.class);
        require(time.getDayLengthMinutes() == GameConsts.DAY_LENGTH_MINUTES,
            "GameScreen supplies the configured day length to world time");
        double clampedWorldSeconds = (24 * 60 / GameConsts.DAY_LENGTH_MINUTES) / 30;
        var beforeVisibleUpdate = time.getTotalInGameSeconds();
        double initialSeconds = seconds(original);
        input.keyDown(Input.Keys.N);
        original.render(1f);
        require(!night(original), "The initial session starts in day even with N held from loading");
        input.keyUp(Input.Keys.N);
        near(seconds(original), initialSeconds + 1 / 30f, "Pulse time uses the clamped simulation delta in day");
        near(time.getTotalInGameSeconds(), beforeVisibleUpdate + clampedWorldSeconds,
            "Visible game rendering advances world time with the clamped simulation delta");
        verifyDebugLayers(engine, GameConsts.DEBUG_LAYERS);
        double beforeNight = time.getTotalInGameSeconds();
        input.keyDown(Input.Keys.N);
        original.render(1f);
        require(night(original), "N enables night");
        near(time.getTotalInGameSeconds() - beforeNight, beforeNight - beforeVisibleUpdate,
            "Equal simulation deltas advance world time identically in both modes");
        input.keyDown(Input.Keys.N);
        GAME.getInputManager().getKeyManager().broadcast(Input.Keys.N, 2);
        original.render(0f);
        require(night(original), "Duplicate down and repeat do not toggle");
        input.keyUp(Input.Keys.N);
        original.render(0f);
        require(night(original), "Release does not toggle");
        tapNight();
        original.render(0f);
        require(!night(original), "A complete tap between frames is not lost");
        tapNight(); tapNight();
        original.render(0f);
        require(!night(original), "Two distinct queued presses toggle twice");
        input.keyDown(Input.Keys.N); original.render(0f);
        GAME.pause(); // Held across a context change after its toggling edge was consumed.
        var pausedAt = time.getTotalInGameSeconds();
        double pausedSeconds = seconds(original);
        original.render(1f);
        near(time.getTotalInGameSeconds(), pausedAt, "Paused game rendering does not advance world time");
        near(seconds(original), pausedSeconds, "Pulse time freezes during pause");
        GAME.resume();
        input.keyDown(Input.Keys.N);
        original.render(1f);
        near(seconds(original), pausedSeconds + 1 / 30f, "Resume advances the existing pulse time once");
        require(night(original), "A consumed press held across pause cannot retrigger on resume");
        input.keyUp(Input.Keys.N);
        tapNight(); // Never rendered; this tap belongs to the context about to end.
        GAME.pause(); GAME.resume(); original.render(0f);
        require(night(original), "Unconsumed tap at pause does not toggle after resume");
        GAME.pause(); input.keyDown(Input.Keys.N); GAME.resume();
        input.keyDown(Input.Keys.N); original.render(0f);
        require(night(original), "A key pressed while paused is ignored until physical release");
        input.keyUp(Input.Keys.N);
        tapNight(); original.render(0f);
        require(!night(original), "Real key-up rearms the physical key after pause");
        tapNight(); original.render(0f);
        near(time.getTotalInGameSeconds(), pausedAt + clampedWorldSeconds, "Resumed game rendering advances world time");

        var player = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
        var loadout = io.mindspice.lute.engine.components.Loadout.MAPPER.get(player);
        var originalWeapon = loadout.getWeaponId();
        loadout.setWeapon("basic_bow");
        var aim = io.mindspice.lute.engine.components.BowAim.MAPPER.get(player);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SPACE);
        original.render(.01f);
        require(aim.isAiming() && !Combat.MAPPER.get(player).isAttacking(), "Real bow draws the aiming overlay");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SPACE);
        original.render(.01f);
        require(!aim.isAiming() && Combat.MAPPER.get(player).isAttacking(), "Release renders the atlas arrow instead");
        for (int i = 0; i < 120; i++) { original.render(1f / 60); }
        loadout.setWeapon(originalWeapon);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.W);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SHIFT_LEFT);
        original.render(0.02f);
        require(Combat.MAPPER.get(player).isAttacking(), "Teardown starts with a live attack");
        var awareness = engine.getSystem(AwarenessSystem.class);
        var actors = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Actor.class).get());
        var oldEnemy = actors.get(2);
        var beforeHide = time.getTotalInGameSeconds();
        var beforeHideSeconds = seconds(original);
        input.keyDown(Input.Keys.N); // Queued in the old visible context and held into the next.
        screens.loadScreen("away", new ScreenAdapter());
        screens.switchScreens("away");
        original.render(1f);
        near(time.getTotalInGameSeconds(), beforeHide, "Hidden game rendering does not advance world time");
        near(seconds(original), beforeHideSeconds, "Pulse time freezes while hidden");
        require(actors.size() == 0 && awareness.inRange(oldEnemy, Actor.Role.PLAYER).isEmpty(),
            "Hide removes awareness actors and queryable fixtures");
        screens.switchScreens("GameScreen");
        input.keyDown(Input.Keys.N);
        original.render(1f);
        require(night(original), "Hidden stale press and still-held down cannot retrigger on show");
        verifyDebugLayers(engine, GameConsts.DEBUG_LAYERS);
        near(seconds(original), beforeHideSeconds + 1 / 30f, "Show resumes the same pulse clock");
        input.keyUp(Input.Keys.N);
        near(time.getTotalInGameSeconds(), beforeHide + clampedWorldSeconds, "Shown game rendering resumes world time");
        require(playerCount(engine) == 1, "Hide/show retains the live player while checking world time");
        require(actors.size() == 3 && !actors.contains(oldEnemy, true)
            && awareness.inRange(oldEnemy, Actor.Role.PLAYER).isEmpty(), "Show rebuilds actors without reusing old targets");
        screens.switchScreens("away");
        require(playerCount(engine) == 0, "Hide removes the old player and its physics body");
        require(engine.getSystem(PhysicSystem.class) == physics, "Hide retains screen systems for reuse");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.W);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SHIFT_LEFT);
        tapNight(); // Press and release entirely while away.
        screens.switchScreens("GameScreen");
        original.render(0f);
        require(night(original), "A hidden tap is not replayed");
        Gdx.input.getInputProcessor().keyDown(Input.Keys.SHIFT_LEFT);
        original.render(0.02f);
        require(playerCount(engine) == 1, "Showing the same screen reloads one player");
        require(engine.getSystem(PhysicSystem.class) == physics, "Show reuses the same physics system");
        var resumed = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
        require(Combat.MAPPER.get(resumed).isAttacking(), "Key-up while hidden allows a fresh press after show");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.SHIFT_LEFT);

        var oldFire = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Emissive.class).get()).first();
        input.keyDown(Input.Keys.N); // Held through disposal and recreation.
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

        for (var layers : List.of(
            new DebugLayers(false, false, false, false, true),
            new DebugLayers(true, false, false, false, true),
            new DebugLayers(false, true, false, false, true),
            new DebugLayers(false, false, true, false, true),
            new DebugLayers(false, false, false, true, true),
            new DebugLayers(true, true, true, true, true))) {
            var authored = GameConsts.DEBUG_LAYERS;
            GameScreen replacement;
            try {
                GameConsts.DEBUG_LAYERS = layers;
                replacement = new GameScreen(assets);
            } finally { GameConsts.DEBUG_LAYERS = authored; }
            screens.loadScreen("GameScreen", replacement);
            screens.switchScreens("GameScreen");
            var lights = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Emissive.class).get());
            input.keyDown(Input.Keys.N);
            replacement.render(0f);
            require(!night(replacement) && seconds(replacement) == 0d, "New session starts day at zero, ignoring held N");
            verifyDebugLayers(engine, layers);
            require(!lights.contains(oldFire, true) && lights.size() > 0, "Only freshly mounted lights exist on first frame");
            input.keyUp(Input.Keys.N);
            input.keyDown(Input.Keys.N);
            replacement.render(.02f);
            require(night(replacement), "Fresh press after real key-up toggles the replacement screen");
            if (layers.equals(new DebugLayers(false, false, false, false, true))) {
                replacement.render(0f);
                var alive = hudGlyphs();
                var view = GAME.getViewport();
                int vx = view.getScreenX(), vy = view.getScreenY(), vw = view.getScreenWidth(), vh = view.getScreenHeight();
                try {
                    view.setScreenBounds(vx + 19, vy + 7, vw - 52, vh - 30);
                    replacement.render(0f);
                    hudGlyphs(); // Recheck both endpoints and the group's center with nonzero X and Y offsets.
                    for (int width : new int[]{260, 96, 72}) {
                        GAME.resize(width, 180);
                        require(view.getScreenWidth() == width && view.getScreenY() > 0,
                            "Resize exercises the letterboxed gameplay viewport at " + width + "px");
                        replacement.render(0f);
                        hudGlyphs();
                    }
                } finally { GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight()); }
                replacement.render(0f);
                require(hudGlyphs().equals(alive), "Restoring the viewport also restores native-size glyphs");
                var activePlayer = engine.getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
                var health = Health.MAPPER.get(activePlayer);
                health.damage(health.getMaximum() / 2);
                replacement.render(0f);
                hudGlyphs(); // Half health must use yellow ink.
                health.damage(Integer.MAX_VALUE);
                replacement.render(0f);
                var dead = hudGlyphs();
                require(!dead.equals(alive), "Live-to-dead health redraws unlit glyphs at night without advancing the clock");
                var largerHealth = new Health(10000);
                largerHealth.damage(Integer.MAX_VALUE); // Keep current HP and time fixed; change only maximum HP.
                activePlayer.add(largerHealth);
                replacement.render(0f);
                require(!hudGlyphs().equals(dead), "HUD reads the player's new maximum HP");
            }
            oldFire = lights.first();
            require(playerCount(engine) == 1, "Recreation installs exactly one live player: " + layers);
            require(engine.getSystem(PhysicSystem.class) != physics, "Recreation uses fresh physics");
            require(engine.getSystem(EventSystem.class) != owned.getFirst(), "Recreation uses a fresh event bus");
            var replacements = snapshot(engine, foreign);
            screens.unloadScreen("GameScreen");
            verifyDetached(engine, foreign, replacements);
            replacement.dispose();
            engine.update(0.02f);
        }

        input.keyUp(Input.Keys.N);
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
        shutdownScreen.render(0.02f);
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

    private static void tapNight() {
        Gdx.input.getInputProcessor().keyDown(Input.Keys.N);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.N);
    }

    static boolean night(GameScreen screen) throws ReflectiveOperationException {
        return field(GameScreen.class, "night").getBoolean(screen);
    }

    private static void verifyDebugLayers(Engine engine, DebugLayers expected) throws ReflectiveOperationException {
        var physics = engine.getSystem(PhysicDebugRenderSystem.class);
        var triggers = engine.getSystem(TriggerDebugRenderSystem.class);
        require(expected.physics() ? physics != null && physics.checkProcessing() : physics == null,
            "Physics debug system installed and processing only when configured");
        require(expected.triggers() ? triggers != null && triggers.checkProcessing() : triggers == null,
            "Trigger debug system installed and processing only when configured");
        var renderer = engine.getSystem(RenderSystem.class);
        require(expected.equals(field(RenderSystem.class, "debugLayers").get(renderer)), "Renderer retains the startup snapshot");
    }

    static double seconds(GameScreen screen) throws ReflectiveOperationException {
        return field(GameScreen.class, "sessionSeconds").getDouble(screen);
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
        if (!(Math.abs(actual - expected) <= 0.000001)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    /** Read the two foreground inks, excluding shadows and the panel. */
    static BitSet hudGlyphs() {
        var view = GAME.getViewport();
        int width = HdpiUtils.toBackBufferX(view.getScreenWidth());
        int height = HdpiUtils.toBackBufferY(24);
        var pixels = Pixmap.createFromFrameBuffer(HdpiUtils.toBackBufferX(view.getScreenX()),
            HdpiUtils.toBackBufferY(view.getScreenY() + view.getScreenHeight()) - height, width, height);
        try {
            var player = GAME.getEngine().getEntitiesFor(com.badlogic.ashley.core.Family.all(Player.class).get()).first();
            var health = Health.MAPPER.get(player);
            float ratio = (float) health.getCurrent() / health.getMaximum();
            int hpRed = Math.round(255 * Math.min(1, 2 * (1 - ratio)));
            int hpGreen = Math.round(255 * Math.min(1, 2 * ratio));
            var glyphs = new BitSet(width * height);
            int left = width, right = -1, hpEnd = -1, timeStart = width;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int rgba = pixels.getPixel(x, y);
                    int r = rgba >>> 24, g = rgba >>> 16 & 255, b = rgba >>> 8 & 255;
                    boolean hp = Math.abs(r - hpRed) < 15 && Math.abs(g - hpGreen) < 15 && b < 10;
                    boolean time = b > 195 && b > g && g > r;
                    if (hp || time) {
                        if (hp) { hpEnd = Math.max(hpEnd, x); }
                        if (time) { timeStart = Math.min(timeStart, x); }
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                        glyphs.set(y * width + x);
                    }
                }
            }
            require(hpEnd >= 0 && timeStart < width && hpEnd < timeStart, "HP in its live ratio color precedes the blue clock");
            int rightMargin = width - 1 - right;
            require(Math.min(left, rightMargin) >= HdpiUtils.toBackBufferX(3)
                    && Math.abs(left - rightMargin) <= HdpiUtils.toBackBufferX(3),
                "HP and time share the viewport center with open side margins at " + width + "px: " + left + "/" + rightMargin);
            int gap = (hpEnd + timeStart) / 2;
            for (int y = 0; y < height; y++) {
                int charcoal = pixels.getPixel(0, y);
                require((charcoal == 0x191919ff || charcoal == 0x232323ff)
                        && pixels.getPixel(width - 1, y) == charcoal,
                    "Neutral charcoal spans the viewport at row " + y);
                require(pixels.getPixel(gap, y) == charcoal, "No divider between HP and time at row " + y);
            }
            return glyphs;
        } finally { pixels.dispose(); }
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
