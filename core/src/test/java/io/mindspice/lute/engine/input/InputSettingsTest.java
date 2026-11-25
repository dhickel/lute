package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.mindspice.lute.engine.input.InputSystemTest.equal;
import static io.mindspice.lute.engine.input.InputSystemTest.throwsType;

final class InputSettingsTest {
    static void run() {
        var defaults = InputSettings.load(new FileHandle("assets/input.setting.json"));
        equal(InputAction.MOVE_UP, defaults.getKey(Input.Keys.W), "W default");
        equal(InputAction.MOVE_DOWN, defaults.getKey(Input.Keys.S), "S default");
        equal(InputAction.MOVE_LEFT, defaults.getKey(Input.Keys.A), "A default");
        equal(InputAction.MOVE_RIGHT, defaults.getKey(Input.Keys.D), "D default");
        equal(InputAction.ATTACK_PRIMARY, defaults.getKey(Input.Keys.SPACE), "Space default");
        equal(InputAction.ATTACK_SECONDARY, defaults.getKey(Input.Keys.SHIFT_LEFT), "left Shift default");
        equal(InputAction.ATTACK_SECONDARY, defaults.getKey(Input.Keys.SHIFT_RIGHT), "right Shift default");
        equal(InputAction.CYCLE_WEAPON, defaults.getKey(Input.Keys.R), "R cycles test weapons");
        equal(InputAction.TOGGLE_NIGHT, defaults.getKey(Input.Keys.N), "N toggles gameplay night");
        equal(null, defaults.getKey(Input.Keys.F3), "F3 is unbound");
        for (var control : GamepadControl.values()) {
            InputSystemTest.check(defaults.getGamepad(control) != InputAction.TOGGLE_NIGHT, "No shipped night button: " + control);
        }
        for (int button = Input.Buttons.LEFT; button <= Input.Buttons.FORWARD; button++) {
            InputSystemTest.check(defaults.getButton(button) != InputAction.TOGGLE_NIGHT, "No shipped night mouse button: " + button);
        }
        equal(InputAction.MOVE_UP, defaults.getGamepad(GamepadControl.DPAD_UP), "D-pad default");
        equal(InputAction.ATTACK_PRIMARY, defaults.getGamepad(GamepadControl.FACE_SOUTH), "south face default");
        equal(InputAction.ATTACK_SECONDARY, defaults.getGamepad(GamepadControl.FACE_WEST), "west face default");
        equal(InputAction.CYCLE_ARENA_WEAPON, defaults.getGamepad(GamepadControl.FACE_NORTH), "north face cycles arena weapons");
        equal(InputAction.MOVE_LEFT, defaults.getGamepad(GamepadControl.LEFT_STICK_LEFT), "left stick default");

        var custom = load("""
            {"controller":{"FACE_SOUTH":"ATTACK_SECONDARY"},"keyboard":{"UP":"MOVE_UP","NUM_1":"ATTACK_PRIMARY"}}
            """);
        equal(InputAction.MOVE_UP, custom.getKey(Input.Keys.UP), "configured key replaces default");
        equal(InputAction.ATTACK_PRIMARY, custom.getKey(Input.Keys.NUM_1), "numeric key constant name");
        equal(null, custom.getKey(Input.Keys.W), "omitted key stays unbound");
        equal(InputAction.ATTACK_SECONDARY, custom.getGamepad(GamepadControl.FACE_SOUTH), "face binding remaps");
        equal(null, custom.getGamepad(GamepadControl.LEFT_STICK_LEFT), "omitted stick binding stays unbound");
        equal(null, custom.getButton(Input.Buttons.LEFT), "controller data does not map to mouse input");
        var empty = load("{\"controller\":{},\"keyboard\":{}}");
        equal(null, empty.getKey(Input.Keys.W), "empty keyboard map is valid");
        equal(null, empty.getGamepad(GamepadControl.FACE_SOUTH), "empty controller map is valid");

        var mouse = load("""
            {"keyboard":{},"controller":{},"mouse":{"MButton1":"ATTACK_PRIMARY","MButton2":"ATTACK_SECONDARY",
              "MButton3":"MOVE_UP","MButton4":"MOVE_LEFT","MButton5":"MOVE_RIGHT"}}
            """);
        equal(InputAction.ATTACK_PRIMARY, mouse.getButton(Input.Buttons.LEFT), "left mouse binding");
        equal(InputAction.ATTACK_SECONDARY, mouse.getButton(Input.Buttons.RIGHT), "right mouse binding");
        equal(InputAction.MOVE_UP, mouse.getButton(Input.Buttons.MIDDLE), "middle mouse binding");
        equal(InputAction.MOVE_LEFT, mouse.getButton(Input.Buttons.BACK), "back mouse binding");
        equal(InputAction.MOVE_RIGHT, mouse.getButton(Input.Buttons.FORWARD), "forward mouse binding");
        equal(null, defaults.getButton(Input.Buttons.LEFT), "shipped mouse bindings are empty");
        rejects("{\"controller\":{},\"keyboard\":{},\"mouse\":null}", "mouse map must be an object");
        rejects("{\"controller\":{},\"keyboard\":{},\"other\":{}}", "unknown section");
        rejects("{\"controller\":{},\"keyboard\":{},\"mouse\":{\"LEFT\":\"MOVE_UP\"}}", "unknown mouse button");
        rejects("{\"controller\":{},\"keyboard\":{},\"mouse\":{\"mbutton1\":\"MOVE_UP\"}}", "wrong mouse button case");
        rejects("{\"controller\":{},\"keyboard\":{},\"mouse\":{\"MButton1\":\"SPACE\"}}", "mouse binds actions, not keys");
        rejects("{\"controller\":{},\"keyboard\":{},\"mouse\":{\"MButton1\":\"MOVE_UP\",\"MButton1\":\"MOVE_DOWN\"}}", "duplicate mouse button");

        rejects("", "empty file");
        rejects("{", "malformed JSON");
        rejects("{\"keyboard\":{}}", "missing controller map");
        rejects("{\"controller\":{}}", "missing keyboard map");
        rejects("{\"controller\":{},\"keyboard\":[]}", "non-object binding map");
        rejects("{\"controller\":{},\"keyboard\":{\"w\":\"MOVE_UP\"}}", "lowercase keyboard name");
        rejects("{\"controller\":{},\"keyboard\":{\"L-Shift\":\"ATTACK_SECONDARY\"}}", "display label");
        rejects("{\"controller\":{},\"keyboard\":{\"META_ALT_ON\":\"MOVE_UP\"}}", "modifier mask is not a key");
        rejects("{\"controller\":{},\"keyboard\":{\"MAX_KEYCODE\":\"MOVE_UP\"}}", "keycode limit is not a key");
        rejects("{\"controller\":{},\"keyboard\":{\"UP\":\"MOVE_UP\",\"DPAD_UP\":\"MOVE_DOWN\"}}", "duplicate key aliases");
        rejects("{\"controller\":{},\"keyboard\":{\"W\":0}}", "numeric action");
        rejects("{\"controller\":{},\"keyboard\":{\"W\":\"MOVE_UP\",\"W\":\"MOVE_DOWN\"}}", "duplicate key");
        rejects("{\"controller\":{\"FACE_SOUTH\":\"JUMP\"},\"keyboard\":{}}", "unknown controller action");
        rejects("{\"controller\":{\" \":\"MOVE_UP\"},\"keyboard\":{}}", "blank controller name");
        rejects("{\"controller\":{\"face_south\":\"ATTACK_PRIMARY\"},\"keyboard\":{}}", "lowercase control name");
        rejects("{\"controller\":{\"42\":\"ATTACK_PRIMARY\"},\"keyboard\":{}}", "raw controller button code");
        rejects("{\"controller\":{\"DPAD_UP\":\"MOVE_UP\",\"DPAD_UP\":\"MOVE_DOWN\"},\"keyboard\":{}}", "duplicate control");
    }

    private static void rejects(String json, String description) {
        throwsType(GdxRuntimeException.class, () -> load(json), description);
    }

    private static InputMap load(String json) {
        return InputSettings.load(new FileHandle("input.setting.json") {
            @Override
            public InputStream read() {
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
        });
    }
}
