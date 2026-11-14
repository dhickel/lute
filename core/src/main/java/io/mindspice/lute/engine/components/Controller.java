package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.KeyListener;

import java.util.List;

public class Controller implements Component {
    public static final ComponentMapper<Controller> MAPPER = ComponentMapper.getFor(Controller.class);

    private final KeyListener inputListener;

    public Controller() {
        this.inputListener = new KeyListener(32, List.of(
            InputAction.MOVE_UP,
            InputAction.MOVE_DOWN,
            InputAction.MOVE_LEFT,
            InputAction.MOVE_RIGHT,
            InputAction.ATTACK
        ));
    }

    public static Controller forInputManager(InputManager inputManager) {
        var controller = new Controller();
        inputManager.regKeyListener(controller.inputListener);
        return controller;
    }


    public KeyListener getInputListener() {
        return inputListener;
    }
}
