package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.components.*;

/** Runs after damage resolves and before the next physics step. Animation owns pose time. */
public final class DeathSystem extends IteratingSystem {
    private static final double POSE_SECONDS = 5d;
    private final World world;

    public DeathSystem(World world) {
        super(Family.all(Actor.class, Health.class, Animation2D.class, AnimationPlayer.class,
            Physic.class, Transform.class, Facing.class, Move.class).get());
        this.world = world;
    }

    @Override
    public void update(float deltaTime) {
        if (!world.isLocked()) { super.update(deltaTime); }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (Health.MAPPER.get(entity).isAlive()) { return; }
        var animation = Animation2D.MAPPER.get(entity);
        var tiled = getEngine().getSystem(TiledSystem.class);
        if (animation.getType() != Animation2D.AnimationType.DEATH) {
            var combat = getEngine().getSystem(CombatSystem.class);
            if (combat != null) { combat.cancelCombat(entity); }
            var ai = ActorAi.MAPPER.get(entity);
            if (ai != null) { ai.forgetTarget(); }
            if (Actor.MAPPER.get(entity).role() == Actor.Role.PLAYER) {
                var hoard = getEngine().getSystem(HoardSystem.class);
                if (hoard != null) { hoard.playerDied(entity); }
                var sensors = getEngine().getSystem(MapSensorSystem.class);
                if (sensors != null) { sensors.forgetPlayer(entity); }
                if (tiled != null) { tiled.detachController(entity); }
            } else if (entity.getComponent(ArenaGuard.class) != null) {
                getEngine().getSystem(HoardSystem.class).dredgeDied(entity);
            }
            var position = Physic.MAPPER.get(entity).getBody().getPosition();
            pose(entity, position.x, position.y, 0d);
        } else if (animation.getDeathSeconds() >= animation.getPlaybackDuration() + POSE_SECONDS) {
            if (tiled == null || !tiled.retireCorpse(entity)) { getEngine().removeEntity(entity); }
        }
    }

    private void pose(Entity entity, float x, float y, double elapsed) {
        Move.MAPPER.get(entity).getDirection().setZero();
        var physics = Physic.MAPPER.get(entity);
        var body = physics.getBody();
        body.setLinearVelocity(0f, 0f);
        body.setAngularVelocity(0f);
        body.setActive(false);
        body.setTransform(x, y, body.getAngle());
        physics.getPrevPos().set(x, y);
        Transform.MAPPER.get(entity).getPosition().set(x, y);
        AnimationPlayer.MAPPER.get(entity).getAnimationFsm().changeState(AnimationState.DEATH);
        Animation2D.MAPPER.get(entity).setDeathSeconds(elapsed);
        // Publish the death frame immediately, even though the animation pass has already run.
        getEngine().getSystem(AnimationSystem.class).processEntity(entity, 0f);
    }

    static Corpse snapshot(Entity entity, double now) {
        var animation = Animation2D.MAPPER.get(entity);
        if (animation == null || animation.getType() != Animation2D.AnimationType.DEATH) { return null; }
        var position = Transform.MAPPER.get(entity).getPosition();
        var corpse = new Corpse(position.x, position.y, Facing.MAPPER.get(entity).getDirection(),
            now - animation.getDeathSeconds(), animation.getPlaybackDuration() + POSE_SECONDS);
        return corpse.expired(now) ? null : corpse;
    }

    void restore(Entity entity, Corpse corpse, double now) {
        var health = Health.MAPPER.get(entity);
        health.damage(health.getCurrent());
        entity.remove(Controller.class); // Staged entity; this listener was never registered.
        Facing.MAPPER.get(entity).setDirection(corpse.facing());
        pose(entity, corpse.x(), corpse.y(), now - corpse.startedAt());
    }

    // Values only: never keep an Ashley entity, native body, or asset alive off-map.
    record Corpse(float x, float y, Facing.Direction facing, double startedAt, double duration) {
        boolean expired(double now) { return now - startedAt >= duration; }
    }
}
