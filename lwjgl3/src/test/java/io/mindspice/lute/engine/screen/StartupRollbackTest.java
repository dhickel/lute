package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.ObjectAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StartupRollbackTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;
    private static int checks;
    private final RuntimeException fault = new IllegalStateException("injected startup failure");
    private final RuntimeException cleanupFault = new IllegalStateException("injected map cleanup failure");
    private final Asset.Sound unrelated = Asset.Sound.of("unrelated", "never-load.wav");
    private String failingPath;
    private Asset.Atlas mapAtlas;
    private int resolutions;
    private Texture attemptedTexture;
    private Texture borrowedTexture;
    private AssetService service;
    private ScreenManager screens;
    private Map<?, ?> loaded;
    private EntitySystem foreign;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Startup rollback regression");
        config.setWindowedMode(320, 240);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new StartupRollbackTest(), config);
        if (failure != null) { throw new AssertionError("Startup rollback regression failed", failure); }
        System.out.println("StartupRollbackTest: " + checks + " real GL checks passed");
    }

    @Override public void create() {
        try {
            exerciseMenu();
            exerciseDirectMap();
            exerciseMapCleanupFailure();
            exercisePendingMap(true);
            exercisePendingMap(false);
        } catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void setup(boolean direct) throws ReflectiveOperationException {
        System.setProperty("luteDirectMap", Boolean.toString(direct));
        GAME.create();
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screens = (ScreenManager) field(GameCore.class, "screenManager").get(GAME);
        loaded = (Map<?, ?>) field(ScreenManager.class, "loadedScreens").get(screens);
        GAME.getAssetService().dispose();
        service = new AssetService(path -> {
            resolutions++;
            if (path.equals(failingPath)) {
                if (mapAtlas != null) {
                    attemptedTexture = atlasTexture(mapAtlas);
                    throw fault;
                }
                attemptedTexture = atlasTexture(CharacterAssets.ENEMIES);
                borrowedTexture = atlasTexture(CharacterAssets.HEAD);
                require(attemptedTexture.getTextureObjectHandle() != 0,
                    "An attempt-owned atlas is resident before the injected load failure");
                require(GAME.getEngine().getSystems().size() == 1 && !loaded.containsKey("GameScreen"),
                    "Asset loading cannot construct or register a game session");
                throw fault;
            }
            return Gdx.files.internal(path);
        }) {
            @Override public void unload(Class<?> type, String id) {
                super.unload(type, id);
                if (mapAtlas != null && type == TiledMap.class && id.equals(mapAtlas.id())) { throw cleanupFault; }
            }
        };
        field(GameCore.class, "assetService").set(GAME, service);
        foreign = new EntitySystem() { };
        GAME.getEngine().addSystem(foreign);
        service.queue(unrelated);
    }

    private void exerciseMenu() throws ReflectiveOperationException {
        setup(false);
        try {
            var menu = (MenuScreen) loaded.get("Menu");
            var shared = service.load(CharacterAssets.CHARACTER);
            service.unload(unrelated.type(), unrelated.id());
            service.queue(CharacterAssets.HEAD);
            service.queue(unrelated);
            service.queue(CharacterAssets.FEET);
            var prequeued = List.of(CharacterAssets.HEAD, CharacterAssets.FEET);
            tap(Keys.ENTER); // Hoard -> name.
            var input = Gdx.input.getInputProcessor();
            input.keyDown(Keys.A); input.keyTyped('A'); input.keyUp(Keys.A);
            failingPath = CharacterAssets.FEET.path();
            tap(Keys.ENTER);
            verifyBlackBeforeWork();
            driveLoading();
            verifyRollback("hoard_mode", shared, prequeued);
            require(attemptedTexture != null && attemptedTexture.getTextureObjectHandle() == 0,
                "Rollback disposes a GL atlas acquired before the asset failure");
            require(borrowedTexture != null && borrowedTexture.getTextureObjectHandle() == 0,
                "Rollback disposes the provisional value of a previously queued atlas");
            require(screens.currScreen == menu && !((String) field(MenuScreen.class, "error").get(menu)).isBlank(),
                "The same menu shows an error after an asset failure");
            failingPath = null;

            var listener = failDuringMount();
            tap(Keys.ENTER);
            driveLoading();
            GAME.getEngine().removeEntityListener(listener);
            verifyRollback("hoard_mode", shared, prequeued);
            require(screens.currScreen == menu && !((String) field(MenuScreen.class, "error").get(menu)).isBlank(),
                "The same menu shows an error after a mount failure");
            require(!service.update() && service.isLoaded(TextureAtlas.class, CharacterAssets.HEAD.id())
                && !service.isLoaded(TextureAtlas.class, CharacterAssets.FEET.id()),
                "Rollback restores the borrowed atlas ahead of unrelated queued work");

            tap(Keys.ENTER); // Retry using the existing name and restored menu input.
            driveLoading();
            require(screens.currScreen instanceof GameScreen && screens.currScreen == loaded.get("GameScreen"),
                "Successful retry commits the game screen");
            require(GAME.getEngine().getEntitiesFor(Family.all(Player.class).get()).size() == 1,
                "Retry mounts exactly one player");
            require(service.get(TextureAtlas.class, CharacterAssets.CHARACTER.id()) == shared,
                "Successful retry reuses the shared atlas");
            require(!loaded.containsKey("InitialLoading"), "Successful handoff removes the loader");
        } finally { GAME.dispose(); }
    }

    private void exerciseDirectMap() throws ReflectiveOperationException {
        setup(true);
        try {
            var shared = service.load(CharacterAssets.CHARACTER);
            var listener = failDuringMount();
            verifyBlackBeforeWork();
            try {
                driveLoading();
                throw new AssertionError("Direct-map failure was swallowed");
            } catch (RuntimeException error) {
                require(error == fault, "Direct-map propagates the original mount exception");
            }
            GAME.getEngine().removeEntityListener(listener);
            verifyRollback(GameConsts.STARTUP_MAP, shared, List.of());
            require(screens.currScreen == null && !loaded.containsKey("Menu"),
                "Direct-map rollback leaves no active screen or menu");
        } finally { GAME.dispose(); }
    }

    private void exerciseMapCleanupFailure() throws ReflectiveOperationException {
        setup(true);
        try {
            var shared = service.load(CharacterAssets.CHARACTER);
            var assets = ManifestLoader.parseMapManifest(GameConsts.STARTUP_MAP);
            mapAtlas = assets.atlasAsset();
            failingPath = assets.mapAsset().path();
            attemptedTexture = null;
            verifyBlackBeforeWork();
            try {
                driveLoading();
                throw new AssertionError("Direct-map load failure was swallowed");
            } catch (GdxRuntimeException error) {
                require(error.getCause() == fault, "The original TMX load failure remains primary");
                require(List.of(error.getSuppressed()).equals(List.of(cleanupFault)),
                    "The unload failure is suppressed on the TMX load failure");
            }
            verifyRollback(GameConsts.STARTUP_MAP, shared, List.of());
            require(attemptedTexture != null && attemptedTexture.getTextureObjectHandle() == 0,
                "Map rollback still releases the atlas after a map cleanup failure");
        } finally { GAME.dispose(); }
    }

    private void exercisePendingMap(boolean failLoad) throws ReflectiveOperationException {
        setup(true);
        try {
            var assets = ManifestLoader.parseMapManifest(GameConsts.STARTUP_MAP);
            mapAtlas = assets.atlasAsset();
            var map = assets.mapAsset();
            service.unload(unrelated.type(), unrelated.id());
            service.queue(mapAtlas);
            service.queue(unrelated);
            service.queue(map);
            failingPath = failLoad ? map.path() : null;
            var listener = failLoad ? null : failDuringMount();
            try {
                driveLoading();
                throw new AssertionError("Expected startup failure with pending map assets");
            } catch (RuntimeException error) {
                require(failLoad ? error.getCause() == fault : error == fault, "Original startup exception survives");
                require(error.getSuppressed().length == 0, "Rollback does not unload the external map registration");
            }
            if (listener != null) { GAME.getEngine().removeEntityListener(listener); }
            for (var asset : List.of(mapAtlas, unrelated, map)) {
                require(service.isRegistered(asset) && !service.isLoaded(asset.type(), asset.id()),
                    "Original registration is pending after startup failure: " + asset);
            }
            failingPath = null;
            require(!service.update() && service.isLoaded(TextureAtlas.class, mapAtlas.id()), "Atlas is still first");
            failingPath = unrelated.path();
            try {
                service.update();
                throw new AssertionError("Unrelated queue entry no longer precedes TMX");
            } catch (GdxRuntimeException error) {
                require(error.getCause() == fault, "Unrelated entry is still second");
            }
            require(service.update() && service.isLoaded(TiledMap.class, map.id()), "TMX retries in its original position");
        } finally { GAME.dispose(); }
    }

    private Texture atlasTexture(Asset.Atlas atlas) {
        return service.get(TextureAtlas.class, atlas.id()).getTextures().first();
    }

    private EntityListener failDuringMount() {
        var listener = new EntityListener() {
            @Override public void entityAdded(Entity entity) {
                require(screens.currScreen instanceof LoadingScreen && !loaded.containsKey("GameScreen"),
                    "A partly mounted gameplay screen is not active or registered");
                for (var asset : sharedAssets()) {
                    require(service.isLoaded(asset.type(), asset.id()), "Mount follows shared asset completion: " + asset);
                }
                throw fault;
            }
            @Override public void entityRemoved(Entity entity) { }
        };
        GAME.getEngine().addEntityListener(Family.all(Player.class).get(), listener);
        return listener;
    }

    private List<Asset<?>> sharedAssets() {
        var weapons = GAME.getGameData().weaponsData();
        var assets = new ArrayList<>(CharacterAssets.all(weapons.atlas()));
        assets.add(ObjectAssets.OBJECTS);
        for (String id : weapons.weapons().keySet()) { assets.addAll(WeaponAssets.of(id, weapons).all()); }
        return assets;
    }

    private void verifyRollback(String map, TextureAtlas shared, List<Asset.Atlas> prequeued) throws ReflectiveOperationException {
        require(!loaded.containsKey("GameScreen") && !loaded.containsKey("InitialLoading"),
            "Failed attempt retains neither game nor loading screen");
        require(GAME.getEngine().getSystems().size() == 1 && GAME.getEngine().getSystems().first() == foreign
            && GAME.getEngine().getEntities().size() == 0, "Failed session detaches owned systems and entities only");
        var keys = GAME.getInputManager().getKeyManager();
        require(field(keys.getClass(), "size").getInt(keys) == 0, "No attempted gameplay input listeners remain");
        require(service.get(TextureAtlas.class, CharacterAssets.CHARACTER.id()) == shared
            && shared.getTextures().first().getTextureObjectHandle() != 0, "Preloaded shared GL resource survives by identity");
        for (var asset : sharedAssets()) {
            if (prequeued.contains(asset)) {
                require(service.isRegistered(asset) && !service.isLoaded(asset.type(), asset.id()),
                    "Pre-existing pending registration stays queued: " + asset);
            } else if (!asset.equals(CharacterAssets.CHARACTER)) {
                require(!service.isRegistered(asset), "Owned startup registration was removed: " + asset);
            }
        }
        for (var asset : ManifestLoader.parseMapManifest(map).all()) {
            require(!service.isRegistered(asset), "Owned map registration was removed: " + asset);
        }
        require(service.isRegistered(unrelated) && !service.isLoaded(unrelated.type(), unrelated.id()),
            "Unrelated queued work stays queued");
    }

    private void verifyBlackBeforeWork() {
        require(GAME.getGameData() == null && !service.isRegistered(CharacterAssets.ENEMIES),
            "Starting the loader does not read gameplay data or register assets");
        ScreenUtils.clear(Color.MAGENTA);
        int before = resolutions;
        GAME.render();
        require(resolutions == before && GAME.getGameData() == null && !service.isRegistered(CharacterAssets.ENEMIES),
            "The first loading frame does no startup asset work");
        var pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            for (int y = 0; y < pixels.getHeight(); y++) {
                for (int x = 0; x < pixels.getWidth(); x++) {
                    if ((pixels.getPixel(x, y) & 0xffffff00) != 0) { throw new AssertionError("Loader framebuffer is not plain black"); }
                }
            }
            checks++;
        } finally { pixels.dispose(); }
    }

    private void driveLoading() {
        for (int i = 0; i < 200 && screens.currScreen instanceof LoadingScreen; i++) { GAME.render(); }
        require(!(screens.currScreen instanceof LoadingScreen), "Loading terminates");
    }

    private static void tap(int key) {
        var input = Gdx.input.getInputProcessor();
        input.keyDown(key); input.keyUp(key);
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
        checks++;
    }
}
