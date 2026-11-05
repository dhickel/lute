package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;

import java.util.Arrays;
import java.util.Objects;


public class KeyManager {
    private KeyListener[] keyListeners;
    private final InputMap inputMap;
    private int size = 0;
    private final boolean[] physicalDown = new boolean[Input.Keys.MAX_KEYCODE + 1];
    private final InputAction[] pressedActions = new InputAction[Input.Keys.MAX_KEYCODE + 1];

    public KeyManager(int size, InputMap inputMap) {
        if (size < 1) { throw new IllegalArgumentException("Listener capacity must be positive"); }
        keyListeners = new KeyListener[size];
        this.inputMap = Objects.requireNonNull(inputMap);
    }

    public void registerListener(KeyListener listener) {
        Objects.requireNonNull(listener);
        for (int i = 0; i < size; i++) {
            if (keyListeners[i] == listener) { return; }
        }
        if (size == keyListeners.length) {
            keyListeners = Arrays.copyOf(keyListeners, keyListeners.length * 2);
        }
        keyListeners[size++] = listener;
    }

    public void unregisterListener(KeyListener listener) {
        for (int i = 0; i < size; i++) {
            if (keyListeners[i] == listener) {
                System.arraycopy(keyListeners, i + 1, keyListeners, i, size - i - 1);
                keyListeners[--size] = null;
                return;
            }
        }
    }

    void releaseHeldInputs() {
        for (int keyCode = 0; keyCode < pressedActions.length; keyCode++) {
            InputAction action = pressedActions[keyCode];
            pressedActions[keyCode] = null;
            if (action != null) { dispatch(action, 0); }
        }
    }

    public void broadcast(int keyCode, int keyAction) {
        if (keyCode < 0 || keyCode >= physicalDown.length) { return; }
        InputAction inputAction;
        if (keyAction == 1) {
            if (physicalDown[keyCode]) { return; }
            physicalDown[keyCode] = true;
            inputAction = inputMap.getKey(keyCode);
            pressedActions[keyCode] = inputAction;
        } else if (keyAction == 0) {
            if (!physicalDown[keyCode]) { return; }
            physicalDown[keyCode] = false;
            inputAction = pressedActions[keyCode];
            pressedActions[keyCode] = null;
        } else if (keyAction == 2) {
            inputAction = pressedActions[keyCode];
        } else {
            return;
        }
        if (inputAction != null) { dispatch(inputAction, keyAction); }
    }

    private void dispatch(InputAction inputAction, int keyAction) {
        for (int i = 0; i < size; i++) {
            KeyListener listener = keyListeners[i];
            if (listener.isListening()) {
                listener.offer(inputAction, keyAction);
            }
        }
    }
}
