package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;

import java.util.Arrays;
import java.util.Objects;


public class MouseManager {
    private MouseListener[] mouseListeners;
    private int size = 0;
    private final boolean[] physicalDown = new boolean[Input.Buttons.FORWARD + 1];
    private final InputAction[] pressedActions = new InputAction[Input.Buttons.FORWARD + 1];

    public MouseManager(int size) {
        if (size < 1) { throw new IllegalArgumentException("Listener capacity must be positive"); }
        mouseListeners = new MouseListener[size];
    }

    public void registerListener(MouseListener listener) {
        Objects.requireNonNull(listener);
        for (int i = 0; i < size; i++) {
            if (mouseListeners[i] == listener) { return; }
        }
        if (size == mouseListeners.length) {
            mouseListeners = Arrays.copyOf(mouseListeners, mouseListeners.length * 2);
        }
        mouseListeners[size++] = listener;
    }

    public void unregisterListener(MouseListener listener) {
        for (int i = 0; i < size; i++) {
            if (mouseListeners[i] == listener) {
                System.arraycopy(mouseListeners, i + 1, mouseListeners, i, size - i - 1);
                mouseListeners[--size] = null;
                return;
            }
        }
    }

    void releaseHeldInputs() {
        for (int button = 0; button < pressedActions.length; button++) {
            InputAction action = pressedActions[button];
            pressedActions[button] = null;
            if (action != null) { dispatchButton(action, 0); }
        }
    }

    void broadcastPos(double posX, double posY) {
        for (int i = 0; i < size; i++) {
            var listener = mouseListeners[i];
            if (listener.isListening()) {
                listener.offerPos(posX, posY);
            }
        }
    }

    void broadcastScroll(int val) {
        for (int i = 0; i < size; i++) {
            var listener = mouseListeners[i];
            if (listener.isListening()) {
                listener.offerScroll(val);
            }
        }
    }

    void broadcastButtons(int button, InputAction action, int val) {
        if (button < 0 || button >= physicalDown.length) { return; }
        if (val == 1) {
            if (physicalDown[button]) { return; }
            physicalDown[button] = true;
            pressedActions[button] = action;
        } else if (val == 0) {
            if (!physicalDown[button]) { return; }
            physicalDown[button] = false;
            action = pressedActions[button];
            pressedActions[button] = null;
        } else {
            return;
        }
        if (action != null) { dispatchButton(action, val); }
    }

    private void dispatchButton(InputAction action, int val) {
        for (int i = 0; i < size; i++) {
            var listener = mouseListeners[i];
            if (listener.isListening()) {
                listener.offerButton(action, val);
            }
        }
    }
}
