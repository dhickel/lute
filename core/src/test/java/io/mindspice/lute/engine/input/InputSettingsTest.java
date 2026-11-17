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

        var custom = load("""
            {"controller":{"FACE_SOUTH":"ATTACK_PRIMARY"},"keyboard":{"Up":"MOVE_UP"}}
            """);
        equal(InputAction.MOVE_UP, custom.getKey(Input.Keys.UP), "configured key replaces default");
        equal(null, custom.getKey(Input.Keys.W), "omitted key stays unbound");
        equal(null, custom.getButton(Input.Buttons.LEFT), "controller data does not map to mouse input");
        equal(null, load("{\"controller\":{},\"keyboard\":{}}").getKey(Input.Keys.W), "empty maps are valid");

        rejects("", "empty file");
        rejects("{", "malformed JSON");
        rejects("{\"keyboard\":{}}", "missing controller map");
        rejects("{\"controller\":{}}", "missing keyboard map");
        rejects("{\"controller\":{},\"keyboard\":[]}", "non-object binding map");
        rejects("{\"controller\":{},\"keyboard\":{\"w\":\"MOVE_UP\"}}", "unknown keyboard name");
        rejects("{\"controller\":{},\"keyboard\":{\"W\":0}}", "numeric action");
        rejects("{\"controller\":{},\"keyboard\":{\"W\":\"MOVE_UP\",\"W\":\"MOVE_DOWN\"}}", "duplicate key");
        rejects("{\"controller\":{\"FACE_SOUTH\":\"JUMP\"},\"keyboard\":{}}", "unknown controller action");
        rejects("{\"controller\":{\" \":\"MOVE_UP\"},\"keyboard\":{}}", "blank controller name");
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
