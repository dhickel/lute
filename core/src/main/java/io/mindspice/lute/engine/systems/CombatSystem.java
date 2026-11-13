package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CombatSystem extends IteratingSystem implements ContactListener, EntityListener {
    private final World world;
    private final List<Hit> pendingHits = new ArrayList<>();
    private final Map<Entity, Combat> activeAttacks = new IdentityHashMap<>();
    private final EntityListener removalListener = new EntityListener() {
        @Override public void entityAdded(Entity entity) { }
        @Override public void entityRemoved(Entity entity) {
            pendingHits.removeIf(hit -> hit.attacker == entity || hit.target == entity);
        }
    };

    public CombatSystem(World world) {
        super(Family.all(Combat.class, Physic.class, Transform.class, Facing.class, Animation2D.class, AnimationPlayer.class).get());
        this.world = world;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        world.setContactListener(this);
        engine.addEntityListener(getFamily(), this);
        engine.addEntityListener(Family.all().get(), removalListener);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        // Removing the system must not leave active attack fixtures behind.
        for (var entity : getEntities()) { stopAttack(entity); }
        world.setContactListener(null);
        engine.removeEntityListener(this);
        engine.removeEntityListener(removalListener);
        pendingHits.clear();
        super.removedFromEngine(engine);
    }

    @Override
    public void update(float deltaTime) {
        // Box2D callbacks only queue data. Health changes and fixture mutations happen unlocked.
        if (world.isLocked()) { return; }
        var iterator = pendingHits.iterator();
        while (iterator.hasNext()) {
            var hit = iterator.next();
            if (!getEngine().getEntities().contains(hit.attacker, true)
                || !getEngine().getEntities().contains(hit.target, true)
                || Combat.MAPPER.get(hit.attacker) != hit.combat) {
                iterator.remove();
                continue;
            }
            var health = Health.MAPPER.get(hit.target);
            var attackerHealth = Health.MAPPER.get(hit.attacker);
            if ((attackerHealth != null && !attackerHealth.isAlive()) || health == null || !health.isAlive()) {
                iterator.remove();
                continue;
            }
            hit.remaining -= deltaTime;
            if (hit.remaining <= 0f) {
                if (hit.damage > 0f) { health.damage(hit.damage); }
                iterator.remove();
            }
        }
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var combat = Combat.MAPPER.get(entity);
        var activeCombat = activeAttacks.get(entity);
        if (activeCombat != null && activeCombat != combat) {
            stopAttack(entity);
        }
        var animation = Animation2D.MAPPER.get(entity);
        var health = Health.MAPPER.get(entity);
        boolean alive = health == null || health.isAlive();
        boolean requested = combat.consumeAttackRequest();
        if (combat.isAttacking()) {
            if (!alive || (animation.getType() == Animation2D.AnimationType.ATTACK
                && !animation.isDirty() && animation.isFinished())) {
                stopAttack(entity);
            }
            return;
        }
        if (!requested || !alive) { return; }

        var body = Physic.MAPPER.get(entity).getBody();
        var anchor = new Vector2(GameConsts.ATTACK_PIVOT_X_PIXELS * GameConsts.UNIT_SCALE,
            GameConsts.ATTACK_PIVOT_Y_PIXELS * GameConsts.UNIT_SCALE);
        var shape = new PolygonShape();
        try {
            shape.set(combat.getCurrentWeapon().vertices(anchor, Facing.MAPPER.get(entity).getDirection()));
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.isSensor = true;
            var sensor = body.createFixture(definition);
            body.setAwake(true);
            combat.beginAttack(sensor, animation.getSpeed());
            activeAttacks.put(entity, combat);
            sensor.setUserData(new AttackSensor(entity, combat));
            animation.setSpeed(combat.getCurrentWeapon().attackSpeed());
        } finally {
            shape.dispose();
        }
    }

    private void stopAttack(Entity entity) {
        // Ashley removes components before delivering family-removal callbacks.
        var combat = activeAttacks.remove(entity);
        if (combat == null || !combat.isAttacking()) { return; }
        var sensor = combat.getSensor();
        sensor.getBody().destroyFixture(sensor);
        var animation = Animation2D.MAPPER.get(entity);
        if (animation != null) { animation.setSpeed(combat.getPreviousAnimationSpeed()); }
        combat.endAttack();
    }

    @Override
    public void beginContact(Contact contact) {
        queueHit(contact.getFixtureA(), contact.getFixtureB());
        queueHit(contact.getFixtureB(), contact.getFixtureA());
    }

    private void queueHit(Fixture sensor, Fixture target) {
        if (sensor.getUserData() instanceof AttackSensor attack
            && !target.isSensor() && target.getBody().getUserData() instanceof Entity entity
            && entity != attack.attacker() && attack.combat().markHit(entity)) {
            var weapon = attack.combat().getCurrentWeapon();
            pendingHits.add(new Hit(attack.attacker(), entity, attack.combat(), weapon.damage(), weapon.hitDelay()));
        }
    }

    @Override public void endContact(Contact contact) { }
    @Override public void preSolve(Contact contact, Manifold oldManifold) { }
    @Override public void postSolve(Contact contact, ContactImpulse impulse) { }
    @Override public void entityAdded(Entity entity) { }

    @Override
    public void entityRemoved(Entity entity) {
        stopAttack(entity);
        pendingHits.removeIf(hit -> hit.attacker == entity);
    }

    private record AttackSensor(Entity attacker, Combat combat) { }

    private static final class Hit {
        final Entity attacker;
        final Entity target;
        final Combat combat;
        final float damage;
        double remaining;

        Hit(Entity attacker, Entity target, Combat combat, float damage, float delay) {
            this.attacker = attacker;
            this.target = target;
            this.combat = combat;
            this.damage = damage;
            this.remaining = delay;
        }
    }
}
