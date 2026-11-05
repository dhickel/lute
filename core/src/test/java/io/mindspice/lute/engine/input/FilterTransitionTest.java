package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;

import java.util.ArrayList;
import java.util.List;

import static io.mindspice.lute.engine.input.InputSystemTest.check;
import static io.mindspice.lute.engine.input.InputSystemTest.equal;
import static io.mindspice.lute.engine.input.InputSystemTest.throwsType;

final class FilterTransitionTest {
    private record KeyEvent(InputAction action, int value) { }
    private record MouseEvent(MouseEventConsumer.Type type, InputAction action, int value, double x, double y) { }

    static void run() {
        keyboardTransitions();
        mouseTransitions();
        physicalBindings();
        directManagerCallbacks();
        directDisableTransitions();
        facadeCallbacks();
        facadeReleaseRequests();
        mouseCallbackBoundary();
        scrollCallbackBoundary();
        exceptionGuards();
    }

    private static KeyCallBackListener key(ActionType type, KeyActionConsumer consumer) {
        return new KeyCallBackListener(type, List.of(InputAction.values()), 1, consumer);
    }

    private static KeyCacheListener keyCache(ActionType type, int capacity) {
        return new KeyCacheListener(type, capacity, List.of(InputAction.values()));
    }

    private static MouseCacheListener mouseCache(ActionType type, int capacity) {
        return new MouseCacheListener(type, capacity, List.of(InputAction.values()));
    }

    private static List<KeyEvent> drain(KeyCacheListener listener) {
        List<KeyEvent> events = new ArrayList<>();
        listener.consume((action, value) -> events.add(new KeyEvent(action, value)));
        return events;
    }

    private static List<MouseEvent> drain(MouseCacheListener listener) {
        List<MouseEvent> events = new ArrayList<>();
        listener.consume((type, action, value, x, y) -> events.add(new MouseEvent(type, action, value, x, y)));
        return events;
    }

    private static MouseEvent button(InputAction action, int value, double x, double y) {
        return new MouseEvent(MouseEventConsumer.Type.BUTTON, action, value, x, y);
    }

    private static MouseEvent scroll(int value, double x, double y) {
        return new MouseEvent(MouseEventConsumer.Type.SCROLL, null, value, x, y);
    }

