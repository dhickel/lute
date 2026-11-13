package io.mindspice.lute.engine.input;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.systems.ControllerSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InputSystemTest {
    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    private record Event(InputAction action, int state) { }

    public static void main(String[] args) {
        run("controller movement event", InputSystemTest::controllerMovementEvent);
        run("adapter keys", InputSystemTest::adapterKeys);
        run("adapter mouse", InputSystemTest::adapterMouse);
        run("cached mouse", MouseListenerTest::run);
        run("listener fanout", InputSystemTest::listenerFanout);
        run("held inputs", HeldInputTest::run);
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

    private static List<Event> drain(KeyListener listener) {
        List<Event> events = new ArrayList<>();
        listener.consume((action, state) -> events.add(new Event(action, state)));
        return events;
    }

    private static List<Event> drainButtons(MouseListener listener) {
        List<Event> events = new ArrayList<>();
        listener.consume((type, action, state, x, y) -> events.add(new Event(action, state)));
        return events;
    }

    private static void controllerMovementEvent() {
        var previousApp = Gdx.app;
        try {
            var field = GameCore.class.getDeclaredField("engine");
            field.setAccessible(true);
            var previousEngine = field.get(GameCore.INSTANCE);
            try {
                Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
                    new Class<?>[]{Application.class}, (proxy, method, args) -> null);
                Engine engine = new Engine();
                field.set(GameCore.INSTANCE, engine);
                EventSystem events = new EventSystem();
                engine.addSystem(events);
                engine.addSystem(new ControllerSystem());
                int[] count = {0};
                events.subscribe(io.mindspice.lute.engine.events.Event.Player.Moved.class, event -> count[0]++);
                InputMap map = new InputMap();
                int[] keys = {Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D};
                InputAction[] actions = {InputAction.MOVE_UP, InputAction.MOVE_DOWN,
                    InputAction.MOVE_LEFT, InputAction.MOVE_RIGHT};
                InputManager input = new InputManager(map);
                Move move = new Move(1);
                engine.addEntity(new Entity().add(Controller.forInputManager(input)).add(move));
                for (int i = 0; i < keys.length; i++) {
                    map.setKey(keys[i], actions[i]);
                    input.keyDown(keys[i]);
                    engine.update(0);
                    engine.update(0);
                    equal(i + 1, count[0], "press emits once");
                    input.keyUp(keys[i]);
                    engine.update(0);
                    engine.update(0);
                    equal(i + 1, count[0], "release emits nothing");
                    near(0, move.getDirection().x, "release clears x");
                    near(0, move.getDirection().y, "release clears y");
                }
                input.keyDown(Input.Keys.W);
                engine.update(0);
                input.releaseHeldInputs();
                engine.update(0);
                engine.update(0);
                equal(5, count[0], "synthetic release emits nothing");
                near(0, move.getDirection().y, "synthetic release stops movement");
                engine.removeAllEntities();
                engine.addEntity(new Entity().add(Controller.forInputManager(input)));
                input.keyUp(Input.Keys.W);
                input.keyDown(Input.Keys.W);
                engine.update(0);
                engine.update(0);
                equal(5, count[0], "entity without Move emits nothing");
            } finally {
                field.set(GameCore.INSTANCE, previousEngine);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } finally {
            Gdx.app = previousApp;
        }
    }

    private static void adapterKeys() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.S, InputAction.MOVE_DOWN);
        map.setKey(Input.Keys.UNKNOWN, InputAction.MOVE_LEFT);
        InputManager input = new InputManager(map);
        KeyListener cached = new KeyListener(4, List.of(InputAction.MOVE_UP));
        input.regKeyListener(cached);
        for (int code : new int[]{-1, Integer.MIN_VALUE, Input.Keys.MAX_KEYCODE + 1,
            Integer.MAX_VALUE, Input.Keys.Q, Input.Keys.UNKNOWN}) {
            check(!input.keyDown(code), "ignored key down propagates " + code);
            check(!input.keyUp(code), "ignored key up propagates " + code);
        }
        check(!input.keyTyped('w'), "keyTyped propagates");
        check(!input.keyTyped('\n'), "typed control character propagates");
        check(!cached.hasInputs(), "ignored keys do not reach cache");
        check(!input.keyDown(Input.Keys.W), "key down propagates");
        check(cached.hasInputs(), "cache queues down");
        check(!input.keyUp(Input.Keys.W), "key up propagates");
        input.keyDown(Input.Keys.S);
        input.keyUp(Input.Keys.S);
        List<Event> expected = List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.MOVE_UP, 0));
        List<Event> drained = new ArrayList<>();
        cached.consume((action, state) -> drained.add(new Event(action, state)));
        equal(expected, drained, "cached delivery preserves down/up FIFO");
        check(!cached.hasInputs(), "cache drained");
        cached.consume((action, state) -> { throw new AssertionError("empty cache callback"); });
        cached.setListening(false);
        cached.offer(InputAction.MOVE_UP, 1);
        input.keyDown(Input.Keys.W);
        check(!cached.hasInputs(), "disabled cache ignores direct and managed offers");
    }

    private static void adapterMouse() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.GUI_RIGHT_CLICK);
        InputManager input = new InputManager(map);
        MouseListener mouse = new MouseListener(16, List.of(InputAction.GUI_LEFT_CLICK, InputAction.GUI_RIGHT_CLICK));
        input.regMousePosListener(mouse);
        check(!input.mouseMoved(10, 20), "move propagates");
        check(!input.touchDragged(11, 21, 0), "primary drag propagates");
        check(!input.touchDown(12, 22, 0, Input.Buttons.LEFT), "primary down propagates");
        check(!input.touchUp(13, 23, 0, Input.Buttons.LEFT), "primary up propagates");
        check(!input.touchDown(13, 23, 0, Input.Buttons.RIGHT), "right down propagates");
        check(!input.touchCancelled(14, 24, 0, Input.Buttons.RIGHT), "cancel propagates");
        equal(List.of(new Event(InputAction.GUI_LEFT_CLICK, 1), new Event(InputAction.GUI_LEFT_CLICK, 0),
            new Event(InputAction.GUI_RIGHT_CLICK, 1), new Event(InputAction.GUI_RIGHT_CLICK, 0)),
            drainButtons(mouse), "mouse button events include cancellation release");
        near(14, mouse.getPos().x, "stored mouse x");
        near(24, mouse.getPos().y, "stored mouse y");

        check(!input.touchDown(90, 90, 1, Input.Buttons.LEFT), "secondary down propagates");
        check(!input.touchUp(90, 90, 1, Input.Buttons.LEFT), "secondary up propagates");
        check(!input.touchCancelled(90, 90, 1, Input.Buttons.RIGHT), "secondary cancel propagates");
        check(!input.touchDragged(90, 90, 1), "secondary drag propagates");
        check(!mouse.hasInputs(), "secondary pointer has no events");
        near(14, mouse.getPos().x, "secondary pointer does not change position");
        check(!input.scrolled(0, -2), "up scroll propagates");
        check(!input.scrolled(0, 3), "down scroll propagates");
        check(!input.scrolled(4, 0), "horizontal scroll propagates");
        check(!input.scrolled(0, 0), "zero scroll propagates");
        equal(List.of(new Event(null, 1), new Event(null, 0)), drainButtons(mouse), "Gdx scroll signs and horizontal ignored");
        input.touchDown(15, 25, 0, -1);
        input.touchUp(16, 26, 0, Input.Keys.MAX_KEYCODE + 1);
        input.touchDown(17, 27, 0, Input.Buttons.MIDDLE);
        check(!mouse.hasInputs(), "invalid/unmapped buttons do not queue events");
        near(17, mouse.getPos().x, "invalid/unmapped buttons update position");
        mouse.setListening(false);
        mouse.offerPos(99, 99);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        mouse.offerScroll(1);
        input.mouseMoved(99, 99);
        input.touchDown(99, 99, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        check(!mouse.hasInputs(), "disabled mouse ignores direct and managed offers");
        near(17, mouse.getPos().x, "disabled listener retains position");
    }

    private static void listenerFanout() {
        InputMap map = new InputMap();
        map.setKey(Input.Keys.W, InputAction.MOVE_UP);
        map.setKey(Input.Keys.ENTER, InputAction.GUI_ENTER);
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        InputManager input = new InputManager(map);
        KeyListener first = new KeyListener(4, List.of(InputAction.MOVE_UP, InputAction.GUI_ENTER));
        KeyListener second = new KeyListener(4, List.of(InputAction.GUI_ENTER));
        input.regKeyListener(first);
        input.regKeyListener(second);
        MouseListener mouse = new MouseListener(4, List.of(InputAction.GUI_LEFT_CLICK, InputAction.MOVE_RIGHT));
        input.regMousePosListener(mouse);
        input.keyDown(Input.Keys.W);
        input.keyDown(Input.Keys.ENTER);
        input.touchDown(1, 2, 0, Input.Buttons.LEFT);
        input.touchDown(3, 4, 0, Input.Buttons.RIGHT);
        equal(List.of(new Event(InputAction.MOVE_UP, 1), new Event(InputAction.GUI_ENTER, 1)), drain(first), "broad key whitelist");
        equal(List.of(new Event(InputAction.GUI_ENTER, 1)), drain(second), "narrow key whitelist");
        equal(List.of(new Event(InputAction.GUI_LEFT_CLICK, 1), new Event(InputAction.MOVE_RIGHT, 1)), drainButtons(mouse), "mouse whitelist receives both actions");
    }

    private static final class CustomKey extends KeyListener {
        private CustomKey() { super(4, List.of(InputAction.MOVE_UP)); }
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
            equal(List.of(new Event(InputAction.MOVE_UP, 1)), drain(listener), "custom key registered once by identity beyond capacity 20");
        }
        for (int index : new int[]{0, 12, 24}) { input.unRegKeyListener(listeners.get(index)); }
        input.unRegKeyListener(new CustomKey());
        input.unRegKeyListener(listeners.get(12));
        input.keyUp(Input.Keys.W);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 0 || i == 12 || i == 24 ? List.of() : List.of(new Event(InputAction.MOVE_UP, 0)),
                drain(listeners.get(i)), "key first/middle/last/absent removal " + i);
        }
        for (int index : new int[]{0, 12, 24}) { input.regKeyListener(listeners.get(index)); }
        listeners.get(5).setListening(false);
        input.keyDown(Input.Keys.W);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 5 ? 0 : 1, drain(listeners.get(i)).size(), "key re-registration and disabled custom listener " + i);
        }
    }

    private static void mouseRegistrations() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        List<MouseListener> listeners = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            MouseListener listener = new MouseListener(4, List.of(InputAction.GUI_LEFT_CLICK)) {
                @Override public boolean equals(Object other) { return other instanceof MouseListener; }
                @Override public int hashCode() { return 1; }
            };
            listeners.add(listener);
            input.regMousePosListener(listener);
            input.regMousePosListener(listener);
        }
        input.touchDown(1, 2, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        for (MouseListener listener : listeners) {
            equal(List.of(new Event(InputAction.GUI_LEFT_CLICK, 1), new Event(null, 1)), drainButtons(listener),
                "mouse registered once by identity beyond capacity 20");
        }
        for (int index : new int[]{0, 12, 24}) { input.unRegMousePosListener(listeners.get(index)); }
        input.unRegMousePosListener(new MouseListener(4, List.of(InputAction.GUI_LEFT_CLICK)));
        input.unRegMousePosListener(listeners.get(12));
        input.touchUp(1, 2, 0, Input.Buttons.LEFT);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 0 || i == 12 || i == 24 ? List.of() : List.of(new Event(InputAction.GUI_LEFT_CLICK, 0)),
                drainButtons(listeners.get(i)), "mouse first/middle/last/absent removal " + i);
        }
        for (int index : new int[]{0, 12, 24}) { input.regMousePosListener(listeners.get(index)); }
        input.mouseMoved(3, 4);
        for (MouseListener listener : listeners) {
            near(3, listener.getPos().x, "mouse re-registration updates position");
            check(!listener.hasInputs(), "movement does not queue events");
        }
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
        KeyListener keys = new KeyListener(8, List.of(InputAction.MOVE_UP, InputAction.MOVE_DOWN));
        MouseListener mouse = new MouseListener(8, List.of(InputAction.MOVE_UP, InputAction.MOVE_DOWN));
        input.regKeyListener(keys);
        input.regMousePosListener(mouse);
        for (int code = Input.Buttons.LEFT; code <= Input.Buttons.FORWARD; code++) {
            map.setKey(code, InputAction.MOVE_UP);
            map.setButton(code, InputAction.MOVE_DOWN);
            drain(keys);
            drainButtons(mouse);
            input.keyDown(code);
            input.keyUp(code);
            equal(code == Input.Keys.UNKNOWN ? List.of() : List.of(new Event(InputAction.MOVE_UP, 1),
                new Event(InputAction.MOVE_UP, 0)), drain(keys), "keyboard uses key namespace " + code);
            equal(List.of(), drainButtons(mouse), "keyboard does not dispatch mouse buttons " + code);
            input.touchDown(1, 2, 0, code);
            input.touchUp(1, 2, 0, code);
            input.touchCancelled(1, 2, 0, code);
            equal(List.of(new Event(InputAction.MOVE_DOWN, 1), new Event(InputAction.MOVE_DOWN, 0)), drainButtons(mouse),
                "mouse uses button namespace and ignores late cancel " + code);
            equal(List.of(), drain(keys), "mouse does not dispatch keyboard events " + code);
            map.removeKey(code);
            input.keyDown(code);
            equal(List.of(), drain(keys), "button-only binding cannot trigger a key action " + code);
            input.touchDown(1, 2, 0, code);
            equal(List.of(new Event(InputAction.MOVE_DOWN, 1)), drainButtons(mouse), "button survives removal of same-code key " + code);
            input.touchUp(1, 2, 0, code);
            drainButtons(mouse);
            input.keyUp(code);
            map.setKey(code, InputAction.MOVE_UP);
            map.removeButton(code);
            input.touchDown(1, 2, 0, code);
            input.touchUp(1, 2, 0, code);
            equal(List.of(), drainButtons(mouse), "key-only binding cannot trigger a mouse action " + code);
            input.keyDown(code);
            equal(code == Input.Keys.UNKNOWN ? List.of() : List.of(new Event(InputAction.MOVE_UP, 1)), drain(keys),
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
            KeyListener listener = new KeyListener(1, List.of(InputAction.MOVE_UP));
            input.regKeyListener(listener);
            InputMultiplexer mux = new InputMultiplexer(upstream, input, downstream);
            Gdx.input.setInputProcessor(mux);
            check(mux.keyDown(Input.Keys.W), "actual multiplexer returns upstream true");
            equal(List.of("upstream"), trace, "upstream true stops adapter and downstream");
            trace.clear();
            stopUpstream[0] = false;
            check(!mux.keyDown(Input.Keys.W), "adapter does not consume event");
            equal(List.of("upstream", "downstream"), trace, "actual multiplexer delivers past adapter");
            equal(List.of(new Event(InputAction.MOVE_UP, 1)), drain(listener), "adapter caches propagated event");
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
