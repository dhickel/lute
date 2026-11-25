package io.mindspice.lute.engine.input;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.controllers.Controller;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.systems.ControllerSystem;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static io.mindspice.lute.engine.input.InputAction.*;
import static io.mindspice.lute.engine.input.InputSystemTest.equal;
import static io.mindspice.lute.engine.input.InputSystemTest.near;

final class GamepadInputTest {
    // Deliberately not the SDL codes, and UP also matches a real keyboard keycode.
    private static final int UP = Input.Keys.W, DOWN = 8, LEFT = 9, RIGHT = 30, SOUTH = 0, WEST = 3, NORTH = 14, EAST = 17;
    private static final int X = 4, Y = 5;
    private static final ControllerMapping MAPPED = new TestMapping(UP, DOWN, LEFT, RIGHT, SOUTH, WEST, X, Y);
    private static final int UNDEFINED = ControllerMapping.UNDEFINED;
    private static final ControllerMapping UNMAPPED = new TestMapping(UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED,
        UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED);

    static void run() {
        new InputManager(new InputMap()).updateGamepad(); // Empty settings never initialize the native backend.
        configuredButtons();
        digitalStick();
        weaponCycleRouting();
        sprintHold();
        resetAndFailover();
        disconnectDuringRead();
        gameplayRouting();
    }

    private static void configuredButtons() {
        var map = new InputMap();
        map.setGamepad(GamepadControl.FACE_SOUTH, ATTACK_SECONDARY);
        var fixture = new Fixture(map);
        var pad = fixture.addPad(MAPPED);
        fixture.expect("initial selection is quiet");
        pad.buttons[WEST] = true;
        fixture.expect("omitted control stays unbound");
        pad.buttons[SOUTH] = true;
        fixture.expect("native button zero uses custom mapping", press(ATTACK_SECONDARY));
        fixture.expect("holding a button does not repeat");
        pad.buttons[SOUTH] = false;
        fixture.expect("remapped button release", release(ATTACK_SECONDARY));

        fixture = new Fixture(defaults());
        pad = fixture.addPad(MAPPED);
        fixture.expect("default pad selection is quiet");
        for (int button : new int[]{UP, DOWN, LEFT, RIGHT, SOUTH, WEST}) { pad.buttons[button] = true; }
        fixture.expect("all six mapped buttons", press(MOVE_UP), press(MOVE_DOWN), press(MOVE_LEFT),
            press(MOVE_RIGHT), press(ATTACK_PRIMARY), press(ATTACK_SECONDARY));
        for (int button : new int[]{UP, DOWN, LEFT, RIGHT, SOUTH, WEST}) { pad.buttons[button] = false; }
        fixture.expect("all six buttons release", release(MOVE_UP), release(MOVE_DOWN), release(MOVE_LEFT),
            release(MOVE_RIGHT), release(ATTACK_PRIMARY), release(ATTACK_SECONDARY));
    }

    private static void digitalStick() {
        var fixture = new Fixture(defaults());
        var pad = fixture.addPad(MAPPED);
        fixture.expect("neutral baseline");
        pad.axes[X] = 0.49f;
        pad.axes[Y] = -0.30f;
        fixture.expect("subthreshold tilt ignored");
        pad.axes[X] = 0.50f;
        pad.axes[Y] = -0.80f;
        fixture.expect("diagonal makes two digital presses", press(MOVE_UP), press(MOVE_RIGHT));
        pad.axes[X] = 0.40f;
        fixture.expect("release threshold prevents chatter");
        pad.axes[X] = 0.35f;
        fixture.expect("right releases at threshold", release(MOVE_RIGHT));
        pad.axes[X] = -1f;
        pad.axes[Y] = 1f;
        fixture.expect("both axes can reverse", release(MOVE_UP), press(MOVE_DOWN), press(MOVE_LEFT));
        pad.axes[X] = 0f;
        pad.axes[Y] = 0f;
        fixture.expect("centering releases both directions", release(MOVE_DOWN), release(MOVE_LEFT));
    }

