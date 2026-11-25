package io.mindspice.lute.weapontester;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

import java.util.Locale;
import java.util.Map;

final class Geometry {
    private final float unit;

    Geometry(float unit) {
        if (!Float.isFinite(unit) || unit <= 0) { throw new IllegalArgumentException("unitScale must be finite and positive."); }
        this.unit = unit;
    }

    enum Direction {
        UP(90), DOWN(270), LEFT(180), RIGHT(0);

        final float degrees;
        Direction(float degrees) { this.degrees = degrees; }
        String key() { return name().toLowerCase(Locale.ROOT); }
    }

    enum AttackShape { SEMICIRCLE, TRIANGLE, RECT }
    enum FixtureShape { ELLIPSE, RECT, CIRCLE, TRIANGLE }
    record Point(float x, float y) { }
    record Box(float x1, float y1, float x2, float y2) {
        Box {
            if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)
                || x2 <= x1 || y2 <= y1) {
                throw new IllegalArgumentException("Bounds require finite coordinates with x2 > x1 and y2 > y1.");
            }
        }
        float width() { return x2 - x1; }
        float height() { return y2 - y1; }
    }
    record AttackBounds(AttackShape shape, Box box, Map<Direction, Point> offsets) { }
    record Fixture(FixtureShape shape, Box box, float rotation) {
        Fixture {
            if (shape == FixtureShape.CIRCLE && box.width() != box.height()) {
                throw new IllegalArgumentException("Player CIRCLE requires equal width and height.");
            }
        }
    }

    // CombatWeapon.Attack: build right-facing vertices, turn around the logical center, then translate.
    Vector2[] weaponWorld(AttackBounds bounds, Point size, Direction direction) {
        var b = bounds.box();
        float centerY = b.y1() + b.height() * .5f;
        Vector2[] points = switch (bounds.shape()) {
            case RECT -> rect(b.x1(), b.y1(), b.x2(), b.y2());
            case TRIANGLE -> new Vector2[]{new Vector2(b.x1(), b.y1()), new Vector2(b.x2(), centerY),
                new Vector2(b.x1(), b.y2())};
            case SEMICIRCLE -> {
                var arc = new Vector2[7];
                for (int i = 0; i < arc.length; i++) {
                    float angle = -MathUtils.HALF_PI + i * MathUtils.PI / (arc.length - 1);
                    arc[i] = new Vector2(b.x1() + b.width() * MathUtils.cos(angle),
                        centerY + b.height() * .5f * MathUtils.sin(angle));
                }
                yield arc;
            }
        };
        var anchor = new Vector2(size.x() * unit * .5f, size.y() * unit * .5f);
        var offset = bounds.offsets().get(direction);
        for (var p : points) {
            p.scl(unit).sub(anchor).rotateDeg(direction.degrees).add(anchor).add(offset.x() * unit, offset.y() * unit);
        }
        return points;
    }

    Map<Direction, Vector2[]> weaponPixels(AttackBounds bounds, Point size) {
        var result = new java.util.EnumMap<Direction, Vector2[]>(Direction.class);
        for (var facing : Direction.values()) {
            try {
                var points = weaponWorld(bounds, size, facing);
                validatePolygon(points, true);
                result.put(facing, toPixels(points));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(facing + ": " + ex.getMessage(), ex);
            }
        }
        return result;
    }

    // CharacterFixtures: ellipses have eight points; actor triangles point up, not right.
    // A native circle is handled separately by the renderer.
    Vector2[] fixturePixels(Fixture fixture) {
        var b = fixture.box();
        float rx = b.width() * unit * .5f;
        float ry = b.height() * unit * .5f;
        var points = switch (fixture.shape()) {
            case RECT -> rect(-rx, -ry, rx, ry);
            case TRIANGLE -> new Vector2[]{new Vector2(0, ry), new Vector2(-rx, -ry), new Vector2(rx, -ry)};
            case ELLIPSE -> {
                var ellipse = new Vector2[8];
                for (int i = 0; i < ellipse.length; i++) {
                    float angle = i * MathUtils.PI2 / ellipse.length;
                    ellipse[i] = new Vector2(rx * MathUtils.cos(angle), ry * MathUtils.sin(angle));
                }
                yield ellipse;
            }
            case CIRCLE -> throw new IllegalArgumentException("A circle is not a polygon.");
        };
        float cx = (b.x1() + b.x2()) * .5f * unit;
        float cy = (b.y1() + b.y2()) * .5f * unit;
        for (var point : points) { point.rotateDeg(fixture.rotation() % 360f).add(cx, cy); }
        validatePolygon(points, false);
        return toPixels(points);
    }

    private Vector2[] toPixels(Vector2[] points) {
        for (var p : points) { p.set(p.x / unit, p.y / unit); }
        return points;
    }

    private static Vector2[] rect(float x1, float y1, float x2, float y2) {
        return new Vector2[]{new Vector2(x1, y1), new Vector2(x2, y1), new Vector2(x2, y2), new Vector2(x1, y2)};
    }

    private static void validatePolygon(Vector2[] points, boolean weapon) {
        float twiceArea = 0f;
        float weightedX = 0f;
        float weightedY = 0f;
        var origin = points[0];
        for (int i = 0; i < points.length; i++) {
            var p = points[i];
            var next = points[(i + 1) % points.length];
            var after = points[(i + 2) % points.length];
            if (!Float.isFinite(p.x) || !Float.isFinite(p.y)) {
                throw new IllegalArgumentException("Bounds produce nonfinite Box2D vertices.");
            }
            for (int j = 0; j < i; j++) {
                float distance = p.dst2(points[j]);
                if (!Float.isFinite(distance) || distance <= .0025f * .0025f) {
                    throw new IllegalArgumentException("Bounds exceed Box2D polygon edge limits.");
                }
            }
            float cross = (next.x - p.x) * (after.y - next.y) - (next.y - p.y) * (after.x - next.x);
            if (weapon && (!Float.isFinite(cross) || cross <= 0f)) {
                throw new IllegalArgumentException("Bounds must form a finite convex Box2D polygon.");
            }
            float triangle = (p.x - origin.x) * (next.y - origin.y) - (p.y - origin.y) * (next.x - origin.x);
            twiceArea += triangle;
            weightedX += triangle * ((p.x - origin.x) + (next.x - origin.x));
            weightedY += triangle * ((p.y - origin.y) + (next.y - origin.y));
        }
        if (!Float.isFinite(twiceArea) || twiceArea * .5f <= Math.ulp(1f)
            || (weapon && (!Float.isFinite(weightedX) || !Float.isFinite(weightedY)))) {
            throw new IllegalArgumentException("Bounds produce an invalid Box2D polygon area.");
        }
    }
}
