package io.mindspice.lute.engine.screen;

import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.controllers.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
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
import io.mindspice.lute.gameplay.HoardRun;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

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
            .putString("pad.FACE_WEST", "GUI_TAB").putString("pad.FACE_NORTH", "GUI_LEFT_CLICK").flush();
        GAME.create();
        try {
            GAME.resize(960, 540);
            GAME.render();
            screenshot("main-menu");
            require(GAME.getEngine().getSystems().size() == 0, "startup is not a game session");
            click(480, 230); // disabled Story, followed by click on Options
            tap(F7); tap(F6); // Hoard -> name -> back using rebound GUI actions
            click(480, 158);
            tap(F7); // Toggle physics
            boolean physics = !GameConsts.DEBUG_LAYERS.physics();
            tap(F8); button(5); // scroll down; mapped GUI left click toggles triggers
            button(4); tap(F7); // mapped GUI tab; toggle raw light map
            tap(F8); tap(F7); // toggle lighting outlines
            require(GameConsts.DEBUG_LAYERS.assetValidation() && !prefs.contains("ASSET_VALIDATION"),
                "asset validation starts enabled without a preference");
            tap(F8); tap(F7); // disable asset validation
            tap(F8); tap(F9); tap(F8); tap(F7); // left then scroll down -> Key Bindings
            require(prefs.contains("PHYSICS") && prefs.contains("TRIGGERS") && prefs.contains("RAW_LIGHT_MAP")
                && prefs.contains("LIGHTING_OUTLINES"), "all four independently persisted via logical GUI actions");
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
            var first = startWithoutPreflight();
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
        tap(UP); tap(UP); tap(ENTER); // Key Bindings from Main, past Hammer Demo
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
                require(restartLayers(file).equals(new GameConsts.DebugLayers(on, on, false, false, false)),
                    profile + " overrides saved visual options only");
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
