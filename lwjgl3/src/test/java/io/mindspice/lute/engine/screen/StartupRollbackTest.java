package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.ObjectAssets;
import io.mindspice.lute.engine.assets.WeaponAssets;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.WeaponsData;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class StartupRollbackTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;
    private static int checks;
    private final RuntimeException fault = new IllegalStateException("injected startup failure");
    private final Error fatal = new AssertionError("injected fatal startup failure");
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
            exerciseSharedMapSound(false, false);
            exerciseSharedMapSound(true, true);
            exerciseSharedMapSound(true, false);
            exerciseMenu();
            exerciseDirectMap();
            exerciseMapCleanupFailure();
            exercisePendingMap(true);
            exercisePendingMap(false);
            exerciseFatalError();
        } catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void setup(boolean direct) throws ReflectiveOperationException {
        failingPath = null;
        mapAtlas = null;
        attemptedTexture = borrowedTexture = null;
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

    private void exerciseSharedMapSound(boolean conflict, boolean preload) throws ReflectiveOperationException {
        setup(true);
        var files = Gdx.files;
        try {
            var shared = sharedAssets(DataLoader.loadGameData().weaponsData());
            var sound = shared.stream().filter(Asset.Sound.class::isInstance).map(Asset.Sound.class::cast)
                .findFirst().orElseThrow();
            String file = conflict ? "conflicting-startup-sound.wav" : sound.path().substring(GameConsts.SOUND_PATH.length());
            overrideStartupManifest(sound.id(), file);
            var map = ManifestLoader.parseMapManifest(GameConsts.STARTUP_MAP);
            var loader = (LoadingScreen) screens.currScreen;
            verifyInitialFrame();
            if (preload) {
                shared.forEach(service::load);
                map.all().stream().filter(asset -> !asset.equals(map.soundAssets().get(sound.id()))).forEach(service::load);
            }
            var original = preload ? service.get(sound.type(), sound.id()) : null;
            if (conflict) {
                int before = resolutions;
                require(driveToFailure() == loader, "Conflicting shared/map paths fail on the loader");
                require(loader.loaded() == 0 && loader.total() == -1,
                    "Conflicting shared/map paths never receive progress credit (loaded=" + loader.loaded()
                        + ", total=" + loader.total() + ", preloaded=" + preload + ")");
                require(loader.failure() instanceof IllegalArgumentException
                    && loader.failure().getMessage().contains(sound.id())
                    && loader.failure().getMessage().contains(sound.path())
                    && loader.failure().getMessage().contains(GameConsts.SOUND_PATH + file),
                    "The conflict identifies the shared key and both paths");
                require(resolutions == before && !loaded.containsKey("GameScreen"),
                    "Conflicting definitions fail before any load or gameplay mount");
                if (preload) {
                    require(service.get(sound.type(), sound.id()) == original, "The preloaded shared sound is untouched");
                }
                verifyHalted(loader, false);
                return;
            }

            for (int i = 0; i < 200 && (loader.total() < 0 || loader.loaded() < loader.total()); i++) { GAME.render(); }
            int unique = totalAssets(GameConsts.STARTUP_MAP);
            require(unique == shared.size() + map.all().size() - 1, "The fixture has exactly one duplicate key");
            require(loader.failure() == null && loader.loaded() == unique && loader.total() == unique
                && screens.currScreen == loader && service.isDefinitionLoaded(sound),
                "An identical shared/map sound contributes exactly one completed entry");
            verifyFrame(loader, false);
            GAME.render();
            require(screens.currScreen instanceof GameScreen, "Identical definitions allow gameplay to mount");
        } finally {
            Gdx.files = files;
            GAME.dispose();
        }
    }

    private void overrideStartupManifest(String soundId, String file) {
        var files = Gdx.files;
        String map = GameConsts.STARTUP_MAP;
        String path = GameConsts.MAPS_PATH + map + "/" + map + ".manifest.json";
        var manifest = new JsonReader().parse(files.internal(path));
        var sounds = manifest.get("sound_assets");
        sounds.remove(soundId);
        sounds.addChild(soundId, new JsonValue(file));
        byte[] json = manifest.toJson(JsonWriter.OutputType.json).getBytes(StandardCharsets.UTF_8);
        Gdx.files = new Files() {
            @Override public FileHandle internal(String name) {
                if (!name.equals(path)) { return files.internal(name); }
                return new FileHandle(name) {
                    @Override public InputStream read() { return new ByteArrayInputStream(json); }
                };
            }
            @Override public FileHandle getFileHandle(String name, FileType type) {
                return type == FileType.Internal ? internal(name) : files.getFileHandle(name, type);
            }
            @Override public FileHandle classpath(String name) { return files.classpath(name); }
            @Override public FileHandle external(String name) { return files.external(name); }
            @Override public FileHandle absolute(String name) { return files.absolute(name); }
            @Override public FileHandle local(String name) { return files.local(name); }
            @Override public String getExternalStoragePath() { return files.getExternalStoragePath(); }
            @Override public boolean isExternalStorageAvailable() { return files.isExternalStorageAvailable(); }
            @Override public String getLocalStoragePath() { return files.getLocalStoragePath(); }
            @Override public boolean isLocalStorageAvailable() { return files.isLocalStorageAvailable(); }
        };
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
            verifyInitialFrame();
            input.keyDown(Keys.ENTER);
            var assetFailure = driveToFailure();
            input.keyUp(Keys.ENTER);
            require(screens.currScreen == assetFailure, "Releasing Enter held before failure does not dismiss it");
            require(assetFailure.failure().getCause() == fault, "Loader retains the original asset failure cause");
            int expected = (int) sharedAssets().stream().takeWhile(asset -> !asset.equals(CharacterAssets.FEET)).count();
            require(assetFailure.loaded() == expected && assetFailure.total() == totalAssets("hoard_mode"),
                "Shared failure counts completed entries, including the preloaded atlas, but not the failed load");
            verifyRollback("hoard_mode", shared, prequeued);
            require(attemptedTexture != null && attemptedTexture.getTextureObjectHandle() == 0,
                "Rollback disposes a GL atlas acquired before the asset failure");
            require(borrowedTexture != null && borrowedTexture.getTextureObjectHandle() == 0,
                "Rollback disposes the provisional value of a previously queued atlas");
            verifyHalted(assetFailure, true);
            tap(Keys.ESCAPE);
            verifyMenuReturn(menu, assetFailure);
            failingPath = null;

            var listener = failDuringMount(fault);
            tap(Keys.ENTER);
            var mountFailure = driveToFailure();
            GAME.getEngine().removeEntityListener(listener);
            require(mountFailure.failure() == fault && mountFailure.loaded() == mountFailure.total(),
                "A mount failure retains the exact error and completed asset count");
            verifyRollback("hoard_mode", shared, prequeued);
            verifyHalted(mountFailure, true);
            tap(Keys.ENTER);
            verifyMenuReturn(menu, mountFailure);
            require(!service.update() && service.isLoaded(TextureAtlas.class, CharacterAssets.HEAD.id())
                && !service.isLoaded(TextureAtlas.class, CharacterAssets.FEET.id()),
                "Rollback restores the borrowed atlas ahead of unrelated queued work");

            var preloadedMapAtlas = ManifestLoader.parseMapManifest("hoard_mode").atlasAsset();
            var mapResource = service.load(preloadedMapAtlas);
            tap(Keys.ENTER); // Retry using the existing name and restored menu input.
            var successful = (LoadingScreen) screens.currScreen;
            GAME.render();
            GAME.render();
            require(successful.loaded() == 4 && successful.total() == totalAssets("hoard_mode"),
                "First advance counts preloaded shared and map assets plus one new entry");
            for (int i = 0; i < 200 && successful.loaded() < successful.total(); i++) {
                int before = successful.loaded();
                GAME.render();
                require(successful.failure() == null && successful.loaded() > before, "Each work frame makes real progress");
            }
            require(successful.loaded() == successful.total() && screens.currScreen == successful
                && !loaded.containsKey("GameScreen"), "Completion is drawn before mounting gameplay");
            verifyFrame(successful, false);
            GAME.render();
            verifyDisposed(successful);
            require(service.get(TextureAtlas.class, preloadedMapAtlas.id()) == mapResource,
                "Successful startup borrows the preloaded map atlas");
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
            var listener = failDuringMount(fault);
            verifyInitialFrame();
            var loader = driveToFailure();
            require(loader.failure() == fault, "Direct-map latches the original mount exception");
            GAME.getEngine().removeEntityListener(listener);
            verifyRollback(GameConsts.STARTUP_MAP, shared, List.of());
            verifyHalted(loader, false);
            tap(Keys.ESCAPE);
            tap(Keys.ENTER);
            GAME.render();
            require(screens.currScreen == loader && !loaded.containsKey("Menu"),
                "Direct-map ignores menu return keys and stays on the failure screen");
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
            verifyInitialFrame();
            var loader = driveToFailure();
            var error = loader.failure();
            require(error.getCause() == fault, "The original TMX load failure remains primary");
            require(List.of(error.getSuppressed()).equals(List.of(cleanupFault)),
                "The unload failure is suppressed on the TMX load failure");
            require(loader.loaded() == sharedAssets().size() + 1 && loader.total() == totalAssets(GameConsts.STARTUP_MAP),
                "Partial map failure counts the atlas, but not the TMX or any remaining entries");
            verifyHalted(loader, false);
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
            var listener = failLoad ? null : failDuringMount(fault);
            var error = driveToFailure().failure();
            require(failLoad ? error.getCause() == fault : error == fault, "Original startup exception survives");
            require(error.getSuppressed().length == 0, "Rollback does not unload the external map registration");
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
            } catch (GdxRuntimeException unrelatedFailure) {
                require(unrelatedFailure.getCause() == fault, "Unrelated entry is still second");
            }
            require(service.update() && service.isLoaded(TiledMap.class, map.id()), "TMX retries in its original position");
        } finally { GAME.dispose(); }
    }

    private void exerciseFatalError() throws ReflectiveOperationException {
        setup(true);
        try {
            var shared = service.load(CharacterAssets.CHARACTER);
            var loader = (LoadingScreen) screens.currScreen;
            var listener = failDuringMount(fatal);
            try {
                for (int i = 0; i < 200; i++) { GAME.render(); }
                throw new AssertionError("Expected fatal startup error");
            } catch (Error error) {
                require(error == fatal, "JVM Error is rethrown unchanged after cleanup");
            }
            GAME.getEngine().removeEntityListener(listener);
            verifyRollback(GameConsts.STARTUP_MAP, shared, List.of());
            require(screens.currScreen == null && !loaded.containsKey("InitialLoading"), "Fatal startup removes the loader");
            verifyDisposed(loader);
        } finally { GAME.dispose(); }
    }

    private Texture atlasTexture(Asset.Atlas atlas) {
        return service.get(TextureAtlas.class, atlas.id()).getTextures().first();
    }

    private EntityListener failDuringMount(Throwable failure) {
        var listener = new EntityListener() {
            @Override public void entityAdded(Entity entity) {
                require(screens.currScreen instanceof LoadingScreen && !loaded.containsKey("GameScreen"),
                    "A partly mounted gameplay screen is not active or registered");
                for (var asset : sharedAssets()) {
                    require(service.isLoaded(asset.type(), asset.id()), "Mount follows shared asset completion: " + asset);
                }
                if (failure instanceof Error error) { throw error; }
                throw (RuntimeException) failure;
            }
            @Override public void entityRemoved(Entity entity) { }
        };
        GAME.getEngine().addEntityListener(Family.all(Player.class).get(), listener);
        return listener;
    }

    private List<Asset<?>> sharedAssets() {
        return sharedAssets(GAME.getGameData().weaponsData());
    }

    private List<Asset<?>> sharedAssets(WeaponsData weapons) {
        var assets = new ArrayList<>(CharacterAssets.all(weapons.atlas()));
        assets.add(ObjectAssets.OBJECTS);
        for (String id : weapons.weapons().keySet()) { assets.addAll(WeaponAssets.of(id, weapons).all()); }
        return assets;
    }

    private void verifyRollback(String map, TextureAtlas shared, List<Asset.Atlas> prequeued) throws ReflectiveOperationException {
        require(!loaded.containsKey("GameScreen"), "No failed attempt registers gameplay");
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

    private void verifyInitialFrame() throws ReflectiveOperationException {
        require(GAME.getGameData() == null && !service.isRegistered(CharacterAssets.ENEMIES),
            "Starting the loader does not read gameplay data or register assets");
        ScreenUtils.clear(Color.MAGENTA);
        int before = resolutions;
        GAME.render();
        require(resolutions == before && GAME.getGameData() == null && !service.isRegistered(CharacterAssets.ENEMIES),
            "The first loading frame does no startup asset work");
        var loader = (LoadingScreen) screens.currScreen;
        require(loader.loaded() == 0 && loader.total() == -1, "Initial frame does not guess the asset total");
        verifyFrame(loader, false);
    }

    private int totalAssets(String mapId) {
        record Key(Class<?> type, String id) { }
        return (int) Stream.concat(sharedAssets().stream(), ManifestLoader.parseMapManifest(mapId).all().stream())
            .map(asset -> new Key(asset.type(), asset.id())).distinct().count();
    }

    private LoadingScreen driveToFailure() {
        var loader = (LoadingScreen) screens.currScreen;
        for (int i = 0; i < 200 && loader.failure() == null && screens.currScreen == loader; i++) { GAME.render(); }
        require(screens.currScreen == loader && loader.failure() != null, "Runtime failure latches on the loading screen");
        return loader;
    }

    private void verifyHalted(LoadingScreen loader, boolean menu) throws ReflectiveOperationException {
        verifyFrame(loader, true);
        int before = resolutions, count = loader.loaded(), total = loader.total();
        var error = loader.failure();
        tap(Keys.SPACE);
        GAME.resize(240, 240);
        for (int i = 0; i < 4; i++) { GAME.render(); }
        verifyFrame(loader, true);
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        require(screens.currScreen == loader && loaded.get("InitialLoading") == loader
            && loader.failure() == error && loader.loaded() == count && loader.total() == total && resolutions == before,
            "Failed loader redraws after resize without advancing, retrying, or leaving");
        require(loaded.containsKey("Menu") == menu, "Only the menu path retains a menu");
    }

    private void verifyMenuReturn(MenuScreen menu, LoadingScreen loader) throws ReflectiveOperationException {
        require(screens.currScreen == menu && !loaded.containsKey("InitialLoading")
            && !((String) field(MenuScreen.class, "error").get(menu)).isBlank(),
            "Explicit acknowledgment returns to the same menu, without starting a retry");
        verifyDisposed(loader);
    }

    private void verifyDisposed(LoadingScreen loader) throws ReflectiveOperationException {
        var font = (com.badlogic.gdx.graphics.g2d.BitmapFont) field(LoadingScreen.class, "font").get(loader);
        var pixel = (Texture) field(LoadingScreen.class, "pixel").get(loader);
        require(font.getRegion().getTexture().getTextureObjectHandle() == 0 && pixel.getTextureObjectHandle() == 0,
            "Leaving the loading screen releases its independent textures");
    }

    private void verifyFrame(LoadingScreen loader, boolean failed) throws ReflectiveOperationException {
        var viewport = (FitViewport) field(LoadingScreen.class, "viewport").get(loader);
        var pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            int track = pixelAt(pixels, viewport, 240, 158);
            require(pixelAt(pixels, viewport, 15, 240) >>> 8 == 0, "Loader clears to black outside the UI");
            require(track >>> 8 != 0, "Loader draws a graphical progress bar");
            if (loader.loaded() == 0) {
                require(track == Color.rgba8888(Color.DARK_GRAY), "Initial bar is empty");
            } else if (loader.loaded() == loader.total() && !failed) {
                require(pixelAt(pixels, viewport, 386, 158) == Color.rgba8888(Color.SKY), "Completed bar fills to the right edge");
            }
            boolean countInk = false, errorInk = false;
            for (int x = 40; x < 440; x++) {
                for (int y = 80; y <= 141; y++) {
                    int pixel = pixelAt(pixels, viewport, x, y);
                    int r = pixel >>> 24, g = (pixel >>> 16) & 255, b = (pixel >>> 8) & 255;
                    if (y >= 123 && r > 160 && r == g && g == b) { countInk = true; }
                    if (y <= 112 && r > 110 && r > 2 * g) { errorInk = true; }
                }
            }
            require(countInk, "Numeric progress is drawn below the bar");
            require(errorInk == failed, "Error message is visibly red only on failure");
        } finally { pixels.dispose(); }
    }

    private int pixelAt(Pixmap pixels, FitViewport viewport, int x, int y) {
        int px = viewport.getScreenX() + x * viewport.getScreenWidth() / 480;
        int py = viewport.getScreenY() + y * viewport.getScreenHeight() / 270;
        return pixels.getPixel(px * pixels.getWidth() / Gdx.graphics.getWidth(),
            py * pixels.getHeight() / Gdx.graphics.getHeight());
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
