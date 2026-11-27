package io.mindspice.lute.engine.systems;

import io.mindspice.lute.engine.data.json.AttackAnimKey;
import io.mindspice.lute.engine.data.json.AttackDamage.Scalar;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.ActorAi.Mode;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.data.json.CollisionData;
import io.mindspice.lute.engine.data.json.ProjectilesData.Projectile;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Physic;

import java.util.List;
import java.util.Map;

public final class AwarenessSystemTest {
    private static int checks;

    public static void main(String[] args) {
        GdxNativesLoader.load();
        Box2D.init();
        var world = new World(Vector2.Zero, true);
        float originalScale = GameConsts.UNIT_SCALE;
        try {
            GameConsts.UNIT_SCALE = .125f;
            run(world);
            checkSemicircle(world);
            checkArcherSight(world);
            checkLungeDecisions(world);
            checkTwirlDecisions(world);
            checkInfighting(world);
            try (var test = new RetaliationTest()) { test.run(); }
        } finally {
            GameConsts.UNIT_SCALE = originalScale;
            world.dispose();
        }
        System.out.println("AwarenessSystemTest: " + checks + " checks passed (native Box2D)");
    }

    private static void checkSemicircle(World world) {
        var engine = new Engine();
        var awareness = new AwarenessSystem(world, false);
        engine.addSystem(awareness);
        var enemy = actor(engine, world, Actor.Role.ENEMY, 30, 30, .25f, .5f);
        var player = actor(engine, world, Actor.Role.PLAYER, 30, 30);
        var ai = ActorAi.MAPPER.get(enemy);
        var facing = Facing.MAPPER.get(enemy);
        var from = new Vector2(30.25f, 30.5f);
        for (var direction : Facing.Direction.values()) {
            var forward = switch (direction) {
                case UP -> new Vector2(0, 1);
                case DOWN -> new Vector2(0, -1);
                case LEFT -> new Vector2(-1, 0);
                case RIGHT -> new Vector2(1, 0);
            };
            var sideways = forward.cpy().rotate90(1);
            for (int side : new int[]{-1, 1}) {
                ai.forgetTarget();
                facing.setDirection(direction);
                var rear = from.cpy().mulAdd(sideways, 2 * side).mulAdd(forward, -.125f);
                body(player).setTransform(rear, 0);
                awareness.processEntity(enemy, 0);
                check(awareness.inRange(enemy, Actor.Role.PLAYER).equals(List.of(player)) && ai.mode() == Mode.WANDER,
                    direction + " rejects just behind either side of the semicircle even inside the range query");
                var edge = from.cpy().mulAdd(sideways, 4 * side);
                body(player).setTransform(edge, 0);
                awareness.processEntity(enemy, 0);
                check(ai.mode() == Mode.ENGAGE, direction + " includes both feet-centered side edges at full radius");
                facing.setDirection(direction);
                body(player).setTransform(rear, 0);
                awareness.processEntity(enemy, 0);
                check(ai.mode() == Mode.INVESTIGATE && !Combat.MAPPER.get(enemy).hasAttackRequest()
                    && Move.MAPPER.get(enemy).getDirection().equals(edge.sub(from)),
                    direction + " investigates the last seen feet when the player moves behind it");
            }
        }
    }

