package io.mindspice.lute.weapontester;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.mindspice.lute.weapontester.Geometry.*;
import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {
    private static final float UNIT = 1f / 32f;
    private final Geometry geometry = new Geometry(UNIT);

    @Test void rotatesAroundLogicalCenterThenAddsWorldAlignedOffsets() {
        var shape = new AttackBounds(AttackShape.TRIANGLE, new Box(48, 20, 72, 44), Map.of(
            Direction.RIGHT, new Point(2, 3), Direction.UP, new Point(4, 5),
            Direction.LEFT, new Point(-6, 7), Direction.DOWN, new Point(8, -9)));
        var size = new Point(64, 64);
        geometry.weaponPixels(shape, size);
        float[][] expected = {{36, 77}, {40, -17}, {-14, 39}, {74, 35}};
        for (var facing : Direction.values()) {
            var tip = geometry.weaponWorld(shape, size, facing)[1];
            assertEquals(expected[facing.ordinal()][0], tip.x / UNIT, .001);
            assertEquals(expected[facing.ordinal()][1], tip.y / UNIT, .001);
        }
    }

    @Test void semicircleUsesSevenVerticesAndRejectsWeldedEdges() {
        var offsets = Map.of(Direction.UP, new Point(0, 0), Direction.DOWN, new Point(0, 0),
            Direction.LEFT, new Point(0, 0), Direction.RIGHT, new Point(0, 0));
        var size = new Point(64, 64);
        var arc = geometry.weaponWorld(new AttackBounds(AttackShape.SEMICIRCLE, new Box(32, 8, 60, 56), offsets),
            size, Direction.RIGHT);
        assertEquals(7, arc.length);
        assertEquals(32, arc[0].x / UNIT, .02);
        assertEquals(8, arc[0].y / UNIT, .02);
        assertEquals(60, arc[3].x / UNIT, .02);
        assertEquals(32, arc[3].y / UNIT, .02);
        assertThrows(IllegalArgumentException.class, () -> geometry.weaponPixels(
            new AttackBounds(AttackShape.RECT, new Box(0, 0, .01f, 1), offsets), size));
    }

    @Test void playerFixtureUsesEightEllipseVerticesAndCenterRotation() {
        var ellipse = geometry.fixturePixels(new Fixture(FixtureShape.ELLIPSE, new Box(20, 4, 44, 14), 90));
        assertEquals(8, ellipse.length);
        assertEquals(32, ellipse[0].x, .001);
        assertEquals(21, ellipse[0].y, .001);
        var triangle = geometry.fixturePixels(new Fixture(FixtureShape.TRIANGLE, new Box(10, 12, 22, 20), 0));
        assertEquals(16, triangle[0].x);
        assertEquals(20, triangle[0].y);
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
