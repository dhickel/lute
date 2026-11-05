package io.mindspice.lute.engine;

import com.badlogic.gdx.*;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.FPSLogger;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputMap;
import io.mindspice.lute.engine.screen.LoadingScreen;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms.
 */
public class GameCore extends Game {
    private Batch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    AssetService assetService;
    FPSLogger fpsLogger;
    GLProfiler glProfiler;
    InputMultiplexer inputMultiplexer;
    private InputManager inputManager;


    private final Map<Class<? extends Screen>, Screen> screenCache = new HashMap<>();

    @Override
    public void create() {
        Gdx.app.setLogLevel(Application.LOG_DEBUG);

        InputMap inputMap = new InputMap();
        inputMap.setKey(Input.Keys.W, InputAction.MOVE_UP);
        inputMap.setKey(Input.Keys.S, InputAction.MOVE_DOWN);
        inputMap.setKey(Input.Keys.A, InputAction.MOVE_LEFT);
        inputMap.setKey(Input.Keys.D, InputAction.MOVE_RIGHT);
        this.inputManager = new InputManager(inputMap);
        this.inputMultiplexer = new InputMultiplexer(inputManager);
        Gdx.input.setInputProcessor(inputMultiplexer);

        this.batch = new SpriteBatch();
        this.camera = new OrthographicCamera();
        this.viewport = new FitViewport(GameConsts.VIEW_WIDTH, GameConsts.VIEW_HEIGHT, camera);
        this.assetService = new AssetService(new InternalFileHandleResolver());

        this.fpsLogger = new FPSLogger();
        this.glProfiler = new GLProfiler(Gdx.graphics);
        glProfiler.enable();


        addScreen(new LoadingScreen(this, assetService));
        setScreen(LoadingScreen.class);

    }


    public void addScreen(Screen screen) {
        screenCache.put(screen.getClass(), screen);
    }

    public void removeScreen(Screen screen) {
        screenCache.remove(screen);
    }


    public void setScreen(Class<? extends Screen> screenClass) {
        var screenInst = screenCache.get(screenClass);
        if (screenInst == null) {
            throw new GdxRuntimeException("No screen with class: " + screenClass + " in cache");
        }
        super.setScreen(screenInst);
    }


    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        super.resize(width, height);
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
        super.render();
    }


    @Override
    public void dispose() {
        batch.dispose();
        screenCache.values().forEach(Screen::dispose);
        screenCache.clear();
        inputManager.dispose();
        this.assetService.debugDiagnostics();
        this.assetService.dispose();
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


}


