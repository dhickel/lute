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
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class CombatSystem extends IteratingSystem implements ContactListener, EntityListener, EventEmitter {
    private static final float LUNGE_WINDUP_RATE = 2f;
    private static final float LUNGE_FINISH_SCALE = 1.5f;
    private final World world;
    private final List<ArrowFlight> arrows = new ArrayList<>();
    private final Projectile arrowType;

    List<ArrowFlight> arrows() { return arrows; }

    void drawBow(Entity owner, BowAim aim, CombatWeapon.Weapon weapon) {
        var combat = Combat.MAPPER.get(owner);
        if (combat == null || combat.isCoolingDown(weapon.id(), Combat.AttackSlot.PRIMARY)
            || combat.hasAttackRequest()) { return; }
        var controller = Controller.MAPPER.get(owner);
        if (combat.isAttacking() && controller != null && !controller.getHeldDirection().isZero()) {
            combat.requestAttack(Combat.AttackSlot.PRIMARY);
            return;
        }
        // Bow input runs before combat. Resolve contacts from the preceding physics step first.
        applyPendingHits(null);
        var health = Health.MAPPER.get(owner);
        if (health != null && !health.isAlive()) { return; }
        if (combat.isAttacking()) { stopAttack(owner); }
        startDraw(owner, aim, weapon);
    }

    private void startDraw(Entity owner, BowAim aim, CombatWeapon.Weapon weapon) {
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
        mountedCombat.put(owner, combat);
        animation.releaseBow();
        aim.cancel();
    }

    private static Vector2 launchCenter(Entity owner) {
        var transform = Transform.MAPPER.get(owner);
        return transform.getPosition().cpy().mulAdd(transform.getSize(), .5f);
    }

    private final List<Hit> pendingHits = new ArrayList<>();
    private final Map<Body, LungePush> lungePushes = new IdentityHashMap<>();
    // Family removal may arrive after Ashley has detached the component, even mid-handoff.
    private final Map<Entity, Combat> mountedCombat = new IdentityHashMap<>();

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
        for (var entity : getEntities()) { cancelCombat(entity); }
        world.setContactListener(null);
        arrows.clear();
        engine.removeEntityListener(this);
        pendingHits.clear();
        var marked = engine.getEntitiesFor(HealthFeedback.FAMILY);
        for (int i = marked.size() - 1; i >= 0; i--) { marked.get(i).remove(HealthFeedback.class); }
        super.removedFromEngine(engine);
    }

    @Override
    public void update(float deltaTime) {
        // Contact damage is queued. Health changes and fixture mutations happen unlocked.
        if (world.isLocked()) { return; }
        var marked = getEngine().getEntitiesFor(HealthFeedback.FAMILY);
        for (int i = marked.size() - 1; i >= 0; i--) {
            var entity = marked.get(i);
            var feedback = HealthFeedback.MAPPER.get(entity);
            feedback.advance(deltaTime);
            if (feedback.numbers.isEmpty()) { entity.remove(HealthFeedback.class); }
        }
        applyPendingHits(null);
        var awareness = getEngine().getSystem(AwarenessSystem.class);
        arrows.removeIf(arrow -> {
            if (!arrow.finished(world, deltaTime)) { return false; }
            var victim = arrow.damagedVictim;
            if (victim != null) {
                if (!Health.MAPPER.get(victim).isAlive()) { creditKill(arrow.owner, victim); }
                else if (awareness != null) { awareness.retaliateAfterArrow(victim, arrow.owner); }
            }
            return true;
        });
        super.update(deltaTime);
    }

    /** Resolve this unlocked arena tick before pickups and clock advancement. */
    void resolveArenaStepHits(TiledSystem arena) {
        if (world.isLocked()) { return; }
        var killed = applyPendingHits(arena);
        if (killed.isEmpty()) { return; }
        var death = getEngine().getSystem(DeathSystem.class);
        for (var victim : killed) { death.beginContactDeath(victim); }
    }

    // Drain in contact order. Only the arena's fixed-tick drain can hit a still-registering enemy.
    private List<Entity> applyPendingHits(TiledSystem arena) {
        if (pendingHits.isEmpty()) { return List.of(); }
        var killed = new ArrayList<Entity>();
        var entities = getEngine().getEntities();
        var awareness = getEngine().getSystem(AwarenessSystem.class);
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
                && health != null && health.isAlive() && clearOfDredgeCover(hit) && combat.markHit(hit.target())) {
                float damageScale = hit.hammerLunge() && combat.getActiveSlot() == Combat.AttackSlot.SECONDARY ? 1.25f : 1f;
                int damage = HealthFeedback.land(hit.target(), health, combat.getActiveAttack().damage(), damageScale,
                    combat.getAttackFacing());
                if (!health.isAlive()) {
                    if (arena != null) { killed.add(hit.target()); }
                    creditKill(hit.attacker(), hit.target());
                } else if (damage > 0 && awareness != null && Actor.MAPPER.has(hit.attacker())
                    && Actor.MAPPER.get(hit.attacker()).role() == Actor.Role.PLAYER && Actor.MAPPER.has(hit.target())
                    && Actor.MAPPER.get(hit.target()).role() == Actor.Role.ENEMY) {
                    awareness.retaliateAfterDamage(hit.target(), hit.attacker());
                }
            }
        }
        pendingHits.clear();
        return killed;
    }

    private boolean clearOfDredgeCover(Hit hit) {
        return !hit.hammerLunge() || !Actor.MAPPER.has(hit.target()) || clearOfCover(hit.attacker(), hit.target());
    }

    private boolean clearOfCover(Entity attacker, Entity target) {
        return AwarenessSystem.clearSight(world, AwarenessSystem.feet(attacker), AwarenessSystem.feet(target));
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
        var animation = Animation2D.MAPPER.get(entity);
        var move = Move.MAPPER.get(entity);
        boolean movingPlayer = alive && Controller.MAPPER.has(entity) && move != null
            && !move.isRooted() && !move.getDirection().isZero();
        if (isHammerLunge(entity, combat)) {
            if (!alive) { stopAttack(entity); }
            return;
        }
        if (alive && combat.isAttacking() && (movingPlayer || combat.hasDeferredRequest())) {
            combat.deferRequest();
            // Input and animation precede combat; movement and fixed physics follow it.
            // Keep the first ready follow-up until the committed hit has had a physics step.
            boolean melee = combat.getActiveAttack().collisionType() == CollisionType.MELEE;
            if (melee && !combat.sensorHasStepped(physicsStep())) {
                activateMeleeSensor(entity, combat, animation);
            } else if (!combat.activeAttackCoolingDown() || (animation.isAttackReady() && animation.isFinished())) {
                stopAttack(entity);
            }
            return;
        }
        var target = combat.requestedTarget();
        var aim = BowAim.MAPPER.get(entity);
        var requested = combat.consumeAttackRequest();
        // Another hit in this update may have killed the AI's target since the awareness pass.
        if (target != null) {
            var targetHealth = Health.MAPPER.get(target);
            if (targetHealth == null || !targetHealth.isAlive() || !getEngine().getEntities().contains(target, true)) {
                requested = null;
            }
        }
        var ai = ActorAi.MAPPER.get(entity);
        if (aim != null && ai != null) {
            var weapon = Loadout.MAPPER.get(entity).getWeapon();
            if (alive && requested == Combat.AttackSlot.PRIMARY && target != null
                && weapon.primary().collisionType() == CollisionType.PROJECTILE) {
                if (!aim.isAiming() || aim.getWeapon() != weapon) {
                    aim.cancel();
                    drawBow(entity, aim, weapon);
                } else if (!aim.isLocked() && animation.hasReachedFrame(BowAim.FULL_DRAW_FRAME / 2)) {
                    lockEnemyAim(entity, aim, target, animation);
                } else if (aim.isLocked() && aim.advanceLock(deltaTime) && animation.hasReachedFrame(BowAim.FULL_DRAW_FRAME)) {
                    launch(entity, aim);
                    ai.clearOpportunity();
                }
                return;
            }
            // A draw requires a renewed request from the live-sight graph every tick.
            aim.cancel();
        }
        if (requested != null && alive && (aim == null || !aim.isAiming())) {
            var weapon = Loadout.MAPPER.get(entity).getWeapon();
            if (!combat.isCoolingDown(weapon.id(), requested)) {
                var next = attackFor(entity, weapon, requested);
                stopAttack(entity);
                if (aim != null && Controller.MAPPER.has(entity) && requested == Combat.AttackSlot.PRIMARY
                    && next.collisionType() == CollisionType.PROJECTILE) {
                    startDraw(entity, aim, weapon);
                    if (move != null) { move.getDirection().setZero(); }
                    return;
                }
                animation.queueAttack(next.animation());
                if (ai != null && next.collisionType() == CollisionType.MELEE) {
                    ai.clearOpportunity();
                    float hitSeconds = next.hitFrame() * next.animation().frameInterval() / next.animation().speed();
                    animation.holdOpeningPose(Math.max(0f, .28f - hitSeconds));
                }
                combat.beginAttack(requested, next, Facing.MAPPER.get(entity).getDirection());
                mountedCombat.put(entity, combat);
                return;
            }
        }
        if (!combat.isAttacking()) { return; }
        updateActiveAttack(entity, combat, animation, alive, ai);
    }

    static CombatWeapon.Attack attackFor(Entity entity, CombatWeapon.Weapon weapon, Combat.AttackSlot slot) {
        if (slot == Combat.AttackSlot.PRIMARY) { return weapon.primary(); }
        if (!usesHammerLunge(entity, weapon.id(), slot)) { return weapon.secondary(); }
        var primary = weapon.primary();
        return new CombatWeapon.Attack(primary.weaponId(), primary.damage(), primary.hitFrame(), primary.cooldownSeconds() + .2f,
            primary.animation(), primary.hitSoundId(), primary.shape(), primary.bounds(), primary.collisionType());
    }

    private static boolean usesHammerLunge(Entity entity, String weaponId, Combat.AttackSlot slot) {
        if (entity.getComponent(DredgeLunge.class) == null) { return false; }
        var actor = Actor.MAPPER.get(entity);
        if (actor != null && actor.role() == Actor.Role.PLAYER) {
            return slot == Combat.AttackSlot.SECONDARY && ("basic_hammer".equals(weaponId) || "black_hammer".equals(weaponId));
        }
        return "black_hammer".equals(weaponId);
    }

    static boolean isHammerLunge(Entity entity, Combat combat) {
        return combat != null && combat.isAttacking()
            && usesHammerLunge(entity, combat.getActiveAttack().weaponId(), combat.getActiveSlot());
    }

    void beforeLungeStep(Entity entity, DredgeLunge lunge, float interval) {
        var combat = Combat.MAPPER.get(entity);
        if (!isHammerLunge(entity, combat)) { return; }
        if (!Health.MAPPER.get(entity).isAlive()) {
            stopAttack(entity);
            return;
        }
        var animation = Animation2D.MAPPER.get(entity);
        if (lunge.beginStep(combat.getAttackId())) {
            float playbackDelta = combat.getSensor() == null ? interval * LUNGE_WINDUP_RATE : interval;
            getEngine().getSystem(AnimationSystem.class).advancePlayback(entity, playbackDelta);
            updateActiveAttack(entity, combat, animation, true, ActorAi.MAPPER.get(entity));
        } else if (animation.getType() != Animation2D.AnimationType.ATTACK) {
            AnimationPlayer.MAPPER.get(entity).getAnimationFsm().changeState(AnimationState.ATTACK);
        } else {
            // A heavy swing can replace an ordinary attack before the next render tick.
            animation.restartQueuedAttack();
        }
        var body = Physic.MAPPER.get(entity).getBody();
        if (lunge.driveStep(body, combat, animation, interval)) {
            // Swap geometry without ending the swing or clearing its hit targets.
            body.destroyFixture(combat.getSensor());
            createMeleeSensor(entity, combat, LUNGE_FINISH_SCALE);
        }
    }

    // Use persistent contacts, not beginContact: a target takes damage once but is
    // carried for every fixed step it remains in the moving hitbox.
    void beforeLungePushStep() {
        for (var contact : world.getContactList()) {
            if (!contact.isTouching()) { continue; }
            pushAlongLunge(contact.getFixtureA(), contact.getFixtureB());
            pushAlongLunge(contact.getFixtureB(), contact.getFixtureA());
        }
    }

    private void pushAlongLunge(Fixture sensor, Fixture target) {
        if (!(sensor.getUserData() instanceof AttackSensor attack) || !attack.hammerLunge()
            || target.getUserData() != Hurtbox.INSTANCE
            || !(target.getBody().getUserData() instanceof Entity victim)) { return; }
        var actor = Actor.MAPPER.get(victim);
        var health = Health.MAPPER.get(victim);
        var body = target.getBody();
        if (actor == null || actor.role() == Actor.Role.NPC || health == null || !health.isAlive()
            || body.getType() != BodyDef.BodyType.DynamicBody || lungePushes.containsKey(body)
            || isHammerLunge(victim, Combat.MAPPER.get(victim))
            || !clearOfCover(attack.attacker(), victim)) { return; }

        var drive = sensor.getBody().getLinearVelocity();
        var direction = new Vector2(drive).nor();
        var velocity = body.getLinearVelocity();
        float baseline = velocity.dot(direction);
        float carrySpeed = Math.min(220f * GameConsts.UNIT_SCALE, drive.len() * .85f);
        if (carrySpeed <= 0f || baseline >= carrySpeed) { return; }
        body.setLinearVelocity(velocity.mulAdd(direction, carrySpeed - baseline));
        lungePushes.put(body, new LungePush(direction, baseline, carrySpeed - baseline));
    }

    void finishLungePushStep() {
        // The temporary carry isn't a lasting knockback impulse. Keep lateral
        // velocity and any independent forward motion from the contact solver.
        lungePushes.forEach((body, push) -> {
            var velocity = body.getLinearVelocity();
            float added = velocity.dot(push.direction()) - push.baseline();
            if (added > 0f) { body.setLinearVelocity(velocity.mulAdd(push.direction(), -Math.min(added, push.added()))); }
        });
        lungePushes.clear();
    }

    private void updateActiveAttack(Entity entity, Combat combat, Animation2D animation, boolean alive, ActorAi ai) {
        if (!alive || (animation.isAttackReady() && animation.isFinished())) {
            if (alive && ai != null && combat.getActiveAttack().collisionType() == CollisionType.MELEE) {
                // The authored follow-through already counts. Never extend the damaging fixture into recovery.
                float frame = animation.getFrameDuration() / animation.getSpeed();
                float tail = (float) animation.getPlaybackDuration() - combat.getActiveAttack().hitFrame() * frame;
                ai.recover(Math.max(.16f, .4f - tail));
            }
            stopAttack(entity);
            return;
        }
        activateMeleeSensor(entity, combat, animation);
    }

    private void lockEnemyAim(Entity archer, BowAim aim, Entity target, Animation2D animation) {
        // Snapshot once, including the remaining draw as well as flight. A late direction change beats the lead.
        float remaining = (BowAim.FULL_DRAW_FRAME / 2) * animation.getFrameDuration() / animation.getSpeed();
        var body = Physic.MAPPER.get(target).getBody();
        var center = body.getPosition().cpy().mulAdd(Transform.MAPPER.get(target).getSize(), .5f);
        var start = launchCenter(archer);
        var point = ArrowFlight.intercept(arrowType, start, center, body.getLinearVelocity(), remaining);
        aim.lockAt(point, remaining);
        Facing.MAPPER.get(archer).faceAlong(point.cpy().sub(start));
    }

    private long physicsStep() {
        var physics = getEngine().getSystem(PhysicSystem.class);
        return physics == null ? 0 : physics.completedSteps();
    }

    private void activateMeleeSensor(Entity entity, Combat combat, Animation2D animation) {
        var attack = combat.getActiveAttack();
        if (attack.collisionType() != CollisionType.MELEE
            || combat.getSensor() != null || !animation.hasReachedFrame(attack.hitFrame())) { return; }
        createMeleeSensor(entity, combat, 1f);
    }

    private void createMeleeSensor(Entity entity, Combat combat, float scale) {
        var body = Physic.MAPPER.get(entity).getBody();
        var size = Transform.MAPPER.get(entity).getSize();
        var anchor = new Vector2(size.x * 0.5f, size.y * 0.5f);
        var shape = new PolygonShape();
        try {
            var vertices = combat.getActiveAttack().vertices(anchor, combat.getAttackFacing());
            if (scale != 1f) {
                var center = new Vector2();
                for (var vertex : vertices) { center.add(vertex); }
                center.scl(1f / vertices.length);
                for (var vertex : vertices) { vertex.sub(center).scl(scale).add(center); }
            }
            shape.set(vertices);
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.isSensor = true;
            var sensor = body.createFixture(definition);
            body.setAwake(true);
            combat.setSensor(sensor, physicsStep());
            sensor.setUserData(new AttackSensor(entity, combat.getAttackId(), isHammerLunge(entity, combat)));
        } finally {
            shape.dispose();
        }
    }

    private void stopAttack(Entity entity) {
        var combat = mountedCombat.get(entity);
        if (combat == null || !combat.isAttacking()) { return; }
        var sensor = combat.getSensor();
        if (sensor != null) { sensor.getBody().destroyFixture(sensor); }
        var physics = Physic.MAPPER.get(entity);
        if (physics != null && isHammerLunge(entity, combat)) { physics.getBody().setLinearVelocity(0f, 0f); }
        combat.endAttack();
    }

    @Override
    public void beginContact(Contact contact) {
        var a = contact.getFixtureA();
        var b = contact.getFixtureB();
        var sensors = getEngine() == null ? null : getEngine().getSystem(MapSensorSystem.class);
        if (sensors != null) { sensors.contact(a, b, true); }
        AwarenessSystem.guardBodyContact(a, b);
        queueHit(a, b);
        queueHit(b, a);
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
        pendingHits.add(new Hit(attack.attacker(), entity, attack.attackId(), attack.hammerLunge()));
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
        var ai = ActorAi.MAPPER.get(entity);
        if (ai != null) { ai.clearOpportunity(); ai.recover(0f); }
        arrows.removeIf(arrow -> arrow.owner == entity);
        pendingHits.removeIf(hit -> hit.attacker() == entity || hit.target() == entity);
        var aim = BowAim.MAPPER.get(entity);
        if (aim != null) { aim.cancel(); }
        var combat = Combat.MAPPER.get(entity);
        if (combat == null) { combat = mountedCombat.get(entity); }
        if (combat != null) { combat.consumeAttackRequest(); }
        stopAttack(entity);
        mountedCombat.remove(entity);
    }

    private record AttackSensor(Entity attacker, long attackId, boolean hammerLunge) { }
    private record Hit(Entity attacker, Entity target, long attackId, boolean hammerLunge) { }
    private record LungePush(Vector2 direction, float baseline, float added) { }
}