    private static void checkLungeDecisions(World world) {
        var engine = new Engine();
        var awareness = new AwarenessSystem(world, false);
        engine.addSystem(awareness);
        var enemy = actor(engine, world, Actor.Role.ENEMY, 90, 90);
        var player = actor(engine, world, Actor.Role.PLAYER, 93, 90);
        body(player).setType(BodyDef.BodyType.DynamicBody);
        for (var fixture : body(enemy).getFixtureList()) {
            if (fixture.getUserData() == AwarenessSystem.SENSOR_TAG) { fixture.getShape().setRadius(12); }
        }
        var primary = new CombatWeapon.Attack("shared", new Scalar(10), 0, .55f,
            AttackAnimKey.SLASH, null, null, null, null, CollisionType.MELEE);
        var secondary = new CombatWeapon.Attack("shared", new Scalar(20), 0, .75f,
            AttackAnimKey.LUNGE, null, null, null, null, CollisionType.MELEE);
        var weapon = new CombatWeapon.Weapon("shared", "shared", primary, secondary);
        enemy.add(new Loadout(new Loadout.Equipment(null, null, null, null), "shared", Map.of("shared", weapon), _ -> { }));
        var ai = new ActorAi("wander_lunge", 12, 0);
        enemy.add(ai);
        var combat = Combat.MAPPER.get(enemy);
        var facing = Facing.MAPPER.get(enemy);

        // Sample the real perception and graph without advancing physics or running combat.
        java.util.function.BiFunction<Float, Vector2, Combat.AttackSlot> choice = (pixels, velocity) -> {
            ActorAi.MAPPER.get(enemy).forgetTarget();
            facing.setDirection(Facing.Direction.RIGHT);
            body(player).setTransform(90 + pixels * GameConsts.UNIT_SCALE, 90, 0);
            body(player).setLinearVelocity(velocity);
            awareness.processEntity(enemy, 0f);
            check(!combat.hasAttackRequest(), "A fresh opportunity samples its decision delay");
            awareness.processEntity(enemy, .11f);
            return combat.consumeAttackRequest();
        };
        check(choice.apply(32f, Vector2.Zero) == Combat.AttackSlot.PRIMARY, "Stationary target at melee boundary gets primary");
        check(choice.apply(28f, new Vector2(-1, 0)) == Combat.AttackSlot.PRIMARY, "Approaching target gets primary");
        check(choice.apply(28f, new Vector2(0, 1)) == Combat.AttackSlot.PRIMARY, "Lateral movement is not moving away");
        check(choice.apply(28f, new Vector2(1, 0)) == Combat.AttackSlot.SECONDARY, "Outward velocity inside melee range gets lunge");
        check(choice.apply(80f, Vector2.Zero) == Combat.AttackSlot.SECONDARY, "Stationary target at extended range gets lunge");
        check(choice.apply(81f, new Vector2(1, 0)) == null, "Outside commit range, a retreating target is pursued, not attacked");

        combat.beginAttack(Combat.AttackSlot.SECONDARY, secondary, Facing.Direction.RIGHT);
        combat.endAttack();
        check(choice.apply(28f, new Vector2(1, 0)) == Combat.AttackSlot.PRIMARY, "Cooling secondary falls back to ready primary in melee");
        check(choice.apply(60f, Vector2.Zero) == null, "Cooling secondary does not let primary attack at extended range");
        combat.tickCooldown(.75f);
        combat.beginAttack(Combat.AttackSlot.PRIMARY, primary, Facing.Direction.RIGHT);
        combat.endAttack();
        check(choice.apply(28f, Vector2.Zero) == null, "Cooling primary does not provoke a lunge on a stationary melee target");
        check(choice.apply(28f, new Vector2(1, 0)) == Combat.AttackSlot.SECONDARY, "Ready secondary ignores primary cooldown");
        combat.tickCooldown(.55f);

        var wall = world.createBody(new BodyDef());
        wall.setTransform(93, 90, 0);
        box(wall, 0, 0, .25f, false);
        check(choice.apply(60f, Vector2.Zero) == null, "Solid cover prevents commitment");
        world.destroyBody(wall);
        var playerHealth = Health.MAPPER.get(player);
        playerHealth.damage(playerHealth.getCurrent());
        check(!playerHealth.isAlive() && choice.apply(60f, Vector2.Zero) == null, "Dead targets cannot trigger the lunge");
        playerHealth.restore();
        check(playerHealth.isAlive(), "Ordinary melee control has a living target");

        enemy.add(new ActorAi("wander_attack", 12, 0));
        check(choice.apply(60f, Vector2.Zero) == Combat.AttackSlot.SECONDARY, "Ordinary melee uses the same hammer to close distance");
        check(choice.apply(28f, new Vector2(1, 0)) == Combat.AttackSlot.SECONDARY, "Ordinary hammer enemy lunges at a retreating target");
        check(choice.apply(28f, Vector2.Zero) == Combat.AttackSlot.PRIMARY, "Ordinary hammer enemy uses primary on a stationary melee target");
    }

