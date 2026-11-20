package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.data.json.EmissiveData;

import java.util.Optional;

import static com.badlogic.gdx.physics.box2d.BodyDef.BodyType.*;

/** Synthetic pulse and stencil-mesh regressions. No assets, window or graphics context. */
public final class LightingBehaviorTest {
    private static final float PIXEL = 1f / 32;
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
        sample(4, 0, true, "dynamic solid casts with zero filter bits");
        sample(0, 5, false, "all fixtures on the excluded body and sensors ignored");
        collect(emitter, 6, 0, 10);
        sample(4, 0, false, "reusing the mask for a different origin");
        sample(0, 0, true, "same wall casts for the second light");
        moving.setTransform(0, 2, 0);
        collect(emitter, 0, 0, 10);
        sample(4, 0, false, "no stale shadow after movement");
        sample(0, 4, true, "new position casts");
        moving.setActive(false);
        collect(emitter, 0, 0, 10);
        check(mesh.triangles.size == 0, "inactive solid is inert");
        moving.setActive(true);
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
        sample(14, 9.3, true, "transformed offset polygon projects absolute world xy");
        sample(14, 10.8, false, "polygon shadow has a finite silhouette");
        sample(11.7, 8, false, "ahead of near face stays lit");
        sample(12, 8, true, "interior of solid itself is shadowed");
        suppressed(null, 12, 8);
        world.destroyBody(body);

        var square = body(StaticBody, 0, 0, 0);
        box(square, 1, 1, 0, 0);
        suppressed(null, 1, 0);
        suppressed(null, -1, 1);
        collect(null, Math.nextUp(1f), 0, 8);
        sample(2, 0, false, "immediately outside is not suppressed");
        collect(null, 1, 2, 8);
        sample(1.1, 0, false, "source on extension of edge is not inside");
        world.destroyBody(square);
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
        sample(14, 8, true, "rotated local circle center");
        sample(8, 8, false, "body origin is not the circle");
        // At x=16 the exact tangent rays are 6/sqrt(3) above and below y=8.
        for (int side : new int[]{-1, 1}) {
            sample(16, 8 + side * (6 / Math.sqrt(3) - .001), true, "inside true tangent");
            sample(16, 8 + side * (6 / Math.sqrt(3) + .001), false, "outside true tangent");
        }
        suppressed(null, 12, 8);
        suppressed(null, 11, 8);
        collect(null, Math.nextDown(11f), 8, 8);
        sample(11.02, 14, true, "wide near-tangent circle shadow reaches disk");
        sample(10.99, 14, false, "near-tangent circle does not suppress whole light");
        body.setTransform(-3, 0, (float) (Math.PI / 2)); // place the center at (1, ~0)
        var center = body.getWorldPoint(new Vector2(0, -4));
        float tangentY = center.y + 1e-10f;
        collect(null, 0, tangentY, 8); // hypot(1, 1e-10) rounds to one, but the source is outside.
        sample(-3, tangentY, false, "tiny transverse offset on tangent line is not containment");
        sample(3, tangentY, true, "near-contact circle still casts");
        world.destroyBody(body);

        // Check both the ordinary full tangent arc and a large disk whose tangencies lie
        // outside the light. Compare front coverage against analytic circle points, not vertices.
        for (float r : new float[]{.6f, 32f}) {
            float cx = r + 1;
            var circle = body(StaticBody, cx, 0, 0);
            var s = new CircleShape();
            try { s.setRadius(r); circle.createFixture(s, 0); }
            finally { s.dispose(); }
            collect(null, 0, 0, 6);
            for (int i = -98; i <= 98; i++) {
                double angle = i / 100. * Math.acos(r / cx);
                double nx = -Math.cos(angle), ny = Math.sin(angle);
                double sx = cx + r * nx, sy = r * ny;
                if (Math.hypot(sx, sy) >= 5.95) { continue; }
                sample(sx * 1.0001, sy * 1.0001, true, "no leak just beyond analytic surface");
                // Displace toward the source across this supporting plane (strictly before shape).
                sample(sx + nx * PIXEL * .26, sy + ny * PIXEL * .26, false, "subpixel near-cap bound");
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
            sample(3.2, -5.3, false, "chains have no filled interior");
            sample(6, -5, true, "first segment after transform");
            sample(3, -2, true, "second segment after transform");
            sample(0, -5, true, "third segment after transform");
            sample(3, -8, loop, "only native loops close");
            world.destroyBody(body);
        }
    }

    private void edges() {
        var wall = body(StaticBody, .001f, 0, 0);
        var shape = new EdgeShape();
        try {
            shape.set(0, -100, 0, 100);
            wall.createFixture(shape, 0);
        } finally { shape.dispose(); }
        collect(null, 0, 0, 8);
        sample(7.99, 0, true, "near-source long wall cannot clip far cap short");
        sample(.002, 7.99, true, "grazing inside disk near edge endpoint ray");
        sample(.0009, 7.99, false, "front of thin surface not filled");
        collect(null, 2, 0, 8);
        sample(-5.99, 0, true, "edge casts from either side");
        wall.setTransform(0, 0, 0);
        for (int sy : new int[]{0, 100, 101}) {
            collect(null, 0, sy, 8);
            check(mesh.triangles.size == 0, "on edge, endpoint and collinear extension have zero shadow area");
        }
        world.destroyBody(wall);

        var thin = body(StaticBody, .02f, 0, 0);
        box(thin, .001f, 16, 0, 0);
        collect(null, 0, 0, 8);
        sample(7.98, .2, true, "thin solid reaches far side of disk");
        sample(.018, 3, false, "thin solid front edge");
        world.destroyBody(thin);
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
        check(mesh.collect(world, excluded, x, y, radius, PIXEL), "unexpected suppression at " + x + ", " + y);
        check(mesh.triangles.size % 6 == 0, "tightly packed triangle xy");
        for (int i = 0; i < mesh.triangles.size; i++) {
            check(Float.isFinite(mesh.triangles.items[i]), "finite vertex");
        }
    }

    private void suppressed(Body excluded, float x, float y) {
        check(!mesh.collect(world, excluded, x, y, 10, PIXEL), "other filled shape covers source " + x + ", " + y);
        check(mesh.triangles.size == 0, "discard partial/old mask on suppression");
    }

    private void sample(double x, double y, boolean expected, String message) {
        float[] t = mesh.triangles.items;
        boolean blocked = false;
        for (int i = 0; i < mesh.triangles.size && !blocked; i += 6) {
            double a = side(t[i], t[i + 1], t[i + 2], t[i + 3], x, y);
            double b = side(t[i + 2], t[i + 3], t[i + 4], t[i + 5], x, y);
            double c = side(t[i + 4], t[i + 5], t[i], t[i + 1], x, y);
            blocked = a + b + c > 0 && a >= 0 && b >= 0 && c >= 0
                || a + b + c < 0 && a <= 0 && b <= 0 && c <= 0;
        }
        check(blocked == expected, message + " at (" + x + ", " + y + "): expected blocked=" + expected);
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
