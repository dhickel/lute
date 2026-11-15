package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapLayers;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record MapTriggers(List<Sensor> sensors, List<SpawnPoint> points, List<SpawnArea> areas) {
    public enum SensorType { GENERAL }
    public enum SpawnType { PLAYER, TELEPORT, NPC, ITEM }

    public record Sensor(String name, SensorType type, Rectangle bounds, String targetMap, String targetSpawn) { }
    public record SpawnPoint(String name, SpawnType type, Vector2 position) { }
    public record SpawnArea(String name, SpawnType type, Rectangle bounds) { }

    public MapTriggers {
        sensors = List.copyOf(sensors);
        points = List.copyOf(points);
        areas = List.copyOf(areas);
    }

    public static MapTriggers parse(TiledMap map) {
        Object orientation = map.getProperties().get("orientation");
        if (orientation != null && !"orthogonal".equals(orientation)) {
            throw new IllegalArgumentException("Trigger geometry requires an orthogonal map");
        }
        var sensors = new ArrayList<Sensor>();
        var points = new ArrayList<SpawnPoint>();
        var areas = new ArrayList<SpawnArea>();
        readLayers(map.getLayers(), false, null, sensors, points, areas);

        var sensorNames = new HashSet<String>();
        for (var sensor : sensors) {
            unique(sensorNames, sensor.name(), "sensor");
        }
        var markerNames = new HashSet<String>();
        int players = 0;
        for (var point : points) {
            unique(markerNames, point.name(), "spawn marker");
            if (point.type() == SpawnType.PLAYER) { players++; }
        }
        for (var area : areas) { unique(markerNames, area.name(), "spawn marker"); }
        if (players > 1) { throw new IllegalArgumentException("Only one PLAYER SpawnPoint is allowed"); }

        var triggers = new MapTriggers(sensors, points, areas);
        for (var sensor : sensors) {
            if ("THIS".equalsIgnoreCase(sensor.targetMap())) {
                triggers.teleportDestination(sensor.targetSpawn());
            }
        }
        return triggers;
    }

    public SpawnPoint teleportDestination(String name) {
        for (var point : points) {
            if (point.name().equals(name) && point.type() == SpawnType.TELEPORT) { return point; }
        }
        throw new IllegalArgumentException("Teleport targetSpawn '" + name + "' must name a TELEPORT SpawnPoint");
    }

    private static void readLayers(MapLayers layers, boolean insideTriggers, String unsupportedTransform,
                                   List<Sensor> sensors, List<SpawnPoint> points, List<SpawnArea> areas) {
        for (var layer : layers) {
            boolean triggers = insideTriggers || "Trigger".equals(layer.getName()) || "Triggers".equals(layer.getName());
            String transform = unsupportedTransform;
            if (hasUnsupportedTransform(layer)) { transform = String.valueOf(layer.getName()); }
            if (triggers) {
                if (transform != null) {
                    throw new IllegalArgumentException("Trigger layer/group '" + transform
                        + "' has unsupported offsets or parallax; use zero offsets and unit parallax");
                }
                if (layer instanceof TiledMapTileLayer) {
                    throw new IllegalArgumentException("Trigger objects must be in an object layer, not a tile layer");
                }
                for (var object : layer.getObjects()) { readObject(object, sensors, points, areas); }
            }
            if (layer instanceof MapGroupLayer group) {
                readLayers(group.getLayers(), triggers, transform, sensors, points, areas);
            }
        }
    }

    private static boolean hasUnsupportedTransform(MapLayer layer) {
        return layer.getOffsetX() != 0 || layer.getOffsetY() != 0
            || layer.getParallaxX() != 1 || layer.getParallaxY() != 1;
    }

    private static void readObject(MapObject object, List<Sensor> sensors,
                                   List<SpawnPoint> points, List<SpawnArea> areas) {
        String name = object.getName();
        if (name == null || name.isBlank()) { throw invalid(object, "object name is required"); }
        Object rotation = object.getProperties().get("rotation");
        if (rotation != null && number(object, rotation, "rotation") != 0) {
            throw invalid(object, "rotated trigger geometry is unsupported");
        }
        String type = text(object, "type");
        switch (type) {
            case "Sensor" -> sensors.add(new Sensor(name, enumValue(object, "sensorType", SensorType.class),
                bounds(object), null, null));
            case "Teleport" -> sensors.add(new Sensor(name, SensorType.GENERAL, bounds(object),
                text(object, "targetMap"), text(object, "targetSpawn")));
            case "SpawnPoint" -> {
                if (!(object instanceof PointMapObject point)) {
                    throw invalid(object, "SpawnPoint requires point geometry");
                }
                for (String dimension : new String[]{"width", "height"}) {
                    Object value = object.getProperties().get(dimension);
                    if (value != null && number(object, value, dimension) != 0) {
                        throw invalid(object, "SpawnPoint must have zero " + dimension);
                    }
                }
                var position = new Vector2(point.getPoint());
                finite(object, position.x, "x");
                finite(object, position.y, "y");
                points.add(new SpawnPoint(name, enumValue(object, "spawnType", SpawnType.class), position));
            }
            case "SpawnArea" -> areas.add(new SpawnArea(name, enumValue(object, "spawnType", SpawnType.class), bounds(object)));
            default -> throw invalid(object, "unsupported trigger class '" + type + "'");
        }
    }

    private static Rectangle bounds(MapObject object) {
        if (!(object instanceof RectangleMapObject rectangle)) {
            throw invalid(object, "requires rectangle geometry");
        }
        var bounds = new Rectangle(rectangle.getRectangle());
        validateBounds(object, bounds);
        bounds.set(bounds.x * GameConsts.UNIT_SCALE, bounds.y * GameConsts.UNIT_SCALE,
            bounds.width * GameConsts.UNIT_SCALE, bounds.height * GameConsts.UNIT_SCALE);
        validateBounds(object, bounds);
        return bounds;
    }

    private static void validateBounds(MapObject object, Rectangle bounds) {
        finite(object, bounds.x, "x");
        finite(object, bounds.y, "y");
        finite(object, bounds.width, "width");
        finite(object, bounds.height, "height");
        finite(object, bounds.x + bounds.width, "right edge");
        finite(object, bounds.y + bounds.height, "top edge");
        if (bounds.width <= 0 || bounds.height <= 0) {
            throw invalid(object, "rectangle dimensions must be positive");
        }
    }

    private static String text(MapObject object, String key) {
        if (object.getProperties().get(key) instanceof String value && !value.isBlank()) { return value; }
        throw invalid(object, key + " must be a nonblank string");
    }

    private static <E extends Enum<E>> E enumValue(MapObject object, String key, Class<E> type) {
        String value = text(object, key);
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException failure) { throw invalid(object, "invalid " + key + " '" + value + "'"); }
    }

    private static float number(MapObject object, Object value, String key) {
        if (!(value instanceof Number number)) { throw invalid(object, key + " must be numeric"); }
        float result = number.floatValue();
        finite(object, result, key);
        return result;
    }

    private static void finite(MapObject object, float value, String key) {
        if (!Float.isFinite(value)) { throw invalid(object, key + " must be finite"); }
    }

    private static void unique(HashSet<String> names, String name, String kind) {
        if (!names.add(name)) { throw new IllegalArgumentException("Duplicate " + kind + " name '" + name + "'"); }
    }

    private static IllegalArgumentException invalid(MapObject object, String message) {
        return new IllegalArgumentException("Trigger '" + object.getName() + "': " + message);
    }
}
