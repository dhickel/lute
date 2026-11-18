package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

/** Continuous collision in the arrow's coordinates: the tip starts at (0,0) and moves along +X. */
final class ArrowTipSweep {
    private final Vector2 origin, direction;
    private final Vector2[] triangle;
    private final float travel;
    private float enter, exit;

    ArrowTipSweep(Vector2 origin, Vector2 direction, Vector2[] triangle, float travel) {
        this.origin = origin;
        this.direction = direction;
        this.travel = travel;
        this.triangle = triangle;
    }

    private Vector2 local(Body body, Vector2 vertex) {
        var world = body.getWorldPoint(vertex);
        float x = world.x - origin.x, y = world.y - origin.y;
        return vertex.set(x * direction.x + y * direction.y, y * direction.x - x * direction.y);
    }

    float distance(Fixture fixture) {
        Body body = fixture.getBody();
        return switch (fixture.getShape()) {
            case PolygonShape polygon -> {
                var vertices = new Vector2[polygon.getVertexCount()];
                for (int i = 0; i < vertices.length; i++) {
                    var point = new Vector2();
                    polygon.getVertex(i, point);
                    vertices[i] = local(body, point);
                }
                yield polygonDistance(vertices);
            }
            case EdgeShape edge -> {
                var a = new Vector2(); var b = new Vector2();
                edge.getVertex1(a); edge.getVertex2(b);
                yield polygonDistance(new Vector2[]{local(body, a), local(body, b)});
            }
            case ChainShape chain -> {
                float first = Float.POSITIVE_INFINITY;
                var point = new Vector2();
                chain.getVertex(0, point);
                var previous = local(body, point);
                for (int i = 1; i < chain.getVertexCount(); i++) {
                    point = new Vector2();
                    chain.getVertex(i, point);
                    var next = local(body, point);
                    first = Math.min(first, polygonDistance(new Vector2[]{previous, next}));
                    previous = next;
                }
                yield first;
            }
            case CircleShape circle -> circleDistance(local(body, circle.getPosition().cpy()), circle.getRadius());
            default -> throw new IllegalArgumentException("Unsupported fixture shape: " + fixture.getType());
        };
    }

    // Intersect the times when projections overlap on every edge normal of both shapes.
    private float polygonDistance(Vector2[] target) {
        enter = 0; exit = travel;
        return axesOverlap(triangle, target) && axesOverlap(target, target) ? enter : Float.POSITIVE_INFINITY;
    }

    private boolean axesOverlap(Vector2[] edges, Vector2[] target) {
        for (int i = 0; i < edges.length; i++) {
            var a = edges[i]; var b = edges[(i + 1) % edges.length];
            float nx = a.y - b.y, ny = b.x - a.x;
            float low = Float.POSITIVE_INFINITY, high = Float.NEGATIVE_INFINITY;
            for (var v : target) {
                float p = v.dot(nx, ny);
                low = Math.min(low, p); high = Math.max(high, p);
            }
            float p1 = triangle[1].dot(nx, ny), p2 = triangle[2].dot(nx, ny);
            low -= Math.max(0, Math.max(p1, p2));
            high -= Math.min(0, Math.min(p1, p2));
            if (nx == 0) {
                if (low > 0 || high < 0) { return false; }
            } else {
                float t1 = low / nx, t2 = high / nx;
                enter = Math.max(enter, Math.min(t1, t2));
                exit = Math.min(exit, Math.max(t1, t2));
                if (enter > exit) { return false; }
            }
        }
        return true;
    }

    // In relative motion the circle center moves left against the triangle expanded by its radius.
    private float circleDistance(Vector2 center, float radius) {
        if (Intersector.isPointInTriangle(center, triangle[0], triangle[1], triangle[2])) { return 0; }
        float first = Float.POSITIVE_INFINITY;
        for (int i = 0; i < 3; i++) {
            var a = triangle[i]; var b = triangle[(i + 1) % 3];
            if (Intersector.distanceSegmentPoint(a, b, center) <= radius) { return 0; }
            float dy = center.y - a.y;
            if (Math.abs(dy) <= radius) {
                float t = center.x - a.x - (float)Math.sqrt(radius * radius - dy * dy);
                if (t >= 0 && t <= travel) { first = Math.min(first, t); }
            }
            var edge = b.cpy().sub(a);
            var normal = new Vector2(edge.y, -edge.x).nor();
            if (normal.x <= 0) { continue; }
            var offset = center.cpy().sub(a);
            float t = (offset.dot(normal) - radius) / normal.x;
            float along = offset.sub(t, 0).dot(edge);
            if (t >= 0 && t <= travel && along >= 0 && along <= edge.len2()) { first = Math.min(first, t); }
        }
        return first;
    }
}
