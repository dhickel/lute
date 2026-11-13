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
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.GameData;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.tiled.ManifestLoader;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputMap;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.LoadingScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.util.Pair;

import java.util.Arrays;

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
    private Engine engine;
    private GameData.Player playerData;
    private GameData.Weapon playerWeapon;



    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);
        playerData = GameData.readPlayer(Gdx.files.internal("data/player.json"));
        playerWeapon = GameData.readWeapon(Gdx.files.internal("data/weapons/" + playerData.loadout().weapon()));

        InputMap inputMap = InputMap.of(
            Pair.of(Input.Keys.W, InputAction.MOVE_UP),
            Pair.of(Input.Keys.S, InputAction.MOVE_DOWN),
            Pair.of(Input.Keys.A, InputAction.MOVE_LEFT),
            Pair.of(Input.Keys.D, InputAction.MOVE_RIGHT),
            Pair.of(Input.Keys.SPACE, InputAction.ATTACK)
        );

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


        Runnable switchToGameScreen = () -> {
            screenManager.getSwitchScreenThunk("GameScreen").run();
            screenManager.unloadScreen("InitialLoading");
        };

        MapAssets assets = ManifestLoader.parseMapManifest("test_tiled");

        this.screenManager = new ScreenManager(
          Pair.of("InitialLoading", new LoadingScreen(assetService, switchToGameScreen, assets.all())),
          Pair.of("GameScreen", new GameScreen(assets))
        );

        screenManager.switchScreens("InitialLoading");
    }



    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
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
        input();
        logic();
        draw();

        Gdx.graphics.setTitle("Lute - Draw Calls: " + glProfiler.getDrawCalls());
        fpsLogger.log();
    }

    private void input() {

    }

    private void logic() {


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
    }


    public GameData.Player getPlayerData() { return playerData; }

    public GameData.Weapon getPlayerWeapon() { return playerWeapon; }

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


    public Engine getEngine() {
        return engine;
    }
}


