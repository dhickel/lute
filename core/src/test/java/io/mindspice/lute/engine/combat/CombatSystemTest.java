package io.mindspice.lute.engine.combat;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.animation.AnimationState;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputMap;
import io.mindspice.lute.engine.tiled.properties.PlayerCharProps;
import io.mindspice.lute.engine.util.Pair;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class CombatSystemTest {
    private static final float STEP = 1f / 60f;
    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        GdxNativesLoader.load();
        Box2D.init();

        run("factory internal JSON and real atlas animation integration", CombatSystemTest::factoryIntegration);
        run("invalid combat data", CombatSystemTest::invalidData);
        run("native-unsafe bounds rejected before JNI", CombatSystemTest::unsafeGeometry);
        run("shape bounds, rotations and native polygons", CombatSystemTest::shapeGeometry);
        run("health validation and clamps", CombatSystemTest::health);
        run("sensor placement for all shapes and facings", CombatSystemTest::sensorPlacement);
        run("input edges and attack to idle", CombatSystemTest::inputAndIdle);
        run("movement/facing lock and attack to walk", CombatSystemTest::movementAndWalk);
        run("weapon attack speed scales completion", CombatSystemTest::attackSpeed);
        run("queued contacts, exclusions, reentry and reattack", CombatSystemTest::contacts);
        run("sleeping owner can attack stationary target", CombatSystemTest::sleepingOwner);
        run("saved semicircle hits beyond sprite in every direction", CombatSystemTest::outsideSpriteContacts);
        run("attacker removal clears sensor and queued damage", CombatSystemTest::attackerRemoval);
        run("target removal discards queued damage", CombatSystemTest::targetRemoval);
        run("system removal clears sensors and queued damage", CombatSystemTest::systemRemoval);
        run("dead attacker cannot attack and death cancels attack", CombatSystemTest::deadAttacker);
        run("dead attacker cannot apply queued damage", CombatSystemTest::deadAttackerQueuedHit);
        run("combat removal clears native sensor", CombatSystemTest::combatRemoval);
        System.out.println("CombatSystemTest: " + checks + " checks, " + failures.size()
            + " failed groups (native Box2D; no graphics context)");
        if (!failures.isEmpty()) { throw new AssertionError(String.join("\n", failures)); }
    }

    private static void actualJson() {
        var data = io.mindspice.lute.engine.data.json.DataLoader.loadGameData();
        var player = data.playerData();
        var weapon = data.weaponsData().weapons().get(player.equippedWeapon());
        check(weapon != null, "Equipped weapon key resolves");
        near(100, player.maxHealth(), "Player health");
        near(4, player.moveSpeed(), "Player speed");
        equal(BoundsType.SEMICIRCLE, weapon.boundsType(), "Weapon shape");
    }

    private static void factoryIntegration() {
        var previousFiles = Gdx.files;
        // Exercise FileHandle's real internal-resource fallback: data/ is not a cwd directory.
        Gdx.files = (Files) Proxy.newProxyInstance(Files.class.getClassLoader(), new Class<?>[]{Files.class},
            (proxy, method, args) -> {
                if (method.getName().equals("internal")) {
                    return new FileHandle((String) args[0], Files.FileType.Internal) { };
                }
                throw new UnsupportedOperationException(method.getName());
            });
        try {
            var field = io.mindspice.lute.engine.GameCore.class.getDeclaredField("gameData");
            field.setAccessible(true);
            field.set(io.mindspice.lute.engine.GameCore.INSTANCE, io.mindspice.lute.engine.data.json.DataLoader.loadGameData());
            actualJson();
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
        var world = new World(Vector2.Zero, true);
        var engine = new Engine();
        var combat = new CombatSystem(world);
        var atlasFile = new FileHandle("assets/objects/packed/objects.atlas");
        var atlas = new TextureAtlas();
        var metadata = new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false);
        for (var region : metadata.getRegions()) {
            var texture = new DimensionTexture((int) region.page.width, (int) region.page.height);
            atlas.addRegion(region.name, texture, region.left, region.top, region.width, region.height).index = region.index;
        }
        var assets = new AssetService(FileHandle::new) {
            @Override @SuppressWarnings("unchecked") public <T> T get(Asset<T> asset) { return (T) atlas; }
        };
        var input = new InputManager(InputMap.of(Pair.of(Input.Keys.SPACE, InputAction.ATTACK)));
        try {
            engine.addSystem(new ControllerSystem());
            engine.addSystem(new FacingSystem());
            engine.addSystem(combat);
            engine.addSystem(new PhysicMoveSystem());
            engine.addSystem(new AnimationPlayerSystem());
            engine.addSystem(new PhysicSystem(world, STEP));
            engine.addSystem(new AnimationSystem(assets));
            var factory = new EntityFactory(world, assets, input);
            engine.addSystem(factory);
            var atlasAsset = Asset.Atlas.of("objects.atlas");
            var mapAssets = new MapAssets(Asset.Map.of("test_tiled.tmx"), null,
                Map.of("OBJECTS", atlasAsset), Map.of(), Map.of());
            var props = new PlayerCharProps("IDLE", 1, 8, "OBJECTS", "player");
            var dataField = io.mindspice.lute.engine.GameCore.class.getDeclaredField("gameData");
            dataField.setAccessible(true);
            var originalData = io.mindspice.lute.engine.GameCore.INSTANCE.getGameData();
            var originalPlayer = originalData.playerData();
            try {
                dataField.set(io.mindspice.lute.engine.GameCore.INSTANCE, new io.mindspice.lute.engine.data.json.GameData(
                    new io.mindspice.lute.engine.data.json.PlayerData(originalPlayer.name(), 0, originalPlayer.maxHealth(), originalPlayer.equippedWeapon()), originalData.weaponsData()));
                rejects(() -> factory.getPlayerEntity(props, new Vector2(320, 640), mapAssets), "Factory rejects invalid move speed");
                dataField.set(io.mindspice.lute.engine.GameCore.INSTANCE, new io.mindspice.lute.engine.data.json.GameData(
                    new io.mindspice.lute.engine.data.json.PlayerData(originalPlayer.name(), originalPlayer.moveSpeed(), originalPlayer.maxHealth(), "missing"), originalData.weaponsData()));
                rejects(() -> factory.getPlayerEntity(props, new Vector2(320, 640), mapAssets), "Factory rejects unknown weapon");
            } finally {
                dataField.set(io.mindspice.lute.engine.GameCore.INSTANCE, originalData);
            }
            var player = factory.getPlayerEntity(props, new Vector2(320, 640), mapAssets);
            engine.addEntity(player);
            near(100, Health.MAPPER.get(player).getCurrent(), "Factory initializes health from internal JSON");
            equal("sword", Combat.MAPPER.get(player).getCurrentWeapon().id(), "Factory resolves weapon JSON");
            vector(new Vector2(9, 19), Physic.MAPPER.get(player).getBody().getPosition(), "Factory sprite body origin");
            near(4, Move.MAPPER.get(player).getMaxSpeed(), "Factory loads player movement speed");
            var animation = Animation2D.MAPPER.get(player);
            for (var facing : Facing.Direction.values()) {
                Facing.MAPPER.get(player).setDirection(facing);
                engine.update(0);
                input.keyDown(Input.Keys.SPACE);
                engine.update(0);
                check(Combat.MAPPER.get(player).isAttacking(), "Real input-manager press reaches factory controller");
                equal(Animation2D.AnimationType.ATTACK, animation.getType(), "Real atlas enters attack " + facing);
                equal(4, animation.getAnimation().getKeyFrames().length, "Real atlas supplies four attack frames " + facing);
                equal(Animation.PlayMode.NORMAL, animation.getAnimation().getPlayMode(), "Real attack does not loop");
                near(.5f, animation.getAnimation().getAnimationDuration(), "Real atlas attack duration");
                input.keyUp(Input.Keys.SPACE);
                engine.update(.5f);
                engine.update(0);
                check(!Combat.MAPPER.get(player).isAttacking(), "Real animation completion removes sensor");
                equal(Animation2D.AnimationType.IDLE, animation.getType(), "Real atlas returns to idle " + facing);
                equal(6, animation.getAnimation().getKeyFrames().length, "Real idle animation restored");
                equal(Animation.PlayMode.LOOP, animation.getAnimation().getPlayMode(), "Real idle loops again");
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        } finally {
            engine.removeAllEntities();
            engine.removeSystem(combat);
            world.dispose();
            atlas.dispose();
            assets.dispose();
            Gdx.files = previousFiles;
        }
    }

    private static void invalidData() {
        rejects(() -> new CombatWeapon.Weapon("sword", 0, 1, BoundsType.RECT, bounds()), "Invalid damage");
        rejects(() -> new CombatWeapon.Weapon("sword", 1, 0, BoundsType.RECT, bounds()), "Invalid speed");
        rejects(() -> new CombatWeapon.Bounds(1, 0, 1, 2), "Invalid bounds");
    }

    private static void unsafeGeometry() {
        for (var type : BoundsType.values()) {
            for (var invalid : new CombatWeapon.Bounds[]{
                new CombatWeapon.Bounds(0, 0, .001f, .001f),
                new CombatWeapon.Bounds(0, 0, Float.MIN_NORMAL, 64),
                new CombatWeapon.Bounds(0, 0, 1e24f, 64),
                new CombatWeapon.Bounds(0, 0, 1e15f, 1e15f),
                new CombatWeapon.Bounds(-Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)}) {
                rejects(() -> new CombatWeapon.Weapon("unsafe", 10, 1, type, invalid),
                    "Unsafe native geometry rejected for " + type + " " + invalid);
            }
            var weapon = weapon(type);
            rejects(() -> weapon.vertices(new Vector2(Float.NaN, 0), Facing.Direction.RIGHT), "Nonfinite anchor");
            rejects(() -> weapon.vertices(new Vector2(Float.MAX_VALUE, Float.MAX_VALUE), Facing.Direction.UP),
                "Unrepresentable rotated geometry");
        }
    }

    private static void shapeGeometry() {
        var anchor = new Vector2(32, 32).scl(GameConsts.UNIT_SCALE);
        var world = new World(Vector2.Zero, true);
        try {
            for (var type : BoundsType.values()) {
                var weapon = weapon(type);
                var right = weapon.vertices(anchor, Facing.Direction.RIGHT);
                equal(switch (type) { case RECT -> 4; case TRIANGLE -> 3; case SEMICIRCLE -> 7; },
                    right.length, "Expected polygon vertex count " + type);
                for (var facing : Facing.Direction.values()) {
                    var vertices = weapon.vertices(anchor, facing);
                    check(vertices.length >= 3 && vertices.length <= 8, "Box2D vertex limit");
                    float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
                    float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
                    for (int i = 0; i < vertices.length; i++) {
                        float dx = right[i].x - anchor.x, dy = right[i].y - anchor.y;
                        var rotated = switch (facing) {
                            case RIGHT -> new Vector2(dx, dy);
                            case UP -> new Vector2(-dy, dx);
                            case LEFT -> new Vector2(-dx, -dy);
                            case DOWN -> new Vector2(dy, -dx);
                        };
                        vector(rotated.add(anchor), vertices[i], type + " rotated about (32,32) " + facing);
                        check(Float.isFinite(vertices[i].x) && Float.isFinite(vertices[i].y), "Finite vertices");
                        var next = vertices[(i + 1) % vertices.length];
                        var after = vertices[(i + 2) % vertices.length];
                        check(next.cpy().sub(vertices[i]).crs(after.cpy().sub(next)) > 0f, "Strictly convex CCW polygon");
                        minX = Math.min(minX, vertices[i].x); maxX = Math.max(maxX, vertices[i].x);
                        minY = Math.min(minY, vertices[i].y); maxY = Math.max(maxY, vertices[i].y);
                    }
                    float[] extents = switch (facing) {
                        case RIGHT -> new float[]{1, 0, 2.25f, 2};
                        case UP -> new float[]{0, 1, 2, 2.25f};
                        case LEFT -> new float[]{-.25f, 0, 1, 2};
                        case DOWN -> new float[]{0, -.25f, 2, 1};
                    };
                    near(extents[0], minX, type + " minX " + facing);
                    near(extents[1], minY, type + " minY " + facing);
                    near(extents[2], maxX, type + " maxX " + facing);
                    near(extents[3], maxY, type + " maxY " + facing);
                    var polygon = new PolygonShape();
                    try {
                        polygon.set(vertices);
                        equal(vertices.length, polygon.getVertexCount(), "Native polygon retains all vertices");
                        var body = world.createBody(new BodyDef());
                        var fixture = body.createFixture(polygon, 0f);
                        equal(Shape.Type.Polygon, fixture.getShape().getType(), "Native fixture is a polygon");
                        world.step(STEP, 6, 2);
                        world.destroyBody(body);
                    } finally { polygon.dispose(); }
                }
            }
        } finally { world.dispose(); }
    }

    private static void health() {
        var health = new Health(100);
        near(100, health.getMaximum(), "Maximum health");
        near(100, health.getCurrent(), "Starts full");
        check(health.isAlive(), "Starts alive");
        health.damage(0); health.damage(25);
        near(75, health.getCurrent(), "Damage subtracts");
        health.damage(Float.MAX_VALUE);
        near(0, health.getCurrent(), "Overkill clamps to zero");
        check(!health.isAlive(), "Zero health is dead");
        health.damage(10);
        near(0, health.getCurrent(), "Further damage cannot underflow");
        near(100, health.getMaximum(), "Damage never changes maximum");
        for (float value : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> new Health(value), "Invalid maximum health");
        }
        var healthy = new Health(100);
        for (float value : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            rejects(() -> healthy.damage(value), "Invalid damage");
            near(100, healthy.getCurrent(), "Rejected damage does not mutate health");
        }
    }

    private static void sensorPlacement() {
        for (var shape : BoundsType.values()) {
            for (var facing : Facing.Direction.values()) {
                try (var game = new Harness(weapon(shape), facing, new Vector2(10, 20))) {
                    int initial = game.body().getFixtureList().size;
                    game.attack();
                    var sensor = game.combat().getSensor();
                    check(sensor != null && sensor.isSensor(), "Attack creates a sensor");
                    check(sensor.getBody() == game.body(), "Sensor is attached to owner, not a separate body");
                    equal(initial + 1, game.body().getFixtureList().size, "Exactly one attack fixture");
                    var expected = game.combat().getCurrentWeapon().vertices(new Vector2(1, 1), facing);
                    var polygon = (PolygonShape) sensor.getShape();
                    equal(expected.length, polygon.getVertexCount(), "Sensor polygon vertex count");
                    for (var vertex : expected) {
                        boolean found = false;
                        for (int i = 0; i < polygon.getVertexCount(); i++) {
                            var actual = new Vector2();
                            polygon.getVertex(i, actual);
                            found |= vertex.epsilonEquals(actual, .0002f);
                        }
                        check(found, "Sensor uses sprite-center anchor rather than feet/world-position: " + vertex);
                    }
                    game.finish();
                    equal(initial, game.body().getFixtureList().size, "Completion removes only sensor");
                }
            }
        }
    }

    private static void inputAndIdle() {
        try (var game = new Harness()) {
            game.input(InputAction.ATTACK, 0); game.update(0);
            check(!game.combat().isAttacking(), "Release alone does not attack");
            game.input(InputAction.ATTACK, 2); game.update(0);
            check(!game.combat().isAttacking(), "Repeat event does not attack");
            game.animation().setSpeed(.75f);
            game.attack();
            long id = game.combat().getAttackId();
            var sensor = game.combat().getSensor();
            equal(AnimationState.ATTACK, game.fsm().getCurrentState(), "Press enters ATTACK");
            equal(Animation2D.AnimationType.ATTACK, game.animation().getType(), "Attack animation selected");
            equal(Animation.PlayMode.NORMAL, game.animation().getAnimation().getPlayMode(), "Attack must not loop");
            equal(4, game.animation().getAnimation().getKeyFrames().length, "Four attack frames");
            near(.5f, game.animation().getAnimation().getAnimationDuration(), "Eight fps / four frames is half a second");
            near(1, game.animation().getSpeed(), "Weapon attackSpeed controls animation");
            game.input(InputAction.ATTACK, 0); game.update(.125f);
            game.input(InputAction.ATTACK, 1); game.update(.125f);
            game.input(InputAction.ATTACK, 0); game.update(.125f);
            check(game.combat().getSensor() == sensor, "Press/release during swing cannot restart sensor");
            equal(id, game.combat().getAttackId(), "Busy presses do not create another attack");
            check(!game.animation().isFinished(), "Attack not finished at .375 seconds");
            game.update(.125f);
            check(game.animation().isFinished(), "Four frames finish at .5 seconds");
            game.input(InputAction.ATTACK, 1);
            game.update(0);
            check(!game.combat().isAttacking() && game.combat().getSensor() == null, "Next combat pass removes completed sensor");
            equal(AnimationState.IDLE, game.fsm().getCurrentState(), "Clean return to idle");
            equal(Animation.PlayMode.LOOP, game.animation().getPlayMode(), "Idle restores looping");
            near(.75f, game.animation().getSpeed(), "Previous animation speed restored");
            equal(6, game.animation().getAnimation().getKeyFrames().length, "Idle frames replace attack frames");
            for (int i = 0; i < 10; i++) { game.update(.1f); }
            equal(id, game.combat().getAttackId(), "Busy input is not buffered");
            game.attack();
            equal(id + 1, game.combat().getAttackId(), "A new press can attack again");
        }
    }

    private static void movementAndWalk() {
        try (var game = new Harness()) {
            game.input(InputAction.MOVE_RIGHT, 1); game.update(0);
            equal(AnimationState.WALK, game.fsm().getCurrentState(), "Movement enters walk");
            vector(new Vector2(4, 0), game.body().getLinearVelocity(), "Movement before attack");
            game.attack();
            game.input(InputAction.MOVE_RIGHT, 0);
            game.input(InputAction.MOVE_UP, 1);
            game.update(.125f);
            equal(Facing.Direction.RIGHT, Facing.MAPPER.get(game.player).getDirection(), "Facing locked during swing");
            vector(Vector2.Zero, game.body().getLinearVelocity(), "Attack blocks movement");
            vector(new Vector2(0, 1), Move.MAPPER.get(game.player).getDirection(), "Held movement remains tracked");
            game.update(.375f); game.update(0);
            equal(AnimationState.WALK, game.fsm().getCurrentState(), "Held movement exits directly to walk");
            equal(Animation.PlayMode.LOOP, game.animation().getPlayMode(), "Walk loops after attack");
            vector(new Vector2(0, 4), game.body().getLinearVelocity(), "Movement resumes when attack completes");
            game.update(0);
            equal(Facing.Direction.UP, Facing.MAPPER.get(game.player).getDirection(), "Facing resumes on next facing pass");
            game.input(InputAction.MOVE_UP, 0); game.update(0);
            equal(AnimationState.IDLE, game.fsm().getCurrentState(), "Release returns to idle without turning");
            game.input(InputAction.MOVE_UP, 1); game.update(0);
            equal(AnimationState.WALK, game.fsm().getCurrentState(), "Restart same direction refreshes animation");
            game.attack();
            game.input(InputAction.MOVE_UP, 0); game.update(0);
            game.finish();
            equal(AnimationState.IDLE, game.fsm().getCurrentState(), "Movement release during attack is not lost");
            vector(Vector2.Zero, game.body().getLinearVelocity(), "No stuck velocity after release");
        }
    }

    private static void attackSpeed() {
        var weapon = new CombatWeapon.Weapon("fast-sword", 10, 2, BoundsType.RECT, bounds());
        try (var game = new Harness(weapon, Facing.Direction.RIGHT, new Vector2())) {
            game.attack();
            near(2, game.animation().getSpeed(), "Configured weapon speed applied");
            game.update(.125f);
            check(!game.animation().isFinished(), "Double-speed attack still active at .125 seconds");
            game.update(.125f);
            check(game.animation().isFinished(), "Double-speed attack completes at .25 seconds");
            game.update(0);
            check(!game.combat().isAttacking(), "Double-speed completion removes sensor");
            near(1, game.animation().getSpeed(), "Normal speed restored after fast attack");
        }
    }

    private static void contacts() {
        try (var game = new Harness()) {
            // Isolate hit bookkeeping; sleeping-body contact creation has its own regression below.
            game.body().setSleepingAllowed(false);
            var target = game.target(100, 2, false);
            var second = game.target(100, 1, false);
            var sensorOnly = game.target(100, 1, true);
            var scenery = game.target(null, 1, false);
            var dead = game.target(100, 1, false);
            Health.MAPPER.get(dead).damage(100);
            var ownerProxy = game.target(null, 1, false);
            Physic.MAPPER.get(ownerProxy).getBody().setUserData(game.player);
            var anonymous = game.target(null, 1, false);
            Physic.MAPPER.get(anonymous).getBody().setUserData(null);
            var probe = new ContactProbe(game, target);
            game.world.setContactListener(probe);
            game.attack();
            game.step();
            check(probe.begins >= 2, "Real Box2D beginContact sees overlapping fixtures");
            near(100, Health.MAPPER.get(target).getCurrent(), "Locked callbacks do not damage health");
            game.combatSystem.update(0);
            near(90, Health.MAPPER.get(target).getCurrent(), "Multiple fixtures damage target only once");
            near(90, Health.MAPPER.get(second).getCurrent(), "Separate target takes its own hit");
            near(100, Health.MAPPER.get(sensorOnly).getCurrent(), "Sensor target ignored");
            near(100, Health.MAPPER.get(game.player).getCurrent(), "Owner fixture metadata ignored");
            near(0, Health.MAPPER.get(dead).getCurrent(), "Dead target stays clamped");
            check(Health.MAPPER.get(scenery) == null, "Nondamageable fixture safely ignored");
            game.combatSystem.update(0);
            near(90, Health.MAPPER.get(target).getCurrent(), "Drained queue cannot hit twice");
            var body = Physic.MAPPER.get(target).getBody();
            body.setTransform(10, 10, 0); game.step();
            body.setTransform(1.7f, 1, 0); game.step();
            game.combatSystem.update(0);
            near(90, Health.MAPPER.get(target).getCurrent(), "Contact reentry within swing cannot hit again");
            game.finish();
            game.attack(); game.step();
            near(90, Health.MAPPER.get(target).getCurrent(), "New swing still defers damage");
            game.combatSystem.update(0);
            near(80, Health.MAPPER.get(target).getCurrent(), "New swing can hit same target again");
            near(80, Health.MAPPER.get(second).getCurrent(), "Per-target hit set resets for all targets");
        }
    }

    private static void sleepingOwner() {
        try (var game = new Harness()) {
            var target = game.target(100, 1, false);
            game.body().setAwake(false);
            check(!game.body().isAwake(), "Idle owner starts asleep");
            game.attack();
            check(game.combat().isAttacking(), "Sleeping owner accepts attack input");
            game.step();
            near(100, Health.MAPPER.get(target).getCurrent(), "Sleeping-owner attack still defers damage");
            game.combatSystem.update(0);
            near(90, Health.MAPPER.get(target).getCurrent(),
                "Attack must create contacts even after idle sleep (owner awake=" + game.body().isAwake() + ")");
        }
    }

    private static void outsideSpriteContacts() {
        var weapon = weapon(BoundsType.SEMICIRCLE);
        for (var facing : Facing.Direction.values()) {
            try (var game = new Harness(weapon, facing, new Vector2())) {
                var target = game.target(100, 1, false);
                var center = switch (facing) {
                    case RIGHT -> new Vector2(2.125f, 1);
                    case UP -> new Vector2(1, 2.125f);
                    case LEFT -> new Vector2(-.125f, 1);
                    case DOWN -> new Vector2(1, -.125f);
                };
                Physic.MAPPER.get(target).getBody().setTransform(center, 0);
                game.attack(); game.step(); game.combatSystem.update(0);
                near(90, Health.MAPPER.get(target).getCurrent(), "Native sensor reaches beyond sprite " + facing);
            }
        }
    }

    private static void attackerRemoval() {
        try (var game = new Harness()) {
            var target = game.target(100, 1, false);
            var combat = game.combat();
            game.attack(); game.step();
            int bodies = game.world.getBodyCount();
            game.engine.removeEntity(game.player);
            check(!combat.isAttacking() && combat.getSensor() == null, "Removed attacker drops sensor reference");
            equal(bodies - 1, game.world.getBodyCount(), "Physics removal destroys attacker body after sensor cleanup");
            game.update(0); game.step(); game.update(0);
            near(100, Health.MAPPER.get(target).getCurrent(), "Removed attacker cannot apply stale queued damage");
        }
    }

    private static void targetRemoval() {
        try (var game = new Harness()) {
            var target = game.target(100, 2, false);
            var health = Health.MAPPER.get(target);
            game.attack(); game.step();
            game.engine.removeEntity(target);
            game.combatSystem.update(0);
            near(100, health.getCurrent(), "Removed target cannot receive queued damage");
            game.finish();
            game.attack(); game.step(); game.combatSystem.update(0);
            near(100, health.getCurrent(), "Removed target stays untouched on subsequent attacks");
        }
    }

    private static void systemRemoval() {
        try (var game = new Harness()) {
            var target = game.target(100, 1, false);
            int fixtures = game.body().getFixtureList().size;
            game.animation().setSpeed(.5f);
            game.attack(); game.step();
            game.engine.removeSystem(game.combatSystem);
            check(!game.combat().isAttacking() && game.combat().getSensor() == null, "System removal stops active attacks");
            equal(fixtures, game.body().getFixtureList().size, "System removal destroys attack fixture");
            game.update(0);
            near(.5f, game.animation().getSpeed(), "Animation state restores speed after system removal");
            game.engine.addSystem(game.combatSystem);
            game.combatSystem.update(0);
            near(100, Health.MAPPER.get(target).getCurrent(), "Reattached system has no stale hit queue");
        }
    }

    private static void deadAttacker() {
        try (var game = new Harness()) {
            game.attack();
            Health.MAPPER.get(game.player).damage(100);
            game.update(0);
            check(!game.combat().isAttacking(), "Death cancels active attack");
            equal(AnimationState.IDLE, game.fsm().getCurrentState(), "Death exits attack state");
            game.attack();
            check(!game.combat().isAttacking(), "Dead player cannot start attack");
        }
    }

    private static void deadAttackerQueuedHit() {
        try (var game = new Harness()) {
            var target = game.target(100, 1, false);
            game.attack(); game.step();
            Health.MAPPER.get(game.player).damage(100);
            game.combatSystem.update(0);
            near(100, Health.MAPPER.get(target).getCurrent(), "Dead owner cannot apply pending damage");
            check(!game.combat().isAttacking(), "Dead owner's pending attack is cleaned up");
        }
    }

    private static void combatRemoval() {
        try (var game = new Harness()) {
            var combat = game.combat();
            game.animation().setSpeed(.75f);
            int initial = game.body().getFixtureList().size;
            game.attack();
            game.player.remove(Combat.class);
            check(!combat.isAttacking() && combat.getSensor() == null, "Removed component drops sensor reference");
            equal(initial, game.body().getFixtureList().size, "Removed component destroys native fixture");
            game.update(0);
            near(.75f, game.animation().getSpeed(), "Animation state restores speed after component removal");
        }
    }

    private static CombatWeapon.Bounds bounds() { return new CombatWeapon.Bounds(32, 0, 72, 64); }
    private static CombatWeapon.Weapon weapon(BoundsType shape) {
        return new CombatWeapon.Weapon("sword", 10, 1, shape, bounds());
    }

    private static FileHandle jsonFile(String json) {
        return new FileHandle("invalid-combat-test.json") {
            @Override public InputStream read() {
                return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
            }
        };
    }

    private static void run(String name, Runnable test) {
        try { test.run(); }
        catch (AssertionError | RuntimeException failure) {
            failures.add(name + ": " + failure);
            failure.printStackTrace(System.err);
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static void equal(Object expected, Object actual, String message) {
        check(java.util.Objects.equals(expected, actual), message + ": expected " + expected + ", got " + actual);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0002f, message + ": expected " + expected + ", got " + actual);
    }
    private static void vector(Vector2 expected, Vector2 actual, String message) {
        near(expected.x, actual.x, message + " x"); near(expected.y, actual.y, message + " y");
    }
    private static void rejects(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException | NullPointerException expected) { return; }
        throw new AssertionError(message + ": invalid data accepted");
    }
    private static void rejectsJson(Runnable action, String message) {
        checks++;
        try { action.run(); }
        catch (GdxRuntimeException expected) {
            check(expected.getMessage().contains(".json"), "JSON failure identifies source file");
            check(expected.getCause() != null, "JSON failure retains validation/parsing cause");
            return;
        }
        throw new AssertionError(message + ": invalid JSON accepted");
    }

    private static final class DimensionTexture extends Texture {
        private final int width;
        private final int height;
        DimensionTexture(int width, int height) { super(); this.width = width; this.height = height; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void dispose() { }
    }

    private static final class FakeAnimationSystem extends AnimationSystem {
        FakeAnimationSystem() { super(null); }

        @Override public void updateAnimation(Animation2D component, Facing.Direction facing) {
            int count = component.getType() == Animation2D.AnimationType.ATTACK ? 4 : 6;
            var frames = new TextureRegion[count];
            for (int i = 0; i < count; i++) { frames[i] = new TextureRegion(); }
            component.setAnimation(new Animation<>(component.getFrameDuration(), frames), facing);
        }
    }

    private static final class ContactProbe implements ContactListener {
        private final Harness game;
        private final Entity target;
        private int begins;

        ContactProbe(Harness game, Entity target) { this.game = game; this.target = target; }
        @Override public void beginContact(Contact contact) {
            begins++;
            check(game.world.isLocked(), "Real contact callback runs while world is locked");
            float before = Health.MAPPER.get(target).getCurrent();
            game.combatSystem.beginContact(contact);
            game.combatSystem.update(0);
            near(before, Health.MAPPER.get(target).getCurrent(), "Even update during locked callback cannot apply damage");
        }
        @Override public void endContact(Contact contact) { game.combatSystem.endContact(contact); }
        @Override public void preSolve(Contact contact, Manifold manifold) { game.combatSystem.preSolve(contact, manifold); }
        @Override public void postSolve(Contact contact, ContactImpulse impulse) { game.combatSystem.postSolve(contact, impulse); }
    }

    private static final class Harness implements AutoCloseable {
        final World world = new World(Vector2.Zero, true);
        final Engine engine = new Engine();
        final CombatSystem combatSystem = new CombatSystem(world);
        final Entity player = new Entity();

        Harness() {
            this(weapon(BoundsType.SEMICIRCLE), Facing.Direction.RIGHT, new Vector2());
        }

        Harness(CombatWeapon.Weapon weapon, Facing.Direction facing, Vector2 position) {
            engine.addSystem(new ControllerSystem());
            engine.addSystem(new FacingSystem());
            engine.addSystem(combatSystem);
            engine.addSystem(new PhysicMoveSystem());
            engine.addSystem(new AnimationPlayerSystem());
            engine.addSystem(new PhysicSystem(world, STEP));
            engine.addSystem(new FakeAnimationSystem());
            var definition = new BodyDef();
            definition.type = BodyDef.BodyType.DynamicBody;
            definition.position.set(position);
            definition.fixedRotation = true;
            var body = world.createBody(definition);
            body.setUserData(player);
            fixture(body, .15f, .1f, new Vector2(1, .15f), false);
            player.add(new Physic(body, position.cpy()));
            player.add(new Transform(position.cpy(), 0, new Vector2(64, 64).scl(GameConsts.UNIT_SCALE),
                new Vector2(1, 1), 0, 0));
            player.add(new Facing(facing));
            player.add(new Move(4));
            player.add(new Controller());
            player.add(new Combat(weapon));
            player.add(new Health(100));
            player.add(new Animation2D(Asset.Atlas.of("objects.atlas"), "player", Animation2D.AnimationType.IDLE,
                Animation.PlayMode.LOOP, 1, 8));
            player.add(new Graphic(new TextureRegion(), Color.WHITE));
            player.add(new AnimationPlayer(player));
            engine.addEntity(player);
            update(0);
        }

        Entity target(Integer health, int fixtureCount, boolean sensor) {
            var entity = new Entity();
            var definition = new BodyDef();
            definition.position.set(1.7f, 1);
            var body = world.createBody(definition);
            body.setUserData(entity);
            for (int i = 0; i < fixtureCount; i++) {
                fixture(body, .08f, .08f, new Vector2(0, i * .03f), sensor);
            }
            entity.add(new Physic(body, new Vector2(1.7f, 1)));
            entity.add(new Transform(new Vector2(1.7f, 1), 0, new Vector2(.16f, .16f), new Vector2(1, 1), 0, 0));
            if (health != null) { entity.add(new Health(health)); }
            engine.addEntity(entity);
            return entity;
        }

        void input(InputAction action, int state) { Controller.MAPPER.get(player).getInputListener().offer(action, state); }
        void attack() { input(InputAction.ATTACK, 1); update(0); }
        void update(float delta) { engine.update(delta); }
        void step() { world.step(STEP, 6, 2); }
        void finish() { update(.5f); update(0); check(!combat().isAttacking(), "Attack finishes after four frames"); }
        Body body() { return Physic.MAPPER.get(player).getBody(); }
        Combat combat() { return Combat.MAPPER.get(player); }
        Animation2D animation() { return Animation2D.MAPPER.get(player); }
        com.badlogic.gdx.ai.fsm.DefaultStateMachine<Entity, AnimationState> fsm() {
            return AnimationPlayer.MAPPER.get(player).getAnimationFsm();
        }
        @Override public void close() {
            engine.removeSystem(combatSystem);
            engine.removeAllEntities();
            world.dispose();
        }
    }

    private static void fixture(Body body, float halfWidth, float halfHeight, Vector2 center, boolean sensor) {
        var shape = new PolygonShape();
        try {
            shape.setAsBox(halfWidth, halfHeight, center, 0);
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.density = 1;
            definition.isSensor = sensor;
            body.createFixture(definition);
        } finally { shape.dispose(); }
    }
}
