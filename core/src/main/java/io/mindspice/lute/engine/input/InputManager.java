package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.utils.Disposable;

public class InputManager extends InputAdapter implements Disposable {
    private final KeyManager keyManager;
    private final MouseManager mouseManager;
    private final InputMap inputMap;
    private int dispatchDepth;
    private boolean applyingFilter;
    private boolean filterPending;
    private ActionType pendingFilter;

    public InputManager(InputMap inputMap) {
        keyManager = new KeyManager(20, inputMap);
        mouseManager = new MouseManager(20);
        this.inputMap = inputMap;
    }

    @Override
    public boolean keyDown(int keycode) {
        beginDispatch();
        try {
            if (keycode != Input.Keys.UNKNOWN) {
                keyManager.broadcast(keycode, 1);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        beginDispatch();
        try {
            if (keycode != Input.Keys.UNKNOWN) {
                keyManager.broadcast(keycode, 0);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        beginDispatch();
        try {
            if (pointer == 0) {
                mouseManager.broadcastPos(screenX, screenY);
                mouseManager.broadcastButtons(button, inputMap.getButton(button), 1);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        beginDispatch();
        try {
            if (pointer == 0) {
                mouseManager.broadcastPos(screenX, screenY);
                mouseManager.broadcastButtons(button, inputMap.getButton(button), 0);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return touchUp(screenX, screenY, pointer, button);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        beginDispatch();
        try {
            mouseManager.broadcastPos(screenX, screenY);
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        beginDispatch();
        try {
            if (pointer == 0) {
                mouseManager.broadcastPos(screenX, screenY);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        beginDispatch();
        try {
            if (amountY < 0) {
                mouseManager.broadcastScroll(1);
            } else if (amountY > 0) {
                mouseManager.broadcastScroll(0);
            }
        } finally {
            endDispatch();
        }
        return false;
    }

    public void bindToWindow() {
        Gdx.input.setInputProcessor(this);
    }

    @Override
    public void dispose() {
        if (Gdx.input == null) { return; }
        var processor = Gdx.input.getInputProcessor();
        if (processor == this) {
            Gdx.input.setInputProcessor(null);
        } else if (processor instanceof InputMultiplexer multiplexer) {
            multiplexer.removeProcessor(this);
        }
    }

    public void cleanup() {
        dispose();
    }

    public void enableRawInput(boolean enabled) {
        if (enabled) {
            throw new UnsupportedOperationException("LibGDX core does not expose raw mouse motion");
        }
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public MouseManager getMouseManager() {
        return mouseManager;
    }

    private void beginDispatch() {
        dispatchDepth++;
        mouseManager.beginDispatch();
    }

    private void endDispatch() {
        try {
            mouseManager.endDispatch();
        } finally {
            dispatchDepth--;
            applyPendingFilter();
        }
    }

    public void setFilter(ActionType actionType) {
        pendingFilter = actionType;
        filterPending = true;
        applyPendingFilter();
    }

    private void applyPendingFilter() {
        if (dispatchDepth != 0 || applyingFilter) { return; }
        applyingFilter = true;
        try {
            while (filterPending) {
                ActionType target = pendingFilter;
                filterPending = false;
                mouseManager.setListenFilter(target);
                keyManager.setListenFilter(target);
            }
        } finally {
            applyingFilter = false;
        }
    }

    public void disableFilter() {
        setFilter(null);
    }

    public void regKeyListener(KeyListener listener) {
        keyManager.registerListener(listener);
    }

    public void unRegKeyListener(KeyListener listener) {
        keyManager.unregisterListener(listener);
    }

    public void regMousePosListener(MouseListener listener) {
        mouseManager.registerListener(listener);
    }

    public void unRegMousePosListener(MouseListener listener) {
        mouseManager.unregisterListener(listener);
    }
}
