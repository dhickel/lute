package io.mindspice.lute.weapontester;

import com.badlogic.gdx.math.Vector2;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static io.mindspice.lute.weapontester.Geometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    record Cases(float unitScale, List<AttackCase> attacks, List<FixtureCase> fixtures) { }
    record AttackCase(String name, AttackShape shape, float[] bounds, float[] size, Map<Direction, float[]> offsets,
                      Map<Direction, float[][]> verticesPixels) { }
    record FixtureCase(String name, FixtureShape shape, float[] bounds, float rotation, float[][] verticesPixels) { }

    @TestFactory Stream<DynamicTest> goldenGeometry() throws IOException {
        Cases cases;
        try (var input = GeometryTest.class.getResourceAsStream("/geometry-golden.json")) {
            assertNotNull(input, "Missing shared geometry fixture");
            cases = JsonMapper.builder().build().readValue(input, Cases.class);
        }
        var geometry = new Geometry(cases.unitScale());
        return Stream.concat(
            cases.attacks().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> verifyAttack(geometry, test))),
            cases.fixtures().stream().map(test -> DynamicTest.dynamicTest(test.name(), () -> verifyFixture(geometry, test))));
    }

    private static void verifyAttack(Geometry geometry, AttackCase test) {
        var offsets = new EnumMap<Direction, Point>(Direction.class);
        for (var direction : Direction.values()) {
            var point = test.offsets().get(direction);
            offsets.put(direction, point == null ? new Point(0, 0) : new Point(point[0], point[1]));
        }
        var b = test.bounds();
        var bounds = new AttackBounds(test.shape(), new Box(b[0], b[1], b[2], b[3]), offsets);
        var size = new Point(test.size()[0], test.size()[1]);
        if (test.verticesPixels() == null) {
            var failure = assertThrows(IllegalArgumentException.class, () -> geometry.weaponPixels(bounds, size));
            assertTrue(failure.getMessage().contains("edge limits"), failure::getMessage);
            return;
        }
        var actual = geometry.weaponPixels(bounds, size);
        test.verticesPixels().forEach((direction, expected) -> assertVertices(expected, actual.get(direction)));
    }

    private static void verifyFixture(Geometry geometry, FixtureCase test) {
        var b = test.bounds();
        var fixture = new Fixture(test.shape(), new Box(b[0], b[1], b[2], b[3]), test.rotation());
        if (test.verticesPixels() == null) {
            var failure = assertThrows(IllegalArgumentException.class, () -> geometry.fixturePixels(fixture));
            assertTrue(failure.getMessage().contains("edge limits"), failure::getMessage);
            return;
        }
        assertVertices(test.verticesPixels(), geometry.fixturePixels(fixture));
    }

    private static void assertVertices(float[][] expected, Vector2[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i][0], actual[i].x, .02, "x at vertex " + i);
            assertEquals(expected[i][1], actual[i].y, .02, "y at vertex " + i);
        }
    }

    @Test void playbackUsesAuthoredIntervalOverSpeedAndLoopsAfterLastFrame() {
        var attack = new WeaponFile.Attack("jab", 2.5f, .1f, 2, 1);
        var clip = new RawPreview.Clip(Map.of(), 8, 128, 128, attack);
        assertEquals(.04, attack.frameSeconds(), .0000001);
        assertEquals(1, clip.frameAt(.079));
        assertEquals(2, clip.frameAt(.081));
        assertEquals(7, clip.frameAt(.319));
        assertEquals(0, clip.frameAt(.321));
        assertFalse(attack.sensorActive(1));
        assertTrue(attack.sensorActive(2));
        assertTrue(attack.sensorActive(7));
    }
}
