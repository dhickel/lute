package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.input.*;
import io.mindspice.lute.engine.screen.MenuSettings.Binding;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.gameplay.HoardRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One focused row for all three input devices; capture never dispatches the captured action. */
public final class MenuScreen extends InputAdapter implements Screen {
    private enum Page { MAIN, OPTIONS, ACTIONS, BINDINGS, SLOT, CAPTURE, CONFLICT, NAME, RESULTS }
    private record Row(String text, Runnable activate) { }
    private static final int WIDTH = 960, HEIGHT = 540, ROW = 33, VISIBLE = 9;
    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_ ";
    private final GameCore game;
    private final InputManager input;
    private final Runnable start;
    private final Runnable startDemo;
    private final MenuSettings settings;
    private final HoardScores scores;
    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final Vector2 pointer = new Vector2();
    private final List<Row> rows = new ArrayList<>();
    private final Texture background, pixel;
    private final BitmapFont font;
    private Page page = Page.MAIN, bindingsParent = Page.MAIN;
    private String title, hint, error = "", name = "";
    private InputAction action;
    private Binding previous, candidate;
    private boolean capturingPad, active, paused, typing;
    private int selected, first, letter;
    private HoardScores.Result result;
    private List<HoardScores.Result> board = List.of();

    public MenuScreen(GameCore game, InputMap map, Runnable start, Runnable startDemo) {
        this.game = game;
        this.input = game.getInputManager();
        this.start = start;
        this.startDemo = startDemo;
        settings = new MenuSettings(Gdx.app.getPreferences("lute-controls-v1"), map, GameConsts.DEBUG_LAYERS);
        scores = new HoardScores(Gdx.app.getPreferences("lute-hoard-scores-v1"));
        background = new Texture(Gdx.files.internal("images/menu/mainmenubg.jpg"));
        background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        var solid = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        solid.setColor(Color.WHITE);
        solid.fill();
        pixel = new Texture(solid);
        solid.dispose();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        open(Page.MAIN);
    }

    public GameConsts.DebugLayers layers() { return settings.layers(); }

    public void finish(HoardRun run) {
        result = HoardScores.Result.snapshot(name, run);
        board = List.of();
        open(Page.RESULTS);
        try { board = scores.record(result); }
        catch (RuntimeException e) { report(e); }
    }

    private void report(RuntimeException e) {
        error = e.getMessage();
        Gdx.app.error("Menu", error, e);
    }

    private void open(Page next) {
        page = next;
        error = "";
        selected = first = 0;
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        hint = "Arrows / D-pad to select    |    Enter / A to choose" + (page == Page.MAIN ? "" : "    |    Right-click to go back");
        switch (page) {
            case MAIN -> {
                title = "Choose a mode";
                row("Story   -   unavailable", null);
                row("Hoard", () -> open(Page.NAME));
                row("Options", () -> open(Page.OPTIONS));
                row("Key Bindings", this::openBindings);
                row("Hoard Hammer Demo", startDemo);
            }
            case OPTIONS -> {
                title = "Options";
                for (var layer : MenuSettings.Layer.values()) {
                    String name = layer == MenuSettings.Layer.ASSET_VALIDATION ? "Asset Validation" : label(layer);
                    row(name + "    " + (settings.enabled(layer) ? "ON" : "OFF"), () -> {
                        settings.toggle(layer);
                        rebuild();
                    });
                }
                row("Key Bindings", this::openBindings);
                backRow();
            }
            case ACTIONS -> {
                title = "Key Bindings";
                for (var item : InputAction.values()) {
                    row(label(item), () -> { action = item; open(Page.BINDINGS); });
                }
                backRow();
            }
            case BINDINGS -> {
                title = label(action);
                for (var binding : settings.bindings(action)) {
                    row(binding.label(), () -> { previous = binding; open(Page.SLOT); });
                }
                row("+ Keyboard", () -> capture(null, false));
                row("+ Controller", () -> capture(null, true));
                backRow();
            }
            case SLOT -> {
                title = previous.label();
                row("Rebind", () -> capture(previous, previous.pad() != null));
                row("Remove", () -> change(null));
                backRow();
            }
            case CAPTURE -> {
                title = "Bind: " + label(action);
                row(capturingPad ? "Press a button or tilt the left stick" : "Press a keyboard key", null);
                row("Cancel", this::back);
                hint = "Click Cancel, right-click, or press " + (capturingPad ? "Esc" : "controller Back") + " to cancel";
            }
            case CONFLICT -> {
                title = candidate.label();
                row("Currently: " + label(candidate.get(settings.map)), null);
                row("Cancel", this::back);
                row("Assign to " + label(action), () -> change(candidate));
            }
            case NAME -> {
                title = "Enter your name";
                row("Start Hoard", this::startRun);
                row("Add letter:  < " + LETTERS.charAt(letter) + " >", () -> append(LETTERS.charAt(letter)));
                row("Next letter", () -> cycle(1));
                row("Previous letter", () -> cycle(-1));
                row("Delete last letter", this::erase);
                backRow();
                hint = "Type your name, or choose letters with Left / Right and A";
            }
            case RESULTS -> {
                title = "Run complete";
                row("Play Again", this::startRun);
                row("Return to Main Menu", () -> open(Page.MAIN));
            }
        }
        selected = Math.min(selected, rows.size() - 1);
        if (rows.get(selected).activate == null) { move(1); }
        reveal();
    }

