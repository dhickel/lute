package io.mindspice.lute.engine.input;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InputSystemTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    private record Event(InputAction action, int state) { }

    public static void main(String[] args) {
        run("adapter keys", InputSystemTest::adapterKeys);
        run("adapter mouse", InputSystemTest::adapterMouse);
        run("cached mouse", MouseCacheListenerTest::run);
        run("filters and disabled listeners", InputSystemTest::filters);
        run("filter transitions", FilterTransitionTest::run);
        run("key registrations", InputSystemTest::keyRegistrations);
        run("mouse registrations", InputSystemTest::mouseRegistrations);
        run("queue", InputSystemTest::queue);
        run("input map", InputSystemTest::inputMap);
        run("device mapping isolation", InputSystemTest::deviceMappingIsolation);
        run("binding and propagation", InputSystemTest::bindingAndPropagation);
        System.out.println("InputSystemTest: " + checks + " checks, " + failures.size() + " failed groups");
        if (!failures.isEmpty()) {
            throw new AssertionError(String.join("\n", failures));
        }
    }

    private static void run(String name, Runnable test) {
        try {
            test.run();
        } catch (AssertionError | RuntimeException failure) {
            failures.add(name + ": " + failure);
            failure.printStackTrace(System.err);
        }
    }

    static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    static void equal(Object expected, Object actual, String message) {
        check(Objects.equals(expected, actual), message + ": expected " + expected + ", got " + actual);
    }

    static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < 0.0001f, message + ": expected " + expected + ", got " + actual);
    }

    static void throwsType(Class<? extends Throwable> type, Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (Throwable failure) {
            if (type.isInstance(failure)) { return; }
            throw new AssertionError(message + ": unexpected exception", failure);
        }
        throw new AssertionError(message + ": did not throw " + type.getSimpleName());
    }

    private static KeyCallBackListener key(ActionType type, List<Event> events, InputAction... actions) {
        return new KeyCallBackListener(type, List.of(actions), 4,
            (action, state) -> events.add(new Event(action, state)));
    }

    private static void adapterKeys() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.S, InputAction.MOVE_DOWN);
        map.setKey(Input.Keys.UNKNOWN, InputAction.MOVE_LEFT);
        InputManager input = new InputManager(map);
        List<Event> immediate = new ArrayList<>();
        KeyCallBackListener callback = key(ActionType.GAME_INPUT, immediate, InputAction.MOVE_UP);
        KeyCacheListener cached = new KeyCacheListener(ActionType.GAME_INPUT, 4, List.of(InputAction.MOVE_UP));
        input.regKeyListener(callback);
        input.regKeyListener(cached);
        for (int code : new int[]{-1, Integer.MIN_VALUE, Input.Keys.MAX_KEYCODE + 1,
            Integer.MAX_VALUE, Input.Keys.Q, Input.Keys.UNKNOWN}) {
            check(!input.keyDown(code), "ignored key down propagates " + code);
            check(!input.keyUp(code), "ignored key up propagates " + code);
        }
        check(!input.keyTyped('w'), "keyTyped propagates");
        check(!input.keyTyped('\n'), "typed control character propagates");
        equal(List.of(), immediate, "invalid/unmapped/UNKNOWN/typed produce no events");
        check(!cached.hasInputs(), "ignored keys do not reach cache");
        check(!input.keyDown(Input.Keys.W), "key down propagates");
        equal(List.of(new Event(InputAction.MOVE_UP, 1)), immediate, "callback is immediate, down is 1");
        check(cached.hasInputs(), "cache queues down");
        check(!callback.getQueue().isFull() && callback.getQueue().isEmpty(), "callback does not cache events");
        check(!input.keyUp(Input.Keys.W), "key up propagates");
        input.keyDown(Input.Keys.S);
        input.keyUp(Input.Keys.S);
        List<Event> expected = List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.MOVE_UP, 0));
        equal(expected, immediate, "up is 0 and action whitelist excludes S");
        List<Event> drained = new ArrayList<>();
        cached.consume((action, state) -> drained.add(new Event(action, state)));
        equal(expected, drained, "cached delivery preserves down/up FIFO");
        check(!cached.hasInputs(), "cache drained");
        cached.consume((action, state) -> { throw new AssertionError("empty cache callback"); });
        callback.setListening(false);
        cached.setListening(false);
        callback.offer(InputAction.MOVE_UP, 1);
        cached.offer(InputAction.MOVE_UP, 1);
        input.keyDown(Input.Keys.W);
        equal(expected, immediate, "disabled callback ignores direct and managed offers");
        check(!cached.hasInputs(), "disabled cache ignores direct and managed offers");
        equal(ActionType.GAME_INPUT, callback.getListeningFor(), "callback category");
        equal(ActionType.GAME_INPUT, cached.getListeningFor(), "cache category");
    }

    private static void adapterMouse() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.GUI_RIGHT_CLICK);
        InputManager input = new InputManager(map);
        MouseCallBackListener mouse = new MouseCallBackListener(ActionType.GUI);
        List<String> events = new ArrayList<>();
        mouse.setPosConsumer((x, y) -> events.add("pos:" + x + "," + y));
        mouse.setButtonConsumer((action, state) -> events.add(action + ":" + state));
        mouse.setScrollConsumer(value -> events.add("scroll:" + value));
        input.regMousePosListener(mouse);
        check(!input.mouseMoved(10, 20), "move propagates");
        check(!input.touchDragged(11, 21, 0), "primary drag propagates");
        check(!input.touchDown(12, 22, 0, Input.Buttons.LEFT), "primary down propagates");
        check(!input.touchUp(13, 23, 0, Input.Buttons.LEFT), "primary up propagates");
        check(!input.touchDown(13, 23, 0, Input.Buttons.RIGHT), "right down propagates");
        check(!input.touchCancelled(14, 24, 0, Input.Buttons.RIGHT), "cancel propagates");
        equal(List.of("pos:10.0,20.0", "pos:11.0,21.0", "pos:12.0,22.0", "GUI_LEFT_CLICK:1",
            "pos:13.0,23.0", "GUI_LEFT_CLICK:0", "pos:13.0,23.0", "GUI_RIGHT_CLICK:1",
            "pos:14.0,24.0", "GUI_RIGHT_CLICK:0"),
            events, "mouse coordinates precede button events, cancel is release");
        near(14, mouse.getPos().x, "stored mouse x");
        near(24, mouse.getPos().y, "stored mouse y");
        mouse.consume(pos -> check(pos.equals(new Vector2(14, 24)), "consume exposes latest position"));
        int before = events.size();
        check(!input.touchDown(90, 90, 1, Input.Buttons.LEFT), "secondary down propagates");
        check(!input.touchUp(90, 90, 1, Input.Buttons.LEFT), "secondary up propagates");
        check(!input.touchCancelled(90, 90, 1, Input.Buttons.RIGHT), "secondary cancel propagates");
        check(!input.touchDragged(90, 90, 1), "secondary drag propagates");
        equal(before, events.size(), "secondary pointer has no events");
        near(14, mouse.getPos().x, "secondary pointer does not change position");
        check(!input.scrolled(0, -2), "up scroll propagates");
        check(!input.scrolled(0, 3), "down scroll propagates");
        check(!input.scrolled(4, 0), "horizontal scroll propagates");
        check(!input.scrolled(0, 0), "zero scroll propagates");
        equal(List.of("scroll:1", "scroll:0"), events.subList(before, events.size()), "Gdx scroll signs and horizontal ignored");
        events.clear();
        input.touchDown(15, 25, 0, -1);
        input.touchUp(16, 26, 0, Input.Keys.MAX_KEYCODE + 1);
        input.touchDown(17, 27, 0, Input.Buttons.MIDDLE);
        equal(List.of("pos:15.0,25.0", "pos:16.0,26.0", "pos:17.0,27.0"), events,
            "invalid/unmapped buttons still update primary position without button event");
        MouseCallBackListener noConsumers = new MouseCallBackListener(ActionType.GUI);
        noConsumers.offerPos(3, 4);
        noConsumers.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        noConsumers.offerScroll(1);
        near(3, noConsumers.getPos().x, "optional consumers are safe");
        mouse.setListening(false);
        before = events.size();
        mouse.offerPos(99, 99);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        mouse.offerScroll(1);
        input.mouseMoved(99, 99);
        input.touchDown(99, 99, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        equal(before, events.size(), "disabled mouse ignores direct and managed offers");
        near(17, mouse.getPos().x, "disabled listener retains position");
    }

    private static void filters() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        InputManager input = new InputManager(map);
        List<Event> gameKeys = new ArrayList<>();
        List<Event> guiKeys = new ArrayList<>();
        input.regKeyListener(key(ActionType.GAME_INPUT, gameKeys, InputAction.MOVE_UP, InputAction.GUI_ENTER));
        input.regKeyListener(key(ActionType.GUI, guiKeys, InputAction.MOVE_UP, InputAction.GUI_ENTER));
        List<String> gameMouse = new ArrayList<>();
        List<String> guiMouse = new ArrayList<>();
        MouseCallBackListener game = filteredMouse(ActionType.GAME_INPUT, gameMouse);
        MouseCallBackListener gui = filteredMouse(ActionType.GUI, guiMouse);
        input.regMousePosListener(game);
        input.regMousePosListener(gui);
        for (ActionType filter : new ActionType[]{null, ActionType.GUI, ActionType.GAME_INPUT, ActionType.WINDOW}) {
            gameKeys.clear(); guiKeys.clear(); gameMouse.clear(); guiMouse.clear();
            if (filter == null) { input.disableFilter(); } else { input.setFilter(filter); }
            input.keyDown(Input.Keys.W);
            input.keyDown(Input.Keys.ENTER);
            input.touchDown(1, 2, 0, Input.Buttons.LEFT);
            input.touchDown(3, 4, 0, Input.Buttons.RIGHT);
            input.scrolled(0, -1);
            boolean allowGame = filter == null || filter == ActionType.GAME_INPUT;
            boolean allowGui = filter == null || filter == ActionType.GUI;
            equal(allowGame ? List.of(new Event(InputAction.MOVE_UP, 1)) : List.of(), gameKeys, "game key category/filter " + filter);
            equal(allowGui ? List.of(new Event(InputAction.GUI_ENTER, 1)) : List.of(), guiKeys, "GUI key category/filter " + filter);
            equal(allowGame ? List.of("pos", "pos", "MOVE_RIGHT:1", "scroll:1") : List.of(), gameMouse,
                "game mouse category/filter " + filter);
            equal(allowGui ? List.of("pos", "GUI_LEFT_CLICK:1", "pos", "scroll:1") : List.of(), guiMouse,
                "GUI mouse category/filter " + filter);
            input.keyUp(Input.Keys.W);
            input.keyUp(Input.Keys.ENTER);
            input.touchUp(3, 4, 0, Input.Buttons.LEFT);
            input.touchUp(3, 4, 0, Input.Buttons.RIGHT);
        }
        gameKeys.clear(); guiKeys.clear(); gameMouse.clear(); guiMouse.clear();
        input.disableFilter();
        game.setListening(false);
        gui.setListening(false);
        input.touchDown(1, 2, 0, Input.Buttons.LEFT);
        input.scrolled(0, 1);
        equal(List.of(), gameMouse, "disabled game mouse");
        equal(List.of(), guiMouse, "disabled GUI mouse");
        input.keyDown(Input.Keys.W);
        equal(List.of(new Event(InputAction.MOVE_UP, 1)), gameKeys, "disableFilter restores delivery");
        input.getKeyManager().setListenFilter(ActionType.GUI);
        input.keyDown(Input.Keys.W);
        equal(List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.MOVE_UP, 0)), gameKeys,
            "key manager's own filter releases held key and blocks duplicate down");
        input.keyUp(Input.Keys.W);
        input.getKeyManager().disableListenFilter();
        input.keyDown(Input.Keys.W);
        equal(3, gameKeys.size(), "key manager filter disabled");
        game.setListening(true);
        input.getMouseManager().setListenFilter(ActionType.GUI);
        input.mouseMoved(0, 0);
        equal(List.of(), gameMouse, "mouse manager's own filter");
        input.getMouseManager().disableListenFilter();
        input.mouseMoved(0, 0);
        equal(List.of("pos"), gameMouse, "mouse manager filter disabled");
    }

    private static MouseCallBackListener filteredMouse(ActionType type, List<String> events) {
        MouseCallBackListener mouse = new MouseCallBackListener(type);
        mouse.setPosConsumer((x, y) -> events.add("pos"));
        mouse.setScrollConsumer(value -> events.add("scroll:" + value));
        mouse.setButtonConsumer((action, value) -> events.add(action + ":" + value));
        return mouse;
    }

    private static final class CustomKey implements KeyListener {
        private boolean listening = true;
        private final List<Event> events = new ArrayList<>();
        public boolean isListening() { return listening; }
        public boolean isListenerFor(ActionType type) { return type == ActionType.GAME_INPUT; }
        public ActionType getListeningFor() { return ActionType.GAME_INPUT; }
        public void offer(InputAction action, int state) { events.add(new Event(action, state)); }
        public void setListening(boolean value) { listening = value; }
        @Override public boolean equals(Object other) { return other instanceof CustomKey; }
        @Override public int hashCode() { return 1; }
    }

    private static void keyRegistrations() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        InputManager input = new InputManager(map);
        List<CustomKey> listeners = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            CustomKey listener = new CustomKey();
            listeners.add(listener);
            input.regKeyListener(listener);
            input.regKeyListener(listener);
        }
        input.keyDown(Input.Keys.W);
        for (CustomKey listener : listeners) {
            equal(List.of(new Event(InputAction.MOVE_UP, 1)), listener.events, "custom key registered once by identity beyond capacity 20");
            listener.events.clear();
        }
        for (int index : new int[]{0, 12, 24}) { input.unRegKeyListener(listeners.get(index)); }
        input.unRegKeyListener(new CustomKey());
        input.unRegKeyListener(listeners.get(12));
        input.keyUp(Input.Keys.W);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 0 || i == 12 || i == 24 ? List.of() : List.of(new Event(InputAction.MOVE_UP, 0)),
                listeners.get(i).events, "key first/middle/last/absent removal " + i);
            listeners.get(i).events.clear();
        }
        for (int index : new int[]{0, 12, 24}) { input.regKeyListener(listeners.get(index)); }
        listeners.get(5).setListening(false);
        input.keyDown(Input.Keys.W);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 5 ? 0 : 1, listeners.get(i).events.size(), "key re-registration and disabled custom listener " + i);
        }
    }

    private static void mouseRegistrations() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        List<MouseCallBackListener> listeners = new ArrayList<>();
        List<List<String>> events = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            List<String> recorded = new ArrayList<>();
            events.add(recorded);
            MouseCallBackListener listener = new MouseCallBackListener(ActionType.GUI) {
                @Override public boolean equals(Object other) { return other instanceof MouseCallBackListener; }
                @Override public int hashCode() { return 1; }
            };
            listener.setPosConsumer((x, y) -> recorded.add("pos"));
            listener.setButtonConsumer((action, state) -> recorded.add("button:" + state));
            listener.setScrollConsumer(state -> recorded.add("scroll:" + state));
            listeners.add(listener);
            input.regMousePosListener(listener);
            input.regMousePosListener(listener);
        }
        input.touchDown(1, 2, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        for (List<String> recorded : events) {
            equal(List.of("pos", "button:1", "scroll:1"), recorded, "mouse registered once by identity beyond capacity 20");
            recorded.clear();
        }
        for (int index : new int[]{0, 12, 24}) { input.unRegMousePosListener(listeners.get(index)); }
        input.unRegMousePosListener(new MouseCallBackListener(ActionType.GUI));
        input.unRegMousePosListener(listeners.get(12));
        input.touchUp(1, 2, 0, Input.Buttons.LEFT);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 0 || i == 12 || i == 24 ? List.of() : List.of("pos", "button:0"), events.get(i),
                "mouse first/middle/last/absent removal " + i);
            events.get(i).clear();
        }
        for (int index : new int[]{0, 12, 24}) { input.regMousePosListener(listeners.get(index)); }
        input.mouseMoved(3, 4);
        for (List<String> recorded : events) { equal(List.of("pos"), recorded, "mouse re-registration"); }
    }

    private static List<Event> drain(CircularKeyQueue queue) {
        List<Event> events = new ArrayList<>();
        queue.consume((action, state) -> events.add(new Event(action, state)));
        return events;
    }

    private static void queue() {
        throwsType(IllegalArgumentException.class, () -> new CircularKeyQueue(0), "zero queue capacity");
        throwsType(IllegalArgumentException.class, () -> new CircularKeyQueue(-1), "negative queue capacity");
        CircularKeyQueue one = new CircularKeyQueue(1);
        check(one.isEmpty() && !one.isFull(), "capacity one initially empty");
        one.poll((action, state) -> { throw new AssertionError("empty poll callback"); });
        one.peek((action, state) -> { throw new AssertionError("empty peek callback"); });
        one.add(InputAction.MOVE_UP, 1);
        check(one.isFull(), "capacity one full");
        for (int i = 0; i < 2; i++) {
            one.peek((action, state) -> equal(new Event(InputAction.MOVE_UP, 1), new Event(action, state), "peek does not remove"));
        }
        one.add(InputAction.MOVE_DOWN, 0);
        equal(List.of(new Event(InputAction.MOVE_DOWN, 0)), drain(one), "capacity one overwrites oldest");
        check(one.isEmpty(), "capacity one drained");
        one.setRejectIfFull(true);
        one.add(InputAction.MOVE_LEFT, 1);
        one.add(InputAction.MOVE_RIGHT, 0);
        equal(List.of(new Event(InputAction.MOVE_LEFT, 1)), drain(one), "capacity one rejection preserves oldest");
        one.setRejectIfFull(false);
        one.add(InputAction.MOVE_UP, 1);
        one.add(InputAction.MOVE_DOWN, 0);
        equal(List.of(new Event(InputAction.MOVE_DOWN, 0)), drain(one), "overwrite can be re-enabled");
        CircularKeyQueue queue = new CircularKeyQueue(3);
        for (int cycle = 0; cycle < 5; cycle++) {
            queue.add(InputAction.MOVE_UP, cycle);
            queue.add(InputAction.MOVE_DOWN, cycle + 10);
            int expectedState = cycle;
            queue.poll((action, state) -> equal(new Event(InputAction.MOVE_UP, expectedState),
                new Event(action, state), "wrapped poll action and state"));
            queue.add(InputAction.MOVE_LEFT, cycle + 20);
            queue.add(InputAction.MOVE_RIGHT, cycle + 30);
            check(queue.isFull(), "wrapped queue full");
            equal(List.of(new Event(InputAction.MOVE_DOWN, cycle + 10), new Event(InputAction.MOVE_LEFT, cycle + 20),
                new Event(InputAction.MOVE_RIGHT, cycle + 30)), drain(queue), "wraparound FIFO cycle " + cycle);
        }
        queue.add(InputAction.MOVE_UP, 1);
        queue.add(InputAction.MOVE_DOWN, 2);
        queue.add(InputAction.MOVE_LEFT, 3);
        queue.add(InputAction.MOVE_RIGHT, 4);
        equal(List.of(new Event(InputAction.MOVE_DOWN, 2), new Event(InputAction.MOVE_LEFT, 3),
            new Event(InputAction.MOVE_RIGHT, 4)), drain(queue), "full queue overwrites only oldest");
        queue.setRejectIfFull(true);
        queue.add(InputAction.MOVE_UP, 1);
        queue.add(InputAction.MOVE_DOWN, 2);
        queue.add(InputAction.MOVE_LEFT, 3);
        queue.add(InputAction.MOVE_RIGHT, 4);
        equal(List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.MOVE_DOWN, 2),
            new Event(InputAction.MOVE_LEFT, 3)), drain(queue), "full queue rejects newest");
        one.add(InputAction.MOVE_UP, 1);
        one.poll((action, state) -> {
            check(one.isEmpty(), "poll removes before callback reentry");
            one.poll((nested, value) -> { throw new AssertionError("reentrant poll saw removed event"); });
            one.add(InputAction.MOVE_DOWN, 0);
        });
        equal(List.of(new Event(InputAction.MOVE_DOWN, 0)), drain(one), "reentrant add survives capacity one reject mode");
        RuntimeException sentinel = new RuntimeException("expected callback failure");
        queue.add(InputAction.MOVE_UP, 1);
        queue.add(InputAction.MOVE_DOWN, 0);
        try {
            queue.poll((action, state) -> { throw sentinel; });
            throw new AssertionError("poll must propagate callback failure");
        } catch (RuntimeException failure) {
            check(failure == sentinel, "original callback exception propagates");
        }
        equal(List.of(new Event(InputAction.MOVE_DOWN, 0)), drain(queue), "throwing callback event already removed, successor retained");
        queue.add(InputAction.MOVE_LEFT, 1);
        throwsType(RuntimeException.class, () -> queue.consume((action, state) -> { throw sentinel; }), "consume propagates callback failure");
        check(queue.isEmpty(), "consume removes throwing event");
        List<Event> reentrant = new ArrayList<>();
        one.add(InputAction.MOVE_UP, 1);
        one.consume((action, state) -> {
            reentrant.add(new Event(action, state));
            if (action == InputAction.MOVE_UP) { one.add(InputAction.MOVE_DOWN, 0); }
        });
        equal(List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.MOVE_DOWN, 0)), reentrant,
            "consume drains reentrant additions in FIFO order");
    }

    private static void inputMap() {
        InputMap map = new InputMap();
        equal(null, map.getKey(Input.Keys.MAX_KEYCODE), "MAX_KEYCODE initially unmapped");
        map.setKey(Input.Keys.MAX_KEYCODE, InputAction.MOVE_RIGHT);
        equal(InputAction.MOVE_RIGHT, map.getKey(Input.Keys.MAX_KEYCODE), "MAX_KEYCODE inclusive");
        check(map.containsKey(Input.Keys.MAX_KEYCODE), "contains MAX_KEYCODE");
        for (int code : new int[]{-1, Integer.MIN_VALUE, Input.Keys.MAX_KEYCODE + 1, Integer.MAX_VALUE}) {
            equal(null, map.getKey(code), "invalid key lookup " + code);
            check(!map.containsKey(code), "invalid key contains " + code);
            throwsType(IllegalArgumentException.class, () -> map.setKey(code, InputAction.MOVE_UP), "invalid key set " + code);
            map.removeKey(code);
        }
        equal(InputAction.MOVE_RIGHT, map.getKey(Input.Keys.MAX_KEYCODE), "invalid operations preserve valid key");
        map.removeKey(Input.Keys.MAX_KEYCODE);
        check(!map.containsKey(Input.Keys.MAX_KEYCODE), "remove MAX_KEYCODE");
        map.removeKey(Input.Keys.MAX_KEYCODE);
        for (int code = Input.Buttons.LEFT; code <= Input.Buttons.FORWARD; code++) {
            equal(null, map.getButton(code), "button initially unmapped " + code);
            map.setKey(code, InputAction.MOVE_UP);
            map.setButton(code, InputAction.GUI_LEFT_CLICK);
            equal(InputAction.MOVE_UP, map.getKey(code), "button set preserves same-code key " + code);
            equal(InputAction.GUI_LEFT_CLICK, map.getButton(code), "button mapping " + code);
            check(map.containsKey(code) && map.containsButton(code), "both device mappings present " + code);
            map.setKey(code, InputAction.MOVE_DOWN);
            equal(InputAction.GUI_LEFT_CLICK, map.getButton(code), "key replacement preserves button " + code);
            map.removeKey(code);
            check(!map.containsKey(code) && map.containsButton(code), "key removal preserves button " + code);
            map.removeKey(code);
            map.setKey(code, InputAction.MOVE_LEFT);
            map.removeButton(code);
            check(map.containsKey(code) && !map.containsButton(code), "button removal preserves key " + code);
            map.removeButton(code);
            map.setButton(code, InputAction.GUI_RIGHT_CLICK);
            map.setKey(code, null);
            check(!map.containsKey(code) && map.containsButton(code), "null key clears only key " + code);
            map.setKey(code, InputAction.MOVE_RIGHT);
            map.setButton(code, null);
            check(map.containsKey(code) && !map.containsButton(code), "null button clears only button " + code);
        }
        map.setButton(Input.Buttons.FORWARD, InputAction.GUI_RIGHT_CLICK);
        for (int code : new int[]{-1, Integer.MIN_VALUE, Input.Buttons.FORWARD + 1, Input.Keys.W,
            Input.Keys.MAX_KEYCODE, Integer.MAX_VALUE}) {
            equal(null, map.getButton(code), "invalid button lookup " + code);
            check(!map.containsButton(code), "invalid button contains " + code);
            throwsType(IllegalArgumentException.class, () -> map.setButton(code, InputAction.MOVE_UP), "invalid button set " + code);
            map.removeButton(code);
        }
        equal(InputAction.GUI_RIGHT_CLICK, map.getButton(Input.Buttons.FORWARD), "invalid operations preserve valid button");
    }

    private static void deviceMappingIsolation() {
        InputMap map = new InputMap();
        InputManager input = new InputManager(map);
        List<Event> keys = new ArrayList<>();
        List<Event> buttons = new ArrayList<>();
        input.regKeyListener(key(ActionType.GAME_INPUT, keys, InputAction.MOVE_UP, InputAction.MOVE_DOWN));
        MouseCallBackListener mouse = new MouseCallBackListener(ActionType.GAME_INPUT);
        mouse.setButtonConsumer((action, state) -> buttons.add(new Event(action, state)));
        input.regMousePosListener(mouse);
        for (int code = Input.Buttons.LEFT; code <= Input.Buttons.FORWARD; code++) {
            map.setKey(code, InputAction.MOVE_UP);
            map.setButton(code, InputAction.MOVE_DOWN);
            keys.clear();
            buttons.clear();
            input.keyDown(code);
            input.keyUp(code);
            equal(code == Input.Keys.UNKNOWN ? List.of() : List.of(new Event(InputAction.MOVE_UP, 1),
                new Event(InputAction.MOVE_UP, 0)), keys, "keyboard uses key namespace " + code);
            equal(List.of(), buttons, "keyboard does not dispatch mouse buttons " + code);
            keys.clear();
            input.touchDown(1, 2, 0, code);
            input.touchUp(1, 2, 0, code);
            input.touchCancelled(1, 2, 0, code);
            equal(List.of(new Event(InputAction.MOVE_DOWN, 1), new Event(InputAction.MOVE_DOWN, 0)), buttons,
                "mouse uses button namespace and ignores late cancel " + code);
            equal(List.of(), keys, "mouse does not dispatch keyboard events " + code);
            buttons.clear();
            map.removeKey(code);
            input.keyDown(code);
            equal(List.of(), keys, "button-only binding cannot trigger a key action " + code);
            input.touchDown(1, 2, 0, code);
            equal(List.of(new Event(InputAction.MOVE_DOWN, 1)), buttons, "button survives removal of same-code key " + code);
            input.touchUp(1, 2, 0, code);
            buttons.clear();
            input.keyUp(code);
            map.setKey(code, InputAction.MOVE_UP);
            map.removeButton(code);
            input.touchDown(1, 2, 0, code);
            input.touchUp(1, 2, 0, code);
            equal(List.of(), buttons, "key-only binding cannot trigger a mouse action " + code);
            input.keyDown(code);
            equal(code == Input.Keys.UNKNOWN ? List.of() : List.of(new Event(InputAction.MOVE_UP, 1)), keys,
                "key survives removal of same-code button " + code);
        }
    }

    private static void bindingAndPropagation() {
        Input previous = Gdx.input;
        InputProcessor[] bound = {null};
        Gdx.input = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[]{Input.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getInputProcessor" -> bound[0];
                case "setInputProcessor" -> { bound[0] = (InputProcessor) args[0]; yield null; }
                case "toString" -> "InputSystemTest.Input";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected Gdx.input call: " + method.getName());
            });
        try {
            InputMap map = new InputMap();
            map.setKey(Input.Keys.W, InputAction.MOVE_UP);
            InputManager input = new InputManager(map);
            InputAdapter unrelated = new InputAdapter();
            Gdx.input.setInputProcessor(unrelated);
            input.bindToWindow();
            check(Gdx.input.getInputProcessor() == input, "bind installs adapter directly");
            input.enableRawInput(false);
            check(Gdx.input.getInputProcessor() == input, "raw false is a no-op");
            throwsType(UnsupportedOperationException.class, () -> input.enableRawInput(true), "raw input unsupported");
            input.dispose();
            equal(null, Gdx.input.getInputProcessor(), "dispose unbinds directly bound adapter");
            input.dispose();
            input.bindToWindow();
            input.cleanup();
            equal(null, Gdx.input.getInputProcessor(), "cleanup unbinds directly bound adapter");
            Gdx.input.setInputProcessor(unrelated);
            input.dispose();
            check(Gdx.input.getInputProcessor() == unrelated, "dispose preserves unrelated direct processor");
            input.cleanup();
            check(Gdx.input.getInputProcessor() == unrelated, "cleanup preserves unrelated direct processor");
            List<String> trace = new ArrayList<>();
            boolean[] stopUpstream = {true};
            InputAdapter upstream = new InputAdapter() {
                @Override public boolean keyDown(int keycode) { trace.add("upstream"); return stopUpstream[0]; }
            };
            InputAdapter downstream = new InputAdapter() {
                @Override public boolean keyDown(int keycode) { trace.add("downstream"); return false; }
            };
            input.regKeyListener(new KeyCallBackListener(ActionType.GAME_INPUT, List.of(InputAction.MOVE_UP), 1,
                (action, state) -> trace.add("adapter")));
            InputMultiplexer mux = new InputMultiplexer(upstream, input, downstream);
            Gdx.input.setInputProcessor(mux);
            check(mux.keyDown(Input.Keys.W), "actual multiplexer returns upstream true");
            equal(List.of("upstream"), trace, "upstream true stops adapter and downstream");
            trace.clear();
            stopUpstream[0] = false;
            check(!mux.keyDown(Input.Keys.W), "adapter does not consume event");
            equal(List.of("upstream", "adapter", "downstream"), trace, "actual multiplexer delivers past adapter");
            input.dispose();
            check(Gdx.input.getInputProcessor() == mux, "dispose preserves top-level multiplexer");
            equal(2, mux.getProcessors().size, "only adapter removed");
            check(mux.getProcessors().get(0) == upstream && mux.getProcessors().get(1) == downstream,
                "dispose preserves unrelated processor identity/order");
            mux.addProcessor(1, input);
            input.cleanup();
            equal(2, mux.getProcessors().size, "cleanup removes adapter from multiplexer");
            check(mux.getProcessors().get(0) == upstream && mux.getProcessors().get(1) == downstream,
                "cleanup preserves unrelated processor identity/order");
            input.cleanup();
            equal(2, mux.getProcessors().size, "repeated cleanup is safe");
            Gdx.input = null;
            input.dispose();
            input.cleanup();
            check(true, "dispose and cleanup safe without Gdx.input");
        } finally {
            Gdx.input = previous;
        }
    }
}
