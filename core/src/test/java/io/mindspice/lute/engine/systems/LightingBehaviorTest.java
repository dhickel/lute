package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.data.json.EmissiveData;

import java.util.Optional;

import static com.badlogic.gdx.physics.box2d.BodyDef.BodyType.*;

/** Synthetic pulse and visibility-mesh regressions. No assets, window or graphics context. */
public final class LightingBehaviorTest {
    private static final float AUTHORED_PIXEL = 1f / 32;
    private static final float VIEW_PIXEL = AUTHORED_PIXEL / 2;
    private final NightOcclusion mesh = new NightOcclusion();
    private final World world = new World(Vector2.Zero, true);

    public static void main(String[] args) {
        pulses();
        GdxNativesLoader.load();
        Box2D.init();
        var test = new LightingBehaviorTest();
        try {
            test.filterAndMove();
            test.polygon();
            test.circles();
            test.chains();
            test.edges();
            test.overlappingTails();
        } finally {
            test.world.dispose();
        }
        System.out.println("LightingBehaviorTest: passed (pulse math and native Box2D shadows)");
    }

    private static void pulses() {
        var pulse = light(new EmissiveData.Pulse(4, .25f, .5f));
        double[] wave = {0, 1, 0, -1, 0};
        for (int t = 0; t < wave.length; t++) {
            double value = pulse.wave(t);
            near(value, wave[t], "pulse at second " + t);
            near(pulse.radius(value), 4 + wave[t], "scaled radius");
            near(pulse.intensity(value), .875 * (1 + .5 * wave[t]), "unclamped intensity");
        }
        near(pulse.red(), 1, "red");
        near(pulse.green(), 170 / 255., "green");
        near(pulse.blue(), 85 / 255., "blue");
        var steady = light(null);
        near(steady.radius(steady.wave(17)), 4, "null pulse radius");
        near(steady.intensity(steady.wave(17)), .875, "null pulse intensity");
        var radiusOnly = light(new EmissiveData.Pulse(4, .25f, 0));
        near(radiusOnly.intensity(radiusOnly.wave(1)), .875, "zero intensity strength");
        near(radiusOnly.radius(radiusOnly.wave(1)), 5, "radius-only pulse");
        var intensityOnly = light(new EmissiveData.Pulse(4, 0, .5f));
        near(intensityOnly.radius(intensityOnly.wave(3)), 4, "zero radius strength");
        near(intensityOnly.intensity(intensityOnly.wave(3)), .4375, "intensity-only pulse");

        double period = 3e-201;
        var fast = light(new EmissiveData.Pulse(period, .5f, .5f));
        double phase = (Double.MAX_VALUE % period) / period;
        near(fast.wave(Double.MAX_VALUE), Math.sin(2 * Math.PI * phase), "finite bounded phase");
        var data = new EmissiveData("#FFFFFF", 2, 1, Optional.of(new EmissiveData.Pulse(1, .75f, 0)));
        rejectsScale(data, Float.MAX_VALUE / 2); // base finite but the peak overflows
        rejectsScale(data, Float.MIN_VALUE); // base positive but the trough rounds to zero
    }

    private static Emissive light(EmissiveData.Pulse pulse) {
        return new Emissive(new EmissiveData("#FFaa55", 32, .875f, Optional.ofNullable(pulse)), .125f);
    }

