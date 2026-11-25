package io.mindspice.lute.weapontester;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static io.mindspice.lute.weapontester.WeaponFile.Slot.*;
import static org.junit.jupiter.api.Assertions.*;

class WeaponFileTest {
    private static final Geometry GEOMETRY = new Geometry(1f / 32f);
    private static final Geometry.Point SIZE = new Geometry.Point(64, 64);
    private static final String ID = "é光/~";
    private static final String BOUNDS = """
        { "type": "TRIANGLE", "x1": 32.00, "y1": 2e1, "x2": 64, "y2": 44,
          "offset_up": { "x": 0, "y": 0 }, "offset_left": { "x": 0, "y": 0 },
          "offset_down": { "x": 0, "y": 0 }, "offset_right": { "x": 0, "y": 0 } }
        """;
    private static final String ATTACK = """
        {"collision_type":"MELEE", "animation":{"speed":2.5,"frame_interval":0.1,"atlas_key":"jab","sound_frame":1},
         "bounds": %s, "stats": {"hit_frame":2,"damage": 11.1234567890123456789}}
        """.formatted(BOUNDS);
    private static final String SOURCE = ("\ufeff{\"weapons\": {\"" + ID + "\":{\"atlas_key\":\"melee/sword\",\"attack_stats\":{"
        + "\"primary\": " + ATTACK + ",\"secondary\": " + ATTACK + "}}}, \"extra\": \"unchanged\"}\n").replace("\n", "\r\n");

    @TempDir Path temp;

    @Test void patchesOnlySelectedLeafTokensAndPreservesEveryOtherByte() throws IOException {
        var path = Files.writeString(temp.resolve("weapons.json"), SOURCE);
        var file = WeaponFile.load(path);
        var draft = file.form(ID, SECONDARY);
        draft.put("type", "RECT");
        draft.put("x1", "32"); // equivalent; must retain 32.00
        draft.put("x2", "70.5");
        draft.put("offset_left/x", "-3.5");
        var updated = file.save(ID, SECONDARY, draft, GEOMETRY, SIZE);
        int split = SOURCE.indexOf("\"secondary\"");
        String expected = SOURCE.substring(0, split) + SOURCE.substring(split)
            .replace("\"TRIANGLE\"", "\"RECT\"").replace("\"x2\": 64", "\"x2\": 70.5")
            .replace("\"offset_left\": { \"x\": 0", "\"offset_left\": { \"x\": -3.5");
        assertEquals(expected, Files.readString(path));
        assertEquals("32.00", updated.form(ID, SECONDARY).get("x1"));
        assertEquals("TRIANGLE", updated.form(ID, PRIMARY).get("type"));
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            var expectedPermissions = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----");
            Files.setPosixFilePermissions(path, expectedPermissions);
            draft.put("x2", "71");
            updated.save(ID, SECONDARY, draft, GEOMETRY, SIZE);
            assertEquals(expectedPermissions, Files.getPosixFilePermissions(path));
        }
    }

    @Test void refusesStaleEditsAndCleansUpTemporaryFile() throws IOException {
        var path = Files.writeString(temp.resolve("weapons.json"), SOURCE);
        var file = WeaponFile.load(path);
        var draft = file.form(ID, PRIMARY);
        draft.put("x1", "28");
        String external = SOURCE.replace("unchanged", "edited externally");
        Files.writeString(path, external);
        assertTrue(assertThrows(IOException.class, () -> file.save(ID, PRIMARY, draft, GEOMETRY, SIZE)).getMessage().contains("changed on disk"));
        assertEquals(external, Files.readString(path));
        try (var children = Files.list(temp)) { assertEquals(Set.of(path), Set.copyOf(children.toList())); }
    }

    @Test void rejectsMalformedInputAndInvalidGeometryWithoutWriting() throws IOException {
        var path = Files.writeString(temp.resolve("weapons.json"), SOURCE);
        var file = WeaponFile.load(path);
        var draft = file.form(ID, PRIMARY);
        draft.put("type", "RECT");
        draft.put("x2", "32.03125");
        assertThrows(IllegalArgumentException.class, () -> file.save(ID, PRIMARY, draft, GEOMETRY, SIZE));
        draft.put("x2", "Infinity");
        assertThrows(IllegalArgumentException.class, () -> file.save(ID, PRIMARY, draft, GEOMETRY, SIZE));
        assertEquals(SOURCE, Files.readString(path));
        Files.writeString(path, SOURCE.replace("\"x1\": 32.00", "\"x1\": 32, \"x1\": 30"));
        assertThrows(IOException.class, () -> WeaponFile.load(path));
    }

    @Test void detectsBowsByPrimaryCollisionTypeNotIdOrAtlas() throws IOException {
        String bow = SOURCE.replaceFirst("MELEE", "PROJECTILE");
        var path = Files.writeString(temp.resolve("weapons.json"), bow);
        var file = WeaponFile.load(path);
        assertTrue(file.secondaryOnly(ID));
        assertThrows(IllegalArgumentException.class, () -> file.form(ID, PRIMARY));
        assertEquals("jab", file.attack(ID, SECONDARY).action());
    }
}
