package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.util.Pair;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ScreenManager implements Disposable {
    private final Map<String, Screen> loadedScreens;
    Screen currScreen = null;


    public ScreenManager(Pair<String, Screen>... initialScreens) {
        this.loadedScreens = new HashMap<>();
        Arrays.stream(initialScreens).forEach(p -> loadedScreens.put(p.first(), p.second()));
    }

    public void loadScreen(String screenKey, Screen screen) {
        loadedScreens.put(screenKey, screen);
    }

    public void unloadScreen(String screenKey) {
        Screen screen = loadedScreens.remove(screenKey);
        if (screen != null) {
            if (screen == currScreen) {
                currScreen.hide();
                currScreen = null;
            }
            screen.dispose();
        }

    }

    public void switchScreens(String targetScreen) {
        Screen screen = loadedScreens.get(targetScreen);
        if (screen == null) {
            throw new GdxRuntimeException(
                "Failed to switch screens. Reason - Failed to find find screen key: " + targetScreen
            );
        }
        if (currScreen != null) { currScreen.hide(); }
        currScreen = screen;
        currScreen.show();
        currScreen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    public Runnable getSwitchScreenThunk(String nextScreenKey) {
        return () -> switchScreens(nextScreenKey);
    }

    public void render(float delta) {
        if (currScreen != null) { currScreen.render(delta); }
    }

    public void resize(int width, int height) {
        if (currScreen != null) { currScreen.resize(width, height); }
    }

    public void pause() {
        if (currScreen != null) { currScreen.pause(); }
    }

    public void resume() {
        if (currScreen != null) { currScreen.resume(); }
    }

    @Override
    public void dispose() {
        if (currScreen != null) { currScreen.hide(); }
        currScreen = null;
        loadedScreens.values().forEach(Screen::dispose);
        loadedScreens.clear();
    }


}
