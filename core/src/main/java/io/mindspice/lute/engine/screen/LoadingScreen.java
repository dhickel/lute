package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public final class LoadingScreen extends ScreenAdapter {
    private static final int WIDTH = 480, HEIGHT = 270, BAR_WIDTH = 300;
    private static final Color ERROR_COLOR = new Color(1f, .28f, .28f, 1f);
    private final Runnable advance;
    private final Runnable returnToMenu;
    private final FitViewport viewport = new FitViewport(WIDTH, HEIGHT);
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Texture pixel = whitePixel();
    private boolean frameDrawn, failureDrawn, disposed;
    private int loaded, total = -1;
    private RuntimeException failure;
    private String errorText;

    private final InputAdapter input = new InputAdapter() {
        private int returnKey = -1;

        @Override public boolean keyDown(int key) {
            if (returnToMenu == null || !failureDrawn || (key != Keys.ESCAPE && key != Keys.ENTER)) { return false; }
            returnKey = key;
            return true;
        }

        @Override public boolean keyUp(int key) {
            if (key != returnKey) { return false; }
            returnKey = -1;
            returnToMenu.run();
            return true;
        }
    };

    public LoadingScreen(Runnable advance, Runnable returnToMenu) {
        this.advance = advance;
        this.returnToMenu = returnToMenu;
    }

    public InputProcessor input() { return input; }
    RuntimeException failure() { return failure; }
    int loaded() { return loaded; }
    int total() { return total; }

    public void progress(int loaded, int total) {
        this.loaded = loaded;
        this.total = total;
    }

    public void fail(RuntimeException failure) {
        this.failure = failure;
        String message = failure.getMessage();
        errorText = message == null || message.isBlank() ? failure.getClass().getSimpleName()
            : message.replaceAll("\\s+", " ").strip();
        if (errorText.length() > 220) { errorText = errorText.substring(0, 217) + "..."; }
    }

    @Override public void render(float delta) {
        // Leave the initial frame for the backend to present before any startup work.
        if (frameDrawn && failure == null) { advance.run(); }
        if (disposed) { return; } // Successful handoff unloaded this screen.

        ScreenUtils.clear(Color.BLACK);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        float x = (WIDTH - BAR_WIDTH) / 2f;
        rect(Color.LIGHT_GRAY, x, 151, BAR_WIDTH, 15);
        rect(Color.DARK_GRAY, x + 1, 152, BAR_WIDTH - 2, 13);
        if (loaded > 0) {
            rect(failure == null ? Color.SKY : ERROR_COLOR, x + 1, 152, (BAR_WIDTH - 2) * loaded / (float) total, 13);
        }
        text(failure == null ? "Loading" : "Startup failed", 196, failure == null ? Color.WHITE : ERROR_COLOR);
        text(loaded + " / " + (total < 0 ? "?" : total), 141, Color.WHITE);
        if (failure != null) {
            text(errorText, 112, ERROR_COLOR);
            text(returnToMenu == null ? "Close the window to exit" : "Esc / Enter: return to menu", 25, Color.LIGHT_GRAY);
        }
        batch.end();
        frameDrawn = true;
        failureDrawn = failure != null;
    }

    private void text(String value, float y, Color color) {
        font.setColor(color);
        font.draw(batch, value, 20, y, WIDTH - 40, Align.center, true);
    }

    private void rect(Color color, float x, float y, float width, float height) {
        batch.setColor(color);
        batch.draw(pixel, x, y, width, height);
        batch.setColor(Color.WHITE);
    }

    private static Texture whitePixel() {
        var pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            return new Texture(pixmap);
        } finally { pixmap.dispose(); }
    }

    @Override public void resize(int width, int height) { viewport.update(width, height, true); }

    @Override public void dispose() {
        if (disposed) { return; }
        disposed = true;
        pixel.dispose();
        font.dispose();
        batch.dispose();
    }
}
