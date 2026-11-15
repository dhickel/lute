package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.EllipseMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Converts Tiled's loaded y-up pixel geometry into filled, Box2D-safe world geometry. */
final class DetectorGeometryParser {
    // Box2D welds vertices closer than half its linear slop and requires positive centroid area.
    private static final double MIN_EDGE_SQUARED = .0025 * .0025;
    private static final double MIN_CROSS = 2 * Math.ulp(1f);

    private DetectorGeometryParser() { }

    static MapTriggers.DetectorGeometry parse(MapObject object, float rotation) {
        float scale = GameConsts.UNIT_SCALE;
        if (!Float.isFinite(scale) || scale <= 0) { throw invalid(object, "unit scale must be finite and positive"); }
        float[] vertices;
        float pivotX;
        float pivotY;
        if (object.getClass() == RectangleMapObject.class) {
            var r = ((RectangleMapObject) object).getRectangle();
            dimensions(object, r.x, r.y, r.width, r.height);
            vertices = new float[]{r.x, r.y, r.x + r.width, r.y,
                r.x + r.width, r.y + r.height, r.x, r.y + r.height};
            pivotX = r.x;
            pivotY = r.y + r.height;
        } else if (object instanceof EllipseMapObject ellipse) {
            var e = ellipse.getEllipse();
            dimensions(object, e.x, e.y, e.width, e.height);
            pivotX = e.x;
            pivotY = e.y + e.height;
            float centerX = e.x + e.width * .5f;
            float centerY = e.y + e.height * .5f;
            if (e.width == e.height) {
                var center = transform(centerX, centerY, pivotX, pivotY, rotation, scale);
                float radius = e.width * .5f * scale;
                if (!finite(center.x, center.y, radius, center.x - radius, center.x + radius,
                    center.y - radius, center.y + radius) || radius <= 0
                    || (double) radius * radius > Float.MAX_VALUE / 16.0) {
                    throw invalid(object, "circle must have a finite center and positive finite Box2D-safe radius");
                }
                return new MapTriggers.DetectorGeometry(center, radius, List.of());
            }
            vertices = new float[16];
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                vertices[2 * i] = centerX + (float) Math.cos(angle) * e.width * .5f;
                vertices[2 * i + 1] = centerY + (float) Math.sin(angle) * e.height * .5f;
            }
        } else if (object instanceof PolygonMapObject polygonObject) {
            var polygon = polygonObject.getPolygon();
            vertices = polygon.getTransformedVertices().clone();
            pivotX = polygon.getX();
            pivotY = polygon.getY();
        } else {
            // TextMapObject extends RectangleMapObject, so rectangle matching above must be exact.
            throw invalid(object, "detector requires rectangle, ellipse/circle, or polygon geometry; point, polyline, tile and text are excluded");
        }
        if (vertices.length < 6 || vertices.length % 2 != 0 || !finite(pivotX, pivotY)) {
            throw invalid(object, "polygon requires at least three finite vertices");
        }
        var outline = new ArrayList<Vector2>();
        for (int i = 0; i < vertices.length; i += 2) {
            if (!finite(vertices[i], vertices[i + 1])) { throw invalid(object, "outline coordinates must be finite"); }
            outline.add(new Vector2(vertices[i], vertices[i + 1]));
        }
        // Remove redundant straight-edge points before float rotation can turn them into tiny ears.
        // Validate first so normalization cannot hide backtracking or self-touching edges.
        validateSimple(object, outline, 0);
        removeCollinear(outline);
        for (var point : outline) {
            point.set(transform(point.x, point.y, pivotX, pivotY, rotation, scale));
            if (!finite(point.x, point.y)) { throw invalid(object, "world outline coordinates must be finite"); }
        }
        validateSimple(object, outline, MIN_EDGE_SQUARED);
        removeCollinear(outline);
        double area = signedArea(outline);
        if (!Double.isFinite(area) || Math.abs(area) <= MIN_CROSS) {
            throw invalid(object, "outline must have nondegenerate positive area");
        }
        if (area < 0) { Collections.reverse(outline); }
        List<float[]> pieces = new ArrayList<>();
        if (outline.size() <= 8 && convex(outline)) {
            pieces.add(vertices(outline));
        } else {
            triangulate(object, outline, pieces);
        }
        for (float[] piece : pieces) { validateFixture(object, piece); }
        return new MapTriggers.DetectorGeometry(new Vector2(), 0, pieces);
    }

    private static Vector2 transform(float x, float y, float pivotX, float pivotY, float rotation, float scale) {
        // Tiled's clockwise rotation is negative in the loader's y-up coordinates.
        return new Vector2(x - pivotX, y - pivotY).rotateDeg(-rotation).add(pivotX, pivotY).scl(scale);
    }

    private static void dimensions(MapObject object, float x, float y, float width, float height) {
        if (!finite(x, y, width, height, x + width, y + height)) {
            throw invalid(object, "geometry coordinates and dimensions must be finite");
        }
        if (width <= 0 || height <= 0) { throw invalid(object, "geometry dimensions must be positive"); }
    }

    private static void validateSimple(MapObject object, List<Vector2> points, double minimumEdgeSquared) {
        int n = points.size();
        for (int i = 0; i < n; i++) {
            var a = points.get(i);
            var b = points.get((i + 1) % n);
            if (distanceSquared(a, b) <= minimumEdgeSquared) {
                throw invalid(object, "outline has duplicate or too-close vertices for Box2D");
            }
            var c = points.get((i + 2) % n);
            if (cross(a, b, c) == 0 && !between(a, c, b)) {
                throw invalid(object, "outline has overlapping adjacent edges");
            }
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) { continue; }
                if (intersects(a, b, points.get(j), points.get((j + 1) % n))) {
                    throw invalid(object, "outline is self-intersecting or self-touching");
                }
            }
        }
    }

    private static boolean intersects(Vector2 a, Vector2 b, Vector2 c, Vector2 d) {
        double abC = cross(a, b, c), abD = cross(a, b, d);
        double cdA = cross(c, d, a), cdB = cross(c, d, b);
        return (abC == 0 && between(a, b, c)) || (abD == 0 && between(a, b, d))
            || (cdA == 0 && between(c, d, a)) || (cdB == 0 && between(c, d, b))
            || ((abC > 0) != (abD > 0) && (cdA > 0) != (cdB > 0)
                && abC != 0 && abD != 0 && cdA != 0 && cdB != 0);
    }

    private static boolean between(Vector2 a, Vector2 b, Vector2 p) {
        return p.x >= Math.min(a.x, b.x) && p.x <= Math.max(a.x, b.x)
            && p.y >= Math.min(a.y, b.y) && p.y <= Math.max(a.y, b.y);
    }

    private static void removeCollinear(List<Vector2> points) {
        boolean removed = true;
        while (removed && points.size() > 3) {
            removed = false;
            for (int i = 0; i < points.size(); i++) {
                if (cross(points.get((i + points.size() - 1) % points.size()), points.get(i),
                    points.get((i + 1) % points.size())) == 0) {
                    points.remove(i);
                    removed = true;
                    break;
                }
            }
        }
    }

    private static boolean convex(List<Vector2> points) {
        for (int i = 0; i < points.size(); i++) {
            if (cross(points.get(i), points.get((i + 1) % points.size()), points.get((i + 2) % points.size())) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static void triangulate(MapObject object, List<Vector2> outline, List<float[]> pieces) {
        var remaining = new ArrayList<>(outline);
        while (remaining.size() > 3) {
            boolean clipped = false;
            for (int i = 0; i < remaining.size(); i++) {
                var a = remaining.get((i + remaining.size() - 1) % remaining.size());
                var b = remaining.get(i);
                var c = remaining.get((i + 1) % remaining.size());
                if (cross(a, b, c) <= MIN_CROSS) { continue; }
                boolean contains = false;
                for (var p : remaining) {
                    if (p != a && p != b && p != c && cross(a, b, p) >= 0
                        && cross(b, c, p) >= 0 && cross(c, a, p) >= 0) {
                        contains = true;
                        break;
                    }
                }
                if (contains) { continue; }
                pieces.add(vertices(List.of(a, b, c)));
                remaining.remove(i);
                clipped = true;
                break;
            }
            if (!clipped) { throw invalid(object, "outline cannot be decomposed into nondegenerate convex pieces"); }
        }
        pieces.add(vertices(remaining));
    }

    private static void validateFixture(MapObject object, float[] vertices) {
        var points = new ArrayList<Vector2>();
        for (int i = 0; i < vertices.length; i += 2) { points.add(new Vector2(vertices[i], vertices[i + 1])); }
        for (int i = 0; i < points.size(); i++) {
            for (int j = i + 1; j < points.size(); j++) {
                double distance = distanceSquared(points.get(i), points.get(j));
                if (distance <= MIN_EDGE_SQUARED || distance > Float.MAX_VALUE / 16.0) {
                    throw invalid(object, "convex piece vertices are too close or too large for Box2D");
                }
            }
            var a = points.get(i);
            var b = points.get((i + 1) % points.size());
            var c = points.get((i + 2) % points.size());
            double turn = cross(a, b, c);
            float nativeTurn = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
            if (turn <= MIN_CROSS || turn > Float.MAX_VALUE / 16.0 || !Float.isFinite(nativeTurn)
                || nativeTurn <= MIN_CROSS) {
                throw invalid(object, "convex piece is degenerate or too large for Box2D");
            }
        }
        // Box2D computes its centroid using float sums/products, not double arithmetic.
        float area = 0, centerX = 0, centerY = 0;
        var origin = points.getFirst();
        for (int i = 1; i + 1 < points.size(); i++) {
            float ax = points.get(i).x - origin.x, ay = points.get(i).y - origin.y;
            float bx = points.get(i + 1).x - origin.x, by = points.get(i + 1).y - origin.y;
            float triangleArea = .5f * (ax * by - ay * bx);
            area += triangleArea;
            centerX += triangleArea * (ax + bx) / 3;
            centerY += triangleArea * (ay + by) / 3;
        }
        if (!finite(area, centerX, centerY, centerX / area + origin.x, centerY / area + origin.y)
            || area <= Math.ulp(1f)) {
            throw invalid(object, "convex piece has unsafe Box2D centroid or area");
        }
    }

    private static float[] vertices(List<Vector2> points) {
        float[] result = new float[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            result[i * 2] = points.get(i).x;
            result[i * 2 + 1] = points.get(i).y;
        }
        return result;
    }

    private static double signedArea(List<Vector2> points) {
        double sum = 0;
        for (int i = 1; i + 1 < points.size(); i++) { sum += cross(points.getFirst(), points.get(i), points.get(i + 1)); }
        return sum;
    }

    private static double cross(Vector2 a, Vector2 b, Vector2 c) {
        return ((double) b.x - a.x) * ((double) c.y - a.y) - ((double) b.y - a.y) * ((double) c.x - a.x);
    }

    private static double distanceSquared(Vector2 a, Vector2 b) {
        double dx = (double) b.x - a.x, dy = (double) b.y - a.y;
        return dx * dx + dy * dy;
    }

    private static boolean finite(float... values) {
        for (float value : values) { if (!Float.isFinite(value)) { return false; } }
        return true;
    }

    private static IllegalArgumentException invalid(MapObject object, String message) {
        return new IllegalArgumentException("Trigger '" + object.getName() + "': " + message);
    }
}
