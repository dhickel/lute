package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.AttackDamage;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.singletons.GameConsts;

public final class ArrowFlightTest {
    private static final Projectile TYPE = projectile(.5f);

    private ArrowFlightTest() { }

    public static void run() {
        rangeRolls(15, 20);
        rangeRolls(10, 10);
        rangeRolls(20, 30);
        rangeRolls(Integer.MAX_VALUE, Integer.MAX_VALUE);
        rollsPerHit();
        collisionShapes();
        var aim = new Vector2(1, 0);
        var world = new World(Vector2.Zero, true);
        try {
            verifyIntercept(world);
            verifySpeed(world, 1f, .15f);
            verifySpeed(world, 8f, .5f);
            verifyNorthShots(world);
            var centered = launch(TYPE, Vector2.Zero);
            require(!centered.finished(world, .5f / TYPE.speed()) && centered.tip.equals(new Vector2(.5f, 0)),
                "Centered aim uses facing and the configured speed");

            var target = hurtbox(world, aim);
            var arrow = launch(TYPE, aim);
            Facing.MAPPER.get(arrow.owner).setDirection(Facing.Direction.DOWN);
            DamageFeedback.land(target, Health.MAPPER.get(target), new AttackDamage.Scalar(1), .25f, Facing.Direction.RIGHT);
            require(Health.MAPPER.get(target).getCurrent() == 100 && DamageFeedback.MAPPER.get(target) == null,
                "A hit rounded to zero consumes no HP and creates no number");
            require(arrow.finished(world, 2f) && oneHit(target), "One long update sweeps through the exact crosshair");
            var feedback = DamageFeedback.MAPPER.get(target);
            var hit = feedback.numbers.getFirst();
            var health = Health.MAPPER.get(target);
            require(hit.facing == Facing.Direction.RIGHT && hit.amount == 100 - health.getCurrent()
                && hit.remaining == health.getCurrent() / 100f, "Feedback snapshots release facing and effective hit");
            feedback.advance(.25f);
            DamageFeedback.land(target, health, new AttackDamage.Scalar(500), 1, Facing.Direction.LEFT);
            var kill = feedback.numbers.getLast();
            require(kill.remaining == 0 && kill.amount == 100 - hit.amount, "Killing hit reports only remaining HP");
            require(GameplayHud.healthText(health).equals("HP 0/100") && GameplayHud.clockText(6 * 60 + 4).equals("06:04"), "Dead player's clock and health");
            require(GameplayHud.healthText(new Health(53)).equals("HP 53/53") && GameplayHud.clockText(23 * 60 + 59).equals("23:59")
                && GameplayHud.clockText(0).equals("00:00"), "24-hour format at the day boundary");
            feedback.advance(.45f);
            require(feedback.numbers.size() == 1 && feedback.numbers.getFirst() == kill, "Numbers expire independently");
            feedback.advance(.25f);
            require(feedback.numbers.isEmpty(), "Last number expires");

            var apexTarget = hurtbox(world, new Vector2(.5f, .15f));
            require(launch(projectile(2f), aim).finished(world, 2f) && oneHit(apexTarget),
                "A long update follows the short pre-aim arc instead of sweeping straight to aim");
        } finally { world.dispose(); }

        world = new World(Vector2.Zero, true);
        try {
            var target = hurtbox(world, new Vector2(2f, -1.2f));
            require(launch(TYPE, aim).finished(world, 6f) && oneHit(target),
                "A long update hits a small hurtbox below the old straight extension");
        } finally { world.dispose(); }
    }

    private static void rangeRolls(int min, int max) {
        var damage = new AttackDamage.Range(min, max);
        for (int offset = 0; offset <= max - min; offset++) {
            final int sample = offset;
            var random = new java.util.Random() {
                @Override public int nextInt(int bound) {
                    require(bound == max - min + 1, "Roll includes every integer through max");
                    return sample;
                }
            };
            for (float scale : new float[]{.25f, .5f, .75f, 1f}) {
                require(damage.roll(random, scale) == Math.round((min + sample) * (double) scale),
                    "Explicit range scales a single inclusive roll without scalar jitter");
            }
        }
    }

    private static void rollsPerHit() {
        var previous = com.badlogic.gdx.math.MathUtils.random;
        try {
            com.badlogic.gdx.math.MathUtils.random = new java.util.Random(17);
            var expected = new java.util.Random(17);
            var target = new Entity();
            var health = new Health(500);
            int remaining = 500;
            for (int i = 0; i < 20; i++) {
                DamageFeedback.land(target, health, new AttackDamage.Scalar(10), 1, Facing.Direction.UP);
                remaining -= Math.round(10 * (.9f + (1.1f - .9f) * expected.nextFloat()));
                require(remaining == health.getCurrent(), "Fresh uniform sample for each hit");
            }
        } finally { com.badlogic.gdx.math.MathUtils.random = previous; }
    }

