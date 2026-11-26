package io.mindspice.lute.engine.input;

import com.badlogic.gdx.controllers.ControllerMapping;

/** Logical controls that can be authored in the controller bindings. */
public enum GamepadControl {
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, FACE_SOUTH, FACE_WEST, FACE_NORTH, FACE_EAST,
    BACK, START, L1, R1, L2, R2, LEFT_STICK_BUTTON, RIGHT_STICK_BUTTON,
    LEFT_STICK_UP, LEFT_STICK_DOWN, LEFT_STICK_LEFT, LEFT_STICK_RIGHT;

    int mappedIndex(ControllerMapping mapping) {
        return switch (this) {
            case DPAD_UP -> mapping.buttonDpadUp;
            case DPAD_DOWN -> mapping.buttonDpadDown;
            case DPAD_LEFT -> mapping.buttonDpadLeft;
            case DPAD_RIGHT -> mapping.buttonDpadRight;
            case FACE_SOUTH -> mapping.buttonA;
            case FACE_EAST -> mapping.buttonB;
            case BACK -> mapping.buttonBack;
            case START -> mapping.buttonStart;
            case L1 -> mapping.buttonL1;
            case R1 -> mapping.buttonR1;
            case L2 -> mapping.buttonL2;
            case R2 -> mapping.buttonR2;
            case LEFT_STICK_BUTTON -> mapping.buttonLeftStick;
            case RIGHT_STICK_BUTTON -> mapping.buttonRightStick;
            case FACE_WEST -> mapping.buttonX;
            case FACE_NORTH -> mapping.buttonY;
            case LEFT_STICK_UP, LEFT_STICK_DOWN -> mapping.axisLeftY;
            case LEFT_STICK_LEFT, LEFT_STICK_RIGHT -> mapping.axisLeftX;
        };
    }
}
