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

/** Per-light triangles: world x/y and visibility, from zero on the collider to one at the tail. */
final class NightOcclusion {
    final FloatArray triangles = new FloatArray();
    private final Vector2 vertex = new Vector2();
    private final QueryCallback query = this::visit;
    private final ObjectSet<Fixture> chains = new ObjectSet<>();
    private double[] outline = new double[16];
    private Body excluded;
    private double x, y, radius, pixel, maxTail;
    private boolean suppressed;

    boolean collect(World world, Body excluded, float x, float y, float radius, float worldUnitsPerPixel, double maxTail) {
        this.excluded = excluded;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.pixel = worldUnitsPerPixel;
        this.maxTail = maxTail;
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
                edge(ax, ay, vertex.x - x, vertex.y - y);
            }
            case ChainShape chain -> {
                // Box2D can report a proxy for each child of the same chain fixture.
                if (chains.add(fixture)) { chain(transform, chain); }
            }
            default -> throw new IllegalStateException("Unknown shadow shape: " + fixture.getType());
        }
        return !suppressed;
    }

    private double tail(double diameter) { return Math.min(.75 * diameter, maxTail); }

    private void polygon(Transform transform, PolygonShape shape) {
        int n = shape.getVertexCount() * 2;
        if (outline.length < n) { outline = new double[n]; }
        double diameter2 = 0;
        for (int i = 0; i < n; i += 2) {
            shape.getVertex(i / 2, vertex);
            transform.mul(vertex);
            double bx = outline[i] = vertex.x - x, by = outline[i + 1] = vertex.y - y;
            for (int j = 0; j < i; j += 2) {
                diameter2 = Math.max(diameter2, square(bx - outline[j]) + square(by - outline[j + 1]));
            }
        }
        double length = tail(Math.sqrt(diameter2));
        boolean inside = true;
        for (int i = 0, j = n - 2; i < n; j = i, i += 2) {
            double ax = outline[j], ay = outline[j + 1], bx = outline[i], by = outline[i + 1];
            double cross = ax * by - ay * bx;
            // Box2D polygons are convex and CCW. Fill the actual solid; start the fade only
            // on exit faces, so a deep solid never lets light through its own interior.
            if (i >= 4) { solid(outline[0], outline[1], ax, ay, bx, by); }
            if (cross < 0) { inside = false; }
            if (cross > 0) { ribbon(ax, ay, bx, by, length); }
        }
        suppressed = inside;
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
            edge(ax, ay, vertex.x - x, vertex.y - y);
        }
    }

    private void edge(double ax, double ay, double bx, double by) {
        ribbon(ax, ay, bx, by, tail(Math.hypot(bx - ax, by - ay)));
    }

    private void circle(double cx, double cy, double r) {
        // Factoring d²-r² retains tiny transverse offsets when hypot alone rounds to r.
        double major = Math.max(Math.abs(cx), Math.abs(cy)), minor = Math.min(Math.abs(cx), Math.abs(cy));
        double tangent2 = (major - r) * (major + r) + minor * minor;
        if (tangent2 <= 0) { suppressed = true; return; }
        double d = Math.hypot(cx, cy), gap = tangent2 / (d + r);
        if (gap >= radius || r == 0) { return; }

        // Circumscribe the circle with supporting tangents (at most 1/4 pixel excess).
        // Split exactly at the point-source tangencies: only the far arc casts a tail.
        // Boundary points have |p|² = gap² + 4*d*r*sin²(angle/2). Don't tessellate
        // offscreen arcs of a huge circle even when its tangencies are inside the light.
        double end = 2 * Math.asin(Math.min(1, Math.sqrt((radius - gap) * (radius + gap) / (4 * d * r))));
        double tangent = Math.min(end, Math.atan2(Math.sqrt(tangent2), r));
        double error = Math.max(pixel * .25, Math.ulp((float) Math.min(radius, r)));
        double maxStep = 2 * Math.atan2(Math.sqrt(error * (2 * r + error)), r);
        double ux = cx / d, uy = cy / d, length = tail(2 * r);
        double depth = gap, width = 0;
        // At grazing incidence a subpixel normal error can move the apparent exit a long
        // way along the ray. Keep the first rear support within a pixel of true tangency.
        double afterTangent = Math.min(end, tangent + Math.min(maxStep, pixel / r));
        double from = 0;
        for (int arc = 0; from < end; arc++) {
            double to = arc == 0 ? tangent : arc == 1 ? afterTangent : end;
            int steps = Math.max(1, (int) Math.ceil((to - from) / maxStep));
            double halfStep = (to - from) / (2 * steps), reach = r / Math.cos(halfStep);
            for (int i = 0; i <= steps; i++) {
                double nextDepth, nextWidth;
                if (i == steps) {
                    nextDepth = gap + 2 * r * square(Math.sin(to * .5));
                    nextWidth = to == Math.PI ? 0 : r * Math.sin(to);
                } else {
                    double angle = from + (2 * i + 1) * halfStep;
                    // d - reach*cos(angle) without cancellation near the source.
                    nextDepth = gap + 2 * reach * Math.sin((angle + halfStep) * .5) * Math.sin((angle - halfStep) * .5);
                    nextWidth = reach * Math.sin(angle);
                }
                circleSlice(ux, uy, depth, width, nextDepth, nextWidth, arc == 0 ? 0 : length);
                depth = nextDepth;
                width = nextWidth;
            }
            from = to;
        }
        // If the query disk cuts the outline, everything in the disk beyond this chord
        // is inside the circle. Fill up to the disk bound without casting from the cut.
        if (end < Math.PI) { circleSlice(ux, uy, depth, width, radius, width, 0); }
    }

    private void circleSlice(double ux, double uy, double a, double wa, double b, double wb, double length) {
        double ax = ux * a - uy * wa, ay = uy * a + ux * wa;
        double bx = ux * b - uy * wb, by = uy * b + ux * wb;
        double cx = ux * b + uy * wb, cy = uy * b - ux * wb;
        double dx = ux * a + uy * wa, dy = uy * a - ux * wa;
        solid(ax, ay, bx, by, cx, cy);
        solid(cx, cy, dx, dy, ax, ay);
        if (length > 0) {
            ribbon(ax, ay, bx, by, length);
            ribbon(dx, dy, cx, cy, length);
        }
    }

    private static double square(double value) { return value * value; }

    private void solid(double ax, double ay, double bx, double by, double cx, double cy) {
        point(ax, ay, 0); point(bx, by, 0); point(cx, cy, 0);
    }

    private void ribbon(double ax, double ay, double bx, double by, double length) {
        if (Math.min(ax, bx) > radius || Math.max(ax, bx) < -radius
            || Math.min(ay, by) > radius || Math.max(ay, by) < -radius) { return; }
        if (ax * by == ay * bx) { return; } // Collinear with the source: no projected area.
        // Bound the variation in ray direction *and* distance along each strip. Otherwise
        // a long grazing wall interpolates the near end's fade far down the wall.
        double da = Math.hypot(ax, ay), db = Math.hypot(bx, by);
        if (Math.hypot(bx - ax, by - ay) > .125 * Math.min(da, db)) {
            double mx = (ax + bx) * .5, my = (ay + by) * .5;
            ribbon(ax, ay, mx, my, length);
            ribbon(mx, my, bx, by, length);
            return;
        }
        double fx = ax + ax / da * length, fy = ay + ay / da * length;
        double gx = bx + bx / db * length, gy = by + by / db * length;
        point(ax, ay, 0); point(bx, by, 0); point(gx, gy, 1);
        point(gx, gy, 1); point(fx, fy, 1); point(ax, ay, 0);
    }

    private void point(double dx, double dy, float visibility) {
        triangles.add(finiteFloat(x + dx), finiteFloat(y + dy), visibility);
    }

    private static float finiteFloat(double value) {
        return (float) Math.clamp(value, -Float.MAX_VALUE, Float.MAX_VALUE);
    }
}
