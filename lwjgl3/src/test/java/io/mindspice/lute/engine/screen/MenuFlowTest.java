package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.controllers.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.utils.BufferUtils;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Pickup;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.GameData;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputSettings;
import io.mindspice.lute.engine.input.GamepadControl;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.HoardSystem;
import io.mindspice.lute.engine.systems.PhysicDebugRenderSystem;
import io.mindspice.lute.engine.systems.RenderSystem;
import io.mindspice.lute.engine.systems.TriggerDebugRenderSystem;
import io.mindspice.lute.gameplay.HoardRun;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Supplier;

import static com.badlogic.gdx.Input.Keys.*;

/** Full desktop session with a private preference directory and a polled mapped controller. */
public final class MenuFlowTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Path temp;
    private int held = -1;
    private float y;

    public static void main(String[] args) throws Exception {
        temp = Files.createTempDirectory("lute-menu-test-");
        Controllers.preferredManager = ControllerManagerStub.class.getName();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setPreferencesConfig(temp.toString(), com.badlogic.gdx.Files.FileType.Absolute);
        config.disableAudio(true);
        config.setWindowedMode(960, 540);
        config.setInitialVisible(false);
        new Lwjgl3Application(new MenuFlowTest(), config);
        System.out.println("MenuFlowTest: bindings, persistence, controller and keyboard name entry, death, scores, retry and return passed");
    }

    @Override public void create() {
        profile();
        visualLaunchProfile();
        addController();
        var prefs = Gdx.app.getPreferences("lute-controls-v1");
        prefs.putString("key." + F6, "GUI_RIGHT_CLICK").putString("key." + F7, "GUI_ENTER")
            .putString("key." + D, "GUI_RIGHT_CLICK").putString("key." + BACKSPACE, "GUI_RIGHT_CLICK")
            .putString("key." + F8, "SCROLL_DOWN").putString("key." + F9, "GUI_LEFT")
            .putString("pad.FACE_WEST", "GUI_TAB").putString("pad.FACE_NORTH", "GUI_LEFT_CLICK")
            .putString("PHYSICS", "false").putString("TRIGGERS", "false").flush();
        var previousProfile = System.getProperty("luteDebugLayers");
        System.clearProperty("luteDebugLayers"); // Let Options control the gameplay debug passes.
        try { GAME.create(); }
        finally {
            if (previousProfile != null) { System.setProperty("luteDebugLayers", previousProfile); }
        }
        try {
            GAME.resize(960, 540);
            GAME.render();
            screenshot("main-menu");
            require(GAME.getEngine().getSystems().size() == 0, "startup is not a game session");
            click(480, 230); // disabled Story, followed by click on Options
            tap(F7); tap(F6); // Hoard -> name -> back using rebound GUI actions
            click(480, 158);
            tap(F7); // Toggle physics
            boolean physics = true;
            tap(F8); button(5); // scroll down; mapped GUI left click toggles triggers
            button(4); tap(F7); // mapped GUI tab; toggle raw light map
            tap(F8); tap(F7); // toggle lighting outlines
            require(GameConsts.DEBUG_LAYERS.assetValidation() && !prefs.contains("ASSET_VALIDATION"),
                "asset validation starts enabled without a preference");
            tap(F8); tap(F7); // disable asset validation
            tap(F8); tap(F9); tap(F8); tap(F7); // left then scroll down -> Key Bindings
            require(prefs.getString("PHYSICS").equals("true") && prefs.getString("TRIGGERS").equals("true")
                && prefs.contains("RAW_LIGHT_MAP") && prefs.contains("LIGHTING_OUTLINES"),
                "all four independently persisted via logical GUI actions");
            require(prefs.getString("ASSET_VALIDATION").equals("false"), "Options toggle persists disabled validation");
            for (int i = 0; i < InputAction.SPRINT.ordinal(); i++) { tap(DOWN); }
            tap(ENTER);
            tap(DOWN); tap(ENTER); // Add Keyboard
            click(480, 358); // explicit Cancel, without capturing a key
            tap(DOWN); tap(ENTER); tap(Q);
            require(prefs.getString("key." + Q).equals("SPRINT"), "keyboard capture saves");
            tap(ENTER); tap(ENTER); tap(E); // replace Q with E
            require(prefs.getString("key." + Q).isEmpty() && prefs.getString("key." + E).equals("SPRINT"), "rebind replaces one slot");
            // On the same action capture an already-authored digital stick direction.
            tap(DOWN); tap(DOWN); tap(DOWN); tap(ENTER);
            GAME.render();
            y = -.7f; GAME.render(); GAME.render();
            y = 0; GAME.render();
            tap(DOWN); tap(ENTER); // confirm transfer
            require(prefs.getString("pad.LEFT_STICK_UP").equals("SPRINT"), "digital stick captured");
            tap(DOWN); tap(DOWN); tap(DOWN); tap(ENTER); tap(Z); // add second keyboard slot
            require(prefs.getString("key." + Z).equals("SPRINT"), "second keyboard binding persisted");
            tap(DOWN); tap(ENTER); tap(DOWN); tap(ENTER); // remove just Z
            require(prefs.getString("key." + Z).isEmpty() && prefs.getString("key." + E).equals("SPRINT"), "remove only selected binding");
            tap(ESCAPE); tap(ESCAPE); tap(ESCAPE);
            button(0); // Hoard
            button(0); // blank name rejected
            require(GAME.getEngine().getSystems().size() == 0, "blank name does not start a run");
            button(3); button(0); // controller enters A
            type('x'); tap(BACKSPACE); type('d'); type('a'); type('!'); // name editing wins over shortcuts
            var first = startAndCheckDebugViewport();
            require((GAME.getEngine().getSystem(PhysicDebugRenderSystem.class) != null) == physics, "options reach game renderer");
            var processor = Gdx.input.getInputProcessor();
            processor.keyDown(E);
            long sprint = GAME.getInputManager().arenaSprintPress();
            require(sprint != 0, "rebound sprint works during play");
            held = 1; GAME.render();
            processor.keyUp(E);
            require(GAME.getInputManager().arenaSprintPress() == sprint, "overlapping keyboard and pad sprint is one hold");
            held = -1; GAME.render();
            require(GAME.getInputManager().arenaSprintPress() == 0, "last sprint release clears hold");
            first.enemyKilled(10); first.advance(12);
            die();
            require(first.isGameOver() && first.kills() == 1 && first.aliveSeconds() == 12, "frozen result snapshot");
            var scorePrefs = Gdx.app.getPreferences("lute-hoard-scores-v1");
            String saved = scorePrefs.getString("topFive.v1");
            require(saved.equals("Ada\t11\t1\t1\t12"), "one named score with all counters: " + saved);
            GAME.render(); GAME.pause(); GAME.render(); GAME.resume(); GAME.render();
            screenshot("results");
            require(scorePrefs.getString("topFive.v1").equals(saved), "results frames and resume never resave");
            tap(ENTER);
            var second = waitForRun();
            require(second != first && second.score() == 0 && second.kills() == 0 && second.round() == 1
                && second.aliveSeconds() == 0, "Play Again creates a fresh run");
            die();
            button(3); button(0); // Return to Main Menu
            require(GAME.getEngine().getSystems().size() == 0, "return uninstalls gameplay");
            require(scorePrefs.getString("topFive.v1").equals(saved + "\nAda\t0\t0\t1\t0"), "second run recorded once below first");
            var disk = new Lwjgl3Preferences(new FileHandle(temp.resolve("lute-controls-v1").toFile()));
            var restart = new MenuSettings(disk, InputSettings.load(Gdx.files.internal("input.setting.json")), GameConsts.DEBUG_LAYERS);
            require(restart.layers().physics() == physics && !restart.layers().assetValidation()
                && restart.map.getKey(E) == InputAction.SPRINT
                && restart.map.getGamepad(GamepadControl.LEFT_STICK_UP) == InputAction.SPRINT, "restart restores overrides");
            remappedBackControls(prefs, physics);
        } finally { GAME.dispose(); Gdx.app.exit(); }
    }

    private HoardRun startAndCheckDebugViewport() {
        var viewport = GAME.getViewport();
        float originalWidth = viewport.getWorldWidth();
        // Force a 4:3 gameplay viewport independently of installed assets; the menu is 16:9.
        viewport.setWorldWidth(viewport.getWorldHeight() * 4f / 3f);
        List<Class<?>> passes = List.of(RenderSystem.class, PhysicDebugRenderSystem.class, TriggerDebugRenderSystem.class);
        try {
            viewport.update(960, 540, true);
            int[] initial = glViewport();
            GAME.render(); // Leave the name-entry menu's viewport installed for the gameplay handoff.
            require(initial[0] > 0 && initial[1] == 0, "wide window pillarboxes gameplay");
            HoardRun run = checkDrawViewports("menu handoff", initial, passes, this::startWithoutPreflight);

            require(Gdx.graphics.setWindowedMode(640, 640), "resize succeeds");
            require(Gdx.graphics.getWidth() == 640 && Gdx.graphics.getHeight() == 640, "square window is active");
            GAME.resize(640, 640);
            int[] resized = glViewport();
            require(resized[0] == 0 && resized[1] > 0, "square window letterboxes gameplay");
            HdpiUtils.glViewport(0, 0, 640, 640); // Simulate another pass leaving a different viewport.
            checkDrawViewports("after resize", resized, passes, () -> { GAME.render(); return run; });
            checkDrawViewports("standalone renderer", resized, List.of(RenderSystem.class), () -> {
                GAME.getEngine().getSystem(RenderSystem.class).update(0f);
                return run;
            });
            return run;
        } finally {
            viewport.setWorldWidth(originalWidth);
            Gdx.graphics.setWindowedMode(960, 540);
            GAME.resize(960, 540);
        }
    }

    private static HoardRun checkDrawViewports(String stage, int[] expected, List<Class<?>> passes,
                                               Supplier<HoardRun> frame) {
        var caller = glViewport();
        require(!Arrays.equals(caller, expected), stage + " must start with a different GL viewport");
        var draws = new LinkedHashMap<Class<?>, int[]>();
        var gl = Gdx.gl20;
        // The compositor also draws into private targets with their own viewports.
        int framebuffer = GL11.glGetInteger(GL20.GL_FRAMEBUFFER_BINDING);
        var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        var probe = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class},
            (proxy, method, args) -> {
                try {
                    var result = method.invoke(gl, args);
                    if ((method.getName().equals("glDrawArrays") || method.getName().equals("glDrawElements"))
                        && GL11.glGetInteger(GL20.GL_FRAMEBUFFER_BINDING) == framebuffer) {
                        walker.walk(stack -> stack.map(StackWalker.StackFrame::getDeclaringClass)
                            .filter(passes::contains).findFirst()).ifPresent(pass -> {
                                int[] actual = glViewport();
                                // Keep any mismatch so later draws of the same pass cannot hide it.
                                if (!Arrays.equals(actual, expected)) { draws.put(pass, actual); }
                                else { draws.putIfAbsent(pass, actual); }
                            });
                    }
                    return result;
                } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        HoardRun run;
        Gdx.gl = Gdx.gl20 = probe;
        try { run = frame.get(); }
        finally { Gdx.gl = Gdx.gl20 = gl; }
        require(List.copyOf(draws.keySet()).equals(passes), stage + " draw order: " + draws.keySet());
        draws.forEach((pass, actual) -> require(Arrays.equals(actual, expected), stage + " " + pass.getSimpleName()
            + " viewport: " + Arrays.toString(actual) + ", expected " + Arrays.toString(expected)));
        require(Arrays.equals(glViewport(), caller), stage + " restores the caller viewport");
        System.out.println("MenuFlowTest: " + stage + " drew in " + Arrays.toString(expected)
            + " and restored " + Arrays.toString(caller));
        return run;
    }

    private static int[] glViewport() {
        var values = BufferUtils.newIntBuffer(4);
        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, values);
        return new int[]{values.get(0), values.get(1), values.get(2), values.get(3)};
    }

    private HoardRun startWithoutPreflight() {
        var original = DataLoader.loadGameData();
        var objects = new HashMap<>(original.objectsData());
        objects.remove("health_pack_2");
        var incomplete = new GameData(original.playerData(), original.weaponsData(), original.projectilesData(),
            original.actorsData(), objects);
        try {
            var field = GameCore.class.getDeclaredField("gameData");
            field.setAccessible(true);
            field.set(GAME, incomplete);
            try {
                tap(UP); tap(ENTER);
                var run = waitForRun();
                require(run.round() == 1 && GAME.getEngine().getEntitiesFor(Family.all(Pickup.class).get()).size() == 0,
                    "validation disabled: missing delayed pickup does not block entry or change round-one spawning");
                return run;
            } finally { field.set(GAME, original); }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private void remappedBackControls(Preferences prefs, boolean physics) {
        tap(UP); tap(UP); tap(ENTER); // Key Bindings from Main, past Test Area
        for (int i = 0; i < InputAction.GUI_ENTER.ordinal(); i++) { tap(DOWN); }
        tap(ENTER);
        tap(DOWN); tap(ENTER); tap(ESCAPE); // + Keyboard after F7
        require(prefs.getString("key." + ESCAPE).equals("GUI_ENTER"), "Escape is capturable");
        tap(DOWN); tap(DOWN); tap(DOWN); tap(ENTER); // + Controller after two keys and + Keyboard
        GAME.render(); // neutral capture baseline
        button(2); // controller Back
        require(prefs.getString("pad.BACK").equals("GUI_ENTER"), "controller Back is capturable");

        tap(F6); tap(F6); // logical back to Main
        tap(DOWN); button(2); // Back must open Options, not navigate back
        tap(ESCAPE); // Escape must toggle physics, not navigate back
        require(prefs.getString("PHYSICS").equals(Boolean.toString(!physics)), "Back and Escape activate selected rows");
        var p = Gdx.input.getInputProcessor();
        p.touchDown(0, 0, 0, Input.Buttons.RIGHT);
        p.touchUp(0, 0, 0, Input.Buttons.RIGHT);
        tap(DOWN); tap(ESCAPE); button(2);
        require(prefs.getString("PHYSICS").equals(Boolean.toString(physics)), "right-click still goes back after remapping");
    }

    private static void visualLaunchProfile() {
        var file = new FileHandle(temp.resolve("test-visual-options").toFile());
        var saved = new Lwjgl3Preferences(file);
        saved.putString("PHYSICS", "true").putString("TRIGGERS", "false")
            .putString("RAW_LIGHT_MAP", "true").putString("LIGHTING_OUTLINES", "true")
            .putString("ASSET_VALIDATION", "false").flush();
        var previous = System.getProperty("luteDebugLayers");
        try {
            for (String profile : List.of("off", "on")) {
                System.setProperty("luteDebugLayers", profile);
                boolean on = profile.equals("on");
                require(restartLayers(file).equals(new GameConsts.DebugLayers(true, false, true, true, false)),
                    "saved options take precedence over the " + profile + " profile");
                var empty = new FileHandle(temp.resolve("unused-visual-options").toFile());
                require(restartLayers(empty).equals(new GameConsts.DebugLayers(on, on, false, false,
                    GameConsts.DEBUG_LAYERS.assetValidation())), profile + " supplies defaults for untouched options");
            }
            System.clearProperty("luteDebugLayers");
            require(restartLayers(file).equals(new GameConsts.DebugLayers(true, false, true, true, false)),
                "restart without a profile restores saved options");
            require(new Lwjgl3Preferences(file).get().equals(saved.get()), "launch profiles leave saved preferences untouched");
        } finally {
            if (previous == null) { System.clearProperty("luteDebugLayers"); }
            else { System.setProperty("luteDebugLayers", previous); }
        }
    }

    private static GameConsts.DebugLayers restartLayers(FileHandle file) {
        GameConsts.loadSettings();
        var map = InputSettings.load(Gdx.files.internal("input.setting.json"));
        return new MenuSettings(new Lwjgl3Preferences(file), map, GameConsts.DEBUG_LAYERS).layers();
    }

    private static void profile() {
        var file = new FileHandle(temp.resolve("test-scores").toFile());
        for (int i = 0; i < 8; i++) {
            var prefs = new Lwjgl3Preferences(file);
            var entry = new HoardScores.Result("P" + i, i / 2, 0, 1, 0);
            var board = new HoardScores(prefs).record(entry);
            if (i == 7) {
                require(board.stream().map(HoardScores.Result::name).toList().equals(List.of("P6", "P7", "P4", "P5", "P2")),
                    "scores survive restart and rank stably, bounded to five");
            }
        }
        var bad = new Lwjgl3Preferences(file);
        bad.putString("topFive.v1", "broken");
        try {
            new HoardScores(bad).record(new HoardScores.Result("P", 0, 0, 1, 0));
            throw new AssertionError("corrupt scores accepted");
        } catch (IllegalStateException expected) { require(bad.getString("topFive.v1").equals("broken"), "corrupt board preserved"); }
    }

    private void addController() {
        var mapping = new ControllerMapping(-1, 0,-1,-1, 0,1,4,5, 2,-1,-1,-1,-1,-1,-1,-1,-1,3,-1,-1) { };
        var pad = (Controller) Proxy.newProxyInstance(Controller.class.getClassLoader(), new Class<?>[]{Controller.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getMapping" -> mapping;
                case "isConnected" -> true;
                case "getAxis" -> y;
                case "getButton" -> (int) args[0] == held;
                default -> throw new AssertionError(method.getName());
            });
        Controllers.getControllers().add(pad);
    }
    private void button(int code) { held = code; GAME.render(); held = -1; GAME.render(); }
    private static void tap(int key) {
        Gdx.input.getInputProcessor().keyDown(key);
        Gdx.input.getInputProcessor().keyUp(key);
    }
    private static void type(char c) {
        var processor = Gdx.input.getInputProcessor();
        int key = c == '!' ? NUM_1 : valueOf(String.valueOf(Character.toUpperCase(c)));
        processor.keyDown(key); processor.keyTyped(c); processor.keyUp(key);
    }
    private static void click(int x, int y) {
        var p = Gdx.input.getInputProcessor();
        p.touchDown(x, 540-y, 0, Input.Buttons.LEFT);
        p.touchUp(x, 540-y, 0, Input.Buttons.LEFT);
    }
    private static HoardRun waitForRun() {
        var hoard = GAME.getEngine().getSystem(HoardSystem.class);
        require(hoard != null, "menu started gameplay");
        for (int i = 0; i < 250 && !hoard.isActive(); i++) { GAME.render(); }
        require(hoard.isActive(), "arena finished loading");
        GAME.render();
        return hoard.run();
    }
    private static void die() {
        var player = GAME.getEngine().getEntitiesFor(Family.all(Player.class).get()).first();
        Health.MAPPER.get(player).damage(Integer.MAX_VALUE);
        GAME.render(); GAME.render();
        require(GAME.getEngine().getSystem(HoardSystem.class) == null, "death transitions out of simulation");
    }
    private static void screenshot(String name) {
        var pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        var file = new FileHandle(temp.resolve(name + ".png").toFile());
        try { PixmapIO.writePNG(file, pixmap, -1, true); } finally { pixmap.dispose(); }
        System.out.println(name + " screenshot: " + file);
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
