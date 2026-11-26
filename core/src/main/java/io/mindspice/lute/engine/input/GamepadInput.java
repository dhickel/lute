package io.mindspice.lute.engine.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.utils.Array;

/** Samples one mapped pad. Both menus and gameplay use the same digital edges. */
final class GamepadInput {
    private static final GamepadControl[] CONTROLS = GamepadControl.values();
    private final KeyManager actions;
    private final InputMap map;
    private final boolean[] down = new boolean[CONTROLS.length];
    private final boolean[] sample = new boolean[CONTROLS.length];
    private final InputAction[] delivered = new InputAction[CONTROLS.length];
    private Controller active;
    private boolean baseline = true;

    GamepadInput(InputMap map, KeyManager actions) {
        this.actions = actions;
        this.map = map;
    }

    boolean isConfigured() {
        for (var control : CONTROLS) { if (map.getGamepad(control) != null) { return true; } }
        return false;
    }

    void update(Array<Controller> controllers) { poll(controllers, false); }

    GamepadControl poll(Array<Controller> controllers, boolean menu) {
        if (active != null && (!controllers.contains(active, true) || !active.isConnected()
            || (baseline && !supportsBinding(active.getMapping(), menu)))) {
            releaseHeldInputs();
            active = null;
        }
        if (active == null) {
            for (var controller : controllers) {
                if (controller.isConnected() && supportsBinding(controller.getMapping(), menu)) {
                    active = controller;
                    break;
                }
            }
            if (active == null) { return null; }
        }

        var mapping = active.getMapping();
        float x = readAxis(mapping.axisLeftX);
        float y = readAxis(mapping.axisLeftY);
        for (var control : CONTROLS) {
            int i = control.ordinal();
            if (!menu && map.getGamepad(control) == null && delivered[i] == null) { sample[i] = false; continue; }
            sample[i] = switch (control) {
                case LEFT_STICK_UP -> stickDown(-y, i);
                case LEFT_STICK_DOWN -> stickDown(y, i);
                case LEFT_STICK_LEFT -> stickDown(-x, i);
                case LEFT_STICK_RIGHT -> stickDown(x, i);
                default -> {
                    int index = control.mappedIndex(mapping);
                    yield index != ControllerMapping.UNDEFINED && active.getButton(index);
                }
            };
        }
        // Discard a partial snapshot on disconnect.
        if (!controllers.contains(active, true) || !active.isConnected()) {
            releaseHeldInputs();
            active = null;
            return null;
        }

        GamepadControl pressed = null;
        for (var control : CONTROLS) {
            int i = control.ordinal();
            if (!baseline && sample[i] != down[i]) {
                if (sample[i]) {
                    if (pressed == null || control == GamepadControl.BACK) { pressed = control; }
                    if (!menu) {
                        delivered[i] = map.getGamepad(control);
                        if (delivered[i] != null) { actions.dispatch(delivered[i], 1); }
                    }
                } else if (delivered[i] != null) {
                    actions.dispatch(delivered[i], 0);
                    delivered[i] = null;
                }
            }
            down[i] = sample[i];
        }
        baseline = false;
        return pressed;
    }

    private boolean supportsBinding(ControllerMapping mapping, boolean menu) {
        if (mapping == null) { return false; }
        for (var control : CONTROLS) {
            if ((menu || map.getGamepad(control) != null) && control.mappedIndex(mapping) != ControllerMapping.UNDEFINED) { return true; }
        }
        return false;
    }

    private float readAxis(int index) {
        return index == ControllerMapping.UNDEFINED ? 0f : active.getAxis(index);
    }

    private boolean stickDown(float tilt, int i) {
        return baseline || down[i] ? tilt > 0.35f : tilt >= 0.50f;
    }

    void releaseHeldInputs() {
        actions.invalidateContext();
        for (int i = 0; i < delivered.length; i++) {
            if (delivered[i] != null) {
                actions.dispatch(delivered[i], 0);
                delivered[i] = null;
            }
        }
        baseline = true;
    }
}
