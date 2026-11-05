package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;
import java.util.ArrayList;
import java.util.List;
import static io.mindspice.lute.engine.input.InputSystemTest.equal;

final class HeldInputTest {
    private record KeyEvent(InputAction action, int value) { }
    private record MouseEvent(InputAction action, int value, double x, double y) { }

    static void run() {
        facadeRelease();
        physicalBindings();
    }

    private static List<KeyEvent> drain(KeyListener listener) {
        List<KeyEvent> events = new ArrayList<>();
        listener.consume((a, v) -> events.add(new KeyEvent(a, v)));
        return events;
    }

    private static List<MouseEvent> drain(MouseListener listener) {
        List<MouseEvent> events = new ArrayList<>();
        listener.consume((t, a, v, x, y) -> events.add(new MouseEvent(a, v, x, y)));
        return events;
    }

    private static void facadeRelease() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        KeyListener keys = new KeyListener(16, List.of(InputAction.MOVE_UP, InputAction.GUI_ENTER));
        MouseListener buttons = new MouseListener(16, List.of(InputAction.GUI_LEFT_CLICK, InputAction.MOVE_RIGHT));
        input.regKeyListener(keys);
        input.regMousePosListener(buttons);
        input.keyUp(Input.Keys.W);
        input.touchUp(1, 2, 0, Input.Buttons.LEFT);
        input.keyDown(Input.Keys.W);
        input.touchDown(10, 20, 0, Input.Buttons.LEFT);
        map.setKey(Input.Keys.W, InputAction.GUI_ENTER);
        map.setButton(Input.Buttons.LEFT, InputAction.MOVE_RIGHT);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.releaseHeldInputs();
        input.releaseHeldInputs();
        input.keyDown(Input.Keys.W);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.touchDown(11, 21, 0, Input.Buttons.LEFT);
        input.keyUp(Input.Keys.W);
        input.keyUp(Input.Keys.W);
        input.touchUp(12, 22, 0, Input.Buttons.LEFT);
        input.touchCancelled(12, 22, 0, Input.Buttons.LEFT);
        equal(List.of(new KeyEvent(InputAction.MOVE_UP, 1), new KeyEvent(InputAction.MOVE_UP, 2),
            new KeyEvent(InputAction.MOVE_UP, 0)), drain(keys),
            "captured key binding, FIFO release, duplicate suppression and late ups");
        equal(List.of(new MouseEvent(InputAction.GUI_LEFT_CLICK, 1, 10, 20),
            new MouseEvent(InputAction.GUI_LEFT_CLICK, 0, 10, 20)), drain(buttons),
            "captured button binding and position, duplicate suppression and late ups");
        input.keyDown(Input.Keys.W);
        input.touchDown(30, 40, 0, Input.Buttons.LEFT);
        map.removeKey(Input.Keys.W);
        map.removeButton(Input.Buttons.LEFT);
        input.keyUp(Input.Keys.W);
        input.touchUp(31, 41, 0, Input.Buttons.LEFT);
        equal(List.of(new KeyEvent(InputAction.GUI_ENTER, 1), new KeyEvent(InputAction.GUI_ENTER, 0)),
            drain(keys), "fresh key press uses new binding and up survives removal");
        equal(List.of(new MouseEvent(InputAction.MOVE_RIGHT, 1, 30, 40), new MouseEvent(InputAction.MOVE_RIGHT, 0, 31, 41)),
            drain(buttons), "fresh button press uses new binding and up survives removal");
    }

    private static void physicalBindings() {
        InputMap map = new InputMap();
        KeyManager keys = new KeyManager(1, map);
        KeyListener cache = new KeyListener(1024, List.of(InputAction.MOVE_UP));
        keys.registerListener(cache);
        for (int code = 0; code <= Input.Keys.MAX_KEYCODE; code++) {
            map.setKey(code, InputAction.MOVE_UP);
            keys.broadcast(code, 1);
            map.removeKey(code);
        }
        int count = Input.Keys.MAX_KEYCODE + 1;
        keys.releaseHeldInputs();
        for (int i = 0; i < count; i++) { keys.broadcast(i, 0); }
        List<KeyEvent> events = drain(cache);
        equal(count * 2, events.size(), "each physical key emits down and release; late ups ignored");
        for (int i = 0; i < count; i++) {
            equal(new KeyEvent(InputAction.MOVE_UP, 1), events.get(i), "per-key down " + i);
            equal(new KeyEvent(InputAction.MOVE_UP, 0), events.get(count + i), "per-key release " + i);
        }
        map.setKey(Input.Keys.MAX_KEYCODE, InputAction.MOVE_UP);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 1);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 0);
        equal(List.of(new KeyEvent(InputAction.MOVE_UP, 1), new KeyEvent(InputAction.MOVE_UP, 0)),
            drain(cache), "fresh upper-bound key press works");

        MouseManager mouse = new MouseManager(1);
        MouseListener mouseCache = new MouseListener(16, List.of(InputAction.MOVE_UP));
        mouse.registerListener(mouseCache);
        for (int b = 0; b <= Input.Buttons.FORWARD; b++) { mouse.broadcastButtons(b, InputAction.MOVE_UP, 1); }
        mouse.releaseHeldInputs();
        int countButtons = Input.Buttons.FORWARD + 1;
        for (int b = 0; b < countButtons; b++) { mouse.broadcastButtons(b, InputAction.GUI_ENTER, 0); }
        List<MouseEvent> buttons = drain(mouseCache);
        equal(countButtons * 2, buttons.size(), "each physical button gets its own release; late ups ignored");
        for (int b = 0; b < countButtons; b++) {
            equal(new MouseEvent(InputAction.MOVE_UP, 1, 0, 0), buttons.get(b), "per-button down " + b);
            equal(new MouseEvent(InputAction.MOVE_UP, 0, 0, 0), buttons.get(countButtons + b), "per-button release " + b);
        }
        mouse.broadcastButtons(Input.Buttons.FORWARD, InputAction.MOVE_UP, 1);
        mouse.broadcastButtons(Input.Buttons.FORWARD, null, 0);
        equal(List.of(new MouseEvent(InputAction.MOVE_UP, 1, 0, 0), new MouseEvent(InputAction.MOVE_UP, 0, 0, 0)),
            drain(mouseCache), "fresh upper-bound button press works");
    }
}
