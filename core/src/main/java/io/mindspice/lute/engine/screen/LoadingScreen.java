package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.ScreenUtils;

public final class LoadingScreen extends ScreenAdapter {
    private final Runnable advance;
    private boolean frameDrawn;

    public LoadingScreen(Runnable advance) {
        this.advance = advance;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(Color.BLACK);
        if (!frameDrawn) {
            // Let the backend present the black frame before starting synchronous asset work.
            frameDrawn = true;
            return;
        }
        advance.run();
    }
}
