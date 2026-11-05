package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;


public class InputMap {
    private final InputAction[] keyMap = new InputAction[Input.Keys.MAX_KEYCODE + 1];
    private final InputAction[] buttonMap = new InputAction[Input.Buttons.FORWARD + 1];

    public void setKey(int keyCode, InputAction inputAction) {
        if (keyCode < 0 || keyCode >= keyMap.length) {
            throw new IllegalArgumentException("Invalid key code: " + keyCode);
        }
        keyMap[keyCode] = inputAction;
    }

    public InputAction getKey(int keyCode) {
        return keyCode >= 0 && keyCode < keyMap.length ? keyMap[keyCode] : null;
    }

    public void removeKey(int keyCode) {
        if (keyCode >= 0 && keyCode < keyMap.length) {
            keyMap[keyCode] = null;
        }
    }

    public boolean containsKey(int keyCode) {
        return getKey(keyCode) != null;
    }

    public void setButton(int button, InputAction inputAction) {
        if (button < 0 || button >= buttonMap.length) {
            throw new IllegalArgumentException("Invalid button code: " + button);
        }
        buttonMap[button] = inputAction;
    }

    public InputAction getButton(int button) {
        return button >= 0 && button < buttonMap.length ? buttonMap[button] : null;
    }

    public void removeButton(int button) {
        if (button >= 0 && button < buttonMap.length) {
            buttonMap[button] = null;
        }
    }

    public boolean containsButton(int button) {
        return getButton(button) != null;
    }
}
