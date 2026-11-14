package io.mindspice.lute.engine.components;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import java.util.Objects;

public final class CombatWeapon {
    private CombatWeapon() {}
    public record Bounds(float x1, float y1, float x2, float y2) {
        public Bounds {
            if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)
                || x2 <= x1 || y2 <= y1) {
                throw new IllegalArgumentException("Weapon bounds must be finite with x2 > x1 and y2 > y1");
            }
        }
    }

    public record Weapon(String id, float damage, float attackSpeed, BoundsType shape, Bounds bounds) {
        public Weapon {
            if (id == null || id.isBlank()) { throw new IllegalArgumentException("Weapon id is required"); }
            positive(damage, "damage");
            positive(attackSpeed, "attackSpeed");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(bounds, "bounds");
            validatePolygon(localVertices(shape, bounds));
        }

        // Bounds are sprite-local pixels facing right. The flat edge is at x1; x2 is the tip.
        // The anchor is body-local world units, independent of the feet collider's center.
        public Vector2[] vertices(Vector2 anchor, Facing.Direction facing) {
            var vertices = localVertices(shape, bounds);
            float degrees = switch (facing) {
                case RIGHT -> 0f;
                case UP -> 90f;
                case LEFT -> 180f;
                case DOWN -> 270f;
            };
            for (var vertex : vertices) { vertex.sub(anchor).rotateDeg(degrees).add(anchor); }
            validatePolygon(vertices);
            return vertices;
        }

        private static Vector2[] localVertices(BoundsType shape, Bounds bounds) {
            float x1 = bounds.x1();
            float x2 = bounds.x2();
            float y1 = bounds.y1();
            float y2 = bounds.y2();
            float centerY = y1 + (y2 - y1) * 0.5f;
            Vector2[] vertices = switch (shape) {
                case RECT -> new Vector2[]{new Vector2(x1, y1), new Vector2(x2, y1),
                    new Vector2(x2, y2), new Vector2(x1, y2)};
                case TRIANGLE -> new Vector2[]{new Vector2(x1, y1), new Vector2(x2, centerY), new Vector2(x1, y2)};
                case SEMICIRCLE -> {
                    // Seven arc points keep the polygon convex and within Box2D's eight-vertex limit.
                    var arc = new Vector2[7];
                    for (int i = 0; i < arc.length; i++) {
                        float angle = -MathUtils.HALF_PI + i * MathUtils.PI / (arc.length - 1);
                        arc[i] = new Vector2(x1 + (x2 - x1) * MathUtils.cos(angle),
                            centerY + (y2 - y1) * 0.5f * MathUtils.sin(angle));
                    }
                    yield arc;
                }
            };
            for (var vertex : vertices) { vertex.scl(GameConsts.UNIT_SCALE); }
            return vertices;
        }

        private static void validatePolygon(Vector2[] vertices) {
            // Box2D welds points closer than half its 0.005-world-unit linear slop.
            // Reject collapsed/overflowing data in Java rather than risking a native assertion.
            float minimumDistanceSquared = 0.0025f * 0.0025f;
            float twiceArea = 0f;
            float weightedX = 0f;
            float weightedY = 0f;
            var origin = vertices[0];
            for (int i = 0; i < vertices.length; i++) {
                var vertex = vertices[i];
                if (!Float.isFinite(vertex.x) || !Float.isFinite(vertex.y)) {
                    throw new IllegalArgumentException("Weapon bounds produce nonfinite Box2D vertices");
                }
                for (int j = 0; j < i; j++) {
                    float distanceSquared = vertex.dst2(vertices[j]);
                    if (!Float.isFinite(distanceSquared) || distanceSquared <= minimumDistanceSquared) {
                        throw new IllegalArgumentException("Weapon bounds exceed Box2D polygon edge limits");
                    }
                }
                var next = vertices[(i + 1) % vertices.length];
                var after = vertices[(i + 2) % vertices.length];
                float cross = (next.x - vertex.x) * (after.y - next.y)
                    - (next.y - vertex.y) * (after.x - next.x);
                if (!Float.isFinite(cross) || cross <= 0f) {
                    throw new IllegalArgumentException("Weapon bounds must produce a finite convex Box2D polygon");
                }
                float triangleCross = (vertex.x - origin.x) * (next.y - origin.y)
                    - (vertex.y - origin.y) * (next.x - origin.x);
                twiceArea += triangleCross;
                weightedX += triangleCross * ((vertex.x - origin.x) + (next.x - origin.x));
                weightedY += triangleCross * ((vertex.y - origin.y) + (next.y - origin.y));
            }
            if (!Float.isFinite(twiceArea) || !Float.isFinite(weightedX) || !Float.isFinite(weightedY)
                || twiceArea * 0.5f <= Math.ulp(1f)) {
                throw new IllegalArgumentException("Weapon bounds produce an invalid Box2D polygon area");
            }
        }
    }

    private static void positive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }
}
