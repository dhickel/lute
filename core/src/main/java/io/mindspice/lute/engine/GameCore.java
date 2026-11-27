package io.mindspice.lute.engine;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.ObjectAssets;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.GameData;
import io.mindspice.lute.engine.tiled.ManifestLoader;
import io.mindspice.lute.engine.systems.AudioSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputSettings;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.LoadingScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.screen.MenuScreen;
import io.mindspice.lute.gameplay.HoardRun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 */
public class GameCore extends Game {
    public static final GameCore INSTANCE = new GameCore();
    private Batch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    AssetService assetService;
    FPSLogger fpsLogger;
    GLProfiler glProfiler;
    InputMultiplexer inputMultiplexer;
    private InputManager inputManager;
    private ScreenManager screenManager;
    private MenuScreen menu;
    private int windowWidth, windowHeight;
    private Engine engine;
    private GameData gameData;


    @Override
    public void create() {
        gameData = null;
        GameConsts.loadSettings();
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        var inputMap = InputSettings.load(Gdx.files.internal("input.setting.json"));
        this.inputManager = new InputManager(inputMap);
        this.inputMultiplexer = new InputMultiplexer(inputManager);
        Gdx.input.setInputProcessor(inputMultiplexer);

        this.engine = new Engine();

        this.batch = new SpriteBatch();
        this.camera = new OrthographicCamera();
        this.viewport = new FitViewport(GameConsts.VIEW_WIDTH, GameConsts.VIEW_HEIGHT, camera);
        this.assetService = new AssetService(new InternalFileHandleResolver());

        this.fpsLogger = new FPSLogger();
        this.glProfiler = new GLProfiler(Gdx.graphics);
        glProfiler.enable();


        this.screenManager = new ScreenManager();
        if (Boolean.getBoolean("luteDirectMap")) {
            menu = null;
            startMap(GameConsts.STARTUP_MAP);
        } else {
            menu = new MenuScreen(this, inputMap, () -> startMap("hoard_mode"));
            screenManager.loadScreen("Menu", menu);
            screenManager.switchScreens("Menu");
        }
    }

    private void startMap(String mapId) {
        var attempt = new StartupAttempt(mapId);
        screenManager.loadScreen("InitialLoading", attempt.screen);
        screenManager.switchScreens("InitialLoading");
        setInputProcessors(attempt.screen.input(), inputManager);
    }

    private void returnFromStartupFailure() {
        menu.launchFailed();
        screenManager.switchScreens("Menu");
        screenManager.unloadScreen("InitialLoading");
    }

    private record AssetKey(Class<?> type, String id) {
        AssetKey(Asset<?> asset) { this(asset.type(), asset.id()); }
    }

    private final class StartupAttempt {
        private final String mapId;
        private final LoadingScreen screen = new LoadingScreen(this::advance,
            menu == null ? null : GameCore.this::returnFromStartupFailure);
        private final HashSet<AssetKey> complete = new HashSet<>();
        private int total;
        private MapAssets mapAssets;
        private AssetService.PendingLoads sharedLoads;
        private AssetService.PendingLoads mapLoads;
        private Iterator<Asset<?>> pending;
        private GameScreen session;

        StartupAttempt(String mapId) { this.mapId = mapId; }

        private void planAssets() {
            if (gameData == null) { gameData = DataLoader.loadGameData(); }
            var assets = new ArrayList<>(CharacterAssets.all(gameData.weaponsData().atlas()));
            assets.add(ObjectAssets.OBJECTS);
            for (String id : gameData.weaponsData().weapons().keySet()) {
                assets.addAll(WeaponAssets.of(id, gameData.weaponsData()).all());
            }
            mapAssets = ManifestLoader.parseMapManifest(mapId);
            var all = new ArrayList<>(assets);
            all.addAll(mapAssets.all());
            var definitions = new LinkedHashMap<AssetKey, Asset<?>>();
            for (var asset : all) {
                var previous = definitions.putIfAbsent(new AssetKey(asset), asset);
                if (previous != null && !previous.path().equals(asset.path())) {
                    throw new IllegalArgumentException("Conflicting asset definition for " + asset.type().getSimpleName()
                        + "[" + asset.id() + "]: '" + previous.path() + "' versus '" + asset.path() + "'");
                }
            }
            definitions.values().forEach(assetService::validateDefinition);
            total = definitions.size();
            definitions.forEach((key, asset) -> {
                if (assetService.isDefinitionLoaded(asset)) { complete.add(key); }
            });
            screen.progress(complete.size(), total);
            sharedLoads = assetService.preservePendingLoads();
            pending = assets.stream().distinct().filter(asset -> !complete.contains(new AssetKey(asset))).iterator();
        }

