package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.behaviorgraph.Action;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import io.mindspice.lute.engine.behaviorgraph.FocusState;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.ArenaGuard;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.FullCircleVision;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;

import java.util.ArrayList;
import java.util.List;

public final class AwarenessSystem extends IteratingSystem {
    static final Object SENSOR_TAG = new Object();
    private static final Family ACTORS = Family.all(Actor.class, Physic.class).get();
    private final World world;
    private final Action stop = Action.stop();
    private final Action followTarget = Action.followTarget();

    public AwarenessSystem(World world) {
        super(ACTORS);
        this.world = world;
    }

    @Override
    protected void processEntity(Entity actor, float deltaTime) {
        var ai = ActorAi.MAPPER.get(actor);
        if (ai == null) { return; }
        ai.tickRecovery(deltaTime);
        var state = new FocusState(deltaTime);
        if (!Health.MAPPER.get(actor).isAlive()) {
            ai.forgetTarget();
            stop.perform(actor, state);
            return;
        }
        var guard = actor.getComponent(ArenaGuard.class);
        if (guard != null) {
            updateGuard(actor, ai, guard, state);
            return;
        }
        var graphs = BehaviorGraphs.forKey(ai.behaviorGraphKey());
        var shooter = ai.retaliationTarget();
        boolean detectsPlayer = Actor.MAPPER.get(actor).role() == Actor.Role.ENEMY
            && graphs.containsKey(ActorAi.Mode.ENGAGE);
        if (detectsPlayer || ai.mode() != ActorAi.Mode.WANDER) {
            var from = feet(actor);
            // Prefer the visible shooter, but keep normal player detection on every tick.
            var visible = shooter == null ? null : retaliationFeet(actor, shooter, from);
            var target = visible == null ? null : shooter;
            if (target == null && detectsPlayer) {
                target = visiblePlayer(actor, from);
                if (target != null) { visible = feet(target); }
            }
            if (shooter != null && (target != null && target != shooter || !livingActor(shooter))) {
                ai.retaliateAgainst(null);
            }
            state.setVisibleTarget(target);
            state.setTargetOffset(ai.updateTarget(from, visible, deltaTime));
        }
        if (BehaviorGraphs.attackLocksMovement(actor)) {
            stop.perform(actor, state);
            return;
        }
        // Passive wanderers still use ordinary pursuit when retaliating.
        var graph = graphs.getOrDefault(ai.mode(), BehaviorGraphs.pursuit(ai.mode()));
        graph.travel(actor, state);
    }

    private void updateGuard(Entity actor, ActorAi ai, ArenaGuard guard, FocusState state) {
        var from = feet(actor);
        var player = visiblePlayer(actor, from);
        if (player != null) {
            state.setVisibleTarget(player);
            state.setTargetOffset(ai.updateTarget(from, feet(player), state.deltaTime()));
            if (BehaviorGraphs.attackLocksMovement(actor)) { stop.perform(actor, state); }
            else { BehaviorGraphs.pursuit(ActorAi.Mode.ENGAGE).travel(actor, state); }
            return;
        }

        ai.forgetTarget();
        Combat.MAPPER.get(actor).consumeAttackRequest();
        var offset = guard.home().cpy().sub(Physic.MAPPER.get(actor).getBody().getPosition());
        state.setTargetOffset(offset);
        if (offset.len2() < .01f || BehaviorGraphs.attackLocksMovement(actor)) { stop.perform(actor, state); }
        else { followTarget.perform(actor, state); }
    }

    void arrowDamaged(Entity victim, Entity shooter) {
        var ai = ActorAi.MAPPER.get(victim);
        if (ai == null || !livingActor(victim) || Actor.MAPPER.get(victim).role() == Actor.Role.PLAYER) { return; }
        if (victim.getComponent(ArenaGuard.class) != null) { return; }
        var from = feet(victim);
        var to = retaliationFeet(victim, shooter, from);
        if (to != null) {
            var combat = Combat.MAPPER.get(victim);
            if (combat.requestedTarget() != shooter) { combat.consumeAttackRequest(); }
            ai.retaliateAgainst(shooter);
            ai.updateTarget(from, to, 0f);
        }
    }

    private Vector2 retaliationFeet(Entity victim, Entity shooter, Vector2 from) {
        if (shooter == victim || !livingActor(shooter)) { return null; }
        var to = feet(shooter);
        if (Actor.MAPPER.get(victim).role() == Actor.Role.ENEMY && !withinFieldOfView(victim, from, to)) { return null; }
        for (var fixture : Physic.MAPPER.get(victim).getBody().getFixtureList()) {
            if (fixture.getUserData() != SENSOR_TAG) { continue; }
            double radius = 3d * ((CircleShape) fixture.getShape()).getRadius();
            double dx = (double) to.x - from.x;
            double dy = (double) to.y - from.y;
            return dx * dx + dy * dy <= radius * radius && clearSight(from, to) ? to : null;
        }
        return null;
    }

    private boolean livingActor(Entity entity) {
        if (entity == null || !getEntities().contains(entity, true)) { return false; }
        var health = Health.MAPPER.get(entity);
        var body = Physic.MAPPER.get(entity).getBody();
        return health != null && health.isAlive() && body.getUserData() == entity && body.isActive();
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

    private Entity visiblePlayer(Entity owner, Vector2 from) {
        for (var player : inRange(owner, Actor.Role.PLAYER)) {
            if (!livingActor(player)) { continue; }
            var to = feet(player);
            if (withinFieldOfView(owner, from, to) && clearSight(from, to)) { return player; }
        }
        return null;
    }

    private static boolean withinFieldOfView(Entity owner, Vector2 from, Vector2 to) {
        if (FullCircleVision.MAPPER.has(owner)) { return true; }
        // Cardinal facing defines an inclusive half-plane through the physical feet center.
        return switch (Facing.MAPPER.get(owner).getDirection()) {
            case UP -> to.y >= from.y;
            case DOWN -> to.y <= from.y;
            case LEFT -> to.x <= from.x;
            case RIGHT -> to.x >= from.x;
        };
    }
}