    private static void collisionShapes() {
        var world = new World(Vector2.Zero, true);
        try {
            float unit = GameConsts.UNIT_SCALE;
            for (var shape : CollisionData.Shape.values()) {
                var bounds = new CharacterBounds(0, 0, 20, shape == CollisionData.Shape.CIRCLE ? 20 : 8);
                var collision = new CollisionData(shape, bounds, 90f);
                var body = world.createBody(new BodyDef());
                var fixture = CharacterFixtures.create(body, collision, false, 10, bounds.y2() / 2);
                require(!fixture.isSensor(), shape + " fixture is solid");
                if (shape == CollisionData.Shape.CIRCLE) {
                    var circle = (CircleShape) fixture.getShape();
                    require(circle.getPosition().isZero() && circle.getRadius() == 10 * unit,
                        "CIRCLE creates a native circle around the bounds center");
                } else {
                    var polygon = (PolygonShape) fixture.getShape();
                    int count = switch (shape) { case RECT -> 4; case TRIANGLE -> 3; default -> 8; };
                    require(polygon.getVertexCount() == count, shape + " vertex count");
                    require(polygonSpan(polygon, true) <= 8 * unit + .0001f
                        && Math.abs(polygonSpan(polygon, false) - 20 * unit) < .0001f,
                        shape + " rotates its width axis around the center");
                }
                world.destroyBody(body);

                var type = new Projectile("test", 12, 5, collision, 0, 0, 0, 0);
                var arrow = launch(type, new Vector2(3, 0));
                var target = hurtbox(world, new Vector2(1.5f, 0));
                require(arrow.finished(world, .25f) && oneHit(target),
                    shape + " sweeps across a small target when rotated");
                var bodies = new com.badlogic.gdx.utils.Array<com.badlogic.gdx.physics.box2d.Body>();
                world.getBodies(bodies);
                world.destroyBody(bodies.first());
            }
        } finally { world.dispose(); }
    }

    private static float polygonSpan(PolygonShape polygon, boolean horizontal) {
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        var point = new Vector2();
        for (int i = 0; i < polygon.getVertexCount(); i++) {
            polygon.getVertex(i, point);
            float value = horizontal ? point.x : point.y;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    private static void verifyIntercept(World world) {
        var center = new Vector2(3, -1);
        require(ArrowFlight.intercept(TYPE, Vector2.Zero, center, Vector2.Zero).equals(center),
            "Stationary target needs no lead");
        require(ArrowFlight.intercept(TYPE, center, center, new Vector2(2, 3)).equals(center),
            "Coincident target does not invent a lead direction");
        require(ArrowFlight.intercept(TYPE, Vector2.Zero, center, new Vector2(20, 0)).equals(center),
            "An outrunning target gets a direct shot");
        var velocity = new Vector2(0, 4);
        var aim = ArrowFlight.intercept(TYPE, Vector2.Zero, center, velocity);
        float time = (aim.y - center.y) / velocity.y;
        var arrow = launch(TYPE, aim);
        require(time > 0 && !arrow.finished(world, time) && arrow.tip.epsilonEquals(aim, .0001f),
            "Lead time matches actual curved flight, not straight-line distance");
    }

    private static void verifySpeed(World world, float aimX, float lift) {
        var arrow = launch(TYPE, new Vector2(aimX, 0));
        float elapsed = 0;
        for (float u : new float[]{.5f, 1f, 1.5f, 2f}) {
            // Independent midpoint integration of distance along the expected curve.
            double distance = 0;
            for (int i = 0; i < 4096; i++) {
                double sample = u * (i + .5) / 4096;
                distance += Math.hypot(aimX, 4 * lift * (1 - 2 * sample)) * u / 4096;
            }
            float time = (float) (distance / TYPE.speed());
            require(!arrow.finished(world, time - elapsed), "Arrow remains active across aim");
            elapsed = time;
            require(Math.abs(arrow.tip.x - aimX * u) < .0001f
                && Math.abs(arrow.tip.y - 4 * lift * u * (1 - u)) < .0001f,
                "Configured speed follows the same parabola before and after aim: distance=" + aimX + ", progress=" + u);
            require(Math.abs(arrow.direction.y / arrow.direction.x - 4 * lift * (1 - 2 * u) / aimX) < .0001f,
                "Sprite follows the same tangent through aim");
        }
    }

    private static void verifyNorthShots(World world) {
        for (float angle : new float[]{-5, 0, 5}) {
            var aim = new Vector2(0, 1).rotateDeg(angle);
            var arrow = launch(TYPE, aim);
            require(!arrow.finished(world, .25f) && arrow.tip.epsilonEquals(aim.cpy().scl(3), .0001f),
                "Near-north shots continue forward at configured speed");
        }
        for (float angle : new float[]{-17.5f, 17.5f}) {
            var aim = new Vector2(0, 1).rotateDeg(angle);
            var arrow = launch(TYPE, aim);
            float apex = .5f + aim.y / .6f;
            require(arrow.finished(world, 4f)
                    && arrow.tip.epsilonEquals(new Vector2(aim.x * apex, aim.y * apex + .3f * apex * (1 - apex)), .0001f),
                "A short transition shot uses half lift and expires at the apex without retracing");
            var longArrow = launch(TYPE, aim.cpy().scl(4));
            require(!longArrow.finished(world, 2f), "A longer transition shot can clear the launch strip");
        }
    }

    private static Projectile projectile(float arcHeight) {
        return new Projectile("test", 12, 5, CollisionData.ellipse(new CharacterBounds(0, 0, 10, 8.5f)), arcHeight, 0, 0, 0);
    }

    private static ArrowFlight launch(Projectile type, Vector2 aim) {
        return new ArrowFlight(new Entity().add(new Facing(Facing.Direction.RIGHT)), type, new Vector2(), aim, new AttackDamage.Scalar(10), 1);
    }

    private static boolean oneHit(Entity target) {
        int remaining = Health.MAPPER.get(target).getCurrent();
        return remaining >= 89 && remaining <= 91;
    }

    private static Entity hurtbox(World world, Vector2 position) {
        var target = new Entity().add(new Health(100)).add(new Transform(position, 0, new Vector2(1, 1), new Vector2(1, 1), 0, 0));
        var bodyDef = new BodyDef();
        bodyDef.position.set(position);
        var body = world.createBody(bodyDef);
        body.setUserData(target);
        var shape = new CircleShape();
        try {
            shape.setRadius(.02f);
            var fixture = body.createFixture(shape, 0);
            fixture.setSensor(true);
            fixture.setUserData(Hurtbox.INSTANCE);
        } finally { shape.dispose(); }
        return target;
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
