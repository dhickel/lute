package io.mindspice.lute.weapontester;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.mindspice.lute.weapontester.Geometry.*;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

final class WeaponFile {
    static final List<String> COORDS = List.of("x1", "y1", "x2", "y2");
    static final List<String> FIELDS = Stream.concat(Stream.of("type", "x1", "y1", "x2", "y2"),
        Arrays.stream(Direction.values()).flatMap(d -> Stream.of(offset(d) + "/x", offset(d) + "/y"))).toList();
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    enum Slot {
        PRIMARY("primary"), SECONDARY("secondary");
        final String key;
        Slot(String key) { this.key = key; }
    }

    record Attack(String action, float speed, float interval, int hitFrame, int soundFrame) {
        double frameSeconds() { return (double) interval / speed; }
        boolean sensorActive(int frame) { return frame >= hitFrame; }
    }

    private final Path path;
    private final String source;
    private final JsonNode weapons;

    static WeaponFile load(Path path) throws IOException {
        Path target = path.getParent().toRealPath().resolve(path.getFileName());
        return new WeaponFile(target, readRegular(target));
    }

    private WeaponFile(Path path, String source) throws IOException {
        this.path = path;
        this.source = source;
        weapons = object(parse(source), "weapons");
        if (weapons.isEmpty()) { throw new IllegalArgumentException("No weapons in " + path); }
    }

    Path path() { return path; }

    List<String> ids() {
        var ids = new ArrayList<String>();
        weapons.fieldNames().forEachRemaining(ids::add);
        return List.copyOf(ids);
    }

    String atlas(String id) { return text(object(weapons, id), "atlas_key"); }
    boolean secondaryOnly(String id) { return projectile(attackNode(id, Slot.PRIMARY)); }

    Attack attack(String id, Slot slot) {
        var node = attackNode(id, slot);
        if (projectile(node) || (slot == Slot.PRIMARY && secondaryOnly(id))) {
            throw new IllegalArgumentException("Projectile primary has no melee sensor; choose Secondary.");
        }
        var animation = object(node, "animation");
        String action = text(animation, "atlas_key");
        if (!(action.equals("jab") || (slot == Slot.PRIMARY && action.equals("slash")))) {
            throw new IllegalArgumentException("Unsupported melee animation: " + action);
        }
        float speed = number(animation, "speed");
        float interval = number(animation, "frame_interval");
        if (speed <= 0 || interval <= 0) { throw new IllegalArgumentException("Animation timing must be positive."); }
        return new Attack(action, speed, interval, index(object(node, "stats"), "hit_frame"),
            index(animation, "sound_frame"));
    }

    Map<String, String> form(String id, Slot slot) throws IOException {
        attack(id, slot);
        var bounds = object(attackNode(id, slot), "bounds");
        String type = text(bounds, "type");
        var values = new LinkedHashMap<String, String>();
        values.put("type", type);
        for (var token : tokens(id, slot)) {
            if (!token.field().equals("type")) { values.put(token.field(), token.raw()); }
        }
        decode(values);
        return values;
    }

    static AttackBounds decode(Map<String, String> values) {
        var offsets = new EnumMap<Direction, Point>(Direction.class);
        for (var d : Direction.values()) {
            offsets.put(d, new Point(decimal(values, offset(d) + "/x").floatValue(),
                decimal(values, offset(d) + "/y").floatValue()));
        }
        return new AttackBounds(AttackShape.valueOf(values.get("type")), new Box(
            decimal(values, "x1").floatValue(), decimal(values, "y1").floatValue(),
            decimal(values, "x2").floatValue(), decimal(values, "y2").floatValue()), Map.copyOf(offsets));
    }

