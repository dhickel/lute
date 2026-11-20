package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
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
            detectorShapes();
            collisionEllipsesMatchDetectors();
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
                    ? Set.of("GENERAL") : java.util.Arrays.stream(SpawnType.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet())), "Project enum values must match Java");
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
            check(triggers.sensors().size() == 2 && triggers.points().size() == 4 && triggers.areas().isEmpty(),
                "Sample must contain a test sensor, teleport, player point, destination point, and two actor points");
            check(triggers.points().stream().anyMatch(p -> p.type() == SpawnType.NPC && p.name().equals("NpcSpawn"))
                && triggers.points().stream().anyMatch(p -> p.type() == SpawnType.ENEMY && p.name().equals("EnemySpawn")),
                "Sample keeps explicitly typed actor points");
            var testSensor = triggers.sensors().stream().filter(s -> s.name().equals("TestSensor")).findFirst().orElseThrow();
            check(testSensor.type() == SensorType.GENERAL && testSensor.targetMap() == null, "Sample test sensor is general");
            var player = triggers.points().stream().filter(p -> p.type() == SpawnType.PLAYER).findFirst().orElseThrow();
            check(player.name().equals("PlayerSpawn"), "Sample player marker name is preserved");
            near(player.position().x, 302.248f, "Sample player x stays in pixels");
            near(player.position().y, 640 - 561.672f, "Sample player y is flipped exactly once by the loader");
            var teleport = triggers.sensors().stream().filter(s -> s.targetMap() != null).findFirst().orElseThrow();
            check(teleport.type() == SensorType.GENERAL, "Sample teleport is a GENERAL sensor");
            if ("THIS".equalsIgnoreCase(teleport.targetMap())) {
                triggers.teleportDestination(teleport.targetSpawn());
            } else if ("test_tiled".equals(teleport.targetMap())) {
                triggers.travelDestination(teleport.targetSpawn());
            }
            var destination = triggers.teleportDestination("TeleportDestination");
            near(destination.position().x, 560, "Sample teleport destination x pixels");
            near(destination.position().y, 240, "Sample teleport destination y pixels");
            geometryRectangle(teleport.geometry(), 432.667f / 32, (640 - 275.333f - 35.3333f) / 32,
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
            geometryRectangle(sensor.geometry(), 1, 17, 2, 1);
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
            near(sensor.geometry().polygons().getFirst()[0], 1, "Parsed geometry does not alias the map");
            near(player.position().x, 96, "Parsed point does not alias the map");
            rejects("missing public lookup", "TELEPORT SpawnPoint", () -> triggers.teleportDestination("absent"));
            rejects("non-teleport public lookup", "TELEPORT SpawnPoint", () -> triggers.teleportDestination("player"));
        } finally { map.dispose(); }

        GameConsts.UNIT_SCALE = .5f;
        try {
            var triggers = parse(layer("Triggers", "", rectangle("sensor", "Sensor", "") + point("p", "NPC", "")));
            geometryRectangle(triggers.sensors().getFirst().geometry(), 16, 272, 32, 16);
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

    private static void detectorShapes() {
        GdxNativesLoader.load();
        Box2D.init();
        for (String type : new String[]{"Sensor", "Teleport"}) {
            for (float rotation : new float[]{0, 90, -30, 360}) {
                String attributes = "x=\"64\" y=\"96\" width=\"64\" height=\"32\" rotation=\"" + rotation + "\"";
                var rectangle = detector(type, attributes, "");
                check(rectangle.polygons().size() == 1, "Rectangle remains one convex fixture");
                outline(rectangle.polygons().getFirst(), new float[]{64, 512, 128, 512, 128, 544, 64, 544},
                    64, 544, rotation);
                nativeCoverage(rectangle, rotated(96, 528, 64, 544, rotation), true);

                var ellipse = detector(type, attributes, "<ellipse/>");
                check(ellipse.radius() == 0 && ellipse.polygons().size() == 1
                    && ellipse.polygons().getFirst().length == 16, "Noncircular ellipse has exactly eight sides");
                float[] ellipseOutline = new float[16];
                for (int i = 0; i < 8; i++) {
                    ellipseOutline[2 * i] = 96 + (float) Math.cos(i * Math.PI / 4) * 32;
                    ellipseOutline[2 * i + 1] = 528 + (float) Math.sin(i * Math.PI / 4) * 16;
                }
                outline(ellipse.polygons().getFirst(), ellipseOutline, 64, 544, rotation);
                nativeCoverage(ellipse, rotated(96, 528, 64, 544, rotation), true);

                var circle = detector(type, attributes.replace("width=\"64\"", "width=\"32\""), "<ellipse/>");
                check(circle.polygons().isEmpty(), "Circle uses a native circle, not a polygon");
                near(circle.radius(), .5f, "Circle radius is in world units");
                var center = rotated(80, 528, 64, 544, rotation);
                near(circle.center().x, center.x, "Circle rotated center x");
                near(circle.center().y, center.y, "Circle rotated center y");
                nativeCoverage(circle, center, true);
                nativeCoverage(circle, new Vector2(center.x + .6f, center.y), false);

                var triangle = detector(type, attributes, "<polygon points=\"0,0 64,0 0,32\"/>");
                check(triangle.polygons().size() == 1, "Triangle is one fixture");
                outline(triangle.polygons().getFirst(), new float[]{64, 544, 128, 544, 64, 512}, 64, 544, rotation);
                nativeCoverage(triangle, rotated(80, 536, 64, 544, rotation), true);

                var concave = detector(type, attributes,
                    "<polygon points=\"0,0 96,0 96,96 64,96 64,32 32,32 32,96 0,96\"/>");
                check(concave.polygons().size() == 6, "Concave U outline is triangulated into six solid pieces");
                near((float) area(concave), 7, "Triangulation preserves the U area without filling its notch");
                nativeCoverage(concave, rotated(112, 480, 64, 544, rotation), false);
                nativeCoverage(concave, rotated(80, 480, 64, 544, rotation), true);
                nativeCoverage(concave, rotated(112, 528, 64, 544, rotation), true);

                StringBuilder points = new StringBuilder();
                double expectedArea = 0;
                float[] decagon = new float[20];
                for (int i = 0; i < 10; i++) {
                    float x = (float) Math.cos(i * Math.PI / 5) * 64;
                    float y = (float) Math.sin(i * Math.PI / 5) * 64;
                    points.append(x).append(',').append(y).append(' ');
                    decagon[2 * i] = x / 32;
                    decagon[2 * i + 1] = -y / 32;
                }
                for (int i = 0; i < 10; i++) {
                    int next = (i + 1) % 10;
                    expectedArea += decagon[2 * i] * decagon[2 * next + 1] - decagon[2 * next] * decagon[2 * i + 1];
                }
                var many = detector(type, attributes, "<polygon points=\"" + points + "\"/>");
                check(many.polygons().size() == 8, "More than eight vertices are split into convex pieces");
                near((float) area(many), (float) Math.abs(expectedArea / 2), "Decagon decomposition preserves area");
                nativeCoverage(many, rotated(64, 544, 64, 544, rotation), true);
            }
            var reversed = detector(type, "x=\"64\" y=\"96\"", "<polygon points=\"0,32 64,0 0,0\"/>");
            near((float) area(reversed), 1, "Both polygon windings are supported");
            for (float rotation : new float[]{0, 6.5f, 13.3f, 20, 90, 120}) {
                var collinear = detector(type, "x=\"64\" y=\"96\" rotation=\"" + rotation + "\"",
                    "<polygon points=\"0,0 32,0 64,0 64,32 0,32\"/>");
                check(collinear.polygons().size() == 1 && collinear.polygons().getFirst().length == 8,
                    "Redundant straight-edge vertices are removed before rotation, without tiny ears");
                nativeCoverage(collinear, rotated(96, 528, 64, 544, rotation), true);
            }
        }
    }

    private static void collisionEllipsesMatchDetectors() {
        for (float rotation : new float[]{0, 90, 33}) {
            for (float height : new float[]{64, 62, 32}) {
                var object = new EllipseMapObject(64, 512, 64, height);
                object.getProperties().put("rotation", rotation);
                var detector = DetectorGeometryParser.parse(object, rotation);
                var collision = TiledPhysics.fixtureDefOf(object, new Vector2(1, 1), Vector2.Zero);
                try {
                    if (height == 64) {
                        check(collision.shape instanceof CircleShape, "True circle uses a circle fixture");
                        var circle = (CircleShape) collision.shape;
                        near(circle.getRadius(), detector.radius(), "Circle collision radius matches detector");
                        near(circle.getPosition().x, detector.center().x, "Circle collision center x matches detector");
                        near(circle.getPosition().y, detector.center().y, "Circle collision center y matches detector");
                    } else {
                        check(collision.shape instanceof PolygonShape, "Noncircular ellipse uses a polygon fixture");
                        var polygon = (PolygonShape) collision.shape;
                        check(polygon.getVertexCount() == 8, "Ellipse collision has eight vertices");
                        var expected = detector.polygons().getFirst();
                        var vertex = new Vector2();
                        for (int i = 0; i < polygon.getVertexCount(); i++) {
                            polygon.getVertex(i, vertex);
                            boolean found = false;
                            for (int j = 0; j < expected.length; j += 2) {
                                if (Math.abs(vertex.x - expected[j]) < .0001f
                                    && Math.abs(vertex.y - expected[j + 1]) < .0001f) { found = true; }
                            }
                            check(found, "Ellipse collision vertex matches detector: " + vertex);
                        }
                    }
                } finally { collision.shape.dispose(); }
            }
        }
        var circle = new EllipseMapObject(64, 512, 64, 64);
        var scaled = TiledPhysics.fixtureDefOf(circle, new Vector2(2, 1), new Vector2(1, 2));
        try {
            check(scaled.shape instanceof PolygonShape, "Nonuniformly scaled circle uses an ellipse fixture");
            var polygon = (PolygonShape) scaled.shape;
            var vertex = new Vector2();
            boolean found = false;
            for (int i = 0; i < polygon.getVertexCount(); i++) {
                polygon.getVertex(i, vertex);
                if (Math.abs(vertex.x - 7) < .0001f && Math.abs(vertex.y - 15) < .0001f) { found = true; }
            }
            check(found, "Ellipse collision applies scaling and relative offset");
        } finally { scaled.shape.dispose(); }
    }

    private static String detectorProperties(String type) {
        return type.equals("Teleport") ? property("targetSpawn", "unused") + property("targetMap", "elsewhere") : "";
    }

    private static MapTriggers.DetectorGeometry detector(String type, String attributes, String shape) {
        var sensors = parse(layer("Triggers", "", object("detector", type, attributes, detectorProperties(type), shape))).sensors();
        check(sensors.size() == 1 && sensors.getFirst().type() == SensorType.GENERAL, "Each shape remains one GENERAL detector");
        check(type.equals("Teleport") == (sensors.getFirst().targetMap() != null), "Teleport metadata survives geometry parsing");
        return sensors.getFirst().geometry();
    }

    private static Vector2 rotated(float x, float y, float pivotX, float pivotY, float rotation) {
        double radians = -rotation * Math.PI / 180;
        return new Vector2((float) (pivotX + (x - pivotX) * Math.cos(radians) - (y - pivotY) * Math.sin(radians)) / 32,
            (float) (pivotY + (x - pivotX) * Math.sin(radians) + (y - pivotY) * Math.cos(radians)) / 32);
    }

    private static void outline(float[] actual, float[] pixels, float pivotX, float pivotY, float rotation) {
        check(actual.length == pixels.length, "Outline retains expected vertex count");
        for (int i = 0; i < pixels.length; i += 2) {
            var expected = rotated(pixels[i], pixels[i + 1], pivotX, pivotY, rotation);
            boolean found = false;
            for (int j = 0; j < actual.length; j += 2) {
                if (Math.abs(actual[j] - expected.x) < .0001f && Math.abs(actual[j + 1] - expected.y) < .0001f) { found = true; }
            }
            check(found, "Outline matches Tiled clockwise rotation around loaded top-left/object anchor: " + expected);
        }
    }

    private static double area(MapTriggers.DetectorGeometry geometry) {
        double area = 0;
        for (float[] polygon : geometry.polygons()) {
            double pieceArea = 0;
            for (int i = 0; i < polygon.length; i += 2) {
                int next = (i + 2) % polygon.length;
                pieceArea += (double) polygon[i] * polygon[next + 1] - (double) polygon[next] * polygon[i + 1];
            }
            check(pieceArea > 0, "Every convex piece is counterclockwise and nondegenerate");
            area += pieceArea / 2;
        }
        return area;
    }

    private static void nativeCoverage(MapTriggers.DetectorGeometry geometry, Vector2 point, boolean expected) {
        var world = new World(new Vector2(), true);
        try {
            var body = world.createBody(new BodyDef());
            if (geometry.radius() > 0) {
                var shape = new CircleShape();
                try {
                    shape.setPosition(geometry.center());
                    shape.setRadius(geometry.radius());
                    body.createFixture(shape, 0);
                } finally { shape.dispose(); }
            } else {
                for (float[] polygon : geometry.polygons()) {
                    check(polygon.length >= 6 && polygon.length <= 16, "Every fixture fits Box2D's vertex limit");
                    var shape = new PolygonShape();
                    try {
                        shape.set(polygon);
                        check(shape.getVertexCount() == polygon.length / 2, "Box2D keeps all supplied convex vertices");
                        body.createFixture(shape, 0);
                    } finally { shape.dispose(); }
                }
            }
            boolean covered = false;
            for (var fixture : body.getFixtureList()) { covered |= fixture.testPoint(point); }
            check(covered == expected, "Native filled detector coverage at " + point + " should be " + expected);
        } finally { world.dispose(); }
    }

    private static void invalidGeometry() {
        for (String type : new String[]{"Sensor", "Teleport", "SpawnArea"}) {
            String props = detectorProperties(type);
            for (String shape : new String[]{"<point/>", "<polyline points=\"0,0 32,32\"/>",
                "<text>not a detector</text>"}) {
                invalid(type + " excluded shape", layer("Triggers", "", object("bad", type,
                    "x=\"1\" y=\"2\" width=\"32\" height=\"32\"", props, shape)), "rectangle");
            }
            invalid(type + " tile", "<tileset firstgid=\"1\" name=\"fixture\" tilewidth=\"32\" tileheight=\"32\" tilecount=\"1\" columns=\"1\">"
                + "<image source=\"fixture.png\" width=\"32\" height=\"32\"/></tileset>"
                + layer("Triggers", "", object("bad", type, "gid=\"1\" x=\"32\" y=\"64\"", props, "")), "rectangle");
            if (type.equals("SpawnArea")) {
                for (String shape : new String[]{"<ellipse/>", "<polygon points=\"0,0 32,0 0,32\"/>"}) {
                    invalid("SpawnArea shape", layer("Triggers", "", object("bad", type,
                        "width=\"32\" height=\"32\"", props, shape)), "rectangle");
                }
            }
        }
        for (String type : new String[]{"Sensor", "Teleport"}) {
            for (String outline : new String[]{"0,0 32,32 0,32 32,0", "0,0 32,0 32,32 16,0 0,32",
                "0,0 32,0 32,32 0,0", "0,0 32,0 16,0 16,32", "0,0 16,0 32,0",
                "0,0 0,0 32,32", "0,0 0.001,0 0,0.001", "0,0 NaN,0 0,32", "0,0 32,Infinity 0,32"}) {
                invalid(type + " invalid polygon", layer("Trigger", "", object("bad", type,
                    "x=\"32\" y=\"64\"", detectorProperties(type), "<polygon points=\"" + outline + "\"/>")), "Trigger 'bad'");
            }
            for (String dimensions : new String[]{"width=\"0\" height=\"32\"", "width=\"32\" height=\"-1\"",
                "width=\"NaN\" height=\"32\"", "width=\"32\" height=\"Infinity\""}) {
                invalid(type + " invalid ellipse", layer("Trigger", "", object("bad", type, dimensions,
                    detectorProperties(type), "<ellipse/>")), "Trigger 'bad'");
            }
            for (String shape : new String[]{"", "<ellipse/>", "<polygon points=\"0,0 32,0 0,32\"/>"}) {
                for (String rotation : new String[]{"NaN", "Infinity"}) {
                    invalid(type + " nonfinite rotation", layer("Trigger", "", object("bad", type,
                        "width=\"32\" height=\"32\" rotation=\"" + rotation + "\"", detectorProperties(type), shape)), "finite");
                }
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
            invalid("rotated spawn rectangle", layer("Triggers", "", object("bad", "SpawnArea",
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
        invalid("missing actor ID", layer("Triggers", "", object("npc", "SpawnPoint", "x=\"96\" y=\"128\"",
            property("spawnType", "NPC"), "<point/>")), "actorId");
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
        String properties = type.isEmpty() ? "" : property("spawnType", type);
        if (type.equals("NPC")) { properties += property("actorId", "basic_npc"); }
        return object(name, "SpawnPoint", coords + attributes, properties, "<point/>");
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

    private static void geometryRectangle(MapTriggers.DetectorGeometry geometry, float x, float y, float width, float height) {
        check(geometry.radius() == 0 && geometry.polygons().size() == 1, "Rectangle is polygon detector data");
        float[] actual = geometry.polygons().getFirst();
        float[] expected = {x, y, x + width, y, x + width, y + height, x, y + height};
        check(actual.length == expected.length, "Rectangle has four vertices");
        for (int i = 0; i < actual.length; i++) { near(actual[i], expected[i], "Rectangle world outline"); }
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
        PropertyLoader() { super(path -> new FileHandle(path.startsWith("maps/")
            ? "raw_assets/maps/test_tiled/" + path.substring(5) : "raw_assets/" + path)); }

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
            return loadTiledMap(resolve("maps/trigger-parser-test.tmx"), parameters(), imagePath ->
                new TextureRegion(new DimensionTexture(32, 32)));
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
