package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.singletons.GameConsts;

public final class CharacterFixtures {
    private CharacterFixtures() { }

    public static Fixture ellipse(Body body, CharacterBounds bounds, boolean sensor) {
        return create(body, CollisionData.ellipse(bounds), sensor, 0f, 0f);
    }

    static Fixture create(Body body, CollisionData collision, boolean sensor, float originX, float originY) {
        var bounds = collision.bounds();
        float unit = GameConsts.UNIT_SCALE;
        float cx = ((bounds.x1() + bounds.x2()) * 0.5f - originX) * unit;
        float cy = ((bounds.y1() + bounds.y2()) * 0.5f - originY) * unit;
        float rx = (bounds.x2() - bounds.x1()) * 0.5f * unit;
        float ry = (bounds.y2() - bounds.y1()) * 0.5f * unit;
        if (collision.shape() == CollisionData.Shape.CIRCLE) {
            if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(rx) || rx <= 0f) {
                throw new IllegalArgumentException("Invalid Box2D circle geometry");
            }
            var circle = new CircleShape();
            try {
                circle.setPosition(new Vector2(cx, cy));
                circle.setRadius(rx);
                return attach(body, circle, sensor);
            } finally { circle.dispose(); }
        }
        var vertices = centeredVertices(collision);
        for (var vertex : vertices) { vertex.add(cx, cy); }
        // Reject authored polygons that Box2D would weld or collapse before entering native code.
        float twiceArea = 0f;
        for (int i = 0; i < vertices.length; i++) {
            var point = vertices[i];
            var next = vertices[(i + 1) % vertices.length];
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) {
                throw new IllegalArgumentException("Collision produces nonfinite Box2D vertices");
            }
            for (int j = 0; j < i; j++) {
                float distanceSquared = point.dst2(vertices[j]);
                if (!Float.isFinite(distanceSquared) || distanceSquared <= 0.0025f * 0.0025f) {
                    throw new IllegalArgumentException("Collision exceeds Box2D polygon edge limits");
                }
            }
            twiceArea += (point.x - cx) * (next.y - cy) - (point.y - cy) * (next.x - cx);
        }
        if (!Float.isFinite(twiceArea) || twiceArea * 0.5f <= Math.ulp(1f)) {
            throw new IllegalArgumentException("Collision must have a finite Box2D polygon area");
        }
        var shape = new PolygonShape();
        try {
            shape.set(vertices);
            return attach(body, shape, sensor);
        } finally { shape.dispose(); }
    }

    private static Fixture attach(Body body, com.badlogic.gdx.physics.box2d.Shape shape, boolean sensor) {
        var fixture = body.createFixture(shape, 0f);
        fixture.setFriction(0f);
        fixture.setSensor(sensor);
        return fixture;
    }

    // Counterclockwise vertices in world units, relative to the bounds center.
    static Vector2[] centeredVertices(CollisionData collision) {
        var bounds = collision.bounds();
        float rx = (bounds.x2() - bounds.x1()) * GameConsts.UNIT_SCALE * .5f;
        float ry = (bounds.y2() - bounds.y1()) * GameConsts.UNIT_SCALE * .5f;
        var points = switch (collision.shape()) {
            case RECT -> new Vector2[]{new Vector2(-rx, -ry), new Vector2(rx, -ry),
                new Vector2(rx, ry), new Vector2(-rx, ry)};
            case TRIANGLE -> new Vector2[]{new Vector2(0, ry), new Vector2(-rx, -ry), new Vector2(rx, -ry)};
            case ELLIPSE, CIRCLE -> {
                // Box2D fixtures use eight points; projectile circles use a finer sweep polygon.
                var vertices = new Vector2[collision.shape() == CollisionData.Shape.CIRCLE ? 32 : 8];
                for (int i = 0; i < vertices.length; i++) {
                    float angle = i * MathUtils.PI2 / vertices.length;
                    vertices[i] = new Vector2(rx * MathUtils.cos(angle), ry * MathUtils.sin(angle));
                }
                yield vertices;
            }
        };
        if (collision.shape() != CollisionData.Shape.CIRCLE && collision.rotation() != 0f) {
            for (var point : points) { point.rotateDeg(collision.rotation() % 360f); }
        }
        return points;
    }
}
