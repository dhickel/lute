package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.JsonReader;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.MapTriggers.SensorType;
import io.mindspice.lute.engine.tiled.MapTriggers.SpawnType;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class MapTriggersTest {
    private static int rejected;

    public static void main(String[] args) {
        float previousScale = GameConsts.UNIT_SCALE;
        try {
            GameConsts.UNIT_SCALE = 1f / 32f;
            projectSchema();
            sampleMap();
            classDefaultsAndCoordinates();
            groupedLayers();
            invalidGeometry();
            invalidProperties();
            invalidNamesAndReferences();
            System.out.println("MapTriggersTest: passed (" + rejected + " rejection cases; no graphics context or game launch)");
        } finally { GameConsts.UNIT_SCALE = previousScale; }
    }

    private static void projectSchema() {
        var types = new JsonReader().parse(new FileHandle("assets/game.tiled-project")).get("propertyTypes");
        var ids = new HashSet<Integer>();
        var names = new HashSet<String>();
        for (var type : types) {
            check(ids.add(type.getInt("id")), "Project property type IDs must be unique");
            check(names.add(type.getString("name")), "Project property type names must be unique");
            if ("SensorType".equals(type.getString("name")) || "SpawnType".equals(type.getString("name"))) {
                check("enum".equals(type.getString("type")) && "string".equals(type.getString("storageType"))
                    && !type.getBoolean("valuesAsFlags"), "Trigger enums must be single string values");
                var values = new HashSet<String>();
                for (var value : type.get("values")) { values.add(value.asString()); }
                check(values.equals("SensorType".equals(type.getString("name"))
                    ? Set.of("GENERAL") : Set.of("PLAYER", "TELEPORT", "NPC", "ITEM")), "Project enum values must match Java");
            }
        }
        check(names.containsAll(Set.of("SensorType", "SpawnType", "Sensor", "Teleport", "SpawnPoint", "SpawnArea")),
            "Project must define all trigger classes and enums");
        check(!names.contains("PlayerCharacter"), "SpawnPoint replaces the obsolete PlayerCharacter authoring class");
    }

    private static void sampleMap() {
        TiledMap map = new PropertyLoader().sample();
        try {
            var triggers = MapTriggers.parse(map);
            check(triggers.sensors().size() == 2 && triggers.points().size() == 2 && triggers.areas().isEmpty(),
                "Sample must contain a test sensor, teleport, player point, and destination point");
            var testSensor = triggers.sensors().stream().filter(s -> s.name().equals("TestSensor")).findFirst().orElseThrow();
            check(testSensor.type() == SensorType.GENERAL && testSensor.targetMap() == null, "Sample test sensor is general");
            var player = triggers.points().stream().filter(p -> p.type() == SpawnType.PLAYER).findFirst().orElseThrow();
            check(player.name().equals("PlayerSpawn"), "Sample player marker name is preserved");
            near(player.position().x, 302.248f, "Sample player x stays in pixels");
            near(player.position().y, 640 - 561.672f, "Sample player y is flipped exactly once by the loader");
            var teleport = triggers.sensors().stream().filter(s -> s.targetMap() != null).findFirst().orElseThrow();
            check(teleport.targetMap().equals("THIS") && teleport.type() == SensorType.GENERAL,
                "Sample teleport is a GENERAL sensor targeting THIS");
            var destination = triggers.teleportDestination(teleport.targetSpawn());
            near(destination.position().x, 560, "Sample teleport destination x pixels");
            near(destination.position().y, 240, "Sample teleport destination y pixels");
            rectangle(teleport.bounds(), 432.667f / 32, (640 - 275.333f - 35.3333f) / 32,
                34.6667f / 32, 35.3333f / 32);
            check(map.getLayers().get("Objects").getObjects().getCount() == 6,
                "Unrelated sample tile objects must remain intact");
        } finally { map.dispose(); }
    }

    private static void classDefaultsAndCoordinates() {
        TiledMap map = fixture(layer("Trigger", "",
            rectangle("sense", "Sensor", "")
                + point("player", "", "")
                + rectangle("area", "SpawnArea", "")
                + teleport("door", "", "landing")
                + point("landing", "TELEPORT", "")
                + point("npc", "NPC", "") + point("item", "ITEM", "")
                + rectangle("itemArea", "SpawnArea", property("spawnType", "ITEM"))
                + rectangle("playerArea", "SpawnArea", property("spawnType", "PLAYER"))));
        try {
            var triggers = MapTriggers.parse(map);
            var sensor = triggers.sensors().getFirst();
            check(sensor.type() == SensorType.GENERAL && sensor.targetMap() == null && sensor.targetSpawn() == null,
                "Generic sensor inherits GENERAL and has no teleport target");
            rectangle(sensor.bounds(), 1, 17, 2, 1);
            var player = triggers.points().getFirst();
            check(player.type() == SpawnType.PLAYER, "SpawnPoint inherits PLAYER default");
            near(player.position().x, 96, "Point remains in pixels");
            near(player.position().y, 512, "Point is not flipped twice");
            check(triggers.areas().getFirst().type() == SpawnType.NPC, "SpawnArea inherits NPC default");
            rectangle(triggers.areas().getFirst().bounds(), 1, 17, 2, 1);
            check(triggers.sensors().get(1).targetMap().equals("THIS"), "Teleport inherits THIS default");
            check(triggers.points().get(2).type() == SpawnType.NPC && triggers.points().get(3).type() == SpawnType.ITEM,
                "Non-player points remain parsed data");
            check(triggers.areas().get(1).type() == SpawnType.ITEM && triggers.areas().get(2).type() == SpawnType.PLAYER,
                "Area categories do not create or count as player points");
            var original = ((RectangleMapObject) map.getLayers().get("Trigger").getObjects().get("sense")).getRectangle();
            near(original.width, 64, "Parsing does not scale the map object's rectangle in place");
            original.x = 999;
            ((PointMapObject) map.getLayers().get("Trigger").getObjects().get("player")).getPoint().x = 999;
            near(sensor.bounds().x, 1, "Parsed bounds do not alias the map");
            near(player.position().x, 96, "Parsed point does not alias the map");
            rejects("missing public lookup", "TELEPORT SpawnPoint", () -> triggers.teleportDestination("absent"));
            rejects("non-teleport public lookup", "TELEPORT SpawnPoint", () -> triggers.teleportDestination("player"));
        } finally { map.dispose(); }

        GameConsts.UNIT_SCALE = .5f;
        try {
            var triggers = parse(layer("Triggers", "", rectangle("sensor", "Sensor", "") + point("p", "NPC", "")));
            rectangle(triggers.sensors().getFirst().bounds(), 16, 272, 32, 16);
            near(triggers.points().getFirst().position().x, 96, "Point units do not depend on UNIT_SCALE");
        } finally { GameConsts.UNIT_SCALE = 1f / 32f; }
    }

    private static void groupedLayers() {
        var parsed = parse("<group name=\"Outer\"><group name=\"Inner\">"
            + layer("Triggers", "", rectangle("nested", "Sensor", "")) + "</group></group>"
            + "<group name=\"Trigger\">" + layer("Markers", "", point("p", "NPC", "")) + "</group>"
            + layer("Unrelated", "offsetx=\"10\"", "<object name=\"ignored\"/>"));
        check(parsed.sensors().size() == 1 && parsed.points().size() == 1,
            "Traverse nested trigger layers and descendants of trigger groups, ignoring unrelated objects");
        check(parse(layer("Other", "", "<object name=\"ignored\"/>")).sensors().isEmpty(), "No trigger layer is valid");
        for (String transform : new String[]{"offsetx=\"1\"", "offsety=\"-2\"", "offsetx=\"NaN\"",
            "offsety=\"Infinity\"", "parallaxx=\"0.5\"", "parallaxy=\"2\""}) {
            invalid("trigger layer transform", layer("Triggers", transform, rectangle("s", "Sensor", "")), "offsets or parallax");
            invalid("ancestor transform", "<group " + transform + ">"
                + layer("Trigger", "", point("p", "NPC", "")) + "</group>", "offsets or parallax");
        }
        invalid("tile trigger layer", "<layer name=\"Triggers\" width=\"1\" height=\"1\"><data encoding=\"csv\">0</data></layer>", "object layer");
    }

    private static void invalidGeometry() {
        for (String type : new String[]{"Sensor", "Teleport", "SpawnArea"}) {
            String props = type.equals("Teleport") ? property("targetSpawn", "p") + property("targetMap", "elsewhere") : "";
            invalid(type + " point", layer("Triggers", "", object("bad", type, "x=\"1\" y=\"2\"", props, "<point/>")), "rectangle");
            for (String shape : new String[]{"<ellipse/>", "<polygon points=\"0,0 32,0 0,32\"/>", "<polyline points=\"0,0 32,32\"/>"}) {
                invalid(type + " shape", layer("Triggers", "", object("bad", type,
                    "x=\"1\" y=\"2\" width=\"32\" height=\"32\"", props, shape)), "rectangle");
            }
        }
        invalid("rectangle SpawnPoint", layer("Triggers", "", rectangle("bad", "SpawnPoint", "")), "point geometry");
        for (String dimensions : new String[]{"width=\"0\" height=\"32\"", "width=\"32\" height=\"0\"",
            "width=\"-1\" height=\"32\"", "width=\"32\" height=\"-1\""}) {
            invalid("rectangle dimensions", layer("Triggers", "", object("bad", "Sensor", "x=\"0\" y=\"0\" " + dimensions, "", "")), "positive");
        }
        for (String coordinates : new String[]{"x=\"NaN\"", "y=\"Infinity\"", "width=\"NaN\"", "height=\"Infinity\""}) {
            invalid("nonfinite rectangle", layer("Triggers", "", object("bad", "Sensor", coordinates, "", "")), "finite");
        }
        for (String attributes : new String[]{"x=\"NaN\"", "y=\"-Infinity\""}) {
            invalid("nonfinite point", layer("Triggers", "", point("bad", "NPC", attributes)), "finite");
        }
        invalid("point with height", layer("Triggers", "", point("bad", "NPC", "height=\"32\"")), "zero height");
        invalid("point with width", layer("Triggers", "", point("bad", "NPC", "width=\"32\"")), "zero width");
        for (String rotation : new String[]{"45", "NaN", "Infinity"}) {
            invalid("rotated rectangle", layer("Triggers", "", object("bad", "Sensor",
                "width=\"32\" height=\"32\" rotation=\"" + rotation + "\"", "", "")), rotation.equals("45") ? "rotated" : "finite");
            invalid("rotated point", layer("Triggers", "", point("bad", "NPC", "rotation=\"" + rotation + "\"")),
                rotation.equals("45") ? "rotated" : "finite");
        }
        var map = fixture(layer("Triggers", "", rectangle("bad", "Sensor", "")));
        try {
            map.getProperties().put("orientation", "isometric");
            rejects("nonorthogonal map", "orthogonal", () -> MapTriggers.parse(map));
        } finally { map.dispose(); }
    }

    private static void invalidProperties() {
        for (String type : new String[]{"Sensor", "SpawnPoint", "SpawnArea"}) {
            String key = type.equals("Sensor") ? "sensorType" : "spawnType";
            for (String value : new String[]{"BOGUS", "general", " ", ""}) {
                String object = type.equals("SpawnPoint")
                    ? object("bad", type, "", property(key, value), "<point/>") : rectangle("bad", type, property(key, value));
                invalid("enum value", layer("Triggers", "", object), key);
            }
            String wrongType = "<property name=\"" + key + "\" type=\"int\" value=\"1\"/>";
            invalid("enum property type", layer("Triggers", "", type.equals("SpawnPoint")
                ? object("bad", type, "", wrongType, "<point/>") : rectangle("bad", type, wrongType)), key);
        }
        for (String key : new String[]{"targetMap", "targetSpawn"}) {
            for (String value : new String[]{"", " "}) {
                String props = property("targetMap", key.equals("targetMap") ? value : "THIS")
                    + property("targetSpawn", key.equals("targetSpawn") ? value : "landing");
                invalid("blank teleport property", layer("Trigger", "", rectangle("bad", "Teleport", props)), key);
            }
            String props = "<property name=\"" + key + "\" type=\"bool\" value=\"true\"/>"
                + property(key.equals("targetMap") ? "targetSpawn" : "targetMap", "elsewhere");
            invalid("teleport property type", layer("Triggers", "", rectangle("bad", "Teleport", props)), key);
        }
        invalid("missing targetSpawn", layer("Triggers", "", rectangle("bad", "Teleport", "")), "targetSpawn");
        var map = fixture(layer("Triggers", "", rectangle("bad", "Sensor", "")));
        try {
            var props = map.getLayers().get("Triggers").getObjects().get(0).getProperties();
            props.remove("sensorType");
            rejects("missing enum with no loaded default", "sensorType", () -> MapTriggers.parse(map));
            props.put("sensorType", "GENERAL");
            props.put("rotation", "0");
            rejects("rotation property type", "numeric", () -> MapTriggers.parse(map));
        } finally { map.dispose(); }
    }

    private static void invalidNamesAndReferences() {
        for (String name : new String[]{"", " "}) {
            invalid("blank object name", layer("Triggers", "", rectangle(name, "Sensor", "")), "name is required");
        }
        invalid("missing object name", layer("Triggers", "", "<object type=\"Sensor\" width=\"32\" height=\"32\"/>"), "name is required");
        invalid("unknown class", layer("Triggers", "", rectangle("bad", "PlayerCharacter", "")), "unsupported trigger class");
        invalid("missing class", layer("Triggers", "", "<object name=\"bad\" width=\"32\" height=\"32\"/>"), "type");
        invalid("duplicate sensors", layer("Triggers", "", rectangle("same", "Sensor", "")
            + teleport("same", "elsewhere", "unused")), "Duplicate sensor");
        invalid("duplicate points", layer("Triggers", "", point("same", "NPC", "") + point("same", "TELEPORT", "")), "Duplicate spawn marker");
        invalid("duplicate areas", layer("Triggers", "", rectangle("same", "SpawnArea", "")
            + rectangle("same", "SpawnArea", "")), "Duplicate spawn marker");
        invalid("point area collision", layer("Triggers", "", point("same", "TELEPORT", "")
            + rectangle("same", "SpawnArea", "")), "Duplicate spawn marker");
        invalid("duplicates across layers", layer("Trigger", "", point("same", "NPC", ""))
            + layer("Triggers", "", point("same", "ITEM", "")), "Duplicate spawn marker");
        invalid("multiple players", layer("Trigger", "", point("one", "PLAYER", ""))
            + "<group>" + layer("Triggers", "", point("two", "PLAYER", "")) + "</group>", "one PLAYER");
        for (String targetMap : new String[]{"THIS", "this", "ThIs"}) {
            invalid("missing local reference", layer("Triggers", "", teleport("door", targetMap, "absent")), "TELEPORT SpawnPoint");
            invalid("NPC local reference", layer("Triggers", "", teleport("door", targetMap, "p") + point("p", "NPC", "")), "TELEPORT SpawnPoint");
            invalid("PLAYER local reference", layer("Triggers", "", teleport("door", targetMap, "p") + point("p", "PLAYER", "")), "TELEPORT SpawnPoint");
            invalid("area local reference", layer("Triggers", "", teleport("door", targetMap, "p")
                + rectangle("p", "SpawnArea", property("spawnType", "TELEPORT"))), "TELEPORT SpawnPoint");
            check(parse(layer("Triggers", "", teleport("door", targetMap, "p") + point("p", "TELEPORT", "")))
                .teleportDestination("p").type() == SpawnType.TELEPORT, "THIS references are case insensitive");
        }
        check(parse(layer("Triggers", "", teleport("door", "another-map", "not-local"))).points().isEmpty(),
            "Non-THIS targets require only nonblank strings, not local references");
    }

    private static String layer(String name, String attributes, String objects) {
        return "<objectgroup name=\"" + name + "\" " + attributes + ">" + objects + "</objectgroup>";
    }

    private static String object(String name, String type, String attributes, String properties, String shape) {
        return "<object name=\"" + name + "\" type=\"" + type + "\" " + attributes + ">"
            + (properties.isEmpty() ? "" : "<properties>" + properties + "</properties>") + shape + "</object>";
    }

    private static String rectangle(String name, String type, String properties) {
        return object(name, type, "x=\"32\" y=\"64\" width=\"64\" height=\"32\"", properties, "");
    }

    private static String point(String name, String type, String attributes) {
        String coords = (attributes.contains("x=") ? "" : "x=\"96\" ") + (attributes.contains("y=") ? "" : "y=\"128\" ");
        return object(name, "SpawnPoint", coords + attributes, type.isEmpty() ? "" : property("spawnType", type), "<point/>");
    }

    private static String teleport(String name, String map, String destination) {
        return rectangle(name, "Teleport", (map.isEmpty() ? "" : property("targetMap", map)) + property("targetSpawn", destination));
    }

    private static String property(String name, String value) {
        return "<property name=\"" + name + "\" value=\"" + value + "\"/>";
    }

    private static TiledMap fixture(String layers) { return new PropertyLoader().fixture(layers); }

    private static MapTriggers parse(String layers) {
        var map = fixture(layers);
        try { return MapTriggers.parse(map); }
        finally { map.dispose(); }
    }

    private static void invalid(String label, String layers, String message) {
        var map = fixture(layers);
        try { rejects(label, message, () -> MapTriggers.parse(map)); }
        finally { map.dispose(); }
    }

    private static void rejects(String label, String message, Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains(message), label + ": unexpected message: " + expected.getMessage());
            rejected++;
            return;
        }
        throw new AssertionError(label + ": invalid trigger data was accepted");
    }

    private static void rectangle(Rectangle actual, float x, float y, float width, float height) {
        near(actual.x, x, "Rectangle world x");
        near(actual.y, y, "Rectangle world y");
        near(actual.width, width, "Rectangle world width");
        near(actual.height, height, "Rectangle world height");
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < .0001f, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class PropertyLoader extends FixedTmxMapLoader {
        PropertyLoader() { super(path -> new FileHandle("assets/" + path)); }

        TiledMap sample() {
            var file = resolve("maps/test_tiled.tmx");
            root = xml.parse(file);
            return loadTiledMap(file, parameters(), imagePath -> {
                try {
                    var image = ImageIO.read(new java.io.File(imagePath));
                    return new TextureRegion(new DimensionTexture(image.getWidth(), image.getHeight()));
                } catch (IOException failure) { throw new AssertionError(failure); }
            });
        }

        TiledMap fixture(String layers) {
            root = xml.parse("<map orientation=\"orthogonal\" width=\"30\" height=\"20\" tilewidth=\"32\" tileheight=\"32\">"
                + layers + "</map>");
            return loadTiledMap(resolve("maps/trigger-parser-test.tmx"), parameters(), imagePath -> {
                throw new AssertionError("Parser fixture must not load textures");
            });
        }

        private static BaseTiledMapLoader.Parameters parameters() {
            var parameters = new BaseTiledMapLoader.Parameters();
            parameters.projectFilePath = "game.tiled-project";
            return parameters;
        }
    }

    private static final class DimensionTexture extends Texture {
        private final int width, height;
        DimensionTexture(int width, int height) { super(); this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }
}