    private static void checkTwirlDecisions(World world) {
        var engine = new Engine();
        var awareness = new AwarenessSystem(world, false);
        engine.addSystem(awareness);
        var enemy = actor(engine, world, Actor.Role.ENEMY, 110, 110);
        var player = actor(engine, world, Actor.Role.PLAYER, 115, 110);
        Facing.MAPPER.get(enemy).setDirection(Facing.Direction.RIGHT);
        for (var fixture : body(enemy).getFixtureList()) {
            if (fixture.getUserData() == AwarenessSystem.SENSOR_TAG) { fixture.getShape().setRadius(6); }
        }
        var primary = new CombatWeapon.Attack("synthetic", new Scalar(10), 0, .5f,
            AttackAnimKey.SLASH, null, null, null, null, CollisionType.MELEE);
        var twirl = new CombatWeapon.Attack("synthetic", new Scalar(10), 0, .5f,
            AttackAnimKey.TWIRL, null, null, null, null, CollisionType.MELEE);
        var jab = new CombatWeapon.Attack("synthetic", new Scalar(10), 0, .5f,
            AttackAnimKey.JAB, null, null, null, null, CollisionType.MELEE);
        var axe = new CombatWeapon.Weapon("synthetic", "", primary, twirl);
        var equipment = new Loadout.Equipment(null, null, null, null);
        enemy.add(new Loadout(equipment, "synthetic", Map.of("synthetic", axe), _ -> { }));
        var combat = Combat.MAPPER.get(enemy);

        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .11f);
        check(ActorAi.MAPPER.get(enemy).mode() == Mode.ENGAGE && !combat.hasAttackRequest(),
            "Visible target beyond melee reach is pursued instead of triggering twirl");
        body(player).setTransform(114, 110, 0);
        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .02f);
        check(!combat.hasAttackRequest(), "Twirl waits at least the minimum melee decision delay");
        awareness.processEntity(enemy, .11f);
        check(combat.requestedTarget() == player && combat.consumeAttackRequest() == Combat.AttackSlot.SECONDARY,
            "Ordinary axe enemy chooses twirl over a ready primary in melee range");

        combat.beginAttack(Combat.AttackSlot.SECONDARY, twirl, Facing.Direction.RIGHT);
        combat.endAttack();
        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .11f);
        check(combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY, "Cooling twirl falls back to primary");

        combat.tickCooldown(twirl.cooldownSeconds());
        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .11f);
        check(combat.consumeAttackRequest() == Combat.AttackSlot.SECONDARY, "Twirl is preferred again once ready");
        combat.beginAttack(Combat.AttackSlot.SECONDARY, twirl, Facing.Direction.RIGHT);
        combat.tickCooldown(twirl.cooldownSeconds());
        awareness.processEntity(enemy, .11f);
        check(!combat.hasAttackRequest(), "A ready twirl cannot interrupt an active attack");
        combat.endAttack();
        awareness.processEntity(enemy, .11f);
        check(!combat.hasAttackRequest(), "Even if cooldown expired during playback, the next twirl starts a fresh delay");
        awareness.processEntity(enemy, .11f);
        check(combat.consumeAttackRequest() == Combat.AttackSlot.SECONDARY, "The new twirl opportunity completes its delay");

        var ordinary = new CombatWeapon.Weapon("synthetic", "", primary, jab);
        enemy.add(new Loadout(equipment, "synthetic", Map.of("synthetic", ordinary), _ -> { }));
        combat.beginAttack(Combat.AttackSlot.PRIMARY, primary, Facing.Direction.RIGHT);
        combat.endAttack();
        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .11f);
        check(!combat.hasAttackRequest(), "An unrelated secondary is not selected even when primary is cooling down");
        combat.tickCooldown(primary.cooldownSeconds());
        awareness.processEntity(enemy, 0f);
        awareness.processEntity(enemy, .11f);
        check(combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY, "Unrelated secondary leaves ordinary primary behavior intact");
    }

    private static void checkArcherSight(World world) {
        var engine = new Engine();
        engine.addSystem(new AwarenessSystem(world, false));
        var enemy = actor(engine, world, Actor.Role.ENEMY, 50, 50);
        var player = actor(engine, world, Actor.Role.PLAYER, 53, 50);
        Facing.MAPPER.get(enemy).setDirection(Facing.Direction.RIGHT);
        var shot = new CombatWeapon.Attack("bow", new Scalar(10), 0, .3f, AttackAnimKey.SHOT, null, null, null, null, CollisionType.PROJECTILE);
        var twirl = new CombatWeapon.Attack("bow", new Scalar(10), 0, .3f, AttackAnimKey.TWIRL, null, null, null, null, CollisionType.MELEE);
        var bow = new CombatWeapon.Weapon("bow", "", shot, twirl);
        body(enemy).setType(BodyDef.BodyType.DynamicBody);
        engine.addSystem(new PhysicMoveSystem());
        enemy.add(new ActorAi(BehaviorGraphs.ARCHER, 4f, 0)).add(new BowAim(1, 1)).add(new Loadout(new Loadout.Equipment(null, null, null, null),
            "bow", Map.of("bow", bow), _ -> { }));
        var combat = Combat.MAPPER.get(enemy);
        engine.update(0);
        check(!combat.hasAttackRequest() && body(enemy).getLinearVelocity().x > 0, "Outer archer approaches while deciding");
        engine.update(.14f);
        check(!combat.hasAttackRequest(), "Archer keeps the 150 ms minimum decision delay");
        engine.update(.161f);
        check(combat.requestedTarget() == player && combat.requestedSlot() == Combat.AttackSlot.PRIMARY
            && Move.MAPPER.get(enemy).getDirection().isZero(), "Archer shoots rather than using a melee secondary");
        combat.consumeAttackRequest();
        combat.beginAttack(Combat.AttackSlot.PRIMARY, shot, Facing.Direction.RIGHT);
        engine.update(0);
        check(body(enemy).getLinearVelocity().x > 0 && !combat.hasAttackRequest(),
            "Outer archer advances during shot recovery without requesting an early draw");
        body(player).setTransform(52, 50, 0);
        engine.update(0);
        check(body(enemy).getLinearVelocity().isZero() && !combat.hasAttackRequest(),
            "Half-radius boundary is inside the hold zone");
        body(player).setTransform(53, 50, 0);
        engine.update(0);
        check(body(enemy).getLinearVelocity().x > 0, "Approach resumes when the visible player moves out");
        combat.tickCooldown(shot.cooldownSeconds());
        engine.update(.35f);
        check(!combat.hasAttackRequest(), "Ready slot cannot truncate release playback");
        combat.endAttack();
        engine.update(0);
        engine.update(.35f);
        check(body(enemy).getLinearVelocity().isZero() && combat.requestedTarget() == player,
            "Ready bow stops for a new opportunity after recovery");
        combat.consumeAttackRequest();
        var wall = world.createBody(new BodyDef());
        wall.setTransform(51, 50, 0);
        box(wall, 0, 0, .25f, false);
        engine.update(0);
        check(!combat.hasAttackRequest() && ActorAi.MAPPER.get(enemy).mode() == Mode.INVESTIGATE,
            "Archer investigates instead of firing through cover");
    }

    private static void run(World world) {
        var engine = new Engine();
        var awareness = new AwarenessSystem(world, false);
        engine.addSystem(awareness);
        var enemy = actor(engine, world, Actor.Role.ENEMY, 0, 0);
        var npc = actor(engine, world, Actor.Role.NPC, 2, 0);
        var player = actor(engine, world, Actor.Role.PLAYER, 4, 4);
        var facing = Facing.MAPPER.get(enemy);
        check(awareness.inRange(enemy, Actor.Role.NPC).equals(List.of(npc)), "Static NPC is detectable");
        check(awareness.inRange(npc, Actor.Role.ENEMY).equals(List.of(enemy)), "Static enemy is detectable");
        check(awareness.inRange(enemy, Actor.Role.ENEMY).isEmpty(), "Exclude self");
        check(awareness.inRange(player, Actor.Role.NPC).isEmpty(), "Player owns no sensor");
        engine.update(.25f);
        check(facing.getDirection() == Facing.Direction.DOWN && !Combat.MAPPER.get(enemy).hasAttackRequest(),
            "AABB corner does not trigger an attack");
        checkWander(awareness, npc);
        checkWander(awareness, enemy);
        checkPursuit(world, engine, enemy, player);
        body(player).setTransform(4, 0, 0);
        check(awareness.inRange(enemy, Actor.Role.PLAYER).equals(List.of(player)), "Boundary is inclusive");
        engine.update(0);
        var combat = Combat.MAPPER.get(enemy);
        for (int i = 0; i < 100; i++) { engine.update(0); }
        engine.update(.024f);
        check(!combat.hasAttackRequest() && Move.MAPPER.get(enemy).getDirection().x > 0,
            "No tick-based rolls: step in before the minimum decision time");
        body(player).setTransform(4.1f, 0, 0);
        engine.update(.3f);
        body(player).setTransform(4, 0, 0);
        engine.update(.3f);
        engine.update(.024f);
        check(!combat.hasAttackRequest(), "Leaving range cancels, reacquisition starts a fresh opportunity");
        engine.update(.077f);
        check(facing.getDirection() == Facing.Direction.RIGHT && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY
            && Move.MAPPER.get(enemy).getDirection().isZero(), "Enemy stops and attacks at the inclusive limit through other actor bodies");
        check(!Combat.MAPPER.get(npc).hasAttackRequest(), "Wander-only graph does not attack a nearby player");
        body(player).setTransform(0, 3, 0);
        facing.setDirection(Facing.Direction.UP);
        var scenery = world.createBody(new BodyDef());
        scenery.setUserData(new Entity()); // Tiled tile-object scenery is entity-backed.
        var wall = box(scenery, 0, 1, 1, false);
        engine.update(0);
        check(!combat.hasAttackRequest(), "Embedded origin is blocked");
        scenery.setTransform(0, 1, 0);
        engine.update(0);
        check(!combat.hasAttackRequest(), "Solid scenery blocks ray");
        wall.setSensor(true);
        engine.update(0);
        engine.update(.024f);
        check(!combat.hasAttackRequest(), "Melee waits at least 25 ms after gaining sight");
        engine.update(.077f);
        check(facing.getDirection() == Facing.Direction.UP && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY,
            "World sensors do not block ray");
        wall.setSensor(false);
        scenery.setTransform(0, -1, 0);
        body(player).setTransform(0, -1, 0);
        facing.setDirection(Facing.Direction.DOWN);
        engine.update(0);
        check(!combat.hasAttackRequest(), "Embedded target is blocked");
        body(player).setTransform(20, 20, 0);
        body(enemy).setTransform(22, 20, 0);
        facing.setDirection(Facing.Direction.LEFT);
        engine.update(0);
        engine.update(.35f);
        check(facing.getDirection() == Facing.Direction.LEFT && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY,
            "Moving the body moves the attached circle");
        Health.MAPPER.get(player).damage(100);
        engine.update(0);
        check(!combat.hasAttackRequest() && ActorAi.MAPPER.get(enemy).mode() == Mode.INVESTIGATE,
            "A dead player cannot be targeted for melee; the enemy can only investigate its remembered position");
        Health.MAPPER.get(enemy).damage(100);
        engine.update(0);
        check(Move.MAPPER.get(enemy).getDirection().isZero() && ActorAi.MAPPER.get(enemy).mode() == Mode.WANDER,
            "Dead enemy stops moving and clears its memory");
        engine.removeEntity(player);
        check(awareness.inRange(enemy, Actor.Role.PLAYER).isEmpty(), "Removed entity isn't returned even before body cleanup");
        world.destroyBody(body(player));
        check(awareness.inRange(enemy, Actor.Role.PLAYER).isEmpty(), "Destroyed target body isn't returned");
        body(npc).setTransform(22, 20, 0);
        check(awareness.inRange(enemy, Actor.Role.NPC).equals(List.of(npc)), "Moved NPC is detected immediately");
        var sensor = body(enemy).getFixtureList().peek();
        body(enemy).destroyFixture(sensor);
        check(awareness.inRange(enemy, Actor.Role.NPC).isEmpty(), "Removed fixture can't query");
        world.destroyBody(body(enemy));
        engine.update(0);
        check(awareness.inRange(enemy, Actor.Role.NPC).isEmpty(), "Destroyed owner body can't query");
    }

    private static void checkWander(AwarenessSystem awareness, Entity actor) {
        var direction = Move.MAPPER.get(actor).getDirection();
        check(direction.isZero(), "Both roles start in a wait phase");
        int phases = 16;
        float elapsed = .25f;
        while (phases > 0) {
            boolean waiting = direction.isZero();
            // Exercise individual graphs, so another actor's perception cannot alter the phase.
            awareness.processEntity(actor, .125f);
            elapsed += .125f;
            if (waiting != direction.isZero()) {
                check(elapsed >= 1f && elapsed <= 3f, "Each wait/move phase lasts within 1–3 seconds");
                if (!direction.isZero()) { check(direction.len2() == 1f, "Wander keeps cardinal movement"); }
                phases--;
                elapsed = 0f;
            } else if (elapsed >= 3f) {
                throw new AssertionError("Wander phase failed to change by the upper bound");
            }
        }
    }

    private static void checkInfighting(World world) {
        for (int chance : new int[]{0, 30, 100}) {
            checkInfighting(world, chance, true);
            checkInfighting(world, chance, false);
        }
    }

    private static void checkInfighting(World world, int authored, boolean enabled) {
        int chance = enabled ? authored : 0;
        var engine = new Engine();
        int[] roll = {29};
        int[] draws = {0};
        var random = new java.util.Random() {
            @Override public int nextInt(int bound) {
                check(bound == 100, "Infighting rolls a percentile");
                draws[0]++;
                return roll[0];
            }
        };
        var awareness = new AwarenessSystem(world, enabled, random);
        engine.addSystem(awareness);
        float x = 100 * (chance + 1);
        var victim = actor(engine, world, Actor.Role.ENEMY, x, 0);
        var attacker = actor(engine, world, Actor.Role.ENEMY, x + 2, 0);
        var ai = new ActorAi("wander_attack", 4f, authored);
        victim.add(ai);
        awareness.retaliateAfterDamage(victim, attacker);
        check(ai.retaliationTarget() == (chance == 0 ? null : attacker), "Roll 29 passes 30 and 100, but not zero");
        ai.forgetTarget();
        roll[0] = chance == 100 ? 99 : 30;
        awareness.retaliateAfterArrow(victim, attacker);
        check(ai.retaliationTarget() == (chance == 100 ? attacker : null), "Roll 30 fails 30 and roll 99 passes 100");
        check(draws[0] == (chance == 0 ? 0 : 2), "Disabled hits do not roll; enabled hits roll once each");
        ai.forgetTarget();
        var player = actor(engine, world, Actor.Role.PLAYER, x - 2, 0);
        awareness.retaliateAfterDamage(victim, player);
        check(ai.retaliationTarget() == player, "Player retaliation ignores the threshold");
        engine.removeAllEntities();
        world.destroyBody(body(victim));
        world.destroyBody(body(attacker));
        world.destroyBody(body(player));
    }

    private static final class RetaliationTest implements AutoCloseable {
        final World world = new World(Vector2.Zero, true);
        final Engine engine = new Engine();
        final Projectile type = new Projectile("test", 12, 5,
            new CollisionData(CollisionData.Shape.RECT, new CharacterBounds(0, 0, 1, 2), 0f), 0, 0, 0, 0);
        final AwarenessSystem awareness = new AwarenessSystem(world, false);
        final CombatSystem combat = new CombatSystem(world, type);

        void run() {
            engine.addSystem(awareness);
            engine.addSystem(combat);
            var victim = actor(engine, world, Actor.Role.NPC, 0, 0);
            box(body(victim), 0, 0, .2f, true).setUserData(Hurtbox.INSTANCE);
            var shooter = actor(engine, world, Actor.Role.ENEMY, -2, 0);
            var ai = ActorAi.MAPPER.get(victim);
            var melee = Combat.MAPPER.get(victim);
            var wall = world.createBody(new BodyDef());
            var cover = box(wall, 0, 1.5f, .25f, false);

            fire(shooter, 10, 1);
            combat.update(.05f); // In flight while the shooter is visible at launch.
            check(combat.arrows().size() == 1, "Arrow is still in flight when shooter moves");
            body(shooter).setTransform(0, 3, 0);
            impact();
            check(Health.MAPPER.get(victim).getCurrent() < 100 && ai.mode() == Mode.WANDER,
                "Arrow damages but won't provoke through cover to the shooter's current feet inside normal radius");

            body(shooter).setTransform(0, 12.0001f, 0);
            cover.setSensor(true);
            fire(shooter, 10, 1);
            impact();
            check(ai.retaliationTarget() == null, "Beyond three radii is ignored");
            body(shooter).setTransform(0, 12, 0);
            fire(shooter, 10, 1);
            impact();
            awareness.processEntity(victim, 0);
            check(ai.mode() == Mode.ENGAGE && ai.retaliationTarget() == shooter
                && Move.MAPPER.get(victim).getDirection().equals(new Vector2(0, 12)) && !melee.hasAttackRequest(),
                "Exactly three radii provokes pursuit of the actual non-player shooter through sensors");

            cover.setSensor(false);
            body(shooter).setTransform(0, 3, 0);
            awareness.processEntity(victim, 0);
            check(ai.mode() == Mode.INVESTIGATE && !melee.hasAttackRequest()
                && Move.MAPPER.get(victim).getDirection().equals(new Vector2(0, 12)),
                "Lost sight uses the old point and never attacks a hidden shooter in melee range");
            cover.setSensor(true);
            awareness.processEntity(victim, 0);
            awareness.processEntity(victim, .35f);
            check(ai.mode() == Mode.ENGAGE && melee.consumeAttackRequest() == Combat.AttackSlot.PRIMARY,
                "NPC with the unarmed loadout can attack on live reacquisition");
            var unarmed = Loadout.MAPPER.get(victim).getWeapon().primary();
            melee.beginAttack(Combat.AttackSlot.PRIMARY, unarmed, Facing.Direction.UP);
            melee.endAttack();
            awareness.processEntity(victim, 0);
            check(!melee.hasAttackRequest() && Move.MAPPER.get(victim).getDirection().isZero(),
                "Retaliation uses the existing unarmed cooldown");
            body(shooter).setTransform(0, 13, 0);
            awareness.processEntity(victim, 0);
            check(ai.mode() == Mode.INVESTIGATE && Move.MAPPER.get(victim).getDirection().equals(new Vector2(0, 3)),
                "A visible shooter leaving extended range also starts investigation");
            awareness.processEntity(victim, 2f);
            check(ai.mode() == Mode.WANDER && ai.retaliationTarget() == null, "Investigation clears the lead on timeout");

            body(shooter).setTransform(0, 3, 0);
            fire(shooter, 10, 1);
            impact();
            check(ai.retaliationTarget() == shooter, "A damaging hit with live sight also provokes inside normal radius");
            ai.forgetTarget();
            fire(shooter, 1, .25f);
            int hp = Health.MAPPER.get(victim).getCurrent();
            impact();
            awareness.retaliateAfterDamage(victim, victim);
            check(Health.MAPPER.get(victim).getCurrent() == hp && ai.retaliationTarget() == null, "Zero damage and self leads are ignored");
            Health.MAPPER.get(shooter).damage(100);
            fire(shooter, 10, 1);
            impact();
            check(ai.retaliationTarget() == null, "Dead shooter is ignored");
            Health.MAPPER.get(shooter).restore();
            engine.removeEntity(shooter);
            fire(shooter, 10, 1);
            impact();
            check(ai.retaliationTarget() == null, "Retired shooter is ignored even if its body is still live");
            engine.addEntity(shooter);
            fire(shooter, 100, 1);
            impact();
            check(!Health.MAPPER.get(victim).isAlive() && ai.retaliationTarget() == null, "Lethal arrow does not provoke");
            checkAttackQueue();
        }

        private void checkAttackQueue() {
            var enemy = actor(engine, world, Actor.Role.ENEMY, 20, 0);
            var player = actor(engine, world, Actor.Role.PLAYER, 20, 3);
            var shooter = actor(engine, world, Actor.Role.NPC, 12, -1);
            Facing.MAPPER.get(enemy).setDirection(Facing.Direction.UP);
            enemy.add(new Animation2D("test", "", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, .125f))
                .add(new Transform(new Vector2(20, 0), 0, new Vector2(1, 1), new Vector2(1, 1), 0, 0));
            var ai = ActorAi.MAPPER.get(enemy);
            var melee = Combat.MAPPER.get(enemy);
            awareness.processEntity(enemy, 0f);
            awareness.processEntity(enemy, .35f);
            check(melee.requestedTarget() == player, "Normal enemy vision queues melee against the live nearby player");
            combat.arrows().add(new ArrowFlight(shooter, type, new Vector2(18, 0), new Vector2(20, 0), new Scalar(10), 1));
            impact();
            check(ai.retaliationTarget() == shooter && melee.getAttackId() == 0 && !melee.hasAttackRequest(),
                "A damaging rear shot immediately retargets the enemy before its queued melee commits");
            awareness.processEntity(enemy, 0f);
            check(!melee.hasAttackRequest() && Move.MAPPER.get(enemy).getDirection().equals(new Vector2(-8, -1)),
                "Enemy pursues the actual shooter behind it rather than attacking the unrelated player");
            body(shooter).setTransform(7, 0, 0);
            awareness.processEntity(enemy, 0f);
            awareness.processEntity(enemy, .35f);
            check(ai.retaliationTarget() == null && melee.requestedTarget() == player,
                "Losing the shooter does not mask normal live enemy detection");
            Health.MAPPER.get(player).damage(100);
            combat.update(0f);
            check(melee.getAttackId() == 0 && !melee.hasAttackRequest(), "Target death before combat consumes the queued melee");
            engine.removeEntity(player);
            var replacement = actor(engine, world, Actor.Role.PLAYER, 20, 3);
            awareness.processEntity(enemy, 0f);
            check(!melee.hasAttackRequest(), "A replacement target cannot inherit the previous decision");
            awareness.processEntity(enemy, .35f);
            check(melee.requestedTarget() == replacement, "Vision acquires the replacement without retaining the retired player");
            combat.update(0f);
            check(melee.getAttackId() == 1 && melee.isAttacking(), "Live sight still commits an attack through the combat system");
        }

        private void fire(Entity shooter, int damage, float scale) {
            combat.arrows().add(new ArrowFlight(shooter, type, new Vector2(-2, 0), new Vector2(0, 0), new Scalar(damage), scale));
        }

        private void impact() {
            combat.update(.2f);
            check(combat.arrows().isEmpty(), "Arrow resolves the impact");
        }

        @Override public void close() { world.dispose(); }
    }

    private static void checkPursuit(World world, Engine engine, Entity enemy, Entity player) {
        var combat = Combat.MAPPER.get(enemy);
        var direction = Move.MAPPER.get(enemy).getDirection();
        var ai = ActorAi.MAPPER.get(enemy);
        float scale = GameConsts.UNIT_SCALE;
        try {
            // Keep the four-world-unit sensor, but shrink the melee threshold to two world units.
            GameConsts.UNIT_SCALE = .0625f;
            Facing.MAPPER.get(enemy).setDirection(Facing.Direction.RIGHT);
            body(player).setTransform(3, 1, 0);
            var wallDef = new BodyDef();
            wallDef.position.set(1.5f, .5f);
            var wall = world.createBody(wallDef);
            box(wall, 0, 0, .25f, false);
            engine.update(0);
            check(!combat.hasAttackRequest() && ai.mode() == Mode.WANDER,
                "A player behind a wall doesn't trigger pursuit before being seen");
            world.destroyBody(wall);
            engine.update(0);
            check(ai.mode() == Mode.ENGAGE && direction.equals(new Vector2(3, 1)) && !combat.hasAttackRequest(),
                "Visible player outside melee range triggers pursuit without swinging");

            wall = world.createBody(wallDef);
            box(wall, 0, 0, .25f, false);
            var attack = Loadout.MAPPER.get(enemy).getWeapon().primary();
            combat.beginAttack(Combat.AttackSlot.PRIMARY, attack, Facing.MAPPER.get(enemy).getDirection());
            var facing = Facing.MAPPER.get(enemy).getDirection();
            body(player).setTransform(3, .75f, 0);
            engine.update(0);
            check(ai.mode() == Mode.INVESTIGATE && direction.isZero() && combat.isAttacking()
                && Facing.MAPPER.get(enemy).getDirection() == facing,
                "Perception updates during an attack without overriding movement or facing");
            combat.endAttack();
            combat.tickCooldown(attack.cooldownSeconds());
            engine.update(0);
            check(ai.mode() == Mode.INVESTIGATE && direction.equals(new Vector2(3, 1)) && !combat.hasAttackRequest(),
                "Occlusion steers toward the last visible point, not the player's new position");
            engine.update(1.75f);
            check(ai.mode() == Mode.INVESTIGATE, "Investigation survives a brief loss of sight");
            engine.update(.25f);
            check(ai.mode() == Mode.WANDER, "Investigation times out when terrain prevents arrival");
            world.destroyBody(wall);

            body(player).setTransform(-1, 3, 0);
            Facing.MAPPER.get(enemy).setDirection(Facing.Direction.UP);
            engine.update(0);
            check(ai.mode() == Mode.ENGAGE && direction.equals(new Vector2(-1, 3)), "The enemy can acquire the player again");
            body(player).setTransform(-2, 4, 0);
            engine.update(0);
            check(ai.mode() == Mode.INVESTIGATE && direction.equals(new Vector2(-1, 3)),
                "Leaving the sensor also starts an investigation of the remembered position");
            body(player).setTransform(3, 1, 0);
            engine.update(0);
            check(ai.mode() == Mode.ENGAGE && direction.equals(new Vector2(3, 1)),
                "A fresh sighting interrupts investigation and updates the goal");
            body(player).setTransform(20, 20, 0);
            body(enemy).setTransform(2.5f, 1, 0);
            engine.update(0);
            check(ai.mode() == Mode.WANDER && !combat.hasAttackRequest(),
                "Arriving within eight pixels ends the investigation without a melee request");
            body(enemy).setTransform(0, 0, 0);
        } finally {
            GameConsts.UNIT_SCALE = scale;
        }
    }

    private static Entity actor(Engine engine, World world, Actor.Role role, float x, float y) {
        return actor(engine, world, role, x, y, 0, 0);
    }

    private static Entity actor(Engine engine, World world, Actor.Role role, float x, float y, float feetX, float feetY) {
        var entity = engine.createEntity();
        var def = new BodyDef();
        def.position.set(x, y);
        var body = world.createBody(def);
        body.setUserData(entity);
        box(body, feetX, feetY, .2f, false);
        if (role != Actor.Role.PLAYER) {
            var shape = new CircleShape();
            try {
                shape.setRadius(4);
                shape.setPosition(new Vector2(feetX, feetY));
                var fixture = new FixtureDef();
                fixture.shape = shape;
                fixture.isSensor = true;
                fixture.filter.maskBits = 0;
                body.createFixture(fixture).setUserData(AwarenessSystem.SENSOR_TAG);
            } finally {
                shape.dispose();
            }
        }
        entity.add(new Actor(role, feetX, feetY)).add(new Physic(body, def.position.cpy()))
            .add(new Facing(Facing.Direction.DOWN)).add(new Health(100));
        if (role != Actor.Role.PLAYER) {
            var attack = new CombatWeapon.Attack("unarmed", new Scalar(10), 0, .3f,
                AttackAnimKey.JAB, new Animation2D.Attack("jab", 1, .125f, 0, "jab"), null, null, null, CollisionType.MELEE);
            var weapon = new CombatWeapon.Weapon("unarmed", "", attack, attack);
            entity.add(new ActorAi(role == Actor.Role.ENEMY ? "wander_attack" : "wander", 4f, 0))
                .add(new Combat()).add(new Move(1f))
                .add(new Loadout(new Loadout.Equipment(null, null, null, null), null, Map.of("unarmed", weapon), _ -> { }));
        }
        engine.addEntity(entity);
        return entity;
    }

    private static Fixture box(Body body, float x, float y, float halfSize, boolean sensor) {
        var shape = new PolygonShape();
        try {
            shape.setAsBox(halfSize, halfSize, new Vector2(x, y), 0);
            var fixture = body.createFixture(shape, 0);
            fixture.setSensor(sensor);
            return fixture;
        } finally {
            shape.dispose();
        }
    }

    private static Body body(Entity entity) { return Physic.MAPPER.get(entity).getBody(); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
}
