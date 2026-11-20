package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.data.json.ProjectilesData.CollisionBounds;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;

public final class ArrowFlightTest {
    private static final Projectile TYPE = projectile(.5f);

    private ArrowFlightTest() { }

    public static void run() {
        var aim = new Vector2(1, 0);
        var world = new World(Vector2.Zero, true);
        try {
            var arrow = launch(TYPE, aim);
            var higherArc = launch(projectile(1f), aim);
            float halfAimTime = .5f / TYPE.speed();
            require(!arrow.finished(world, halfAimTime) && arrow.tip.x == .5f && arrow.tip.y > .1f,
                "A one-tile shot has visible lift at the authored speed and lifetime");
            require(!higherArc.finished(world, halfAimTime) && higherArc.tip.y > arrow.tip.y + .02f,
                "Increasing arc height measurably raises the apex even for a short shot");

            // Compare the slopes of equally short chords immediately on either side of the join.
            float step = halfAimTime / 16f;
            require(!arrow.finished(world, halfAimTime - step), "Arrow is live just before aim");
            var beforeAim = arrow.tip.cpy();
            require(!arrow.finished(world, step) && arrow.tip.equals(aim), "Arrow crosses the exact aim without expiring");
            require(!higherArc.finished(world, halfAimTime) && higherArc.tip.equals(aim),
                "Raising the apex does not change where or when the arrow crosses aim");
            require(!arrow.finished(world, step) && arrow.tip.y < 0, "Arrow descends below the aim line");
            float entrySlope = -beforeAim.y / (aim.x - beforeAim.x);
            float exitSlope = arrow.tip.y / (arrow.tip.x - aim.x);
            require(Math.abs(exitSlope - entrySlope) < .03f, "The descent matches the incoming tangent");

            require(!arrow.finished(world, 4f), "Close aim does not end flight early");
            float lateSlope = arrow.direction.y / arrow.direction.x;
            require(arrow.finished(world, 5f) && arrow.tip.x == 60,
                "Expiry clips the last update at the authored flight distance");
            float finalSlope = arrow.direction.y / arrow.direction.x;
            require(finalSlope < lateSlope - .01f && finalSlope > exitSlope * 1.6f,
                "Descent steepens until expiry without runaway curvature after a close aim");

            var centered = launch(TYPE, Vector2.Zero);
            require(!centered.finished(world, 2.5f) && centered.tip.x == 30 && centered.tip.y > 0,
                "Centered aim uses facing and still arcs");

            var target = hurtbox(world, aim);
            require(launch(TYPE, aim).finished(world, 2f) && Health.MAPPER.get(target).getCurrent() == 90,
                "One long update sweeps through the exact crosshair");

            var apexTarget = hurtbox(world, new Vector2(.5f, .182f));
            require(launch(projectile(2f), aim).finished(world, 2f) && Health.MAPPER.get(apexTarget).getCurrent() == 90,
                "A long update follows the short pre-aim arc instead of sweeping straight to aim");
        } finally { world.dispose(); }

        world = new World(Vector2.Zero, true);
        try {
            // At x=4.75 the continued arc is more than two units below the line of aim.
            var target = hurtbox(world, new Vector2(4.75f, -2.177f));
            require(launch(TYPE, aim).finished(world, 6f) && Health.MAPPER.get(target).getCurrent() == 90,
                "A long update hits a small hurtbox below the old straight extension");
        } finally { world.dispose(); }
    }

    private static Projectile projectile(float arcHeight) {
        return new Projectile("test", 12, 5, new CollisionBounds(8.5f, 10), arcHeight, 0, 0, 0);
    }

    private static ArrowFlight launch(Projectile type, Vector2 aim) {
        return new ArrowFlight(new Entity().add(new Facing(Facing.Direction.RIGHT)), type, new Vector2(), aim, 10);
    }

    private static Entity hurtbox(World world, Vector2 position) {
        var target = new Entity().add(new Health(100));
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