    WeaponFile save(String id, Slot slot, Map<String, String> values, Geometry geometry, Point logicalSize) throws IOException {
        var old = form(id, slot);
        geometry.weaponPixels(decode(values), logicalSize);
        var patched = new StringBuilder(source);
        for (var token : tokens(id, slot)) {
            String field = token.field();
            if (field.equals("type")) {
                if (!old.get(field).equals(values.get(field))) {
                    patched.replace(token.start(), token.end(), JSON.writeValueAsString(values.get(field)));
                }
            } else {
                var value = decimal(values, field);
                if (value.compareTo(decimal(old, field)) != 0) {
                    patched.replace(token.start(), token.end(), value.toString());
                }
            }
        }
        String updated = patched.toString();
        if (updated.equals(source)) {
            checkCurrent();
            return this;
        }
        var saved = new WeaponFile(path, updated);
        Path temporary = Files.createTempFile(path.getParent(), ".weapons-", ".tmp");
        try {
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(path, NOFOLLOW_LINKS));
            }
            try (var channel = FileChannel.open(temporary, WRITE)) {
                var bytes = ByteBuffer.wrap(updated.getBytes(StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) { channel.write(bytes); }
                channel.force(true);
            }
            checkCurrent();
            // Never fall back to a non-atomic overwrite.
            Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return saved;
    }

    private void checkCurrent() throws IOException {
        if (!source.equals(readRegular(path))) {
            throw new IOException("weapons.json changed on disk. Copy your edits if needed, then Reload and reapply them.");
        }
    }

    private record Token(String field, int start, int end, String raw) { }

    private List<Token> tokens(String id, Slot slot) throws IOException {
        var prefix = JsonPointer.compile("/weapons").appendProperty(id)
            .appendProperty("attack_stats").appendProperty(slot.key).appendProperty("bounds");
        var fields = new LinkedHashMap<JsonPointer, String>();
        for (String field : FIELDS) { fields.put(prefix.append(JsonPointer.compile("/" + field)), field); }
        var matches = new ArrayList<Token>();
        try (var parser = JSON.createParser(jsonInput(source))) {
            for (JsonToken token; (token = parser.nextToken()) != null;) {
                if (!token.isScalarValue()) { continue; }
                String field = fields.get(parser.getParsingContext().pathAsPointer());
                if (field == null) { continue; }
                if (!(field.equals("type") ? token == JsonToken.VALUE_STRING : token.isNumeric())) {
                    throw new IllegalArgumentException("Invalid bounds value for " + field);
                }
                parser.finishToken();
                int start = (int) parser.currentTokenLocation().getCharOffset();
                int end = (int) parser.currentLocation().getCharOffset();
                matches.add(new Token(field, start, end, source.substring(start, end)));
            }
        }
        if (matches.size() != fields.size()) {
            throw new IllegalArgumentException("Bounds require a type, four coordinates and all four directional offsets.");
        }
        matches.sort(Comparator.comparingInt(Token::start).reversed());
        return matches;
    }

    private JsonNode attackNode(String id, Slot slot) {
        return object(object(object(weapons, id), "attack_stats"), slot.key);
    }

    private static boolean projectile(JsonNode node) {
        return switch (text(node, "collision_type")) {
            case "MELEE" -> false;
            case "PROJECTILE" -> true;
            default -> throw new IllegalArgumentException("Unknown attack collision_type.");
        };
    }

    static String offset(Direction d) { return "offset_" + d.key(); }
    static JsonNode parse(String json) throws IOException {
        // Retain any UTF-8 BOM in the source and token offsets by treating it as leading whitespace.
        return JSON.readTree(jsonInput(json));
    }

    private static String jsonInput(String json) {
        return json.startsWith("\ufeff") ? " " + json.substring(1) : json;
    }

    private static String readRegular(Path path) throws IOException {
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) {
            throw new IOException("Missing or not a regular file: " + path);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    static JsonNode object(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isObject()) { throw new IllegalArgumentException("Expected object: " + field); }
        return value;
    }

    static String text(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Expected nonblank text: " + field);
        }
        return value.textValue();
    }

    static float number(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isNumber() || !Float.isFinite(value.floatValue())) {
            throw new IllegalArgumentException("Expected finite number: " + field);
        }
        return value.floatValue();
    }

    private static int index(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("Expected nonnegative frame index: " + field);
        }
        return value.intValue();
    }

    private static BigDecimal decimal(Map<String, String> values, String name) {
        try {
            var number = new BigDecimal(values.getOrDefault(name, "").strip());
            if (Float.isFinite(number.floatValue())) { return number; }
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a finite number for " + name.replace('_', ' ') + ".");
    }
}
