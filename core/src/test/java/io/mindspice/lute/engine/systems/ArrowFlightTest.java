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
    private static final Projectile TYPE = new Projectile("test", 8, 2, new CollisionBounds(8, 8), 1.5f, 0, 0, 0);

    private ArrowFlightTest() { }

    public static void run() {
        var aim = new Vector2(8, 0);
        var world = new World(Vector2.Zero, true);
        try {
            var arrow = launch(aim);
            require(!arrow.finished(world, .5f) && arrow.tip.y > 0, "Arrow rises before the crosshair");
            require(!arrow.finished(world, .5f) && arrow.tip.equals(aim), "Arrow passes through the exact crosshair");
            require(!arrow.finished(world, .5f) && arrow.tip.x == 12 && arrow.tip.y < 0,
                "Arrow continues curving past the crosshair");
            float descent = arrow.direction.y;
            require(arrow.finished(world, 5f) && arrow.tip.x == 16 && arrow.tip.y == -TYPE.arcHeight()
                && arrow.direction.y < descent, "Descent continues until expiry and clips a long update");

            var centered = launch(Vector2.Zero);
            require(!centered.finished(world, 1f) && centered.tip.x == 8 && centered.tip.y > 0,
                "Centered aim uses facing and still arcs");

            var target = hurtbox(world, aim);
            require(launch(aim).finished(world, 2f) && Health.MAPPER.get(target).getCurrent() == 90,
                "One long update sweeps through the exact crosshair");
        } finally { world.dispose(); }

        world = new World(Vector2.Zero, true);
        try {
            var target = hurtbox(world, new Vector2(12, -.5625f));
            require(launch(aim).finished(world, 3f) && Health.MAPPER.get(target).getCurrent() == 90,
                "A hurtbox below the old straight extension is hit on the continued arc");
        } finally { world.dispose(); }
    }

    private static ArrowFlight launch(Vector2 aim) {
        return new ArrowFlight(new Entity().add(new Facing(Facing.Direction.RIGHT)), TYPE, new Vector2(), aim, 10);
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
