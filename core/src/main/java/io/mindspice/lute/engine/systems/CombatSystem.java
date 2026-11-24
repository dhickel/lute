package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventEmitter;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CombatSystem extends IteratingSystem implements ContactListener, EntityListener, EventEmitter {
    private final World world;
    private final List<ArrowFlight> arrows = new ArrayList<>();
    private final Projectile arrowType;

    List<ArrowFlight> arrows() { return arrows; }

    void drawBow(Entity owner, BowAim aim, CombatWeapon.Weapon weapon) {
        var combat = Combat.MAPPER.get(owner);
        if (combat == null || combat.isCoolingDown(weapon.id(), Combat.AttackSlot.PRIMARY)
            || combat.hasAttackRequest()) { return; }
        // Bow input runs before combat. Resolve contacts from the preceding physics step first.
        applyPendingHits(null);
        var health = Health.MAPPER.get(owner);
        if (health != null && !health.isAlive()) { return; }
        if (combat.isAttacking()) { stopAttack(owner); }
        Animation2D.MAPPER.get(owner).queueAttack(weapon.primary().animation());
        AnimationPlayer.MAPPER.get(owner).getAnimationFsm().changeState(AnimationState.ATTACK);
        aim.start(weapon, Transform.MAPPER.get(owner), Facing.MAPPER.get(owner).getDirection());
    }

    void launch(Entity owner, BowAim aim) {
        applyPendingHits(null);
        var health = Health.MAPPER.get(owner);
        if (health != null && !health.isAlive()) {
            aim.cancel();
            return;
        }
        var start = launchCenter(owner);
        var target = new Vector2(aim.getTargetX(), aim.getTargetY());
        float dx = target.x - start.x, dy = target.y - start.y;
        var facing = Facing.MAPPER.get(owner);
        if (Math.abs(dx) > Math.abs(dy)) {
            facing.setDirection(dx > 0 ? Facing.Direction.RIGHT : Facing.Direction.LEFT);
        } else if (dy != 0) { // Vertical wins ties, as in FacingSystem. At center, keep facing.
            facing.setDirection(dy > 0 ? Facing.Direction.UP : Facing.Direction.DOWN);
        }
        var attack = aim.getWeapon().primary();
        var animation = Animation2D.MAPPER.get(owner);
        int drawFrame = animation.isAttackReady() ? animation.getFrameIndex() : 0;
        float damageScale = Math.min(drawFrame + 1, 4) / 4f;
        arrows.add(new ArrowFlight(owner, arrowType, start, target, attack.damage(), damageScale));
        var combat = Combat.MAPPER.get(owner);
        combat.beginAttack(Combat.AttackSlot.PRIMARY, attack, facing.getDirection());
        activeAttacks.put(owner, combat);
        animation.releaseBow();
        aim.cancel();
    }

    private static Vector2 launchCenter(Entity owner) {
        var transform = Transform.MAPPER.get(owner);
        return transform.getPosition().cpy().mulAdd(transform.getSize(), .5f);
    }

    private final List<Hit> pendingHits = new ArrayList<>();
    private final Map<Entity, Combat> activeAttacks = new IdentityHashMap<>();

    public CombatSystem(World world, Projectile arrowType) {
        super(Family.all(Combat.class, Loadout.class, Animation2D.class, Physic.class, Transform.class, Facing.class).get());
        this.world = world;
        this.arrowType = arrowType;
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
        arrows.clear();
        engine.removeEntityListener(this);
        pendingHits.clear();
        var victims = engine.getEntitiesFor(DamageFeedback.FAMILY);
        for (int i = victims.size() - 1; i >= 0; i--) { victims.get(i).remove(DamageFeedback.class); }
        super.removedFromEngine(engine);
    }

    @Override
    public void update(float deltaTime) {
        // Box2D callbacks only queue data. Health changes and fixture mutations happen unlocked.
        if (world.isLocked()) { return; }
        var victims = getEngine().getEntitiesFor(DamageFeedback.FAMILY);
        for (int i = victims.size() - 1; i >= 0; i--) {
            var victim = victims.get(i);
            var feedback = DamageFeedback.MAPPER.get(victim);
            feedback.advance(deltaTime);
            if (feedback.numbers.isEmpty()) { victim.remove(DamageFeedback.class); }
        }
        applyPendingHits(null);
        var awareness = getEngine().getSystem(AwarenessSystem.class);
        arrows.removeIf(arrow -> {
            if (!arrow.finished(world, deltaTime)) { return false; }
            var victim = arrow.damagedVictim;
            if (victim != null) {
                if (!Health.MAPPER.get(victim).isAlive()) { creditKill(arrow.owner, victim); }
                else if (awareness != null) { awareness.arrowDamaged(victim, arrow.owner); }
            }
            return true;
        });
        super.update(deltaTime);
    }

    /** Drain unlocked, in contact order. Only the arena's fixed-tick drain can hit a still-registering enemy. */
    List<Entity> applyPendingHits(TiledSystem arena) {
        if (pendingHits.isEmpty()) { return List.of(); }
        var killed = new ArrayList<Entity>();
        var entities = getEngine().getEntities();
        for (var hit : pendingHits) {
            if (!entities.contains(hit.attacker(), true)) { continue; }
            if (!entities.contains(hit.target(), true) && (arena == null || !arena.ownsArrivingEnemy(hit.target()))) {
                continue;
            }
            var combat = Combat.MAPPER.get(hit.attacker());
            var health = Health.MAPPER.get(hit.target());

            var attackerHealth = Health.MAPPER.get(hit.attacker());
            if ((attackerHealth == null || attackerHealth.isAlive())
                && combat != null && combat.isAttacking() && combat.getAttackId() == hit.attackId()
                && health != null && health.isAlive() && combat.markHit(hit.target())) {
                DamageFeedback.land(hit.target(), health, combat.getActiveAttack().damage(), 1, combat.getAttackFacing());
                if (!health.isAlive()) {
                    killed.add(hit.target());
                    creditKill(hit.attacker(), hit.target());
                }
            }
        }
        pendingHits.clear();
        return killed;
    }

    private void creditKill(Entity attacker, Entity victim) {
        var source = Actor.MAPPER.get(attacker);
        var target = Actor.MAPPER.get(victim);
        if (source != null && source.role() == Actor.Role.PLAYER && target != null && target.role() == Actor.Role.ENEMY) {
            eventEmitter.accept(new Event.Player.KilledEnemy(attacker, victim));
        }
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var combat = Combat.MAPPER.get(entity);
        var health = Health.MAPPER.get(entity);
        boolean alive = health == null || health.isAlive();
        var target = combat.requestedTarget();
        var requested = combat.consumeAttackRequest();
        // Another hit in this update may have killed the AI's target since the awareness pass.
        if (target != null) {
            var targetHealth = Health.MAPPER.get(target);
            if (targetHealth == null || !targetHealth.isAlive() || !getEngine().getEntities().contains(target, true)) {
                requested = null;
            }
        }
        var animation = Animation2D.MAPPER.get(entity);
        var aim = BowAim.MAPPER.get(entity);
        var facing = Facing.MAPPER.get(entity);
        var move = Move.MAPPER.get(entity);
        boolean playerSteering = Controller.MAPPER.has(entity) && move != null && !move.getDirection().isZero();
        if (aim != null && ActorAi.MAPPER.has(entity)) {
            var weapon = Loadout.MAPPER.get(entity).getWeapon();
            if (alive && requested == Combat.AttackSlot.PRIMARY && target != null
                && weapon.primary().collisionType() == CollisionType.PROJECTILE) {
                if (!aim.isAiming() || aim.getWeapon() != weapon) {
                    aim.cancel();
                    drawBow(entity, aim, weapon);
                } else if (animation.hasReachedFrame(BowAim.FULL_DRAW_FRAME)) {
                    var body = Physic.MAPPER.get(target).getBody();
                    var center = body.getPosition().cpy().mulAdd(Transform.MAPPER.get(target).getSize(), .5f);
                    aim.aimAt(ArrowFlight.intercept(arrowType, launchCenter(entity), center, body.getLinearVelocity()));
                    launch(entity, aim);
                }
                return;
            }
            // A draw requires a renewed request from the live-sight graph every tick.
            aim.cancel();
        }
        if (requested != null && alive && (aim == null || !aim.isAiming())) {
            var weapon = Loadout.MAPPER.get(entity).getWeapon();
            if (!combat.isCoolingDown(weapon.id(), requested)) {
                var next = requested == Combat.AttackSlot.PRIMARY ? weapon.primary() : weapon.secondary();
                if (playerSteering && next.collisionType() == CollisionType.MELEE && combat.isAttacking()
                    && combat.getActiveAttack().collisionType() == CollisionType.PROJECTILE) {
                    // Retarget the new jab, not the release pose that animation already sampled.
                    FacingSystem.faceFromMovement(facing, move.getDirection());
                }
                stopAttack(entity);
                animation.queueAttack(next.animation());
                combat.beginAttack(requested, next, facing.getDirection());
                activeAttacks.put(entity, combat);
                return;
            }
        }
        if (!combat.isAttacking()) { return; }
        var attack = combat.getActiveAttack();
        if (!alive || (animation.isAttackReady() && animation.isFinished())) {
            stopAttack(entity);
            return;
        }
        if (attack.collisionType() != CollisionType.MELEE) { return; }
        if (playerSteering && facing.getDirection() != combat.getAttackFacing()) {
            // Previous contacts already resolved in their original direction. Keep this attack's hit set.
            removeSensor(combat);
            combat.setAttackFacing(facing.getDirection());
        }
        if (combat.getSensor() != null || !animation.hasReachedFrame(attack.hitFrame())) { return; }

        var body = Physic.MAPPER.get(entity).getBody();
        var size = Transform.MAPPER.get(entity).getSize();
        var anchor = new Vector2(size.x * 0.5f, size.y * 0.5f);
        var shape = new PolygonShape();
        try {
            shape.set(attack.vertices(anchor, combat.getAttackFacing()));
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.isSensor = true;
            var sensor = body.createFixture(definition);
            body.setAwake(true);
            combat.setSensor(sensor);
            sensor.setUserData(new AttackSensor(entity, combat.getAttackId()));
        } finally {
            shape.dispose();
        }
    }

    private void stopAttack(Entity entity) {
        // Ashley removes components before delivering family-removal callbacks.
        var combat = activeAttacks.remove(entity);
        if (combat == null || !combat.isAttacking()) { return; }
        removeSensor(combat);
        combat.endAttack();
    }

    private void removeSensor(Combat combat) {
        var sensor = combat.getSensor();
        if (sensor != null) {
            sensor.getBody().destroyFixture(sensor);
            combat.setSensor(null);
        }
    }

    @Override
    public void beginContact(Contact contact) {
        var sensors = getEngine() == null ? null : getEngine().getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.contact(contact.getFixtureA(), contact.getFixtureB(), true); }
        queueHit(contact.getFixtureA(), contact.getFixtureB());
        queueHit(contact.getFixtureB(), contact.getFixtureA());
    }

    private void queueHit(Fixture sensor, Fixture target) {
        if (!(sensor.getUserData() instanceof AttackSensor attack)
            || !(target.getBody().getUserData() instanceof Entity entity) || entity == attack.attacker()) { return; }
        boolean hurtbox = target.getUserData() == Hurtbox.INSTANCE;
        if (!hurtbox) {
            if (target.isSensor()) { return; }
            for (var fixture : target.getBody().getFixtureList()) {
                if (fixture.getUserData() == Hurtbox.INSTANCE) { return; }
            }
        }
        pendingHits.add(new Hit(attack.attacker(), entity, attack.attackId()));
    }

    @Override
    public void endContact(Contact contact) {
        var sensors = getEngine() == null ? null : getEngine().getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.contact(contact.getFixtureA(), contact.getFixtureB(), false); }
    }
    @Override public void preSolve(Contact contact, Manifold oldManifold) { }
    @Override public void postSolve(Contact contact, ContactImpulse impulse) { }
    @Override public void entityAdded(Entity entity) { }

    @Override
    public void entityRemoved(Entity entity) {
        cancelCombat(entity);
    }

    void cancelCombat(Entity entity) {
        arrows.removeIf(arrow -> arrow.owner == entity);
        pendingHits.removeIf(hit -> hit.attacker() == entity || hit.target() == entity);
        var aim = BowAim.MAPPER.get(entity);
        if (aim != null) { aim.cancel(); }
        var combat = Combat.MAPPER.get(entity);
        if (combat != null) { combat.consumeAttackRequest(); }
        stopAttack(entity);
    }

    private record AttackSensor(Entity attacker, long attackId) { }
    private record Hit(Entity attacker, Entity target, long attackId) { }
}
