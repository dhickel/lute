package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.ChainShape;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.EdgeShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.QueryCallback;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ObjectSet;

/** A per-light stencil mesh derived from the current Box2D snapshot. */
final class NightOcclusion {
    final FloatArray triangles = new FloatArray();
    private final Vector2 vertex = new Vector2();
    private final QueryCallback query = this::visit;
    private final ObjectSet<Fixture> chains = new ObjectSet<>();
    // A square clipped by three half-planes has at most seven vertices.
    private double[] clipped = new double[14], scratch = new double[14];
    private int count;
    private Body excluded;
    private double x, y, radius, pixel;
    private boolean suppressed;

    boolean collect(World world, Body excluded, float x, float y, float radius, float worldUnitsPerPixel) {
        this.excluded = excluded;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.pixel = worldUnitsPerPixel;
        suppressed = false;
        triangles.clear();
        try {
            world.QueryAABB(query, finiteFloat(this.x - radius), finiteFloat(this.y - radius),
                finiteFloat(this.x + radius), finiteFloat(this.y + radius));
            if (suppressed) { triangles.clear(); }
            return !suppressed;
        } finally {
            this.excluded = null;
            chains.clear();
        }
    }

    private boolean visit(Fixture fixture) {
        Body body = fixture.getBody();
        if (body == excluded || fixture.isSensor() || !body.isActive()) { return true; }
        Transform transform = body.getTransform();
        switch (fixture.getShape()) {
            case PolygonShape polygon -> polygon(transform, polygon);
            case CircleShape circle -> {
                transform.mul(vertex.set(circle.getPosition()));
                circle(vertex.x - x, vertex.y - y, circle.getRadius());
            }
            case EdgeShape edge -> {
                edge.getVertex1(vertex);
                transform.mul(vertex);
                double ax = vertex.x - x, ay = vertex.y - y;
                edge.getVertex2(vertex);
                transform.mul(vertex);
                segment(ax, ay, vertex.x - x, vertex.y - y);
            }
            case ChainShape chain -> {
                // Box2D can report a proxy for each child of the same chain fixture.
                if (chains.add(fixture)) { chain(transform, chain); }
            }
            default -> throw new IllegalStateException("Unknown shadow shape: " + fixture.getType());
        }
        return !suppressed;
    }

    private void polygon(Transform transform, PolygonShape shape) {
        int n = shape.getVertexCount();
        shape.getVertex(n - 1, vertex);
        transform.mul(vertex);
        boolean inside = true;
        for (int i = 0; i < n; i++) {
            double ax = vertex.x - x, ay = vertex.y - y;
            shape.getVertex(i, vertex);
            transform.mul(vertex);
            double bx = vertex.x - x, by = vertex.y - y;
            // Box2D's convex polygon is CCW. Only faces visible from the source need projecting.
            if (ax * by - ay * bx < 0) {
                inside = false;
                segment(bx, by, ax, ay);
            }
        }
        suppressed = inside; // Inclusive of an actual edge or vertex, not its infinite supporting line.
    }

    private void chain(Transform transform, ChainShape shape) {
        int n = shape.getVertexCount();
        shape.getVertex(0, vertex);
        transform.mul(vertex);
        // Native loop vertices already repeat the first point; an open chain never closes.
        for (int i = 1; i < n; i++) {
            double ax = vertex.x - x, ay = vertex.y - y;
            shape.getVertex(i, vertex);
            transform.mul(vertex);
            segment(ax, ay, vertex.x - x, vertex.y - y);
        }
    }