        private void load(AssetService.PendingLoads loads, Asset<?> asset) {
            loads.load(asset);
            complete.add(new AssetKey(asset));
            screen.progress(complete.size(), total);
        }

        void advance() {
            try {
                if (pending == null) { planAssets(); }
                if (pending.hasNext()) { load(sharedLoads, pending.next()); }
                if (pending.hasNext()) { return; }
                if (mapLoads == null) {
                    AudioSystem.validateMapMusic(mapAssets);
                    mapLoads = assetService.preservePendingLoads();
                    for (var asset : mapAssets.all()) {
                        if (!complete.contains(new AssetKey(asset))) { load(mapLoads, asset); }
                    }
                    return; // Draw the completed count before attempting to mount gameplay.
                }

                session = menu == null ? new GameScreen(mapAssets)
                    : new GameScreen(mapAssets, menu.layers(), GameCore.this::finishRun);
                var tiled = engine.getSystem(TiledSystem.class);
                tiled.beginStartupMount(mapLoads);
                screenManager.mountScreen("GameScreen", session);
                tiled.commitStartupMount();
            } catch (RuntimeException | Error failure) {
                rollback(failure);
                Gdx.app.error("Startup", "Could not start map " + mapId, failure);
                if (failure instanceof Error error) { throw error; }
                screen.fail((RuntimeException) failure);
                return;
            }
            screenManager.unloadScreen("InitialLoading");
        }

        private void rollback(Throwable failure) {
            if (session != null) { cleanUp(failure, session::dispose); }
            if (mapLoads != null) { cleanUp(failure, () -> mapLoads.rollback(failure)); }
            if (sharedLoads != null) { cleanUp(failure, () -> sharedLoads.rollback(failure)); }
            if (failure instanceof Error) {
                cleanUp(failure, () -> setInputProcessors(inputManager));
                cleanUp(failure, () -> screenManager.unloadScreen("InitialLoading"));
            } else {
                cleanUp(failure, () -> setInputProcessors(screen.input(), inputManager));
            }
        }

        private void cleanUp(Throwable failure, Runnable cleanup) {
            try { cleanup.run(); }
            catch (RuntimeException | Error suppressed) {
                if (suppressed != failure) { failure.addSuppressed(suppressed); }
            }
        }
    }


    @Override
    public void resize(int width, int height) {
        viewport.update(width, height);
        screenManager.resize(width, height);
    }

    @Override
    public void pause() {
        screenManager.pause();
    }

    @Override
    public void resume() {
        screenManager.resume();
    }

    @Override
    public void render() {
        glProfiler.reset();
        draw();

        Gdx.graphics.setTitle("Lute - Draw Calls: " + glProfiler.getDrawCalls());
        fpsLogger.log();
    }

    private void draw() {
        screenManager.render(Gdx.graphics.getDeltaTime());
    }


    @Override
    public void dispose() {
        screenManager.dispose();
        batch.dispose();
        inputManager.dispose();
        this.assetService.debugDiagnostics();
        this.assetService.dispose();
        glProfiler.disable();
    }


    private void finishRun(HoardRun run) {
        menu.finish(run);
        screenManager.unloadScreen("GameScreen");
        screenManager.switchScreens("Menu");
    }

    public boolean windowAction(InputAction action) {
        if (action == InputAction.CLOSE_WINDOW) { Gdx.app.exit(); return true; }
        if (action != InputAction.RESIZE_WINDOW) { return false; }
        if (Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(windowWidth > 0 ? windowWidth : 960, windowHeight > 0 ? windowHeight : 540);
        } else {
            windowWidth = Gdx.graphics.getWidth();
            windowHeight = Gdx.graphics.getHeight();
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
        return true;
    }

    public Batch getBatch() {
        return batch;
    }

    public AssetService getAssetService() {
        return assetService;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public Viewport getViewport() {
        return viewport;
    }

    public OrthographicCamera getCamera() {
        return camera;
    }

    public void setInputProcessors(InputProcessor... inputProcessors) {
        inputMultiplexer.clear();
        if (inputProcessors == null) { return; }

        Arrays.stream(inputProcessors).forEach(inputMultiplexer::addProcessor);
    }


    public GameData getGameData() {
        return gameData;
    }

    public Engine getEngine() {
        return engine;
    }
}