    private static void resetAndFailover() {
        var fixture = new Fixture(defaults());
        fixture.addPad(null);
        fixture.addPad(UNMAPPED);
        var first = fixture.addPad(MAPPED);
        var fallback = fixture.addPad(MAPPED);
        first.buttons[SOUTH] = true;
        fallback.buttons[WEST] = true;
        fixture.expect("held on selection and inactive pad ignored");
        first.buttons[SOUTH] = false;
        fixture.expect("initially held button has no unmatched release");
        first.buttons[SOUTH] = true;
        first.axes[X] = -0.6f;
        fixture.expect("first mapped pad owns input", press(ATTACK_PRIMARY), press(MOVE_LEFT));

        fixture.gamepad.releaseHeldInputs();
        fixture.gamepad.releaseHeldInputs();
        first.axes[X] = -0.4f;
        fixture.expect("lifecycle reset balances holds exactly once", release(ATTACK_PRIMARY), release(MOVE_LEFT));
        first.axes[X] = -0.7f;
        fixture.expect("still-held button and stick cannot reactivate after reset");
        first.buttons[SOUTH] = false;
        first.axes[X] = 0;
        fixture.expect("neutral re-arms without generating transitions");
        first.buttons[SOUTH] = true;
        first.axes[X] = -0.6f;
        fixture.expect("fresh presses after reset", press(ATTACK_PRIMARY), press(MOVE_LEFT));

        fixture.pads.removeValue(first.controller, true);
        fallback.axes[Y] = 0.8f;
        fixture.expect("disconnect releases old holds but ignores held fallback controls",
            release(ATTACK_PRIMARY), release(MOVE_LEFT));
        fixture.pads.insert(0, first.controller);
        first.buttons[LEFT] = true;
        fixture.expect("reconnected pad does not steal active ownership");
        fallback.buttons[WEST] = false;
        fallback.axes[Y] = 0;
        fixture.expect("fallback held controls re-arm individually");
        fallback.buttons[WEST] = true;
        fallback.axes[Y] = 1f;
        fixture.expect("fresh fallback inputs work", press(ATTACK_SECONDARY), press(MOVE_DOWN));
        fallback.connected = false;
        fixture.expect("disconnected pad still in backend list is skipped", release(ATTACK_SECONDARY), release(MOVE_DOWN));
        first.buttons[LEFT] = false;
        fixture.expect("reselected pad ignores release of held control");
        first.buttons[LEFT] = true;
        fixture.expect("reselected pad accepts fresh press", press(MOVE_LEFT));
    }

    private static void disconnectDuringRead() {
        var fixture = new Fixture(defaults());
        var pad = fixture.addPad(MAPPED);
        fixture.expect("neutral before device removal");
        pad.buttons[UP] = true;
        fixture.expect("move before disconnect", press(MOVE_UP));
        pad.buttons[SOUTH] = true;
        pad.onButtonRead = () -> fixture.pads.removeValue(pad.controller, true);
        fixture.expect("discard partial sample; release only accepted holds", release(MOVE_UP));
    }

    private static void gameplayRouting() {
        var fixture = new Fixture(defaults());
        var pad = fixture.addPad(MAPPED);
        var engine = new Engine();
        engine.addSystem(new ControllerSystem());
        var move = new Move(1);
        var combat = new Combat();
        engine.addEntity(new Entity().add(io.mindspice.lute.engine.components.Controller.forInputManager(fixture.input))
            .add(move).add(combat));
        fixture.expect("initial pad baseline");
        fixture.input.keyDown(Input.Keys.W);
        fixture.expect("keyboard action reaches the shared listeners", press(MOVE_UP));
        engine.update(0);
        near(1, move.getDirection().y, "keyboard starts moving");
        pad.buttons[UP] = true;
        pad.axes[Y] = -0.8f;
        pad.buttons[WEST] = true;
        fixture.expect("D-pad and stick contribute independently of keyboard and native code overlaps",
            press(MOVE_UP), press(ATTACK_SECONDARY), press(MOVE_UP));
        engine.update(0);
        equal(Combat.AttackSlot.SECONDARY, combat.consumeAttackRequest(), "pad attack uses existing combat routing");
        pad.axes[Y] = 0f;
        fixture.expect("releasing the stick leaves the other holds", release(MOVE_UP));
        engine.update(0);
        near(2, move.getDirection().y, "keyboard and D-pad remain held");
        fixture.pads.clear();
        fixture.expect("disconnect sends only pad releases", release(MOVE_UP), release(ATTACK_SECONDARY));
        engine.update(0);
        near(1, move.getDirection().y, "pad disconnect preserves the held keyboard direction");
        fixture.input.keyUp(Input.Keys.W);
        engine.update(0);
        near(0, move.getDirection().y, "last release stops movement");
        equal(null, combat.consumeAttackRequest(), "releases never attack");
    }

