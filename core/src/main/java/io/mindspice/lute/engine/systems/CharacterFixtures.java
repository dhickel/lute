package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.singletons.GameConsts;

public final class CharacterFixtures {
    private CharacterFixtures() { }

    public static Fixture ellipse(Body body, CharacterBounds bounds, boolean sensor) {
        float unit = GameConsts.UNIT_SCALE;
        float cx = (bounds.x1() + bounds.x2()) * 0.5f * unit;
        float cy = (bounds.y1() + bounds.y2()) * 0.5f * unit;
        float rx = (bounds.x2() - bounds.x1()) * 0.5f * unit;
        float ry = (bounds.y2() - bounds.y1()) * 0.5f * unit;
        var vertices = new Vector2[8];
        for (int i = 0; i < vertices.length; i++) {
            float angle = i * MathUtils.PI2 / vertices.length;
            vertices[i] = new Vector2(cx + rx * MathUtils.cos(angle), cy + ry * MathUtils.sin(angle));
        }
        // Reject authored ellipses that Box2D would weld or collapse before entering native code.
        float twiceArea = 0f;
        for (int i = 0; i < vertices.length; i++) {
            var point = vertices[i];
            var next = vertices[(i + 1) % vertices.length];
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y)) {
                throw new IllegalArgumentException("Character ellipse produces nonfinite Box2D vertices");
            }
            for (int j = 0; j < i; j++) {
                float distanceSquared = point.dst2(vertices[j]);
                if (!Float.isFinite(distanceSquared) || distanceSquared <= 0.0025f * 0.0025f) {
                    throw new IllegalArgumentException("Character ellipse exceeds Box2D polygon edge limits");
                }
            }
            twiceArea += (point.x - cx) * (next.y - cy) - (point.y - cy) * (next.x - cx);
        }
        if (!Float.isFinite(twiceArea) || twiceArea * 0.5f <= Math.ulp(1f)) {
            throw new IllegalArgumentException("Character ellipse must have a finite Box2D polygon area");
        }
        var shape = new PolygonShape();
        try {
            shape.set(vertices);
            var fixture = body.createFixture(shape, 0f);
            fixture.setFriction(0f);
            fixture.setSensor(sensor);
            return fixture;
        } finally { shape.dispose(); }
    }
}
