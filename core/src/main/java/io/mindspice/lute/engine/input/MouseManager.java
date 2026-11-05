package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;

import java.util.Arrays;
import java.util.Objects;


public class MouseManager {
    private MouseListener[] mouseListeners;
    private ActionType listenFilter = null;
    private int size = 0;
    private final boolean[] physicalDown = new boolean[Input.Buttons.FORWARD + 1];
    private final InputAction[] pressedActions = new InputAction[Input.Buttons.FORWARD + 1];
    private int dispatchDepth;
    private boolean applyingFilter;
    private boolean filterPending;
    private ActionType pendingFilter;

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

    public void setListenFilter(ActionType actionType) {
        pendingFilter = actionType;
        filterPending = true;
        applyPendingFilter();
    }

    public void disableListenFilter() {
        setListenFilter(null);
    }

    private void applyPendingFilter() {
        if (dispatchDepth != 0 || applyingFilter) { return; }
        applyingFilter = true;
        try {
            while (filterPending) {
                ActionType target = pendingFilter;
                filterPending = false;
                if (listenFilter == target) { continue; }
                for (int button = 0; button < pressedActions.length; button++) {
                    InputAction action = pressedActions[button];
                    pressedActions[button] = null;
                    if (action != null) { dispatchButton(action, 0); }
                }
                listenFilter = target;
            }
        } finally {
            applyingFilter = false;
        }
    }

    void beginDispatch() {
        dispatchDepth++;
    }

    void endDispatch() {
        dispatchDepth--;
        applyPendingFilter();
    }

    void broadcastPos(double posX, double posY) {
        beginDispatch();
        try {
            for (int i = 0; i < size; i++) {
                var listener = mouseListeners[i];

                if (listenFilter != null && !listener.isListenerFor(listenFilter)) {
                    continue;
                }

                if (listener.isListening()) {
                    listener.offerPos(posX, posY);
                }
            }
        } finally {
            endDispatch();
        }
    }

    void broadcastScroll(int val) {
        beginDispatch();
        try {
            for (int i = 0; i < size; i++) {
                var listener = mouseListeners[i];

                if (listenFilter != null && !listener.isListenerFor(listenFilter)) {
                    continue;
                }

                if (listener.isListening()) {
                    listener.offerScroll(val);
                }
            }
        } finally {
            endDispatch();
        }
    }

    void broadcastButtons(int button, InputAction action, int val) {
        if (button < 0 || button >= physicalDown.length) { return; }
        beginDispatch();
        try {
            if (val == 1) {
                if (physicalDown[button]) { return; }
                physicalDown[button] = true;
                if (action == null || (listenFilter != null && action.actionType != listenFilter)) { return; }
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
        } finally {
            endDispatch();
        }
    }

    private void dispatchButton(InputAction action, int val) {
        for (int i = 0; i < size; i++) {
            var listener = mouseListeners[i];

            if (listenFilter != null && !listener.isListenerFor(listenFilter)) {
                continue;
            }

            if (listener.isListening() && listener.isListenerFor(action.actionType)) {
                listener.offerButton(action, val);
            }
        }
    }
}
