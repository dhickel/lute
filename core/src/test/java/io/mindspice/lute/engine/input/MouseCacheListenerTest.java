package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import io.mindspice.lute.engine.input.MouseEventConsumer.Type;

import java.util.ArrayList;
import java.util.List;

import static io.mindspice.lute.engine.input.InputSystemTest.check;
import static io.mindspice.lute.engine.input.InputSystemTest.equal;
import static io.mindspice.lute.engine.input.InputSystemTest.near;
import static io.mindspice.lute.engine.input.InputSystemTest.throwsType;

final class MouseCacheListenerTest {
    private record Event(Type type, InputAction action, int value, double x, double y) { }

    static void run() {
        deferredSnapshots();
        listeningAndWhitelist();
        adapterEvents();
        filtersAndPropagation();
        mixedRegistrations();
        singleSlotQueue();
        wrappedQueue();
        reentrantQueue();
        throwingCallbacks();
    }

    private static Event button(InputAction action, int value, double x, double y) {
        return new Event(Type.BUTTON, action, value, x, y);
    }

    private static Event scroll(int value, double x, double y) {
        return new Event(Type.SCROLL, null, value, x, y);
    }

    private static List<Event> drain(CircularMouseQueue queue) {
        List<Event> events = new ArrayList<>();
        queue.consume((type, action, value, x, y) -> events.add(new Event(type, action, value, x, y)));
        return events;
    }

    private static List<Event> drain(MouseCacheListener listener) {
        List<Event> events = new ArrayList<>();
        listener.consume((type, action, value, x, y) -> events.add(new Event(type, action, value, x, y)));
        return events;
    }

    private static MouseCacheListener cached(ActionType type, int capacity, InputAction... actions) {
        return new MouseCacheListener(type, capacity, List.of(actions));
    }

