package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/** Trace the actual fixture in world coordinates for both sensor and lighting diagnostics. */
abstract class FixtureOutlines {
    private final Vector2 a = new Vector2(), b = new Vector2();

    abstract void line(float x1, float y1, float x2, float y2);
    abstract void circle(float x, float y, float radius);

    final void drawFixture(Fixture fixture) {
        var transform = fixture.getBody().getTransform();
        switch (fixture.getShape()) {
            case CircleShape circle -> {
                transform.mul(a.set(circle.getPosition()));
                circle(a.x, a.y, circle.getRadius());
            }
            case PolygonShape polygon -> {
                int count = polygon.getVertexCount();
                polygon.getVertex(count - 1, a);
                transform.mul(a);
                for (int i = 0; i < count; i++) {
                    polygon.getVertex(i, b);
                    segment(transform);
                }
            }
            case ChainShape chain -> {
                // Box2D loop vertices already repeat the first point; never close an open chain.
                chain.getVertex(0, a);
                transform.mul(a);
                for (int i = 1; i < chain.getVertexCount(); i++) {
                    chain.getVertex(i, b);
                    segment(transform);
                }
            }
            case EdgeShape edge -> {
                edge.getVertex1(a);
                transform.mul(a);
                edge.getVertex2(b);
                segment(transform);
            }
            default -> throw new IllegalStateException("Unknown fixture shape: " + fixture.getType());
        }
    }

    private void segment(Transform transform) {
        transform.mul(b);
        line(a.x, a.y, b.x, b.y);
        a.set(b);
    }
}
