package io.mindspice.lute.weapontester;

import com.badlogic.gdx.math.Vector2;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.scene.image.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static io.mindspice.lute.weapontester.Geometry.*;
import static io.mindspice.lute.weapontester.WeaponFile.*;

final class RawPreview {
    private static final int FRAME_COUNT = 8; // CharacterCompositor requires eight indexed frames per facing.

    record Player(Point size, Geometry geometry, Vector2[] hurtbox, Vector2[] solid, Box solidCircle,
                  Map<String, String> armor, String defaultWeapon) { }
    record Pose(List<Image> layers) { }
    record Clip(Map<Direction, List<Pose>> poses, int frames, int width, int height, Attack attack) {
        int frameAt(double seconds) { return (int) ((seconds % (frames * attack.frameSeconds())) / attack.frameSeconds()); }
    }

    static Player player(Path raw) throws IOException {
        var settings = parse(Files.readString(raw.resolve("game.settings.json")));
        var geometry = new Geometry(number(settings, "unitScale"));
        var node = parse(Files.readString(raw.resolve("data/player.json")));
        var size = object(node, "logical_size");
        var logical = new Point(number(size, "width"), number(size, "height"));
        if (logical.x() <= 0 || logical.y() <= 0) {
            throw new IllegalArgumentException("Player logical_size must be positive.");
        }
        var collision = object(node, "collision");
        var solid = new Fixture(FixtureShape.valueOf(text(collision, "shape")), box(object(collision, "bounds")),
            number(collision, "rotation"));
        var hurt = new Fixture(FixtureShape.ELLIPSE, box(object(node, "hitbox_bounds")), 0);
        for (var box : List.of(solid.box(), hurt.box())) {
            if (box.x1() < 0 || box.y1() < 0 || box.x2() > logical.x() || box.y2() > logical.y()) {
                throw new IllegalArgumentException("Player bounds must fit the logical frame.");
            }
        }
        boolean circle = solid.shape() == FixtureShape.CIRCLE;
        var loadout = object(node, "loadout");
        var armor = new LinkedHashMap<String, String>();
        for (String slot : List.of("feet", "body", "hands", "head")) {
            if (loadout.hasNonNull(slot)) { armor.put(slot, text(loadout, slot)); }
        }
        return new Player(logical, geometry, geometry.fixturePixels(hurt), circle ? null : geometry.fixturePixels(solid),
            circle ? solid.box() : null, armor, loadout.hasNonNull("weapon") ? text(loadout, "weapon") : "unarmed");
    }

    private static Box box(JsonNode node) {
        return new Box(number(node, "x1"), number(node, "y1"), number(node, "x2"), number(node, "y2"));
    }

    static Clip load(Path raw, Player player, String atlas, Attack attack) throws IOException {
        Path sprites = raw.resolve("sprites");
        Path base = sprites.resolve("character/player");
        Path weapon = atlas.equals("NONE") ? null : inside(sprites.resolve("weapons"), atlas);
        var reference = readImage(framePaths(base, "idle_right").getFirst(), 0, 0);
        int width = (int) reference.getWidth();
        int height = (int) reference.getHeight();
        if (player.size().x() > width || player.size().y() > height) {
            throw new IOException("Player logical_size does not fit the raw frame canvas.");
        }
        var directions = new EnumMap<Direction, List<Pose>>(Direction.class);
        if (FRAME_COUNT <= Math.max(attack.hitFrame(), attack.soundFrame())) {
            throw new IOException("Hit or sound frame lies beyond " + attack.action() + " (" + FRAME_COUNT + " frames).");
        }
        for (var direction : Direction.values()) {
            String sequence = attack.action() + "_" + direction.key();
            var baseFrames = framePaths(base, sequence);
            var paths = new ArrayList<List<Path>>();
            if (weapon != null && direction == Direction.UP) { paths.add(framePaths(weapon, sequence)); }
            paths.add(baseFrames);
            for (var entry : player.armor().entrySet()) {
                Path equipment = inside(sprites.resolve("equipment"), entry.getValue()).resolve(entry.getKey());
                paths.add(framePaths(equipment, sequence));
            }
            if (weapon != null && direction != Direction.UP) { paths.add(framePaths(weapon, sequence)); }
            var frames = new ArrayList<Pose>();
            for (int i = 0; i < FRAME_COUNT; i++) {
                var layers = new ArrayList<Image>();
                for (var layer : paths) { layers.add(readImage(layer.get(i), width, height)); }
                frames.add(new Pose(List.copyOf(layers)));
            }
            directions.put(direction, List.copyOf(frames));
        }
        return new Clip(Map.copyOf(directions), FRAME_COUNT, width, height, attack);
    }

    private static Path inside(Path parent, String name) throws IOException {
        var path = parent.resolve(name).normalize();
        if (path.equals(parent) || !path.startsWith(parent)) {
            throw new IOException("Sprite key must stay inside " + parent + ": " + name);
        }
        return path;
    }

    private static List<Path> framePaths(Path dir, String sequence) throws IOException {
        if (!Files.isDirectory(dir)) { throw new IOException("Missing raw sprite directory: " + dir); }
        var pattern = Pattern.compile(Pattern.quote(sequence) + "_([0-9]+)\\.png");
        var frames = new TreeMap<Integer, Path>();
        try (var files = Files.list(dir)) {
            for (var file : files.toList()) {
                var match = pattern.matcher(file.getFileName().toString());
                if (!match.matches()) { continue; }
                int index = Integer.parseInt(match.group(1));
                if (frames.putIfAbsent(index, file) != null) { throw new IOException("Duplicate frame: " + file); }
            }
        }
        if (frames.isEmpty() || frames.firstKey() != 0 || frames.lastKey() != frames.size() - 1) {
            throw new IOException("Missing or noncontiguous frames: " + dir.resolve(sequence + "_00.png"));
        }
        if (frames.size() != FRAME_COUNT) {
            throw new IOException("Expected eight frames for " + dir.resolve(sequence) + ", found " + frames.size());
        }
        return List.copyOf(frames.values());
    }

    private static Image readImage(Path path, int width, int height) throws IOException {
        try (var stream = Files.newInputStream(path)) {
            var image = new Image(stream);
            if (image.isError() || image.getWidth() < 1 || image.getHeight() < 1) {
                throw new IOException("Cannot decode raw frame: " + path, image.getException());
            }
            if (width != 0 && (image.getWidth() != width || image.getHeight() != height)) {
                throw new IOException("Expected " + width + " × " + height + " canvas: " + path);
            }
            return image;
        }
    }
}