    private static void deferredSnapshots() {
        MouseCacheListener mouse = cached(ActionType.GUI, 4, InputAction.GUI_LEFT_CLICK);
        check(mouse.isListening(), "cache initially listening");
        equal(ActionType.GUI, mouse.getListeningFor(), "cache category");
        check(mouse.isListenerFor(ActionType.GUI) && !mouse.isListenerFor(ActionType.GAME_INPUT),
            "cache category matching");
        near(0, mouse.getPos().x, "initial position x");
        near(0, mouse.getPos().y, "initial position y");
        mouse.offerPos(10.25, -20.5);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        mouse.offerPos(30.5, 40.25);
        mouse.offerScroll(1);
        mouse.offerPos(-50.75, 60.5);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 0);
        mouse.offerScroll(0);
        check(mouse.hasInputs() && mouse.getQueue().isFull(), "button and scroll offers are deferred");
        for (int i = 0; i < 30; i++) { mouse.offerPos(100 + i, 200 + i); }
        near(129, mouse.getPos().x, "movement retains latest x");
        near(229, mouse.getPos().y, "movement retains latest y");
        mouse.getPos().set(900, 901);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 10.25, -20.5), scroll(1, 30.5, 40.25),
            button(InputAction.GUI_LEFT_CLICK, 0, -50.75, 60.5), scroll(0, -50.75, 60.5)), drain(mouse),
            "FIFO snapshots survive later movement and mutation of exposed position; movement cannot overwrite events");
        check(!mouse.hasInputs() && mouse.getQueue().isEmpty(), "listener fully drained");
        mouse.consume((type, action, value, x, y) -> { throw new AssertionError("empty listener callback"); });
        mouse.offerPos(1, 2);
        check(!mouse.hasInputs(), "movement alone has no queued history");
        near(1, mouse.getPos().x, "movement after consume still updates x");
        near(2, mouse.getPos().y, "movement after consume still updates y");
    }

    private static void listeningAndWhitelist() {
        MouseCacheListener mouse = cached(ActionType.GUI, 4, InputAction.GUI_LEFT_CLICK);
        mouse.offerPos(3, 4);
        mouse.offerButton(InputAction.GUI_RIGHT_CLICK, 1);
        mouse.offerButton(InputAction.MOVE_UP, 1);
        mouse.offerButton(null, 1);
        check(!mouse.hasInputs(), "own whitelist excludes unlisted same-category, foreign and null actions");
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        mouse.offerScroll(1);
        mouse.setListening(false);
        check(!mouse.isListening(), "cache can disable listening");
        mouse.offerPos(90, 91);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 0);
        mouse.offerScroll(0);
        near(3, mouse.getPos().x, "disabled direct position ignored x");
        near(4, mouse.getPos().y, "disabled direct position ignored y");
        check(mouse.hasInputs(), "disabling retains pending events");
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 3, 4), scroll(1, 3, 4)), drain(mouse),
            "consume while disabled drains only pending events");
        check(!mouse.hasInputs(), "disabled consume empties cache");
        mouse.setListening(true);
        mouse.offerButton(InputAction.GUI_LEFT_CLICK, 0);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 0, 3, 4)), drain(mouse),
            "reenabling retains last accepted position");
        MouseCacheListener noButtons = cached(ActionType.GUI, 2);
        noButtons.offerButton(InputAction.GUI_LEFT_CLICK, 1);
        noButtons.offerScroll(0);
        equal(List.of(scroll(0, 0, 0)), drain(noButtons), "scroll is independent of button whitelist");
    }

    private static void adapterEvents() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.GUI_RIGHT_CLICK);
        InputManager input = new InputManager(map);
        MouseCacheListener mouse = cached(ActionType.GUI, 8, InputAction.GUI_LEFT_CLICK, InputAction.GUI_RIGHT_CLICK);
        input.regMousePosListener(mouse);
        check(!input.mouseMoved(10, 20), "cached move propagates");
        check(!input.touchDragged(11, 21, 0), "cached primary drag propagates");
        check(!mouse.hasInputs(), "adapter move and drag do not enqueue");
        near(11, mouse.getPos().x, "primary drag latest x");
        near(21, mouse.getPos().y, "primary drag latest y");
        check(!input.touchDown(12, 22, 0, Input.Buttons.LEFT), "cached primary down propagates");
        check(!input.scrolled(0, -2), "cached up scroll propagates");
        check(!input.touchUp(13, 23, 0, Input.Buttons.LEFT), "cached primary up propagates");
        check(!input.scrolled(0, 3), "cached down scroll propagates");
        check(!input.touchDown(13, 23, 0, Input.Buttons.RIGHT), "cached right down propagates");
        check(!input.touchCancelled(14, 24, 0, Input.Buttons.RIGHT), "cached cancel propagates");
        check(!input.scrolled(4, 0), "horizontal-only scroll propagates");
        check(!input.scrolled(0, 0), "zero scroll propagates");
        check(!input.touchDown(90, 91, 1, Input.Buttons.LEFT), "secondary down propagates");
        check(!input.touchUp(90, 91, 1, Input.Buttons.LEFT), "secondary up propagates");
        check(!input.touchCancelled(90, 91, 1, Input.Buttons.RIGHT), "secondary cancel propagates");
        check(!input.touchDragged(90, 91, 1), "secondary drag propagates");
        near(14, mouse.getPos().x, "secondary pointer leaves latest x unchanged");
        near(24, mouse.getPos().y, "secondary pointer leaves latest y unchanged");
        check(!input.mouseMoved(15, 25), "later movement propagates");
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 12, 22), scroll(1, 12, 22),
            button(InputAction.GUI_LEFT_CLICK, 0, 13, 23), scroll(0, 13, 23),
            button(InputAction.GUI_RIGHT_CLICK, 1, 13, 23), button(InputAction.GUI_RIGHT_CLICK, 0, 14, 24)), drain(mouse),
            "adapter position precedes down/up/cancel snapshots, scroll uses legacy 1/0, no secondary or movement events");
        near(15, mouse.getPos().x, "later movement remains latest x after drain");
        near(25, mouse.getPos().y, "later movement remains latest y after drain");
        for (int code : new int[]{-1, Integer.MIN_VALUE, Input.Buttons.FORWARD + 1,
            Integer.MAX_VALUE, Input.Buttons.MIDDLE}) {
            check(!input.touchDown(16, 26, 0, code), "invalid/unmapped down propagates " + code);
            check(!input.touchUp(17, 27, 0, code), "invalid/unmapped up propagates " + code);
            check(!input.touchCancelled(18, 28, 0, code), "invalid/unmapped cancel propagates " + code);
            check(!mouse.hasInputs(), "invalid/unmapped button creates no cached event " + code);
            near(18, mouse.getPos().x, "invalid/unmapped button still updates x " + code);
            near(28, mouse.getPos().y, "invalid/unmapped button still updates y " + code);
        }
        mouse.setListening(false);
        input.mouseMoved(99, 99);
        input.touchDragged(99, 99, 0);
        input.touchDown(99, 99, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        check(!mouse.hasInputs(), "disabled cache ignores managed offers");
        near(18, mouse.getPos().x, "disabled managed offers retain x");
        near(28, mouse.getPos().y, "disabled managed offers retain y");
    }

    private static MouseCallBackListener callback(ActionType type, List<Event> events) {
        MouseCallBackListener listener = new MouseCallBackListener(type);
        listener.setButtonConsumer((action, value) -> events.add(button(action, value,
            listener.getPos().x, listener.getPos().y)));
        listener.setScrollConsumer(value -> events.add(scroll(value, listener.getPos().x, listener.getPos().y)));
        return listener;
    }

    private static void filtersAndPropagation() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        map.setButton(Input.Buttons.RIGHT, InputAction.MOVE_RIGHT);
        InputManager input = new InputManager(map);
        MouseCacheListener gui = cached(ActionType.GUI, 8, InputAction.GUI_LEFT_CLICK, InputAction.MOVE_RIGHT);
        MouseCacheListener game = cached(ActionType.GAME_INPUT, 8, InputAction.GUI_LEFT_CLICK, InputAction.MOVE_RIGHT);
        List<Event> guiImmediate = new ArrayList<>();
        List<Event> gameImmediate = new ArrayList<>();
        MouseCallBackListener guiCallback = callback(ActionType.GUI, guiImmediate);
        MouseCallBackListener gameCallback = callback(ActionType.GAME_INPUT, gameImmediate);
        input.regMousePosListener(gui);
        input.regMousePosListener(gameCallback);
        input.regMousePosListener(game);
        input.regMousePosListener(guiCallback);
        List<String> downstream = new ArrayList<>();
        InputAdapter observer = new InputAdapter() {
            @Override public boolean mouseMoved(int x, int y) { downstream.add("move:" + x + ":" + y); return false; }
            @Override public boolean touchDown(int x, int y, int p, int b) { downstream.add("down:" + x + ":" + y + ":" + p + ":" + b); return false; }
            @Override public boolean touchUp(int x, int y, int p, int b) { downstream.add("up:" + x + ":" + y + ":" + p + ":" + b); return false; }
            @Override public boolean touchCancelled(int x, int y, int p, int b) { downstream.add("cancel:" + x + ":" + y + ":" + p + ":" + b); return false; }
            @Override public boolean touchDragged(int x, int y, int p) { downstream.add("drag:" + x + ":" + y + ":" + p); return false; }
            @Override public boolean scrolled(float x, float y) { downstream.add("scroll:" + x + ":" + y); return false; }
        };
        InputMultiplexer mux = new InputMultiplexer(input, observer);
        for (ActionType filter : new ActionType[]{null, ActionType.GUI, ActionType.GAME_INPUT, ActionType.WINDOW}) {
            guiImmediate.clear();
            gameImmediate.clear();
            downstream.clear();
            gui.offerPos(-1, -2);
            game.offerPos(-1, -2);
            guiCallback.offerPos(-1, -2);
            gameCallback.offerPos(-1, -2);
            if (filter == null) { input.disableFilter(); } else { input.setFilter(filter); }
            check(!mux.mouseMoved(1, 2), "filtered move observer flag " + filter);
            check(!mux.touchDown(3, 4, 0, Input.Buttons.LEFT), "filtered down observer flag " + filter);
            check(!mux.touchDown(4, 5, 0, Input.Buttons.RIGHT), "filtered game down observer flag " + filter);
            check(!mux.touchUp(5, 6, 0, Input.Buttons.RIGHT), "filtered up observer flag " + filter);
            check(!mux.touchCancelled(7, 8, 0, Input.Buttons.LEFT), "filtered cancel observer flag " + filter);
            check(!mux.touchDragged(9, 10, 0), "filtered drag observer flag " + filter);
            check(!mux.scrolled(2, -3), "filtered scroll observer flag " + filter);
            equal(List.of("move:1:2", "down:3:4:0:" + Input.Buttons.LEFT,
                "down:4:5:0:" + Input.Buttons.RIGHT, "up:5:6:0:" + Input.Buttons.RIGHT,
                "cancel:7:8:0:" + Input.Buttons.LEFT,
                "drag:9:10:0", "scroll:2.0:-3.0"), downstream,
                "cached/callback coexistence preserves all downstream arguments " + filter);
            boolean allowGui = filter == null || filter == ActionType.GUI;
            boolean allowGame = filter == null || filter == ActionType.GAME_INPUT;
            List<Event> expectedGui = allowGui ? List.of(button(InputAction.GUI_LEFT_CLICK, 1, 3, 4),
                button(InputAction.GUI_LEFT_CLICK, 0, 7, 8), scroll(1, 9, 10)) : List.of();
            List<Event> expectedGame = allowGame ? List.of(button(InputAction.MOVE_RIGHT, 1, 4, 5),
                button(InputAction.MOVE_RIGHT, 0, 5, 6), scroll(1, 9, 10)) : List.of();
            equal(expectedGui, guiImmediate, "GUI callback immediate category/filter " + filter);
            equal(expectedGame, gameImmediate, "game callback immediate category/filter " + filter);
            equal(allowGui, gui.hasInputs(), "GUI deferred category/filter " + filter);
            equal(allowGame, game.hasInputs(), "game deferred category/filter " + filter);
            equal(expectedGui, drain(gui), "GUI cache category/filter despite broad whitelist " + filter);
            equal(expectedGame, drain(game), "game cache category/filter despite broad whitelist " + filter);
            near(allowGui ? 9 : -1, gui.getPos().x, "GUI filtered latest x " + filter);
            near(allowGui ? 10 : -2, gui.getPos().y, "GUI filtered latest y " + filter);
            near(allowGame ? 9 : -1, game.getPos().x, "game filtered latest x " + filter);
            near(allowGame ? 10 : -2, game.getPos().y, "game filtered latest y " + filter);
        }
        input.disableFilter();
        input.touchDown(11, 12, 0, Input.Buttons.LEFT);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 11, 12)), drain(gui), "disableFilter restores cached delivery");
        input.getMouseManager().setListenFilter(ActionType.GUI);
        input.scrolled(0, 1);
        equal(List.of(button(InputAction.GUI_LEFT_CLICK, 0, 11, 12), scroll(0, 11, 12)), drain(gui),
            "manager filter releases held button then admits GUI cache");
        check(!game.hasInputs(), "manager filter excludes game cache");
        input.getMouseManager().disableListenFilter();
        input.scrolled(0, 1);
        equal(List.of(scroll(0, 11, 12)), drain(game), "manager filter disable restores game cache");
    }

    private static final class CustomMouse implements MouseListener {
        private boolean listening = true;
        private double x;
        private double y;
        private final List<Event> events;

        private CustomMouse(List<Event> events) { this.events = events; }
        public boolean isListening() { return listening; }
        public boolean isListenerFor(ActionType type) { return type == ActionType.GUI; }
        public ActionType getListeningFor() { return ActionType.GUI; }
        public void setListening(boolean value) { listening = value; }
        public void offerPos(double x, double y) { this.x = x; this.y = y; }
        public void offerButton(InputAction action, int value) { events.add(button(action, value, x, y)); }
        public void offerScroll(int value) { events.add(scroll(value, x, y)); }
        @Override public boolean equals(Object other) { return other instanceof CustomMouse; }
        @Override public int hashCode() { return 1; }
    }

    private static void collectCaches(List<MouseListener> listeners, List<List<Event>> recorded) {
        for (int i = 0; i < listeners.size(); i++) {
            if (listeners.get(i) instanceof MouseCacheListener cache) {
                check(recorded.get(i).isEmpty(), "cache registration remains deferred " + i);
                recorded.get(i).addAll(drain(cache));
            }
        }
    }

    private static void mixedRegistrations() {
        InputMap map = new InputMap();
        map.setButton(Input.Buttons.LEFT, InputAction.GUI_LEFT_CLICK);
        InputManager input = new InputManager(map);
        List<MouseListener> listeners = new ArrayList<>();
        List<List<Event>> recorded = new ArrayList<>();
        for (int i = 0; i < 27; i++) {
            List<Event> events = new ArrayList<>();
            recorded.add(events);
            MouseListener listener = switch (i % 3) {
                case 0 -> cached(ActionType.GUI, 4, InputAction.GUI_LEFT_CLICK);
                case 1 -> callback(ActionType.GUI, events);
                default -> new CustomMouse(events);
            };
            listeners.add(listener);
            input.regMousePosListener(listener);
            input.regMousePosListener(listener);
        }
        check(listeners.get(2) != listeners.get(5) && listeners.get(2).equals(listeners.get(5)),
            "custom listeners are equal but distinct identities");
        input.touchDown(1, 2, 0, Input.Buttons.LEFT);
        input.scrolled(0, -1);
        collectCaches(listeners, recorded);
        for (List<Event> events : recorded) {
            equal(List.of(button(InputAction.GUI_LEFT_CLICK, 1, 1, 2), scroll(1, 1, 2)), events,
                "mixed and interface listeners grow beyond 20 and deduplicate only identical objects");
            events.clear();
        }
        for (int index : new int[]{0, 13, 26}) { input.unRegMousePosListener(listeners.get(index)); }
        input.unRegMousePosListener(new CustomMouse(new ArrayList<>()));
        input.unRegMousePosListener(listeners.get(13));
        input.touchUp(3, 4, 0, Input.Buttons.LEFT);
        input.scrolled(0, 1);
        collectCaches(listeners, recorded);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i == 0 || i == 13 || i == 26 ? List.of() :
                List.of(button(InputAction.GUI_LEFT_CLICK, 0, 3, 4), scroll(0, 3, 4)), recorded.get(i),
                "mixed first/middle/last/repeated/absent removal " + i);
            recorded.get(i).clear();
        }
        for (int index : new int[]{0, 13, 26}) { input.regMousePosListener(listeners.get(index)); }
        listeners.get(3).setListening(false);
        listeners.get(4).setListening(false);
        listeners.get(5).setListening(false);
        input.touchDown(5, 6, 0, Input.Buttons.LEFT);
        collectCaches(listeners, recorded);
        for (int i = 0; i < listeners.size(); i++) {
            equal(i >= 3 && i <= 5 ? List.of() : List.of(button(InputAction.GUI_LEFT_CLICK, 1, 5, 6)),
                recorded.get(i), "mixed re-registration and disabled cache/callback/interface listener " + i);
        }
    }

    private static void add(CircularMouseQueue queue, Event event) {
        queue.add(event.type(), event.action(), event.value(), event.x(), event.y());
    }

    private static void singleSlotQueue() {
        throwsType(IllegalArgumentException.class, () -> new CircularMouseQueue(0), "zero mouse queue capacity");
        throwsType(IllegalArgumentException.class, () -> new CircularMouseQueue(-1), "negative mouse queue capacity");
        throwsType(IllegalArgumentException.class, () -> cached(ActionType.GUI, 0), "listener zero capacity");
        throwsType(IllegalArgumentException.class, () -> cached(ActionType.GUI, -3), "listener negative capacity");
        CircularMouseQueue queue = new CircularMouseQueue(1);
        check(queue.isEmpty() && !queue.isFull(), "capacity one initially empty");
        MouseEventConsumer unexpected = (type, action, value, x, y) -> { throw new AssertionError("empty queue callback"); };
        queue.poll(unexpected);
        queue.peek(unexpected);
        queue.consume(unexpected);
        Event first = button(InputAction.GUI_LEFT_CLICK, 1, -1.25, 2.5);
        Event second = scroll(0, 3.75, -4.5);
        add(queue, first);
        check(queue.isFull() && !queue.isEmpty(), "capacity one full");
        for (int i = 0; i < 2; i++) {
            queue.peek((type, action, value, x, y) -> equal(first, new Event(type, action, value, x, y),
                "peek preserves all scalar fields without removal"));
            check(queue.isFull(), "peek is nondestructive");
        }
        add(queue, second);
        equal(List.of(second), drain(queue), "capacity one overwrites oldest across event types");
        queue.setRejectIfFull(true);
        add(queue, first);
        add(queue, second);
        queue.poll((type, action, value, x, y) -> equal(first, new Event(type, action, value, x, y),
            "capacity one rejects newest and poll returns oldest"));
        check(queue.isEmpty() && !queue.isFull(), "poll removes the one entry");
        queue.setRejectIfFull(false);
        add(queue, second);
        add(queue, first);
        equal(List.of(first), drain(queue), "capacity one overwrite can be reenabled");
    }

    private static void wrappedQueue() {
        CircularMouseQueue queue = new CircularMouseQueue(3);
        for (int cycle = 0; cycle < 5; cycle++) {
            Event first = button(InputAction.GUI_LEFT_CLICK, 1, cycle + 0.25, -cycle - 0.5);
            Event second = scroll(1, cycle + 10.25, cycle + 20.5);
            Event third = button(InputAction.GUI_RIGHT_CLICK, 0, cycle + 30.75, cycle + 40.25);
            Event fourth = scroll(0, cycle + 50.5, cycle + 60.75);
            add(queue, first);
            add(queue, second);
            queue.poll((type, action, value, x, y) -> equal(first, new Event(type, action, value, x, y),
                "wrapped poll preserves all fields"));
            add(queue, third);
            add(queue, fourth);
            check(queue.isFull(), "capacity three full after wrap " + cycle);
            equal(List.of(second, third, fourth), drain(queue), "mixed wraparound FIFO " + cycle);
            check(queue.isEmpty() && !queue.isFull(), "wrapped drain clears full state " + cycle);
        }
        Event first = button(InputAction.GUI_LEFT_CLICK, 1, 1, 2);
        Event second = scroll(1, 3, 4);
        Event third = button(InputAction.GUI_RIGHT_CLICK, 0, 5, 6);
        Event fourth = scroll(0, 7, 8);
        add(queue, first);
        add(queue, second);
        add(queue, third);
        add(queue, fourth);
        equal(List.of(second, third, fourth), drain(queue), "capacity three overwrites only oldest");
        queue.setRejectIfFull(true);
        add(queue, first);
        add(queue, second);
        add(queue, third);
        add(queue, fourth);
        equal(List.of(first, second, third), drain(queue), "capacity three rejects newest");
        add(queue, first);
        add(queue, second);
        add(queue, third);
        queue.setRejectIfFull(false);
        add(queue, fourth);
        equal(List.of(second, third, fourth), drain(queue), "switching a full queue back to overwrite preserves FIFO");
    }

    private static void reentrantQueue() {
        CircularMouseQueue one = new CircularMouseQueue(1);
        one.setRejectIfFull(true);
        Event first = button(InputAction.GUI_LEFT_CLICK, 1, 1.25, 2.5);
        Event second = scroll(0, 3.75, 4.25);
        add(one, first);
        one.poll((type, action, value, x, y) -> {
            check(one.isEmpty() && !one.isFull(), "poll removes before callback reentry");
            one.poll((t, a, v, px, py) -> { throw new AssertionError("reentrant poll saw removed event"); });
            add(one, second);
            equal(first, new Event(type, action, value, x, y), "callback scalar snapshot survives slot reuse");
        });
        equal(List.of(second), drain(one), "reentrant add survives capacity one reject mode");
        add(one, first);
        List<Event> reentrant = new ArrayList<>();
        one.consume((type, action, value, x, y) -> {
            Event event = new Event(type, action, value, x, y);
            reentrant.add(event);
            if (event.equals(first)) { add(one, second); }
        });
        equal(List.of(first, second), reentrant, "consume drains bounded reentrant additions in FIFO order");
        CircularMouseQueue queue = new CircularMouseQueue(3);
        Event third = button(InputAction.GUI_RIGHT_CLICK, 0, 5.5, 6.75);
        add(queue, first);
        add(queue, second);
        List<Event> nested = new ArrayList<>();
        queue.poll((type, action, value, x, y) -> {
            nested.add(new Event(type, action, value, x, y));
            queue.poll((t, a, v, px, py) -> nested.add(new Event(t, a, v, px, py)));
            add(queue, third);
        });
        equal(List.of(first, second), nested, "reentrant poll observes successor, not current event");
        equal(List.of(third), drain(queue), "nested poll/add retains new successor");
    }

    private static void throwingCallbacks() {
        CircularMouseQueue queue = new CircularMouseQueue(3);
        Event first = button(InputAction.GUI_LEFT_CLICK, 1, 1, 2);
        Event second = scroll(0, 3, 4);
        RuntimeException sentinel = new RuntimeException("expected mouse callback failure");
        for (boolean consume : new boolean[]{false, true}) {
            add(queue, first);
            add(queue, second);
            MouseEventConsumer throwing = (type, action, value, x, y) -> {
                equal(first, new Event(type, action, value, x, y), "throwing callback receives removed head");
                queue.peek((t, a, v, px, py) -> equal(second, new Event(t, a, v, px, py),
                    "successor already visible inside throwing callback"));
                throw sentinel;
            };
            try {
                if (consume) { queue.consume(throwing); } else { queue.poll(throwing); }
                throw new AssertionError("mouse callback exception must propagate");
            } catch (RuntimeException failure) {
                check(failure == sentinel, "original callback exception propagates, consume=" + consume);
            }
            equal(List.of(second), drain(queue), "throwing event removed exactly once, successor preserved, consume=" + consume);
        }
        add(queue, first);
        throwsType(RuntimeException.class, () -> queue.consume((type, action, value, x, y) -> { throw sentinel; }),
            "consume propagates failure from final event");
        check(queue.isEmpty(), "throwing final callback still drains its event");
        add(queue, second);
        equal(List.of(second), drain(queue), "queue remains reusable after callback failures");
    }
}
