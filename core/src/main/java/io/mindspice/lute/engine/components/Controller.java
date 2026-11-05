package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import io.mindspice.lute.engine.input.ActionType;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.KeyCacheListener;

import java.util.List;

public class Controller implements Component {
    public static final ComponentMapper<Controller> MAPPER = ComponentMapper.getFor(Controller.class);

    private final KeyCacheListener inputListener;

    public Controller() {
        this.inputListener = new KeyCacheListener(ActionType.GAME_INPUT, 32, List.of(
            InputAction.MOVE_UP,
            InputAction.MOVE_DOWN,
            InputAction.MOVE_LEFT,
            InputAction.MOVE_RIGHT
        ));
    }

    public KeyCacheListener getInputListener() {
        return inputListener;
    }
}
