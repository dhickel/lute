package io.mindspice.lute.engine.data.json;

import io.mindspice.lute.engine.singletons.GameConsts;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LightingDataTest {
    private static final String PULSE = "{\"period_seconds\":1.25,\"radius_strength\":0.5,\"intensity_strength\":0.25}";
    private static final String LIGHT = "{\"color\":\"#Fe901c\",\"radius_pixels\":8,\"intensity\":1,\"pulse\":" + PULSE + "}";
    private static final String ANIMATION = "{\"speed\":2,\"atlas_key\":\"fire/fire\",\"sound_frame\":1,\"frame_interval\":0.125}";
    private static final String COLLISION = "{\"shape\":\"RECT\",\"rotation\":30,\"bounds\":{\"x1\":1,\"y1\":2,\"x2\":4,\"y2\":5}}";
    private static final String AMBIENT = "{\"tint\":\"#7fbeeF\",\"brightness\":0.375}";
    private static final String DEBUG_LAYERS = "{\"physics\":true,\"triggers\":false,\"rawLightMap\":true,\"lightingOutlines\":false}";
    private static final String LEGACY_FIELDS = """
        "unitScale":0.03125,"viewHeight":18,"viewWidth":32,"physicInterval":0.016,
        "physicScaling":{"x":1,"y":1},"cameraSmoothFactor":4,"projectPath":"game.tiled-project",
        "startupMap":"map","mapsPath":"maps/","dataPath":"data/","soundPath":"audio/",
        "musicPath":"audio/","objectPath":"objects/","atlasPath":"atlases/"
        """;
    private static final String SETTINGS_FIELDS = LEGACY_FIELDS + ",\"debugLayers\":" + DEBUG_LAYERS;
    private static int checks;

    public static void main(String[] args) {
        catalogueSchema();
        lightSchema();
        ambientSchema();
        debugLayersSchema();
        System.out.println("LightingDataTest: " + checks + " checks passed");
    }

    private static void catalogueSchema() {
        var empty = object("{\"animation\":null,\"collision\":null,\"emissive\":null}");
        check(empty.animation().isEmpty() && empty.collision().isEmpty() && empty.emissive().isEmpty(), "Explicit nulls bind as empty");
        requiredMembers(LightingDataTest::object, "\"animation\":null", "\"collision\":null", "\"emissive\":null");
        rejects(() -> object("{\"animation\":null,\"collision\":null,\"emssive\":null}"), "emssive is not an alias");
        for (String invalid : List.of("null", "{}", "[]", "{\"fixture\":null}", "{\" \":" + definition(LIGHT) + "}",
            "{\"fixture\":" + definition(LIGHT) + ",\"fixture\":" + definition(LIGHT) + "}", "{\"fixture\":" + definition(LIGHT) + "}{}")) {
            rejects(() -> catalogue(invalid), "Invalid object catalogue: " + invalid);
        }
        String animated = "{\"animation\":" + ANIMATION + ",\"collision\":" + COLLISION + ",\"emissive\":" + LIGHT + "}";
        var bound = object(animated);
        check(bound.animation().orElseThrow().equals(new AnimationData(2, "fire/fire", 1, .125f)), "Existing animation values bind");
        check(bound.collision().orElseThrow().equals(new CollisionData(CollisionData.Shape.RECT, new CharacterBounds(1, 2, 4, 5), 30f)),
            "Existing collision values bind");
        for (String invalid : List.of(animated.replace("fire/fire", " "), animated.replace("\"speed\":2", "\"speed\":0"),
            animated.replace("\"speed\":2", "\"speed\":1,\"speed\":2"), animated.replace("\"speed\":2", "\"other\":0,\"speed\":2"),
            animated.replace("\"RECT\"", "\"CIRCLE\"").replace("\"x2\":4", "\"x2\":5"),
            animated.replace("\"x1\":1", "\"other\":1,\"x1\":1"))) {
            rejects(() -> object(invalid), "Legacy validation: " + invalid);
        }
    }

    private static void lightSchema() {
        var light = light(LIGHT);
        check(light.color().equals("#Fe901c") && light.radiusPixels() == 8 && light.intensity() == 1, "Base emission binds");
        check(light.pulse().orElseThrow().equals(new EmissiveData.Pulse(1.25, .5f, .25f)), "Independent pulse channels bind");
        check(light.worldRadius(1f / 32) == .25f, "Radius converts from pixels to world units");
        check(light(LIGHT.replace(PULSE, "null")).pulse().isEmpty(), "Null pulse binds as empty");
        var steady = light(LIGHT.replace(":0.5", ":0").replace(":0.25", ":0")).pulse().orElseThrow();
        check(steady.radiusStrength() == 0 && steady.intensityStrength() == 0, "Zero strengths allowed");
        requiredMembers(LightingDataTest::parseLight, "\"color\":\"#Fe901c\"", "\"radius_pixels\":8", "\"intensity\":1", "\"pulse\":" + PULSE);
        requiredMembers(json -> parseLight(LIGHT.replace(PULSE, json)), "\"period_seconds\":1.25", "\"radius_strength\":0.5", "\"intensity_strength\":0.25");
        for (String bad : List.of("[]", "true", "\"\"", "\"light\"", "3")) {
            rejects(() -> parseLight(bad), "Invalid emissive: " + bad);
            rejects(() -> parseLight(LIGHT.replace(PULSE, bad)), "Invalid pulse: " + bad);
        }
        invalidValues(LightingDataTest::parseLight, LIGHT, "\"#Fe901c\"", "null", "7", "\"Fe901c\"", "\"#ffaa5588\"", "\"#zz901c\"");
        invalidNumbers("\"radius_pixels\":", "8", "0", "-1", "1e1000", "1e-1000", "3e38", "1.4e-45");
        invalidNumbers("\"intensity\":", "1", "0", "-1", "1e-1000", "1e309", "1.00000000000000000001");
        rejects(() -> parseLight(LIGHT.replace("\"intensity\":1", "\"intensity\":1.4e-45").replace(":0.25", ":0.5")),
            "Underflow at the pulsed intensity trough");
        invalidNumbers("\"period_seconds\":", "1.25", "-1", "0", "1e400", "1e-400");
        for (String[] member : List.of(new String[]{"\"radius_strength\":", "0.5"}, new String[]{"\"intensity_strength\":", "0.25"})) {
            invalidNumbers(member[0], member[1], "-1e-500", "1", "0.99999999999999999", "1e-1000");
        }
        rejects(() -> light.worldRadius(Float.MAX_VALUE), "Overflow converting to world radius");
        var tiny = light(LIGHT.replace("\"radius_pixels\":8", "\"radius_pixels\":1e-40"));
        rejects(() -> tiny.worldRadius(1e-10f), "Underflow converting to world radius");
        rejects(() -> tiny.worldRadius(1.4e-5f), "World radius positive, pulsed trough rounds to zero");
        rejects(() -> light.worldRadius(4e37f), "World radius finite, pulsed peak overflows");
    }

    private static void ambientSchema() {
        var ambient = settings(AMBIENT).nightAmbient();
        check(ambient.equals(new GameConsts.NightAmbient("#7fbeeF", .375f)), "Ambient values bind");
        for (int endpoint : List.of(0, 1)) {
            check(settings(AMBIENT.replace("0.375", Integer.toString(endpoint))).nightAmbient().brightness() == endpoint,
                "Brightness includes " + endpoint);
        }
        rejects(() -> GameConsts.parseSettings("{" + SETTINGS_FIELDS + "}"), "Missing nightAmbient");
        rejects(() -> GameConsts.parseSettings("{\"nightAmbient\":" + AMBIENT + "," + SETTINGS_FIELDS + ",\"nightAmbient\":" + AMBIENT + "}"),
            "Duplicate nightAmbient");
        for (String bad : List.of("null", "true", "[]", "\"ambient\"", "1")) {
            rejects(() -> settings(bad), "Invalid nightAmbient: " + bad);
        }
        requiredMembers(LightingDataTest::settings, "\"tint\":\"#7fbeeF\"", "\"brightness\":0.375");
        invalidValues(LightingDataTest::settings, AMBIENT, "\"#7fbeeF\"", "null", "2", "\"#FFF\"", "\"#00112G\"", "\" 7fbeeF\"");
        invalidValues(LightingDataTest::settings, AMBIENT, "0.375", "null", "\"0\"", "true", "[]", "{}", "-1e-500", "1.000000000000000000001", "1e500");
        String legacy = SETTINGS_FIELDS.replace("\"viewWidth\":32", "\"viewWidth\":31,\"viewWidth\":32")
            .replace("\"x\":1", "\"x\":0,\"x\":1");
        var result = GameConsts.parseSettings("{\"nightAmbient\":" + AMBIENT + "," + legacy + ",\"extra\":{\"nightAmbient\":null}}");
        check(result.viewWidth() == 32 && result.physicScaling().x() == 1, "Legacy settings retain unknown-field and duplicate behavior");
    }

    private static void debugLayersSchema() {
        String prefix = "{" + LEGACY_FIELDS + ",\"nightAmbient\":" + AMBIENT;
        rejects(() -> GameConsts.parseSettings(prefix + "}"), "Missing debugLayers");
        rejects(() -> GameConsts.parseSettings(prefix + ",\"debugLayers\":null,\"debugLayers\":" + DEBUG_LAYERS + "}"),
            "Duplicate debugLayers");
        Consumer<String> parse = value -> GameConsts.parseSettings(prefix + ",\"debugLayers\":" + value + "}");
        for (String bad : List.of("null", "true", "[]", "\"debug\"", "1")) {
            rejects(() -> parse.accept(bad), "Invalid debugLayers: " + bad);
        }
        requiredMembers(parse, "\"physics\":true", "\"triggers\":false", "\"rawLightMap\":true", "\"lightingOutlines\":false");
        var keys = List.of("physics", "triggers", "rawLightMap", "lightingOutlines");
        for (String key : keys) {
            var flags = DataLoader.mapper.createObjectNode();
            for (String field : keys) {
                flags.put(field, field.equals(key));
            }
            var layers = GameConsts.parseSettings(prefix + ",\"debugLayers\":" + flags + "}").debugLayers();
            check(layers.equals(new GameConsts.DebugLayers(key.equals("physics"), key.equals("triggers"),
                key.equals("rawLightMap"), key.equals("lightingOutlines"))), "Binds " + key + " independently");
            invalidValues(parse, flags.toString(), "true", "null", "\"true\"", "1", "0", "[]", "{}");
        }
    }

    private static void requiredMembers(Consumer<String> parse, String... fields) {
        var members = List.of(fields);
        for (String field : members) {
            var edited = new ArrayList<>(members);
            edited.remove(field);
            rejects(() -> parse.accept("{" + String.join(",", edited) + "}"), "Missing " + field);
            edited.add(field);
            edited.add(field);
            rejects(() -> parse.accept("{" + String.join(",", edited) + "}"), "Duplicate " + field);
        }
        rejects(() -> parse.accept("{\"unknown\":0," + String.join(",", members) + "}"), "Unknown member");
    }

    private static void invalidNumbers(String key, String valid, String... invalid) {
        Consumer<String> parse = value -> parseLight(LIGHT.replace(key + valid, key + value));
        for (String bad : List.of("null", "\"" + valid + "\"", "true", "{}", "[]")) {
            rejects(() -> parse.accept(bad), key + bad);
        }
        for (String bad : invalid) { rejects(() -> parse.accept(bad), key + bad); }
    }

    private static void invalidValues(Consumer<String> parse, String json, String original, String... invalid) {
        for (String bad : invalid) { rejects(() -> parse.accept(json.replace(original, bad)), "Invalid " + original + ": " + bad); }
    }

    private static Map<String, ObjectData> catalogue(String json) {
        return DataLoader.parseObjectDefinitions(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static ObjectData object(String json) { return catalogue("{\"fixture\":" + json + "}").get("fixture"); }
    private static String definition(String light) { return "{\"collision\":null,\"animation\":null,\"emissive\":" + light + "}"; }
    private static ObjectData parseLight(String json) { return object(definition(json)); }
    private static EmissiveData light(String json) { return parseLight(json).emissive().orElseThrow(); }
    private static GameConsts.Settings settings(String json) { return GameConsts.parseSettings("{" + SETTINGS_FIELDS + ",\"nightAmbient\":" + json + "}"); }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) { throw new AssertionError(message); }
    }

    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Accepted " + message);
    }
}