    private static void keyboardTransitions() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        InputManager input = new InputManager(map);
        List<KeyEvent> game = new ArrayList<>();
        List<KeyEvent> gui = new ArrayList<>();
        KeyCacheListener gameCache = keyCache(ActionType.GAME_INPUT, 16);
        KeyCacheListener guiCache = keyCache(ActionType.GUI, 16);
        input.regKeyListener(key(ActionType.GAME_INPUT, (a, v) -> game.add(new KeyEvent(a, v))));
        input.regKeyListener(gameCache);
        input.regKeyListener(key(ActionType.GUI, (a, v) -> gui.add(new KeyEvent(a, v))));
        input.regKeyListener(guiCache);
        input.setFilter(ActionType.GAME_INPUT);
        input.keyUp(Input.Keys.W);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        equal(List.of(), game, "unmatched keyboard up and repeat are ignored");
        input.keyDown(Input.Keys.W);
        input.keyDown(Input.Keys.W);
        input.keyDown(Input.Keys.ENTER);
        input.keyDown(Input.Keys.Q);
        map.setKey(Input.Keys.Q, InputAction.GUI_ENTER);
        map.setKey(Input.Keys.W, InputAction.GUI_ENTER);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.setFilter(ActionType.GAME_INPUT);
        equal(List.of(new KeyEvent(InputAction.MOVE_UP, 1), new KeyEvent(InputAction.MOVE_UP, 2)), game,
            "same filter is a no-op; direct repeat uses original mapping");
        input.setFilter(ActionType.GUI);
        List<KeyEvent> outgoing = List.of(new KeyEvent(InputAction.MOVE_UP, 1),
            new KeyEvent(InputAction.MOVE_UP, 2), new KeyEvent(InputAction.MOVE_UP, 0));
        equal(outgoing, game, "outgoing keyboard callback receives original action release immediately");
        check(gameCache.hasInputs(), "switch retains pending outgoing keyboard cache");
        equal(outgoing, drain(gameCache), "outgoing keyboard queue drains press/repeat/reset FIFO after switch");
        input.keyDown(Input.Keys.W);
        input.getKeyManager().broadcast(Input.Keys.W, 2);
        input.keyDown(Input.Keys.ENTER);
        input.keyDown(Input.Keys.Q);
        equal(List.of(), gui, "reset, filter-rejected and unmapped held keys cannot activate on repeat");
        for (int code : new int[]{Input.Keys.W, Input.Keys.ENTER, Input.Keys.Q}) {
            input.keyUp(code);
            input.keyUp(code);
            input.keyDown(code);
            input.keyUp(code);
        }
        equal(6, gui.size(), "real up allows each held key to re-press with its new mapping");
        equal(gui, drain(guiCache), "GUI keyboard cache matches callbacks after real re-press");
        equal(outgoing, game, "late keyboard ups never release outgoing action twice");
        input.keyDown(Input.Keys.ENTER);
        input.disableFilter();
        equal(new KeyEvent(InputAction.GUI_ENTER, 0), gui.get(gui.size() - 1), "disable facade filter resets held key");
        int before = gui.size();
        input.disableFilter();
        input.keyDown(Input.Keys.ENTER);
        equal(before, gui.size(), "repeated disable and autorepeat after disable do not emit");
        input.keyUp(Input.Keys.ENTER);
        input.keyDown(Input.Keys.ENTER);
        input.keyUp(Input.Keys.ENTER);
        equal(before + 2, gui.size(), "disabled filter accepts a fresh press and release");
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        input.keyDown(Input.Keys.W);
        map.removeKey(Input.Keys.W);
        input.keyUp(Input.Keys.W);
        equal(List.of(new KeyEvent(InputAction.MOVE_UP, 1), new KeyEvent(InputAction.MOVE_UP, 0)),
            game.subList(outgoing.size(), game.size()), "real key up releases original action even after mapping removal");
    }

    private static void mouseTransitions() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        List<MouseEvent> immediate = new ArrayList<>();
        MouseCallBackListener game = new MouseCallBackListener(ActionType.GAME_INPUT);
        game.setButtonConsumer((a, v) -> immediate.add(button(a, v, game.getPos().x, game.getPos().y)));
        game.setScrollConsumer(v -> immediate.add(scroll(v, game.getPos().x, game.getPos().y)));
        MouseCacheListener gameCache = mouseCache(ActionType.GAME_INPUT, 16);
        MouseCacheListener guiCache = mouseCache(ActionType.GUI, 16);
        input.regMousePosListener(game);
        input.regMousePosListener(gameCache);
        input.regMousePosListener(guiCache);
        input.setFilter(ActionType.GAME_INPUT);
        input.touchUp(1, 2, 0, Input.Buttons.RIGHT);
        check(!gameCache.hasInputs(), "unmatched mouse up is ignored");
        input.touchDown(10, 20, 0, Input.Buttons.RIGHT);
        input.scrolled(0, -1);
        input.touchDown(11, 21, 0, Input.Buttons.RIGHT);
        input.touchDown(12, 22, 0, Input.Buttons.LEFT);
        input.touchDown(13, 23, 0, Input.Buttons.MIDDLE);
        map.setButton(Input.Buttons.MIDDLE, InputAction.GUI_RIGHT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.GUI_RIGHT_CLICK);
        input.setFilter(ActionType.GAME_INPUT);
        equal(2, immediate.size(), "same mouse filter and duplicate down emit no buttons");
        input.setFilter(ActionType.GUI);
        List<MouseEvent> outgoing = List.of(button(InputAction.MOVE_RIGHT, 1, 10, 20), scroll(1, 10, 20),
            button(InputAction.MOVE_RIGHT, 0, 13, 23));
        equal(outgoing, immediate, "synthetic mouse release uses original action and latest outgoing position");
        check(gameCache.hasInputs(), "switch does not clear outgoing mouse queue");
        equal(outgoing, drain(gameCache), "old mouse queue drains down/scroll/reset FIFO after filter switch");
        for (int b : new int[]{Input.Buttons.RIGHT, Input.Buttons.LEFT, Input.Buttons.MIDDLE}) {
            input.touchDown(30, 40, 0, b);
        }
        check(!guiCache.hasInputs(), "reset, rejected and unmapped mouse presses remain blocked while physical held");
        for (int b : new int[]{Input.Buttons.RIGHT, Input.Buttons.LEFT, Input.Buttons.MIDDLE}) {
            input.touchUp(31, 41, 0, b);
            input.touchCancelled(32, 42, 0, b);
            input.touchDown(33, 43, 0, b);
            input.touchCancelled(34, 44, 0, b);
        }
        List<MouseEvent> expected = List.of(button(InputAction.GUI_RIGHT_CLICK, 1, 33, 43),
            button(InputAction.GUI_RIGHT_CLICK, 0, 34, 44), button(InputAction.GUI_LEFT_CLICK, 1, 33, 43),
            button(InputAction.GUI_LEFT_CLICK, 0, 34, 44), button(InputAction.GUI_RIGHT_CLICK, 1, 33, 43),
            button(InputAction.GUI_RIGHT_CLICK, 0, 34, 44));
        equal(expected, drain(guiCache), "real mouse up unblocks fresh mapped down/cancel with position-first snapshots");
        equal(outgoing, immediate, "late physical mouse ups never duplicate outgoing reset");
        input.touchDown(50, 60, 0, Input.Buttons.LEFT);
        input.disableFilter();
        input.touchDown(51, 61, 0, Input.Buttons.LEFT);
        input.touchUp(52, 62, 0, Input.Buttons.LEFT);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 50, 60), button(InputAction.GUI_LEFT_CLICK, 0, 50, 60)),
            drain(guiCache), "disable resets mouse and suppresses repeat and late up");
        input.touchDown(53, 63, 0, Input.Buttons.LEFT);
        map.removeButton(Input.Buttons.LEFT);
        input.touchUp(54, 64, 0, Input.Buttons.LEFT);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 53, 63), button(InputAction.GUI_LEFT_CLICK, 0, 54, 64)),
            drain(guiCache), "real mouse up releases original action after mapping removal");
    }

    private static void physicalBindings() {
        InputMap map = new InputMap();
        KeyManager keys = new KeyManager(1, map);
        KeyCacheListener keyCache = keyCache(ActionType.GAME_INPUT, 1024);
        List<KeyEvent> keyEvents = new ArrayList<>();
        keys.registerListener(key(ActionType.GAME_INPUT, (a, v) -> keyEvents.add(new KeyEvent(a, v))));
        keys.registerListener(keyCache);
        for (int code = 0; code <= Input.Keys.MAX_KEYCODE; code++) {
            map.setKey(code, InputAction.MOVE_UP);
            keys.broadcast(code, 1);
            map.removeKey(code);
        }
        int count = Input.Keys.MAX_KEYCODE + 1;
        equal(count, keyEvents.size(), "each physical key mapped to one action emits its own down");
        keys.setListenFilter(ActionType.GUI);
        equal(count * 2, keyEvents.size(), "reset emits one up per physical key, not one per action");
        for (int i = 0; i < count; i++) {
            equal(new KeyEvent(InputAction.MOVE_UP, 1), keyEvents.get(i), "per-key down " + i);
            equal(new KeyEvent(InputAction.MOVE_UP, 0), keyEvents.get(count + i), "per-key reset " + i);
            keys.broadcast(i, 0);
        }
        equal(keyEvents, drain(keyCache), "all same-action physical key pairs remain queued independently");
        equal(count * 2, keyEvents.size(), "physical ups after mass reset are ignored");
        keys.disableListenFilter();
        map.setKey(Input.Keys.MAX_KEYCODE, InputAction.MOVE_UP);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 1);
        keys.broadcast(Input.Keys.MAX_KEYCODE, 0);
        equal(count * 2 + 2, keyEvents.size(), "direct key manager disable restores fresh presses at upper bound");

        MouseManager mouse = new MouseManager(1);
        MouseCacheListener mouseCache = mouseCache(ActionType.GAME_INPUT, 16);
        List<Integer> mouseEvents = new ArrayList<>();
        MouseCallBackListener listener = new MouseCallBackListener(ActionType.GAME_INPUT);
        listener.setButtonConsumer((a, v) -> mouseEvents.add(v));
        mouse.registerListener(listener);
        mouse.registerListener(mouseCache);
        for (int b = 0; b <= Input.Buttons.FORWARD; b++) { mouse.broadcastButtons(b, InputAction.MOVE_UP, 1); }
        mouse.setListenFilter(ActionType.GUI);
        int buttons = Input.Buttons.FORWARD + 1;
        equal(buttons * 2, mouseEvents.size(), "each physical mouse binding gets its own reset");
        for (int i = 0; i < buttons; i++) {
            equal(1, mouseEvents.get(i), "per-button down " + i);
            equal(0, mouseEvents.get(buttons + i), "per-button reset " + i);
            mouse.broadcastButtons(i, InputAction.GUI_ENTER, 0);
        }
        equal(buttons * 2, drain(mouseCache).size(), "same-action mouse downs and resets remain queued");
        equal(buttons * 2, mouseEvents.size(), "late mouse ups after mass reset are ignored");
        mouse.disableListenFilter();
        mouse.broadcastButtons(Input.Buttons.FORWARD, InputAction.MOVE_UP, 1);
        mouse.broadcastButtons(Input.Buttons.FORWARD, null, 0);
        equal(buttons * 2 + 2, mouseEvents.size(), "direct mouse disable restores fresh presses at upper bound");
    }

    private static void directManagerCallbacks() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        KeyManager keys = new KeyManager(1, map);
        List<String> trace = new ArrayList<>();
        keys.registerListener(key(ActionType.GAME_INPUT, (a, v) -> {
            trace.add("first:" + v);
            if (v == 1) { keys.setListenFilter(ActionType.GUI); }
            if (v == 0) {
                keys.setListenFilter(ActionType.WINDOW);
                keys.disableListenFilter();
            }
        }));
        keys.registerListener(key(ActionType.GAME_INPUT, (a, v) -> trace.add("second:" + v)));
        keys.broadcast(Input.Keys.W, 1);
        equal(List.of("first:1", "second:1", "first:0", "second:0"), trace,
            "direct key filter request waits for complete down fan-out, release requests do not recurse");
        keys.broadcast(Input.Keys.W, 0);
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        List<Integer> guiKeys = new ArrayList<>();
        keys.registerListener(key(ActionType.GUI, (a, v) -> guiKeys.add(v)));
        keys.broadcast(Input.Keys.ENTER, 1);
        keys.broadcast(Input.Keys.ENTER, 0);
        equal(List.of(1, 0), guiKeys, "last pending direct key filter request (null) wins");

        MouseManager mouse = new MouseManager(1);
        trace.clear();
        MouseCallBackListener first = new MouseCallBackListener(ActionType.GAME_INPUT);
        first.setButtonConsumer((a, v) -> {
            trace.add("first:" + v);
            if (v == 1) { mouse.setListenFilter(ActionType.GUI); }
            if (v == 0) {
                mouse.setListenFilter(ActionType.WINDOW);
                mouse.disableListenFilter();
            }
        });
        MouseCallBackListener second = new MouseCallBackListener(ActionType.GAME_INPUT);
        second.setButtonConsumer((a, v) -> trace.add("second:" + v));
        mouse.registerListener(first);
        mouse.registerListener(second);
        mouse.broadcastButtons(Input.Buttons.LEFT, InputAction.MOVE_UP, 1);
        equal(List.of("first:1", "second:1", "first:0", "second:0"), trace,
            "direct mouse filter requests wait for button fan-out and nested release fan-out");
        mouse.broadcastButtons(Input.Buttons.LEFT, null, 0);
        MouseCacheListener gui = mouseCache(ActionType.GUI, 4);
        mouse.registerListener(gui);
        mouse.broadcastButtons(Input.Buttons.RIGHT, InputAction.GUI_RIGHT_CLICK, 1);
        mouse.broadcastButtons(Input.Buttons.RIGHT, null, 0);
        equal(2, drain(gui).size(), "last pending direct mouse filter request (null) wins");
    }

    private static void directDisableTransitions() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        KeyManager keys = new KeyManager(1, map);
        KeyCacheListener keyCache = keyCache(ActionType.GUI, 8);
        List<KeyEvent> immediate = new ArrayList<>();
        keys.registerListener(key(ActionType.GUI, (a, v) -> immediate.add(new KeyEvent(a, v))));
        keys.registerListener(keyCache);
        keys.setListenFilter(ActionType.GUI);
        keys.broadcast(Input.Keys.ENTER, 1);
        keys.disableListenFilter();
        List<KeyEvent> pair = List.of(new KeyEvent(InputAction.GUI_ENTER, 1), new KeyEvent(InputAction.GUI_ENTER, 0));
        equal(pair, immediate, "direct key disable releases held action under outgoing filter");
        keys.disableListenFilter();
        keys.broadcast(Input.Keys.ENTER, 1);
        keys.broadcast(Input.Keys.ENTER, 2);
        keys.broadcast(Input.Keys.ENTER, 0);
        keys.broadcast(Input.Keys.ENTER, 0);
        equal(pair, drain(keyCache), "direct key disable retains queued pair and blocks repeat/late ups");
        keys.broadcast(Input.Keys.ENTER, 1);
        keys.broadcast(Input.Keys.ENTER, 0);
        equal(pair, drain(keyCache), "direct key disable permits fresh press");

        MouseManager mouse = new MouseManager(1);
        MouseCacheListener mouseCache = mouseCache(ActionType.GUI, 8);
        List<Integer> buttons = new ArrayList<>();
        MouseCallBackListener callback = new MouseCallBackListener(ActionType.GUI);
        callback.setButtonConsumer((a, v) -> buttons.add(v));
        mouse.registerListener(callback);
        mouse.registerListener(mouseCache);
        mouse.setListenFilter(ActionType.GUI);
        mouse.broadcastPos(7, 8);
        mouse.broadcastButtons(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK, 1);
        mouse.disableListenFilter();
        equal(List.of(1, 0), buttons, "direct mouse disable releases held action under outgoing filter");
        mouse.disableListenFilter();
        mouse.broadcastButtons(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK, 1);
        mouse.broadcastButtons(Input.Buttons.LEFT, null, 0);
        mouse.broadcastButtons(Input.Buttons.LEFT, null, 0);
        List<MouseEvent> mousePair = List.of(button(InputAction.GUI_LEFT_CLICK, 1, 7, 8), button(InputAction.GUI_LEFT_CLICK, 0, 7, 8));
        equal(mousePair, drain(mouseCache), "direct mouse disable retains queued pair and blocks repeats/late ups");
        mouse.broadcastButtons(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK, 1);
        mouse.broadcastButtons(Input.Buttons.LEFT, null, 0);
        equal(mousePair, drain(mouseCache), "direct mouse disable permits fresh press");
    }

    private static void facadeCallbacks() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        input.setFilter(ActionType.GAME_INPUT);
        List<String> trace = new ArrayList<>();
        input.regKeyListener(key(ActionType.GAME_INPUT, (a, v) -> {
            trace.add("key-first:" + v);
            if (v == 1) {
                input.setFilter(ActionType.GUI);
                input.disableFilter();
                input.setFilter(ActionType.WINDOW);
            } else {
                input.setFilter(ActionType.WINDOW);
                input.setFilter(ActionType.GUI);
            }
        }));
        input.regKeyListener(key(ActionType.GAME_INPUT, (a, v) -> trace.add("key-second:" + v)));
        MouseCallBackListener mouse = new MouseCallBackListener(ActionType.GAME_INPUT);
        mouse.setButtonConsumer((a, v) -> {
            trace.add("mouse:" + v);
            if (v == 0) {
                input.setFilter(ActionType.GAME_INPUT);
                input.disableFilter();
            }
        });
        input.regMousePosListener(mouse);
        input.touchDown(1, 2, 0, Input.Buttons.RIGHT);
        trace.clear();
        input.keyDown(Input.Keys.W);
        equal(List.of("key-first:1", "key-second:1", "mouse:0", "key-first:0", "key-second:0"), trace,
            "facade coalesces normal callback requests, finishes both outgoing managers, then handles nested requests");
        KeyCacheListener guiKeys = keyCache(ActionType.GUI, 8);
        MouseCacheListener guiMouse = mouseCache(ActionType.GUI, 8);
        input.regKeyListener(guiKeys);
        input.regMousePosListener(guiMouse);
        input.keyDown(Input.Keys.ENTER);
        input.touchDown(3, 4, 0, Input.Buttons.LEFT);
        input.setFilter(ActionType.GUI);
        input.setFilter(ActionType.GUI);
        equal(List.of(new KeyEvent(InputAction.GUI_ENTER, 1)), drain(guiKeys), "final nested target is GUI for keys; repeated target no-op");
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 3, 4)), drain(guiMouse), "final nested target is GUI for mouse; repeated target no-op");
        input.keyUp(Input.Keys.W);
        input.touchUp(5, 6, 0, Input.Buttons.RIGHT);
        input.keyDown(Input.Keys.W);
        input.touchDown(7, 8, 0, Input.Buttons.RIGHT);
        equal(5, trace.size(), "both managers reject game input after final nested GUI request");
        input.disableFilter();
        equal(List.of(new KeyEvent(InputAction.GUI_ENTER, 0)), drain(guiKeys), "facade disable releases held GUI key");
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 0, 7, 8)), drain(guiMouse), "facade disable releases held GUI mouse with latest coordinates");
    }

    private static void facadeReleaseRequests() {
        for (ActionType target : new ActionType[]{null, ActionType.GUI, ActionType.WINDOW}) {
            InputMap map = new InputMap();
            map.setKey(Input.Keys.W, InputAction.MOVE_UP);
            map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
            map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
            map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
            InputManager input = new InputManager(map);
            input.setFilter(ActionType.GAME_INPUT);
            input.regKeyListener(key(ActionType.GAME_INPUT, (a, v) -> {
                if (v == 0) { input.setFilter(target); }
            }));
            MouseCallBackListener callback = new MouseCallBackListener(ActionType.GAME_INPUT);
            callback.setButtonConsumer((a, v) -> {
                if (v == 0) { input.setFilter(ActionType.WINDOW); }
            });
            input.regMousePosListener(callback);
            KeyCacheListener gameKeys = keyCache(ActionType.GAME_INPUT, 8);
            KeyCacheListener guiKeys = keyCache(ActionType.GUI, 8);
            MouseCacheListener gameMouse = mouseCache(ActionType.GAME_INPUT, 8);
            MouseCacheListener guiMouse = mouseCache(ActionType.GUI, 8);
            input.regKeyListener(gameKeys);
            input.regKeyListener(guiKeys);
            input.regMousePosListener(gameMouse);
            input.regMousePosListener(guiMouse);
            input.keyDown(Input.Keys.W);
            input.touchDown(1, 2, 0, Input.Buttons.RIGHT);
            input.setFilter(ActionType.GUI);
            equal(List.of(new KeyEvent(InputAction.MOVE_UP, 1), new KeyEvent(InputAction.MOVE_UP, 0)), drain(gameKeys),
                "nested facade requests preserve outgoing keyboard pair; final target=" + target);
            equal(List.of(button(InputAction.MOVE_RIGHT, 1, 1, 2), button(InputAction.MOVE_RIGHT, 0, 1, 2)), drain(gameMouse),
                "nested facade requests preserve outgoing mouse pair; final target=" + target);
            input.keyUp(Input.Keys.W);
            input.touchUp(1, 2, 0, Input.Buttons.RIGHT);
            input.keyDown(Input.Keys.W);
            input.keyDown(Input.Keys.ENTER);
            input.touchDown(3, 4, 0, Input.Buttons.RIGHT);
            input.touchDown(5, 6, 0, Input.Buttons.LEFT);
            equal(target == null, gameKeys.hasInputs(), "last nested target governs game keyboard: " + target);
            equal(target == null, gameMouse.hasInputs(), "last nested target governs game mouse: " + target);
            equal(target == null || target == ActionType.GUI, guiKeys.hasInputs(), "last nested target governs GUI keyboard: " + target);
            equal(target == null || target == ActionType.GUI, guiMouse.hasInputs(), "last nested target governs GUI mouse: " + target);
        }
    }

    private static void mouseCallbackBoundary() {
        for (boolean direct : new boolean[]{false, true}) {
            InputMap map = new InputMap();
            map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
            map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
            InputManager input = new InputManager(map);
            input.setFilter(ActionType.GAME_INPUT);
            List<String> trace = new ArrayList<>();
            MouseCallBackListener first = new MouseCallBackListener(ActionType.GAME_INPUT);
            first.setPosConsumer((x, y) -> {
                trace.add("first-pos");
                if (direct) { input.getMouseManager().setListenFilter(ActionType.GUI); }
                else { input.setFilter(ActionType.GUI); }
            });
            first.setButtonConsumer((a, v) -> trace.add("first-button:" + v));
            MouseCallBackListener second = new MouseCallBackListener(ActionType.GAME_INPUT);
            second.setPosConsumer((x, y) -> trace.add("second-pos"));
            second.setButtonConsumer((a, v) -> trace.add("second-button:" + v));
            MouseCacheListener outgoing = mouseCache(ActionType.GAME_INPUT, 4);
            MouseCacheListener incoming = mouseCache(ActionType.GUI, 4);
            input.regMousePosListener(first);
            input.regMousePosListener(second);
            input.regMousePosListener(outgoing);
            input.regMousePosListener(incoming);
            check(!input.touchDown(10, 20, 0, Input.Buttons.RIGHT), "boundary observer flag " + direct);
            equal(List.of("first-pos", "second-pos", "first-button:1", "second-button:1", "first-button:0", "second-button:0"),
                trace, "position callback cannot split native position/button event between filters, direct=" + direct);
            equal(List.of(button(InputAction.MOVE_RIGHT, 1, 10, 20), button(InputAction.MOVE_RIGHT, 0, 10, 20)),
                drain(outgoing), "mouse boundary queues full outgoing press/reset " + direct);
            check(!incoming.hasInputs(), "incoming filter receives none of same native event " + direct);
            input.touchDown(30, 40, 0, Input.Buttons.LEFT);
            input.scrolled(0, -1);
            input.touchUp(31, 41, 0, Input.Buttons.LEFT);
            equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 30, 40), scroll(1, 30, 40),
                button(InputAction.GUI_LEFT_CLICK, 0, 31, 41)), drain(incoming), "next event uses new filter and preserves scroll order " + direct);
        }
    }

    private static void scrollCallbackBoundary() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        InputManager input = new InputManager(map);
        input.setFilter(ActionType.GAME_INPUT);
        List<String> trace = new ArrayList<>();
        MouseCallBackListener first = new MouseCallBackListener(ActionType.GAME_INPUT);
        first.setScrollConsumer(v -> {
            trace.add("first-scroll:" + v);
            input.setFilter(ActionType.GUI);
        });
        first.setButtonConsumer((a, v) -> trace.add("first-button:" + v));
        MouseCallBackListener second = new MouseCallBackListener(ActionType.GAME_INPUT);
        second.setScrollConsumer(v -> trace.add("second-scroll:" + v));
        second.setButtonConsumer((a, v) -> trace.add("second-button:" + v));
        MouseCacheListener cache = mouseCache(ActionType.GAME_INPUT, 8);
        input.regMousePosListener(first);
        input.regMousePosListener(second);
        input.regMousePosListener(cache);
        input.touchDown(4, 5, 0, Input.Buttons.RIGHT);
        trace.clear();
        input.scrolled(0, 1);
        equal(List.of("first-scroll:0", "second-scroll:0", "first-button:0", "second-button:0"), trace,
            "scroll callback filter change waits for complete outgoing scroll fan-out");
        equal(List.of(button(InputAction.MOVE_RIGHT, 1, 4, 5), scroll(0, 4, 5), button(InputAction.MOVE_RIGHT, 0, 4, 5)),
            drain(cache), "scroll-triggered transition appends reset behind pending button and scroll snapshots");
    }

    private static void exceptionGuards() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        InputManager input = new InputManager(map);
        boolean[] fail = {true};
        input.regKeyListener(key(ActionType.GAME_INPUT, (a, v) -> {
            if (fail[0]) { fail[0] = false; throw new IllegalStateException("key callback"); }
        }));
        throwsType(IllegalStateException.class, () -> input.keyDown(Input.Keys.W), "callback exception propagates");
        KeyCacheListener gui = keyCache(ActionType.GUI, 4);
        input.regKeyListener(gui);
        input.setFilter(ActionType.GUI);
        input.keyUp(Input.Keys.W);
        map.setKey(Input.Keys.W, InputAction.GUI_ENTER);
        input.keyDown(Input.Keys.W);
        equal(List.of(new KeyEvent(InputAction.GUI_ENTER, 1)), drain(gui), "key/facade guards reset in finally");

        MouseCallBackListener mouse = new MouseCallBackListener(ActionType.GUI);
        mouse.setPosConsumer((x, y) -> { throw new IllegalStateException("position callback"); });
        input.regMousePosListener(mouse);
        throwsType(IllegalStateException.class, () -> input.mouseMoved(1, 2), "position exception propagates");
        input.unRegMousePosListener(mouse);
        MouseCacheListener game = mouseCache(ActionType.GAME_INPUT, 4);
        input.regMousePosListener(game);
        input.setFilter(ActionType.GAME_INPUT);
        map.setButton(Input.Buttons.LEFT, InputAction.MOVE_UP);
        input.touchDown(3, 4, 0, Input.Buttons.LEFT);
        equal(List.of(button(InputAction.MOVE_UP, 1, 3, 4)), drain(game), "mouse/facade guards reset in finally");
    }
}
