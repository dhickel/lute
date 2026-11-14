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

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CombatSystem extends IteratingSystem implements ContactListener, EntityListener {
    private final World world;
    private final List<Hit> pendingHits = new ArrayList<>();
    private final Map<Entity, Combat> activeAttacks = new IdentityHashMap<>();

    public CombatSystem(World world) {
        super(Family.all(Combat.class, Physic.class, Transform.class, Facing.class, Animation2D.class, AnimationPlayer.class).get());
        this.world = world;
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        world.setContactListener(this);
        engine.addEntityListener(getFamily(), this);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        // Removing the system must not leave active attack fixtures behind.
        for (var entity : getEntities()) { stopAttack(entity); }
        world.setContactListener(null);
        engine.removeEntityListener(this);
        pendingHits.clear();
        super.removedFromEngine(engine);
    }

    @Override
    public void update(float deltaTime) {
        // Box2D callbacks only queue data. Health changes and fixture mutations happen unlocked.
        if (world.isLocked()) { return; }
        for (var hit : pendingHits) {
            if (!getEngine().getEntities().contains(hit.attacker(), true)
                || !getEngine().getEntities().contains(hit.target(), true)) { continue; }
            var combat = Combat.MAPPER.get(hit.attacker());
            var health = Health.MAPPER.get(hit.target());
            var attackerHealth = Health.MAPPER.get(hit.attacker());
            if ((attackerHealth == null || attackerHealth.isAlive())
                && combat != null && combat.isAttacking() && combat.getAttackId() == hit.attackId()
                && health != null && health.isAlive() && combat.markHit(hit.target())) {
                health.damage(combat.getCurrentWeapon().damage());
            }
        }
        pendingHits.clear();
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var combat = Combat.MAPPER.get(entity);
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
        var size = Transform.MAPPER.get(entity).getSize();
        var anchor = new Vector2(size.x * 0.5f, size.y * 0.5f);
        var shape = new PolygonShape();
        try {
            shape.set(combat.getCurrentWeapon().vertices(anchor, Facing.MAPPER.get(entity).getDirection()));
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.isSensor = true;
            var sensor = body.createFixture(definition);
            body.setAwake(true);
            combat.beginAttack(sensor);
            activeAttacks.put(entity, combat);
            sensor.setUserData(new AttackSensor(entity, combat.getAttackId()));
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
            && entity != attack.attacker()) {
            pendingHits.add(new Hit(attack.attacker(), entity, attack.attackId()));
        }
    }

    @Override public void endContact(Contact contact) { }
    @Override public void preSolve(Contact contact, Manifold oldManifold) { }
    @Override public void postSolve(Contact contact, ContactImpulse impulse) { }
    @Override public void entityAdded(Entity entity) { }

    @Override
    public void entityRemoved(Entity entity) {
        stopAttack(entity);
    }

    private record AttackSensor(Entity attacker, long attackId) { }
    private record Hit(Entity attacker, Entity target, long attackId) { }
}
