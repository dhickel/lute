package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.ai.decisiongraph.Action;
import io.mindspice.lute.engine.ai.decisiongraph.FocusState;
import io.mindspice.lute.engine.ai.decisiongraph.Graphs;
import io.mindspice.lute.engine.ai.decisiongraph.RootNode;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AwarenessSystem extends IteratingSystem {
    static final Object SENSOR_TAG = new Object();
    private static final Family ACTORS = Family.all(Actor.class, Physic.class).get();
    private final World world;
    private final Map<String, RootNode> graphs = Graphs.build(this::seesPlayer);
    private final Action stop = Action.stop();

    public AwarenessSystem(World world) {
        super(ACTORS);
        this.world = world;
    }

    @Override
    protected void processEntity(Entity actor, float deltaTime) {
        var ai = ActorAi.MAPPER.get(actor);
        if (ai == null) { return; }
        var state = new FocusState(deltaTime);
        if (!Health.MAPPER.get(actor).isAlive() || Combat.MAPPER.get(actor).isAttacking()) {
            stop.perform(actor, state);
            return;
        }
        graphs.get(ai.graphKey()).travel(actor, state);
    }

    /** Snapshot of matching actors whose physical feet centers lie in the owner's circle, including its edge. */
    public List<Entity> inRange(Entity owner, Actor.Role role) {
        if (!getEntities().contains(owner, true)) { return List.of(); }
        var body = Physic.MAPPER.get(owner).getBody();
        if (body.getUserData() != owner || !body.isActive()) { return List.of(); }
        for (var fixture : body.getFixtureList()) {
            if (fixture.getUserData() != SENSOR_TAG) { continue; }
            var shape = (CircleShape) fixture.getShape();
            var center = body.getWorldPoint(shape.getPosition()).cpy();
            float radius = shape.getRadius();
            double radiusSquared = (double) radius * radius;
            var result = new ArrayList<Entity>();
            world.QueryAABB(candidate -> {
                // The feet center is inside its solid fixture, so its proxy cannot be missed by this AABB.
                if (!candidate.isSensor() && candidate.getBody().getUserData() instanceof Entity actor
                    && actor != owner && getEntities().contains(actor, true)
                    && Actor.MAPPER.get(actor).role() == role && !result.contains(actor)) {
                    var point = feet(actor);
                    double dx = (double) point.x - center.x;
                    double dy = (double) point.y - center.y;
                    if (dx * dx + dy * dy <= radiusSquared) { result.add(actor); }
                }
                return true;
            }, center.x - radius, center.y - radius, center.x + radius, center.y + radius);
            return result;
        }
        return List.of();
    }

    private static Vector2 feet(Entity entity) {
        var actor = Actor.MAPPER.get(entity);
        return Physic.MAPPER.get(entity).getBody().getWorldPoint(new Vector2(actor.feetX(), actor.feetY()));
    }

    private static boolean solidWorld(Fixture fixture) {
        // Both tile-layer bodies and entity-backed scenery count; character fixtures and all sensors don't.
        return !fixture.isSensor() && !(fixture.getBody().getUserData() instanceof Entity entity
            && Actor.MAPPER.has(entity));
    }

    private boolean clearSight(Vector2 from, Vector2 to) {
        // Box2D ray casts ignore shapes containing the ray origin.
        boolean[] blocked = {false};
        world.QueryAABB(fixture -> {
            blocked[0] = solidWorld(fixture) && fixture.testPoint(from);
            return !blocked[0];
        }, from.x, from.y, from.x, from.y);
        if (blocked[0] || from.equals(to)) { return !blocked[0]; }
        world.rayCast((fixture, point, normal, fraction) -> {
            if (!solidWorld(fixture)) { return -1f; }
            blocked[0] = true;
            return 0f;
        }, from, to);
        return !blocked[0];
    }

    private boolean seesPlayer(Entity owner, FocusState state) {
        for (var player : inRange(owner, Actor.Role.PLAYER)) {
            if (!Health.MAPPER.get(player).isAlive()) { continue; }
            var from = feet(owner);
            var to = feet(player);
            if (!clearSight(from, to)) { continue; }
            state.setTargetOffset(to.sub(from));
            return true;
        }
        return false;
    }
}
