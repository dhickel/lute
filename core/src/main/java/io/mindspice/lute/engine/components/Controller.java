package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.KeyListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Controller implements Component {
    public static final ComponentMapper<Controller> MAPPER = ComponentMapper.getFor(Controller.class);

    private final KeyListener inputListener;
    private final List<String> weaponCycle;
    private final Vector2 heldDirection = new Vector2();
    private int primaryHolds;

    public Vector2 getHeldDirection() { return heldDirection; }

    public void resetInput() {
        primaryHolds = 0;
        heldDirection.setZero();
    }

    // Physical bindings on different devices contribute independent press/release pairs.
    public boolean primaryEdge(boolean pressed) {
        if (pressed) { return ++primaryHolds == 1; }
        return primaryHolds > 0 && --primaryHolds == 0;
    }

    public Controller() { this(List.of()); }

    public Controller(List<String> weaponCycle) { this(weaponCycle, false); }

    public static Controller forArena(String initialWeapon) {
        return new Controller(List.of(initialWeapon), true);
    }

    private Controller(List<String> weaponCycle, boolean arena) {
        this.weaponCycle = new ArrayList<>(weaponCycle);
        var actions = new ArrayList<>(List.of(
            InputAction.MOVE_UP,
            InputAction.MOVE_DOWN,
            InputAction.MOVE_LEFT,
            InputAction.MOVE_RIGHT,
            InputAction.ATTACK_PRIMARY,
            InputAction.ATTACK_SECONDARY,
            InputAction.CYCLE_WEAPON
        ));
        if (arena) { actions.add(InputAction.CYCLE_ARENA_WEAPON); }
        this.inputListener = new KeyListener(32, actions);
    }

    public static Controller forInputManager(InputManager inputManager) {
        var controller = new Controller();
        inputManager.regKeyListener(controller.inputListener);
        return controller;
    }


    public void collectWeapon(String id) {
        if (!weaponCycle.contains(id)) { weaponCycle.add(id); }
    }

    public List<String> getWeaponCycle() { return Collections.unmodifiableList(weaponCycle); }

    public KeyListener getInputListener() {
        return inputListener;
    }
}
