package io.mindspice.lute.engine.input;

import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.utils.Array;

import java.util.Arrays;

/** Translates snapshots of one mapped pad into the existing queued action pairs. */
final class GamepadInput {
    private static final float STICK_PRESS = 0.50f;
    private static final float STICK_RELEASE = 0.35f;

    private final KeyManager actions;
    private final GamepadControl[] controls;
    private final InputAction[] bindings;
    private final boolean[] down;
    private final boolean[] delivered;
    private final boolean[] sample;
    private Controller active;
    private boolean baseline = true;

    GamepadInput(InputMap map, KeyManager actions) {
        this.actions = actions;
        controls = Arrays.stream(GamepadControl.values()).filter(control -> map.getGamepad(control) != null)
            .toArray(GamepadControl[]::new);
        bindings = Arrays.stream(controls).map(map::getGamepad).toArray(InputAction[]::new);
        down = new boolean[controls.length];
        delivered = new boolean[controls.length];
        sample = new boolean[controls.length];
    }

    boolean isConfigured() {
        return controls.length > 0;
    }

    void update(Array<Controller> controllers) {
        if (active != null && (!controllers.contains(active, true) || !active.isConnected())) {
            releaseHeldInputs();
            active = null;
        }
        if (active == null) {
            for (var controller : controllers) {
                if (controller.isConnected() && supportsBinding(controller.getMapping())) {
                    active = controller;
                    break;
                }
            }
            if (active == null) { return; }
        }

        var mapping = active.getMapping();
        float x = readAxis(mapping.axisLeftX);
        float y = readAxis(mapping.axisLeftY);
        for (int i = 0; i < controls.length; i++) {
            sample[i] = switch (controls[i]) {
                case LEFT_STICK_UP -> stickDown(-y, i);
                case LEFT_STICK_DOWN -> stickDown(y, i);
                case LEFT_STICK_LEFT -> stickDown(-x, i);
                case LEFT_STICK_RIGHT -> stickDown(x, i);
                default -> {
                    int index = controls[i].mappedIndex(mapping);
                    yield index != ControllerMapping.UNDEFINED && active.getButton(index);
                }
            };
        }
        // A device can disappear during a native read. Do not dispatch a partial snapshot.
        if (!controllers.contains(active, true) || !active.isConnected()) {
            releaseHeldInputs();
            active = null;
            return;
        }

        for (int i = 0; i < controls.length; i++) {
            if (!baseline && sample[i] != down[i]) {
                if (sample[i]) {
                    delivered[i] = true;
                    actions.dispatch(bindings[i], 1);
                } else if (delivered[i]) {
                    delivered[i] = false;
                    actions.dispatch(bindings[i], 0);
                }
            }
            down[i] = sample[i];
        }
        baseline = false;
    }

    private boolean supportsBinding(ControllerMapping mapping) {
        if (mapping == null) { return false; }
        for (var control : controls) {
            if (control.mappedIndex(mapping) != ControllerMapping.UNDEFINED) { return true; }
        }
        return false;
    }

    private float readAxis(int index) {
        return index == ControllerMapping.UNDEFINED ? 0f : active.getAxis(index);
    }

    private boolean stickDown(float directedTilt, int control) {
        // A baseline must see the full release threshold so a partially held stick is not re-armed.
        return baseline || down[control] ? directedTilt > STICK_RELEASE : directedTilt >= STICK_PRESS;
    }

    void releaseHeldInputs() {
        for (int i = 0; i < controls.length; i++) {
            if (delivered[i]) {
                delivered[i] = false;
                actions.dispatch(bindings[i], 0);
            }
        }
        baseline = true;
    }
}
