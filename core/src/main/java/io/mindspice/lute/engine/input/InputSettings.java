package io.mindspice.lute.engine.input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxRuntimeException;
import io.mindspice.lute.engine.data.json.DataLoader;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

import java.io.IOException;

public final class InputSettings {
    private InputSettings() { }

    public static InputMap load(FileHandle file) {
        try (var stream = file.read()) {
            var settings = DataLoader.mapper.reader()
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(stream);
            if (!settings.isObject() || settings.size() != 2
                || !settings.path("keyboard").isObject() || !settings.path("controller").isObject()) {
                throw new IllegalArgumentException("Expected keyboard and controller binding maps");
            }

            var inputMap = new InputMap();
            for (var binding : settings.get("keyboard").properties()) {
                String key = binding.getKey();
                int keyCode = Input.Keys.valueOf(key);
                if (keyCode <= Input.Keys.UNKNOWN || keyCode > Input.Keys.MAX_KEYCODE) {
                    throw new IllegalArgumentException("Unknown keyboard key: " + key);
                }
                inputMap.setKey(keyCode, readAction("keyboard." + key, binding.getValue()));
            }

            // Controller names are logical identifiers, not mouse or native button codes.
            for (var binding : settings.get("controller").properties()) {
                if (binding.getKey().isBlank()) {
                    throw new IllegalArgumentException("Controller button name must not be blank");
                }
                readAction("controller." + binding.getKey(), binding.getValue());
            }
            return inputMap;
        } catch (IOException | RuntimeException failure) {
            throw new GdxRuntimeException("Failed to load " + file.path() + ": " + failure.getMessage(), failure);
        }
    }

    private static InputAction readAction(String binding, JsonNode value) {
        if (value.isString()) {
            try {
                return InputAction.valueOf(value.stringValue());
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Unknown action for " + binding + ": " + value, failure);
            }
        }
        throw new IllegalArgumentException("Expected an action name for " + binding);
    }
}