    static String label(Enum<?> item) {
        String text = item.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void row(String text, Runnable activate) { rows.add(new Row(text, activate)); }
    private void backRow() { row("Back", this::back); }
    private void openBindings() { bindingsParent = page; open(Page.ACTIONS); }
    private void back() {
        switch (page) {
            case MAIN -> { }
            case NAME, OPTIONS, RESULTS -> open(Page.MAIN);
            case ACTIONS -> open(bindingsParent);
            case BINDINGS -> { open(Page.ACTIONS); selected = action.ordinal(); reveal(); }
            case SLOT, CAPTURE, CONFLICT -> open(Page.BINDINGS);
        }
    }

    private void capture(Binding old, boolean pad) {
        previous = old;
        capturingPad = pad;
        open(Page.CAPTURE);
        input.releaseHeldInputs();
    }
    private void captured(Binding binding) {
        candidate = binding;
        var existing = binding.get(settings.map);
        if (existing != null && existing != action) { open(Page.CONFLICT); }
        else { change(binding); }
    }
    private void change(Binding next) {
        settings.change(previous, next, action);
        open(Page.BINDINGS);
    }
    private void append(char c) {
        if (name.length() >= HoardScores.NAME_LIMIT) { error = "Names may contain up to 16 characters"; return; }
        name += c;
        error = "";
    }
    private void erase() { if (!name.isEmpty()) { name = name.substring(0, name.length() - 1); } error = ""; }
    private void cycle(int step) { letter = Math.floorMod(letter + step, LETTERS.length()); rebuild(); }
    private void startRun() {
        name = name.strip();
        if (name.isEmpty()) { error = "Enter a name before starting"; return; }
        start.run();
    }

    private void move(int step) {
        do { selected = Math.floorMod(selected + step, rows.size()); } while (rows.get(selected).activate == null);
        reveal();
    }
    private void reveal() { first = Math.max(0, Math.min(selected, Math.max(first, selected - VISIBLE + 1))); }
    private void activate() {
        try { rows.get(selected).activate.run(); }
        catch (RuntimeException e) { report(e); }
    }

    private boolean route(InputAction command) {
        if (command == null) { return false; }
        switch (command) {
            case GUI_UP, SCROLL_UP -> move(-1);
            case GUI_TAB, GUI_DOWN, SCROLL_DOWN -> move(1);
            case GUI_LEFT -> { if (page == Page.NAME) { cycle(-1); } else { move(-1); } }
            case GUI_RIGHT -> { if (page == Page.NAME) { cycle(1); } else { move(1); } }
            case GUI_ENTER, GUI_LEFT_CLICK -> activate();
            case GUI_RIGHT_CLICK -> back();
            default -> { return game.windowAction(command); }
        }
        return true;
    }

    private void pad(GamepadControl pad) {
        if (page == Page.CAPTURE) {
            if (capturingPad) { captured(Binding.pad(pad)); }
            else if (pad == GamepadControl.BACK || pad == GamepadControl.FACE_EAST) { back(); }
            return;
        }
        if (route(settings.map.getGamepad(pad))) { return; }
        switch (pad) {
            case DPAD_UP, LEFT_STICK_UP -> move(-1);
            case DPAD_DOWN, LEFT_STICK_DOWN -> move(1);
            case DPAD_LEFT, LEFT_STICK_LEFT -> route(InputAction.GUI_LEFT);
            case DPAD_RIGHT, LEFT_STICK_RIGHT -> route(InputAction.GUI_RIGHT);
            case START, FACE_SOUTH -> activate();
            case BACK, FACE_EAST -> back();
        }
    }

    @Override public boolean keyDown(int code) {
        boolean held = input.getKeyManager().isKeyDown(code);
        input.keyDown(code); // Keep physical state through screen handoff, even for unbound keys.
        if (!active || paused || held) { return true; }
        typing = page == Page.NAME;
        try {
            if (page == Page.CAPTURE) {
                if (!capturingPad && code > 0 && code <= Input.Keys.MAX_KEYCODE) { captured(Binding.key(code)); }
                else if (capturingPad && code == Input.Keys.ESCAPE) { back(); }
                return true;
            }
            if (page == Page.NAME && code == Input.Keys.BACKSPACE) { erase(); return true; }
            // Printable keys belong to text entry rather than menu shortcuts on this page.
            boolean textKey = page == Page.NAME && ((code >= Input.Keys.A && code <= Input.Keys.Z)
                || (code >= Input.Keys.NUM_0 && code <= Input.Keys.NUM_9)
                || (code >= Input.Keys.NUMPAD_0 && code <= Input.Keys.NUMPAD_9)
                || code == Input.Keys.SPACE || code == Input.Keys.MINUS || code == Input.Keys.SHIFT_LEFT || code == Input.Keys.SHIFT_RIGHT);
            if (!textKey && route(settings.map.getKey(code))) { return true; }
            switch (code) {
                case Input.Keys.ESCAPE -> back();
                case Input.Keys.UP -> move(-1);
                case Input.Keys.TAB, Input.Keys.DOWN -> move(1);
                case Input.Keys.LEFT -> route(InputAction.GUI_LEFT);
                case Input.Keys.RIGHT -> route(InputAction.GUI_RIGHT);
                case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER -> activate();
            }
        } catch (RuntimeException e) { report(e); }
        return true;
    }
    @Override public boolean keyUp(int code) { input.keyUp(code); return true; }
    @Override public boolean keyTyped(char c) {
        if (active && !paused && typing && page == Page.NAME && c >= 32 && c != 127) {
            if (c >= 'a' && c <= 'z' || LETTERS.indexOf(c) >= 0) { append(c); }
            else { error = "Use letters, numbers, spaces, dashes or underscores"; }
        }
        return true;
    }
    private int rowAt(int x, int y) {
        viewport.unproject(pointer.set(x, y));
        if (pointer.x < left() || pointer.x > WIDTH - left() || pointer.y > top()) { return -1; }
        int slot = (int) ((top() - pointer.y) / ROW), index = first + slot;
        return slot < VISIBLE && index < rows.size() && rows.get(index).activate != null ? index : -1;
    }
    @Override public boolean mouseMoved(int x, int y) {
        int index = rowAt(x, y);
        if (active && !paused && index >= 0) { selected = index; }
        return true;
    }
    @Override public boolean touchDown(int x, int y, int pointerId, int button) {
        input.touchDown(x, y, pointerId, button);
        if (!active || paused || pointerId != 0) { return true; }
        if (button == Input.Buttons.RIGHT) { back(); return true; }
        int index = rowAt(x, y);
        if (button == Input.Buttons.LEFT && index >= 0) { selected = index; activate(); }
        return true;
    }
    @Override public boolean touchUp(int x, int y, int pointerId, int button) {
        input.touchUp(x, y, pointerId, button);
        return true;
    }
    @Override public boolean touchCancelled(int x, int y, int pointerId, int button) {
        input.touchCancelled(x, y, pointerId, button);
        return true;
    }
    @Override public boolean scrolled(float x, float y) {
        if (active && !paused && y != 0) { move(y > 0 ? 1 : -1); }
        return true;
    }

    @Override public void show() { active = true; paused = false; input.releaseHeldInputs(); game.setInputProcessors(this); }
    @Override public void hide() { active = false; typing = false; input.releaseHeldInputs(); game.setInputProcessors(input); }
    @Override public void pause() { paused = true; input.releaseHeldInputs(); if (page == Page.CAPTURE) { back(); } }
    @Override public void resume() { paused = false; typing = false; input.releaseHeldInputs(); }
    @Override public void resize(int width, int height) { viewport.update(width, height, true); }
    @Override public void dispose() { font.dispose(); pixel.dispose(); background.dispose(); }

    private int left() { return page == Page.MAIN ? 335 : 156; }
    private int top() { return switch (page) { case MAIN -> 244; case NAME -> 350; case RESULTS -> 149; default -> 408; }; }

    @Override public void render(float delta) {
        if (!active || paused) { return; }
        var pressed = input.pollMenuPad();
        if (pressed != null) {
            try { pad(pressed); } catch (RuntimeException e) { report(e); }
            if (!active) { return; }
        }
        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        Batch batch = game.getBatch();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.setColor(Color.WHITE);
        batch.begin();
        batch.draw(background, 0, 0, WIDTH, HEIGHT);
        int panelTop = page == Page.MAIN ? 303 : 487;
        int panelBottom = page == Page.MAIN ? 91 : 53;
        rect(batch, left()-12, panelBottom, WIDTH-2*left()+24, panelTop-panelBottom, .052f,.058f,.065f,.94f);
        rect(batch, left()-12, panelTop, WIDTH-2*left()+24, 2, .70f,.53f,.30f,1);
        text(batch, title, panelTop - 15, 1.55f, Color.GOLD);
        if (page == Page.NAME) { text(batch, name.isEmpty() ? "Name: _" : name + "_", 401, 1.35f, Color.WHITE); }
        if (page == Page.RESULTS) { drawResults(batch); }
        for (int i = first; i < rows.size() && i < first + VISIBLE; i++) {
            var row = rows.get(i);
            int y = top() - (i - first) * ROW;
            if (i == selected) { rect(batch, left(), y-ROW+2, WIDTH-2*left(), ROW-3, .35f,.27f,.18f,1); }
            text(batch, row.text, y-8, 1.1f, row.activate == null ? Color.LIGHT_GRAY : Color.WHITE);
        }
        if (rows.size() > VISIBLE) { text(batch, (selected + 1) + " / " + rows.size(), 93, .95f, Color.GOLD); }
        if (!error.isEmpty()) { text(batch, error, 83, 1f, Color.SALMON); }
        rect(batch, 0, 0, WIDTH, 35, .03f,.035f,.04f,.91f);
        text(batch, hint, 24, 1f, Color.LIGHT_GRAY);
        batch.setColor(Color.WHITE);
        batch.end();
    }
    private void drawResults(Batch batch) {
        text(batch, result.name() + "    |    Score " + result.score() + "    |    Kills " + result.kills(), 427, 1.25f, Color.WHITE);
        text(batch, "Round " + result.round() + "    |    Survived " + result.seconds()/60 + "m " + result.seconds()%60 + "s", 395, 1.1f, Color.WHITE);
        text(batch, "LOCAL HIGH SCORES", 345, 1.1f, Color.GOLD);
        for (int i = 0; i < board.size(); i++) {
            var entry = board.get(i);
            text(batch, (i+1) + ".   " + entry.name() + "     " + entry.score(), 311-i*28, 1.1f, Color.WHITE);
        }
    }
    private void text(Batch batch, String value, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        font.draw(batch, value, 0, y, WIDTH, Align.center, false);
    }
    private void rect(Batch batch, int x, int y, int w, int h, float r, float g, float b, float a) {
        batch.setColor(r,g,b,a);
        batch.draw(pixel,x,y,w,h);
        batch.setColor(Color.WHITE);
    }
}