    private void circle(double cx, double cy, double r) {
        double distance = Math.hypot(cx, cy);
        // Factor d²-r² so a tiny transverse offset on a tangent line isn't rounded into
        // containment by hypot. A false positive here would suppress the entire light.
        double major = Math.max(Math.abs(cx), Math.abs(cy)), minor = Math.min(Math.abs(cx), Math.abs(cy));
        double tangentLength2 = (major - r) * (major + r) + minor * minor;
        if (tangentLength2 <= 0) { suppressed = true; return; }
        double gap = tangentLength2 / (distance + r);
        if (gap >= radius) { return; }

        // Only the source-facing arc, between the two exact tangencies, can start a shadow.
        // For a huge circle only approximate the part of that arc inside the light disk:
        // |arc point|² = gap² + 4*d*r*sin²(angle/2), angle measured from the near normal.
        double halfArc = Math.min(Math.atan2(Math.sqrt(tangentLength2), r), 2 * Math.asin(Math.min(1,
            Math.sqrt((radius - gap) * (radius + gap) / (4 * distance * r)))));
        double ux = cx / distance, uy = cy / distance;
        double endDepth = gap + 2 * r * square(Math.sin(halfArc * .5));
        double endWidth = r * Math.sin(halfArc);
        double ax = ux * endDepth + uy * endWidth, ay = uy * endDepth - ux * endWidth;
        double bx = ux * endDepth - uy * endWidth, by = uy * endDepth + ux * endWidth;
        segment(ax, ay, bx, by); // exact cone beyond this chord

        // Fill the near cap with intersections of adjacent supporting tangents. Unlike inscribed
        // chords this never leaks through the curved surface. Max excess is r*(sec(step/2)-1),
        // bounded by 1/4 viewport pixel (or one mesh-coordinate ULP at unrenderable scales).
        double error = Math.max(pixel * .25, Math.ulp((float) Math.min(radius, r)));
        double maxHalfStep = Math.atan2(Math.sqrt(error * (2 * r + error)), r);
        int steps = 2 * Math.max(1, (int) Math.ceil(halfArc / (2 * maxHalfStep)));
        double halfStep = halfArc / steps, reach = r / Math.cos(halfStep);
        double px = ax, py = ay;
        for (int i = 0; i < steps; i++) {
            double angle = -halfArc + (2 * i + 1) * halfStep;
            // d - reach*cos(angle), expressed without subtracting nearly equal large radii.
            double depth = gap + 2 * reach * Math.sin((angle + halfStep) * .5) * Math.sin((angle - halfStep) * .5);
            double width = reach * Math.sin(angle);
            double qx = ux * depth - uy * width, qy = uy * depth + ux * width;
            if (i != 0) { triangle(ax, ay, qx, qy, px, py); }
            px = qx;
            py = qy;
        }
        triangle(ax, ay, bx, by, px, py);
    }

    private static double square(double value) { return value * value; }

    private void segment(double ax, double ay, double bx, double by) {
        if (Math.min(ax, bx) > radius || Math.max(ax, bx) < -radius
            || Math.min(ay, by) > radius || Math.max(ay, by) < -radius) { return; }
        double cross = ax * by - ay * bx;
        if (cross == 0) { return; } // A collinear zero-thickness surface subtends no area.
        if (cross < 0) { segment(bx, by, ax, ay); return; }

        // Intersect the bounding square with the two endpoint rays and the far side of the edge.
        // No finite extrusion length can truncate a wide, near-source shadow this way.
        count = 0;
        append(clipped, -radius, -radius); append(clipped, radius, -radius);
        append(clipped, radius, radius); append(clipped, -radius, radius);
        clip(-ay, ax, 0);
        clip(by, -bx, 0);
        clip(by - ay, ax - bx, cross);
        for (int i = 4; i < count; i += 2) {
            triangle(clipped[0], clipped[1], clipped[i - 2], clipped[i - 1], clipped[i], clipped[i + 1]);
        }
    }

    /** Keep points satisfying nx*x + ny*y >= offset. */
    private void clip(double nx, double ny, double offset) {
        int end = count;
        if (end == 0) { return; }
        count = 0;
        double px = clipped[end - 2], py = clipped[end - 1];
        double previous = nx * px + ny * py - offset;
        for (int i = 0; i < end; i += 2) {
            double qx = clipped[i], qy = clipped[i + 1];
            double current = nx * qx + ny * qy - offset;
            if ((previous > 0 && current < 0) || (previous < 0 && current > 0)) {
                double t = previous / (previous - current);
                append(scratch, px + t * (qx - px), py + t * (qy - py));
            }
            if (current >= 0) { append(scratch, qx, qy); }
            px = qx;
            py = qy;
            previous = current;
        }
        double[] swap = clipped; clipped = scratch; scratch = swap;
    }

    private void append(double[] points, double px, double py) {
        points[count++] = px;
        points[count++] = py;
    }

    private void triangle(double ax, double ay, double bx, double by, double cx, double cy) {
        triangles.add(finiteFloat(x + ax), finiteFloat(y + ay));
        triangles.add(finiteFloat(x + bx), finiteFloat(y + by));
        triangles.add(finiteFloat(x + cx), finiteFloat(y + cy));
    }

    private static float finiteFloat(double value) {
        return (float) Math.clamp(value, -Float.MAX_VALUE, Float.MAX_VALUE);
    }
}