    private static void rejectsScale(EmissiveData data, float scale) {
        try { new Emissive(data, scale); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Accepted unusable world radius at scale " + scale);
    }

    private void filterAndMove() {
        Body emitter = body(StaticBody, 0, 0, 0);
        box(emitter, 1, 1, 0, 0);
        box(emitter, .5f, .5f, 0, 3);
        Body sensor = body(StaticBody, 0, 0, 0);
        box(sensor, 2, 2, 0, 0).setSensor(true);
        Body moving = body(DynamicBody, 2, 0, 0);
        var wall = box(moving, .2f, 1, 0, 0);
        var filter = wall.getFilterData();
        filter.maskBits = 0;
        filter.categoryBits = 0;
        wall.setFilterData(filter);
        collect(emitter, 0, 0, 10);
        tail(0, 0, 2.2, 0, .75, "dynamic solid casts with zero filter bits");
        lit(0, 3, "all fixtures on the excluded body and sensors ignored");
        collect(emitter, 6, 0, 10);
        lit(2.5, 0, "reusing the mask for a different origin");
        tail(6, 0, 1.8, 0, .75, "same wall casts for the second light");
        moving.setTransform(0, 2, 0);
        collect(emitter, 0, 0, 10);
        lit(2.5, 0, "no stale shadow after movement");
        tail(0, 0, 0, 3, .75, "new position casts");
        moving.setActive(false);
        collect(emitter, 0, 0, 10);
        check(mesh.triangles.size == 0, "inactive solid is inert");
        moving.setActive(true);
        collect(emitter, 0, 0, 10);
        dark(0, 2, "reactivated fixture blocks again");
        suppressed(null, 0, 0); // Self exclusion belongs to the light, not the fixture.
        world.destroyBody(emitter);
        world.destroyBody(sensor);
        world.destroyBody(moving);
        collect(null, 0, 0, 10);
        check(mesh.triangles.size == 0, "no retired blockers");
    }

    private void polygon() {
        Body body = body(KinematicBody, 8, 8, (float) (Math.PI / 2));
        box(body, .9f, .2f, 0, -4);
        collect(null, 10, 8, 8);
        tail(10, 8, 12.2, 8.4, .75, "transformed offset polygon casts from its rear face");
        lit(12.4, 9.4, "outside the silhouette");
        lit(11.7, 8, "ahead of near face stays lit");
        dark(12, 8, "interior of solid itself is shadowed");
        suppressed(null, 12, 8);
        world.destroyBody(body);

        var square = body(StaticBody, 0, 0, 0);
        box(square, 1, 1, 0, 0);
        collect(null, -3, 0, 8);
        dark(.96, .67, "filled all the way to the rear, even when thicker than the maximum tail");
        tail(-3, 0, 1, 0, .75, "thick solid");
        suppressed(null, 1, 0);
        suppressed(null, -1, 1);
        collect(null, Math.nextUp(1f), 0, 8);
        lit(2, 0, "immediately outside is not suppressed");
        collect(null, 1, 2, 8);
        lit(1.1, 0, "source on extension of edge is not inside");
        world.destroyBody(square);

        // Max chord is 2*r, neither the side length nor the diagonal of the world AABB.
        for (float r : new float[]{.125f, .375f}) {
            var diamond = body(StaticBody, 2, 0, 0);
            var shape = new PolygonShape();
            try {
                shape.set(new float[]{-r, 0, 0, -r, r, 0, 0, r});
                diamond.createFixture(shape, 0);
            } finally { shape.dispose(); }
            collect(null, 0, 0, 8);
            dark(2 + r / 2, r / 4, "inside diamond");
            double length = .75 * 2 * r;
            tail(0, 0, 2 + r, 0, length, "polygon diameter " + (2 * r));
            // This ray exits at a vertex, so its endpoint is not shortened by a chord.
            expect(2 + r + .95 * length, 0, .95, .02, "almost restored before the endpoint");
            world.destroyBody(diamond);
        }
    }

    private void circles() {
        Body body = body(StaticBody, 8, 8, (float) (Math.PI / 2));
        var shape = new CircleShape();
        try {
            shape.setRadius(1);
            shape.setPosition(new Vector2(0, -4));
            body.createFixture(shape, 0);
        } finally { shape.dispose(); }
        collect(null, 10, 8, 8);
        dark(12.94, 8.2, "rotated local circle filled to the rear");
        lit(8, 8, "body origin is not the circle");
        for (int i = -12; i <= 12; i++) {
            double nx = Math.cos(i * .1), ny = Math.sin(i * .1);
            dark(12 + (1 - VIEW_PIXEL) * nx, 8 + (1 - VIEW_PIXEL) * ny, "inside analytic rear arc");
            // Two pixels radially past the rear arc, with a pixel of allowance for tessellation.
            double step = 2 * VIEW_PIXEL / Math.hypot(2 + nx, ny);
            expect(12 + nx + step * (2 + nx), 8 + ny + step * ny, 2 * VIEW_PIXEL / .75,
                VIEW_PIXEL / .75, "tail begins at the curved rear surface");
        }
        for (int sign : new int[]{-1, 1}) {
            tail(10, 8, 12 + Math.cos(.7), 8 + sign * Math.sin(.7), .75, "circle rear arc");
            // Tangency relative to the light is (1.5, ±sqrt(3)/2), unit ray (sqrt(3)/2, ±.5).
            double x = 11.5 + .375 * Math.sqrt(3) / 2;
            double dy = Math.sqrt(3) / 2 + .375 * .5;
            expect(x, 8 + sign * (dy - .0001), .5, .06, "tail just inside the true tangent ray");
            lit(x, 8 + sign * (dy + .0001), "just outside the true tangent ray");
        }
        suppressed(null, 12, 8);
        suppressed(null, 11, 8);
        collect(null, Math.nextDown(11f), 8, 8);
        dark(11.04, 8.2, "near-contact circle still fills its interior");
        lit(10.99, 8.45, "near-contact circle does not suppress whole light");
        body.setTransform(-3, 0, (float) (Math.PI / 2)); // place the center at (1, ~0)
        var center = body.getWorldPoint(new Vector2(0, -4));
        float tangentY = center.y + 1e-10f;
        collect(null, 0, tangentY, 8); // hypot(1, 1e-10) rounds to one, but the source is outside.
        lit(-3, tangentY, "tiny transverse offset on tangent line is not containment");
        tail(0, tangentY, 2, tangentY, .75, "near-contact circle still casts");
        world.destroyBody(body);

        // Check both the ordinary full tangent arc and a large disk whose tangencies lie
        // outside the light. Compare front coverage against analytic circle points, not vertices.
        for (float r : new float[]{.125f, .375f, 32f}) {
            float cx = r + 1;
            var circle = body(StaticBody, cx, 0, 0);
            var s = new CircleShape();
            try { s.setRadius(r); circle.createFixture(s, 0); }
            finally { s.dispose(); }
            collect(null, 0, 0, 6);
            if (r < 1) { tail(0, 0, cx + r, 0, .75 * 2 * r, "circle diameter " + (2 * r)); }
            for (int i = -98; i <= 98; i++) {
                double angle = i / 100. * Math.acos(r / cx);
                double nx = -Math.cos(angle), ny = Math.sin(angle);
                double sx = cx + r * nx, sy = r * ny;
                if (Math.hypot(sx, sy) >= 5.95) { continue; }
                // Allow tessellation error in either direction, but less than one display pixel.
                double offset = VIEW_PIXEL * .75;
                dark(sx - nx * offset, sy - ny * offset, "inside analytic circle surface");
                lit(sx + nx * offset, sy + ny * offset, "outside supporting plane of near arc");
            }
            world.destroyBody(circle);
        }
    }

    private void chains() {
        for (boolean loop : new boolean[]{false, true}) {
            Body body = body(StaticBody, 3, -5, (float) (Math.PI / 2));
            var shape = new ChainShape();
            try {
                float[] corners = {-1, -1, 1, -1, 1, 1, -1, 1};
                if (loop) { shape.createLoop(corners); } else { shape.createChain(corners); }
                body.createFixture(shape, 0);
            } finally { shape.dispose(); }
            collect(null, 3, -5, 6);
            lit(3.2, -5.3, "chains have no filled interior");
            tail(3, -5, 4, -5, .75, "first segment after transform");
            tail(3, -5, 3, -4, .75, "second segment after transform");
            tail(3, -5, 2, -5, .75, "third segment after transform");
            if (loop) { tail(3, -5, 3, -6, .75, "native loop closes"); }
            else { lit(3, -6.375, "open chain stays open"); }
            world.destroyBody(body);
        }

        var chain = body(StaticBody, 2, 0, 0);
        var shape = new ChainShape();
        try {
            shape.createChain(new float[]{0, -2, 0, 0, 0, .25f});
            chain.createFixture(shape, 0);
        } finally { shape.dispose(); }
        collect(null, 0, 0, 8);
        tail(0, 0, 2, -.75, .75, "long chain child is capped");
        tail(0, 0, 2, .125, .1875, "short child uses its own length, not total chain length");
        world.destroyBody(chain);
    }

    private void edges() {
        var wall = body(StaticBody, .125f, 0, 0);
        edge(wall, -100, 100);
        collect(null, 0, 0, 8);
        dark(.125, 0, "at wall contact");
        lit(.12, 3, "front of wall");
        tail(0, 0, .125, 0, .75, "long near-source wall needs more than endpoint extrusion");
        tail(0, 0, .125, .2, .75, "wide wall, oblique ray");
        tail(0, 0, .125, 4, .75, "grazing ray recovers after its own exit, not the wall endpoint");
        lit(.45, 5, "grazing wall does not cast a wall-normal strip");
        collect(null, 2, 0, 8);
        tail(2, 0, .125, 0, .75, "edge casts from either side");
        collect(null, 0, 0, 1.5f);
        tail(0, 0, .125, 0, .375, "quarter of the actual light radius is the tighter cap");
        collect(null, 0, 0, 1.5f, 8);
        tail(0, 0, .125, 0, .75, "same queried reach, larger actual radius");
        wall.setTransform(0, 0, 0);
        for (int sy : new int[]{0, 100, 101}) {
            collect(null, 0, sy, 8);
            check(mesh.triangles.size == 0, "on edge, endpoint and collinear extension have zero shadow area");
        }
        world.destroyBody(wall);

        var shortEdge = body(StaticBody, .5f, 0, 0);
        edge(shortEdge, 1.25f, 1.5f);
        collect(null, 0, 0, 8);
        tail(0, 0, .5, 1.375, .1875, "small grazing edge casts a short radial tail");
        world.destroyBody(shortEdge);

        var thin = body(StaticBody, .02f, 0, 0);
        box(thin, .001f, 16, 0, 0);
        collect(null, 0, 0, 8);
        dark(.0205, 3, "narrow grazing solid has no interior leak");
        lit(.018, 3, "front of thin solid");
        tail(0, 0, .021, 0, .75, "thin solid casts from rear, not just the corner rays");
        world.destroyBody(thin);
    }

    private void overlappingTails() {
        var first = body(StaticBody, 1, 0, 0);
        var second = body(StaticBody, 1.25f, 0, 0);
        edge(first, -1, 1);
        edge(second, -1, 1);
        collect(null, 0, 0, 8);
        // Both tails cover this point. Their linear visibilities are .75 and 5/12.
        expect(1.5625, 0, 5.0 / 12, .04, "overlap takes the darkest contribution");
        world.destroyBody(first);
        world.destroyBody(second);
    }

    private static void edge(Body body, float minY, float maxY) {
        var shape = new EdgeShape();
        try {
            shape.set(0, minY, 0, maxY);
            body.createFixture(shape, 0);
        } finally { shape.dispose(); }
    }

    private Body body(BodyDef.BodyType type, float x, float y, float angle) {
        var def = new BodyDef();
        def.type = type;
        def.position.set(x, y);
        def.angle = angle;
        return world.createBody(def);
    }

    private static Fixture box(Body body, float halfWidth, float halfHeight, float cx, float cy) {
        var shape = new PolygonShape();
        try {
            shape.setAsBox(halfWidth, halfHeight, new Vector2(cx, cy), 0);
            return body.createFixture(shape, 0);
        } finally { shape.dispose(); }
    }

    private void collect(Body excluded, float x, float y, float radius) {
        collect(excluded, x, y, radius, radius);
    }

    private void collect(Body excluded, float x, float y, float reach, float actualRadius) {
        check(mesh.collect(world, excluded, x, y, reach, VIEW_PIXEL, maxTail(actualRadius)),
            "unexpected suppression at " + x + ", " + y);
        check(mesh.triangles.size % 9 == 0, "tightly packed xyz triangles");
        float[] t = mesh.triangles.items;
        for (int i = 0; i < mesh.triangles.size; i += 3) {
            check(Float.isFinite(t[i]) && Float.isFinite(t[i + 1]), "finite world position");
            check(t[i + 2] >= 0 && t[i + 2] <= 1, "finite visibility in [0,1]");
        }
    }

    private static double maxTail(float actualRadius) {
        return Math.min(24 * AUTHORED_PIXEL, .25 * actualRadius);
    }

    private void suppressed(Body excluded, float x, float y) {
        check(!mesh.collect(world, excluded, x, y, 10, VIEW_PIXEL, maxTail(10)),
            "other filled shape covers source " + x + ", " + y);
        check(mesh.triangles.size == 0, "discard partial/old mask on suppression");
    }

    private void tail(double ox, double oy, double backX, double backY, double length, String message) {
        double distance = Math.hypot(backX - ox, backY - oy);
        double dx = length * (backX - ox) / distance, dy = length * (backY - oy) / distance;
        // CPU interpolant is linear; smoothstep belongs to the shader. Allow for tessellation.
        expect(backX + .25 * dx, backY + .25 * dy, .25, .06, message + " at quarter tail");
        expect(backX + .75 * dx, backY + .75 * dy, .75, .06, message + " at three-quarter tail");
        lit(backX + 1.1 * dx, backY + 1.1 * dy, message + " beyond tail end");
    }

    private void dark(double x, double y, String message) { expect(x, y, 0, 1e-6, message); }
    private void lit(double x, double y, String message) { expect(x, y, 1, 1e-6, message); }

    private void expect(double x, double y, double expected, double tolerance, String message) {
        double actual = visibility(x, y);
        check(Math.abs(actual - expected) <= tolerance, message + " at (" + x + ", " + y
            + "): expected visibility " + expected + " ± " + tolerance + ", got " + actual);
    }

    private double visibility(double x, double y) {
        float[] t = mesh.triangles.items;
        double result = 1;
        for (int i = 0; i < mesh.triangles.size; i += 9) {
            double ax = t[i], ay = t[i + 1], bx = t[i + 3], by = t[i + 4], cx = t[i + 6], cy = t[i + 7];
            double area = side(ax, ay, bx, by, cx, cy);
            if (area == 0) { continue; }
            double a = side(bx, by, cx, cy, x, y) / area;
            double b = side(cx, cy, ax, ay, x, y) / area;
            double c = side(ax, ay, bx, by, x, y) / area;
            if (a >= 0 && b >= 0 && c >= 0) {
                result = Math.min(result, a * t[i + 2] + b * t[i + 5] + c * t[i + 8]);
            }
        }
        return result;
    }

    private static double side(double ax, double ay, double bx, double by, double x, double y) {
        return (bx - ax) * (y - ay) - (by - ay) * (x - ax);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual + " vs " + expected);
    }

    private static void check(boolean result, String message) {
        if (!result) { throw new AssertionError(message); }
    }
}
