package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.ScreenAdapter;

public final class LoadingScreen extends ScreenAdapter {
    private final Runnable advance;

    public LoadingScreen(Runnable advance) {
        this.advance = advance;
    }

    @Override
    public void render(float delta) {
        advance.run();
    }
}
