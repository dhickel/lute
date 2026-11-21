package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.components.Move;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Physic;

import java.util.List;
import java.util.Map;

public final class AwarenessSystemTest {
    public static void main(String[] args) {
        GdxNativesLoader.load();
        Box2D.init();
        var world = new World(Vector2.Zero, true);
        float originalScale = GameConsts.UNIT_SCALE;
        try {
            GameConsts.UNIT_SCALE = .125f;
            run(world);
        } finally {
            GameConsts.UNIT_SCALE = originalScale;
            world.dispose();
        }
        System.out.println("AwarenessSystemTest: passed (native Box2D)");
    }

    private static void run(World world) {
        var engine = new Engine();
        var awareness = new AwarenessSystem(world);
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
        check(!Move.MAPPER.get(npc).getDirection().isZero() && !Move.MAPPER.get(enemy).getDirection().isZero(),
            "Both graphs wander without a target");
        checkPursuit(world, engine, enemy, player);
        body(player).setTransform(4, 0, 0);
        check(awareness.inRange(enemy, Actor.Role.PLAYER).equals(List.of(player)), "Boundary is inclusive");
        engine.update(0);
        var combat = Combat.MAPPER.get(enemy);
        check(facing.getDirection() == Facing.Direction.RIGHT && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY
            && Move.MAPPER.get(enemy).getDirection().isZero(), "Enemy stops and attacks at the inclusive limit through other actor bodies");
        check(!Combat.MAPPER.get(npc).hasAttackRequest(), "Wander-only graph does not attack a nearby player");
        body(player).setTransform(0, 3, 0);
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
        check(facing.getDirection() == Facing.Direction.UP && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY,
            "World sensors do not block ray");
        wall.setSensor(false);
        scenery.setTransform(0, -1, 0);
        body(player).setTransform(0, -1, 0);
        engine.update(0);
        check(!combat.hasAttackRequest(), "Embedded target is blocked");
        body(player).setTransform(20, 20, 0);
        body(enemy).setTransform(21, 20, 0);
        engine.update(0);
        check(facing.getDirection() == Facing.Direction.LEFT && combat.consumeAttackRequest() == Combat.AttackSlot.PRIMARY,
            "Moving the body moves the attached circle");
        Health.MAPPER.get(player).damage(100f);
        engine.update(0);
        check(!combat.hasAttackRequest() && !Move.MAPPER.get(enemy).getDirection().isZero(),
            "Enemy resumes wandering when the player dies");
        Health.MAPPER.get(enemy).damage(100f);
        engine.update(0);
        check(Move.MAPPER.get(enemy).getDirection().isZero(), "Dead enemy stops wandering");
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

    private static void checkPursuit(World world, Engine engine, Entity enemy, Entity player) {
        var combat = Combat.MAPPER.get(enemy);
        var direction = Move.MAPPER.get(enemy).getDirection();
        float scale = GameConsts.UNIT_SCALE;
        try {
            // Keep the four-world-unit sensor, but shrink the melee threshold to two world units.
            GameConsts.UNIT_SCALE = .0625f;
            body(player).setTransform(3, 1, 0);
            var wallDef = new BodyDef();
            wallDef.position.set(1.5f, .5f);
            var wall = world.createBody(wallDef);
            box(wall, 0, 0, .25f, false);
            engine.update(0);
            check(!combat.hasAttackRequest() && (direction.x == 0 || direction.y == 0),
                "A player behind a wall doesn't trigger pursuit");
            world.destroyBody(wall);
            engine.update(0);
            check(direction.equals(new Vector2(3, 1)) && !combat.hasAttackRequest(),
                "Visible player outside melee range triggers pursuit without swinging");
            body(player).setTransform(-1, 3, 0);
            engine.update(0);
            check(direction.equals(new Vector2(-1, 3)), "Pursuit updates to the player's new position");
            body(player).setTransform(-2, 4, 0);
            engine.update(0);
            check((direction.x == 0 || direction.y == 0) && !combat.hasAttackRequest(),
                "The enemy stops pursuing outside the detection circle");
        } finally {
            GameConsts.UNIT_SCALE = scale;
        }
    }

    private static Entity actor(Engine engine, World world, Actor.Role role, float x, float y) {
        var entity = engine.createEntity();
        var def = new BodyDef();
        def.position.set(x, y);
        var body = world.createBody(def);
        body.setUserData(entity);
        box(body, 0, 0, .2f, false);
        if (role != Actor.Role.PLAYER) {
            var shape = new CircleShape();
            try {
                shape.setRadius(4);
                var fixture = new FixtureDef();
                fixture.shape = shape;
                fixture.isSensor = true;
                fixture.filter.maskBits = 0;
                body.createFixture(fixture).setUserData(AwarenessSystem.SENSOR_TAG);
            } finally {
                shape.dispose();
            }
        }
        entity.add(new Actor(role, 0, 0)).add(new Physic(body, def.position.cpy()))
            .add(new Facing(Facing.Direction.DOWN)).add(new Health(100));
        if (role != Actor.Role.PLAYER) {
            var attack = new CombatWeapon.Attack("test", 10, 0, .3f, null, null, null, null, CollisionType.MELEE);
            var weapon = new CombatWeapon.Weapon("test", "", attack, attack);
            entity.add(new ActorAi(role == Actor.Role.ENEMY ? "wander_attack" : "wander"))
                .add(new Combat()).add(new Move(1f))
                .add(new Loadout(new Loadout.Equipment(null, null, null, null), "test", Map.of("test", weapon), _ -> { }));
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
        if (!condition) { throw new AssertionError(message); }
    }
}