    private static void weaponCycleRouting() {
        var fixture = new Fixture(defaults());
        var pad = fixture.addPad(MAPPED);
        var ordinary = new io.mindspice.lute.engine.components.Controller(List.of("first", "second"));
        var arena = io.mindspice.lute.engine.components.Controller.forArena("first");
        fixture.input.regKeyListener(ordinary.getInputListener());
        fixture.input.regKeyListener(arena.getInputListener());
        fixture.expect("neutral before Y");
        pad.buttons[NORTH] = true;
        fixture.expect("native Y maps to arena cycle", press(CYCLE_ARENA_WEAPON));
        var actions = new ArrayList<InputAction>();
        ordinary.getInputListener().consume((action, state) -> actions.add(action));
        equal(List.of(), actions, "ordinary controller ignores Y");
        arena.getInputListener().consume((action, state) -> actions.add(action));
        equal(List.of(CYCLE_ARENA_WEAPON), actions, "arena controller receives Y");
        fixture.expect("held Y does not repeat");
        pad.buttons[NORTH] = false;
        fixture.expect("Y release balances its press", release(CYCLE_ARENA_WEAPON));
    }

    private static void sprintHold() {
        var fixture = new Fixture(defaults());
        var pad = fixture.addPad(MAPPED);
        pad.buttons[EAST] = true;
        fixture.expect("held B at first sample does not dispatch other actions");
        equal(0L, fixture.gamepad.arenaSprintPress(), "held B at baseline is suppressed");
        pad.buttons[EAST] = false;
        fixture.expect("B release is quiet");
        pad.buttons[EAST] = true;
        fixture.expect("native B uses the mapped button without a queued action");
        long first = fixture.gamepad.arenaSprintPress();
        equal(true, first != 0, "fresh B press holds sprint");
        fixture.expect("B hold is quiet");
        equal(first, fixture.gamepad.arenaSprintPress(), "hold identity stays constant");
        fixture.gamepad.releaseHeldInputs();
        equal(0L, fixture.gamepad.arenaSprintPress(), "invalidation cancels B immediately");
        fixture.expect("still-held B cannot rearm on resume");
        equal(0L, fixture.gamepad.arenaSprintPress(), "re-baseline suppresses held B");
        pad.buttons[EAST] = false;
        fixture.expect("release after invalidation");
        pad.buttons[EAST] = true;
        fixture.expect("fresh B re-arms");
        long next = fixture.gamepad.arenaSprintPress();
        equal(true, next != 0 && next != first, "fresh B has a distinct hold identity");
        pad.connected = false;
        var replacement = fixture.addPad(MAPPED);
        replacement.buttons[EAST] = true;
        fixture.expect("held B on failover is quiet");
        equal(0L, fixture.gamepad.arenaSprintPress(), "failover cancels old B and ignores held replacement B");
    }

    private static InputMap defaults() {
        return InputSettings.load(new FileHandle("assets/input.setting.json"));
    }

    private record Transition(InputAction action, int state) { }

    private static Transition press(InputAction action) { return new Transition(action, 1); }
    private static Transition release(InputAction action) { return new Transition(action, 0); }

    private static final class Fixture {
        final Array<Controller> pads = new Array<>();
        final InputManager input;
        final GamepadInput gamepad;
        final KeyListener listener = new KeyListener(32, List.of(InputAction.values()));

        Fixture(InputMap map) {
            input = new InputManager(map);
            gamepad = new GamepadInput(map, input.getKeyManager());
            input.regKeyListener(listener);
        }

        FakePad addPad(ControllerMapping mapping) {
            var pad = new FakePad(mapping);
            pads.add(pad.controller);
            return pad;
        }

        void expect(String message, Transition... expected) {
            gamepad.update(pads);
            List<Transition> actual = new ArrayList<>();
            listener.consume((action, state) -> actual.add(new Transition(action, state)));
            equal(List.of(expected), actual, message);
        }
    }

    private static final class FakePad {
        final boolean[] buttons = new boolean[256];
        final float[] axes = new float[8];
        final Controller controller;
        boolean connected = true;
        Runnable onButtonRead = () -> { };

        FakePad(ControllerMapping mapping) {
            controller = (Controller) Proxy.newProxyInstance(Controller.class.getClassLoader(),
                new Class<?>[]{Controller.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getMapping" -> mapping;
                    case "isConnected" -> connected;
                    case "getButton" -> {
                        onButtonRead.run();
                        yield buttons[(Integer) args[0]];
                    }
                    case "getAxis" -> axes[(Integer) args[0]];
                    default -> throw new AssertionError("Unexpected backend method: " + method.getName());
                });
        }
    }

    private static final class TestMapping extends ControllerMapping {
        TestMapping(int up, int down, int left, int right, int south, int west, int x, int y) {
            super(x, y, UNDEFINED, UNDEFINED, south, south == UNDEFINED ? UNDEFINED : EAST, west,
                south == UNDEFINED ? UNDEFINED : NORTH, UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED, UNDEFINED,
                UNDEFINED, UNDEFINED, UNDEFINED, up, down, left, right);
        }
    }
}
