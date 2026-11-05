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

    private static void facadeRelease() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        List<KeyEvent> keys = new ArrayList<>();
        List<MouseEvent> buttons = new ArrayList<>();
        KeyCacheListener keyCache = new KeyCacheListener(16, List.of(InputAction.MOVE_UP, InputAction.GUI_ENTER));
        MouseCacheListener mouseCache = new MouseCacheListener(16, List.of(InputAction.GUI_LEFT_CLICK, InputAction.MOVE_RIGHT));
        input.regKeyListener(new KeyCallBackListener(List.of(InputAction.MOVE_UP, InputAction.GUI_ENTER), 4,
            (a, v) -> keys.add(new KeyEvent(a, v))));
        MouseCallBackListener mouse = new MouseCallBackListener();
        mouse.setButtonConsumer((a, v) -> buttons.add(new MouseEvent(a, v, mouse.getPos().x, mouse.getPos().y)));
        input.regKeyListener(keyCache);
        input.regMousePosListener(mouse);
        input.regMousePosListener(mouseCache);
        input.keyUp(Input.Keys.W);
        input.touchUp(1, 2, 0, Input.Buttons.LEFT);
        input.keyDown(Input.Keys.W);
        input.touchDown(10, 20, 0, Input.Buttons.LEFT);
        map.setKey(Input.Keys.W, InputAction.GUI_ENTER);
        map.setButton(Input.Buttons.LEFT, InputAction.MOVE_RIGHT);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.releaseHeldInputs();
        List<KeyEvent> initialKeys = List.of(new KeyEvent(InputAction.MOVE_UP, 1),
            new KeyEvent(InputAction.MOVE_UP, 2), new KeyEvent(InputAction.MOVE_UP, 0));
        List<MouseEvent> initialButtons = List.of(new MouseEvent(InputAction.GUI_LEFT_CLICK, 1, 10, 20),
            new MouseEvent(InputAction.GUI_LEFT_CLICK, 0, 10, 20));
        equal(initialKeys, keys, "release synchronously uses captured key binding");
        equal(initialButtons, buttons, "release synchronously uses captured button binding and position");
        input.releaseHeldInputs();
        input.keyDown(Input.Keys.W);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.touchDown(11, 21, 0, Input.Buttons.LEFT);
        input.keyUp(Input.Keys.W);
        input.keyUp(Input.Keys.W);
        input.touchUp(12, 22, 0, Input.Buttons.LEFT);
        input.touchCancelled(12, 22, 0, Input.Buttons.LEFT);
        equal(initialKeys, keys, "idempotent release suppresses held duplicate, repeat and late ups");
        equal(initialButtons, buttons, "idempotent release suppresses held button duplicate and late ups");
        List<KeyEvent> cachedKeys = new ArrayList<>();
        keyCache.consume((a, v) -> cachedKeys.add(new KeyEvent(a, v)));
        equal(initialKeys, cachedKeys, "cached key down, repeat and release remain FIFO");
        List<MouseEvent> cachedButtons = new ArrayList<>();
        mouseCache.consume((type, a, v, x, y) -> cachedButtons.add(new MouseEvent(a, v, x, y)));
        equal(initialButtons, cachedButtons, "cached button down and release remain FIFO");
        input.keyDown(Input.Keys.W);
        input.touchDown(30, 40, 0, Input.Buttons.LEFT);
        map.removeKey(Input.Keys.W);
        map.removeButton(Input.Buttons.LEFT);
        input.keyUp(Input.Keys.W);
        input.touchUp(31, 41, 0, Input.Buttons.LEFT);
        equal(List.of(new KeyEvent(InputAction.GUI_ENTER, 1), new KeyEvent(InputAction.GUI_ENTER, 0)),
            keys.subList(initialKeys.size(), keys.size()), "fresh key press uses new binding and up survives removal");
        equal(List.of(new MouseEvent(InputAction.MOVE_RIGHT, 1, 30, 40), new MouseEvent(InputAction.MOVE_RIGHT, 0, 31, 41)),
            buttons.subList(initialButtons.size(), buttons.size()), "fresh button press uses new binding and up survives removal");
    }

    private static void physicalBindings() {
        InputMap map = new InputMap();
        KeyManager keys = new KeyManager(1, map);
        List<KeyEvent> events = new ArrayList<>();
        KeyCacheListener cache = new KeyCacheListener(1024, List.of(InputAction.MOVE_UP));
        keys.registerListener(new KeyCallBackListener(List.of(InputAction.MOVE_UP), 1,
            (a, v) -> events.add(new KeyEvent(a, v))));
        keys.registerListener(cache);
        for (int code = 0; code <= Input.Keys.MAX_KEYCODE; code++) {
            map.setKey(code, InputAction.MOVE_UP);
            keys.broadcast(code, 1);
            map.removeKey(code);
        }
        int count = Input.Keys.MAX_KEYCODE + 1;
        equal(count, events.size(), "each physical key emits its own down");
        keys.releaseHeldInputs();
        equal(count * 2, events.size(), "release emits one up per physical key");
        for (int i = 0; i < count; i++) {
            equal(new KeyEvent(InputAction.MOVE_UP, 1), events.get(i), "per-key down " + i);
            equal(new KeyEvent(InputAction.MOVE_UP, 0), events.get(count + i), "per-key release " + i);
            keys.broadcast(i, 0);
        }
        List<KeyEvent> cached = new ArrayList<>();
        cache.consume((a, v) -> cached.add(new KeyEvent(a, v)));
        equal(events, cached, "same-action physical key pairs remain queued independently");
        equal(count * 2, events.size(), "late physical key ups ignored");
        map.setKey(Input.Keys.MAX_KEYCODE, InputAction.MOVE_UP);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 1);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 0);
        equal(count * 2 + 2, events.size(), "fresh upper-bound key press works");

        MouseManager mouse = new MouseManager(1);
        MouseCacheListener mouseCache = new MouseCacheListener(16, List.of(InputAction.MOVE_UP));
        List<Integer> buttons = new ArrayList<>();
        MouseCallBackListener callback = new MouseCallBackListener();
        callback.setButtonConsumer((a, v) -> buttons.add(v));
        mouse.registerListener(callback);
        mouse.registerListener(mouseCache);
        for (int b = 0; b <= Input.Buttons.FORWARD; b++) { mouse.broadcastButtons(b, InputAction.MOVE_UP, 1); }
        mouse.releaseHeldInputs();
        int countButtons = Input.Buttons.FORWARD + 1;
        equal(countButtons * 2, buttons.size(), "each physical button gets its own release");
        for (int b = 0; b < countButtons; b++) {
            equal(1, buttons.get(b), "per-button down " + b);
            equal(0, buttons.get(countButtons + b), "per-button release " + b);
            mouse.broadcastButtons(b, InputAction.GUI_ENTER, 0);
        }
        List<Integer> cachedButtons = new ArrayList<>();
        mouseCache.consume((t, a, v, x, y) -> cachedButtons.add(v));
        equal(buttons, cachedButtons, "same-action button pairs remain queued");
        equal(countButtons * 2, buttons.size(), "late physical button ups ignored");
        mouse.broadcastButtons(Input.Buttons.FORWARD, InputAction.MOVE_UP, 1);
        mouse.broadcastButtons(Input.Buttons.FORWARD, null, 0);
        equal(countButtons * 2 + 2, buttons.size(), "fresh upper-bound button press works");
    }
}
