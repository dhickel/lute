package io.mindspice.lute.engine.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Preferences;
import io.mindspice.lute.engine.input.*;
import io.mindspice.lute.engine.singletons.GameConsts.DebugLayers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local overrides; an absent preference always means use the shipped binding or layer. */
final class MenuSettings {
    record Binding(int key, GamepadControl pad) {
        static Binding key(int code) { return new Binding(code, null); }
        static Binding pad(GamepadControl control) { return new Binding(0, control); }
        String id() { return pad == null ? "key." + key : "pad." + pad.name(); }
        String label() { return pad == null ? "Key: " + Input.Keys.toString(key) : "Pad: " + MenuScreen.label(pad); }
        InputAction get(InputMap map) { return pad == null ? map.getKey(key) : map.getGamepad(pad); }
        void set(InputMap map, InputAction action) {
            if (pad == null) { map.setKey(key, action); } else { map.setGamepad(pad, action); }
        }
    }

    enum Layer { PHYSICS, TRIGGERS, RAW_LIGHT_MAP, LIGHTING_OUTLINES }
    final InputMap map;
    private final Preferences prefs;
    private final List<Binding> controls = new ArrayList<>();
    private final boolean[] layers;

    MenuSettings(Preferences prefs, InputMap map, DebugLayers defaults) {
        this.prefs = prefs;
        this.map = map;
        for (int i = 1; i <= Input.Keys.MAX_KEYCODE; i++) { controls.add(Binding.key(i)); }
        for (var pad : GamepadControl.values()) { controls.add(Binding.pad(pad)); }
        for (var control : controls) {
            if (!prefs.contains(control.id())) { continue; }
            String value = prefs.getString(control.id());
            try { control.set(map, value.isEmpty() ? null : InputAction.valueOf(value)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid saved binding: " + control.id(), e); }
        }
        layers = new boolean[]{defaults.physics(), defaults.triggers(), defaults.rawLightMap(), defaults.lightingOutlines()};
        for (var layer : Layer.values()) {
            var value = prefs.getString(layer.name(), Boolean.toString(enabled(layer)));
            if (!value.equals("true") && !value.equals("false")) { throw new IllegalArgumentException("Invalid saved layer: " + layer); }
            layers[layer.ordinal()] = Boolean.parseBoolean(value);
        }
    }

    boolean enabled(Layer layer) { return layers[layer.ordinal()]; }
    DebugLayers layers() { return new DebugLayers(layers[0], layers[1], layers[2], layers[3]); }

    void toggle(Layer layer) {
        boolean next = !enabled(layer);
        save(Map.of(layer.name(), Boolean.toString(next)));
        layers[layer.ordinal()] = next;
    }

    List<Binding> bindings(InputAction action) { return controls.stream().filter(c -> c.get(map) == action).toList(); }

    void change(Binding previous, Binding next, InputAction action) {
        var changes = new LinkedHashMap<String, String>();
        if (previous != null) { changes.put(previous.id(), ""); }
        if (next != null) { changes.put(next.id(), action.name()); }
        save(changes);
        if (previous != null) { previous.set(map, null); }
        if (next != null) { next.set(map, action); }
    }

    private void save(Map<String, String> changes) {
        var before = new LinkedHashMap<String, String>();
        for (var key : changes.keySet()) { before.put(key, prefs.contains(key) ? prefs.getString(key) : null); }
        try {
            changes.forEach(prefs::putString);
            prefs.flush();
        } catch (RuntimeException failure) {
            before.forEach((key, value) -> { if (value == null) { prefs.remove(key); } else { prefs.putString(key, value); } });
            throw new IllegalStateException("Could not save options", failure);
        }
    }
}
