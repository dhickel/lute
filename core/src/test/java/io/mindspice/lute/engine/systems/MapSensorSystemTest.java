package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.MapAssets;
import io.mindspice.lute.engine.components.Animation2D;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.CombatWeapon;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputMap;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.MapTriggers;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Standalone native integration checks; no graphics context or map assets required. */
public final class MapSensorSystemTest {
    private static final float STEP = 1f / 60f;
    private static final List<String> LOGS = new ArrayList<>();
    private static int checks;

    public static void main(String[] args) {
        GdxNativesLoader.load();
        Box2D.init();
        var previousApp = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(),
            new Class<?>[]{Application.class}, (proxy, method, values) -> {
                if ((method.getName().equals("log") || method.getName().equals("error")) && values != null) {
                    LOGS.add(values[0] + ": " + values[1]);
                }
                return null;
            });
        try {
            nativeContactsAndFixtureDeduplication();
            onlyPlayerPhysicalFixtures();
            sameMapTeleport("THIS");
            sameMapTeleport("this");
            unsupportedMapStaysPut();
            missingSameMapDestinationFailsBeforeLoading();
            combatAndMapContactsCoexist();
            pendingAndActivePlayerRemoval();
            replacementAndSystemCleanup();
            lockedMapCleanupIsRejected();
            initialPlayerAndDataOnlyMarkers();
            eventDrivenPlayerSpawn();
            invalidMapLoadCleansPartialBodies();
            System.out.println("MapSensorSystemTest: " + checks
                + " checks passed (native Box2D; no graphics context)");
        } finally {
            Gdx.app = previousApp;
        }
    }

    private static void nativeContactsAndFixtureDeduplication() {
        try (var game = new Harness()) {
            var map = map(sensor("room", 4, 4, 4, 4));
            game.load(map);
            check(game.tiled.getCurrentMap() == map, "TiledSystem retains the actual programmatic map");
            check(game.tiled.getMapTriggers().sensors().size() == 1, "TiledSystem parses and wires the map sensor");
            var player = game.entity(5, 5, true, false, 2);
            game.sensors.update(0);
            game.flush();
            check(game.received.isEmpty(), "Geometric overlap alone cannot trigger before native world.step");

            game.step();
            game.flush();
            check(game.received.isEmpty(), "Locked contact callbacks do not emit gameplay events");
            game.sensors.update(0);
            check(game.received.isEmpty(), "Sensor events wait for EventSystem dispatch");
            game.flush();
            game.expect(new Event.Sensor.Enter("room", MapTriggers.SensorType.GENERAL, player));
            game.tick(STEP * 5);
            check(game.received.size() == 1, "Persistent contacts and multiple fixtures enter only once");

            game.move(player, 7.2f, 5);
            game.tick(STEP * 2);
            check(game.received.size() == 1, "Losing one fixture does not exit while another remains inside");
            game.move(player, 9, 5);
            game.tick(STEP * 2);
            game.expect(new Event.Sensor.Enter("room", MapTriggers.SensorType.GENERAL, player),
                new Event.Sensor.Exit("room", MapTriggers.SensorType.GENERAL, player));
            for (int i = 0; i < 3; i++) {
                game.move(player, 5, 5);
                game.tick(STEP * 2);
                game.move(player, 9, 5);
                game.tick(STEP * 2);
            }
            check(game.countEnter("room") == 4 && game.countExit("room") == 4,
                "Repeated leave/reentry produces exactly one typed pair per visit");
            check(game.unlockedDeliveries == game.received.size(), "Every event is delivered with native world unlocked");
        }
    }

    private static void onlyPlayerPhysicalFixtures() {
        try (var game = new Harness()) {
            game.load(map(sensor("players-only", 4, 4, 4, 4)));
            game.entity(5, 5, false, false, 1);
            game.entity(5, 5, true, true, 2);
            var player = game.entity(20, 20, true, false, 1);
            // A sensor fixture reaches into the map sensor while this player's feet remain outside.
            fixture(body(player), -14, -14.75f, true);
            game.tick(STEP * 4);
            check(game.received.isEmpty(), "NPC bodies and player-owned sensor fixtures are excluded");
            game.move(player, 5, 5);
            game.tick(STEP * 2);
            game.expect(new Event.Sensor.Enter("players-only", MapTriggers.SensorType.GENERAL, player));
        }
    }

    private static void sameMapTeleport(String targetMap) {
        try (var game = new Harness()) {
            game.load(map(sensor("old-room", 4, 4, 4, 4),
                portal("departure", 4, 4, 4, 4, targetMap, "landing"),
                sensor("new-room", 18, 14, 5, 4),
                portal("arrival", 18, 14, 5, 4, "THIS", "return"),
                portal("arrival-two", 18, 14, 5, 4, "THIS", "return"),
                point("landing", "TELEPORT", 20, 16), point("return", "TELEPORT", 6, 6)));
            var player = game.entity(5, 5, true, false, 2);
            var originalBody = body(player);
            originalBody.setTransform(5, 5, .2f);
            originalBody.setLinearVelocity(2, 3);
            originalBody.setAngularVelocity(1);
            Facing.MAPPER.get(player).setDirection(Facing.Direction.UP);
            game.sensors.update(0);
            near(5, originalBody.getPosition().x, "Teleport cannot run before contact discovery: " + targetMap);
            game.step();
            check(game.received.isEmpty(), "Teleport callback queues rather than dispatches");
            check(originalBody.getPosition().x < 6, "Native contact callback does not move a locked body");
            game.sensors.update(0);
            game.flush();

            check(body(player) == originalBody, "Same-map teleport preserves native body identity: " + targetMap);
            position(player, 19, 14.5f, "Pixel destination centers the 2x3 world-space sprite: " + targetMap);
            check(originalBody.getLinearVelocity().isZero() && originalBody.getAngularVelocity() == 0,
                "Teleport clears linear and angular velocity");
            check(Facing.MAPPER.get(player).getDirection() == Facing.Direction.UP, "Teleport preserves facing");
            check(game.countEnter("old-room") == 1 && game.countExit("old-room") == 1,
                "Teleport preserves the old generic sensor enter and exit");
            check(game.countEnter("new-room") == 1 && game.countExit("new-room") == 0,
                "Teleport immediately reconciles the new generic sensor enter");
            check(game.countEnter("arrival") == 1 && game.countEnter("arrival-two") == 1,
                "All overlapping arrival teleports still emit typed enter without activation");
            int delivered = game.received.size();
            game.tick(STEP * 8.5f);
            position(player, 19, 14.5f, "Arrival suppression survives multiple fixed ticks and interpolation");
            game.tick(STEP * .5f);
            position(player, 19, 14.5f, "Arrival suppression survives the next accumulator boundary");
            check(game.received.size() == delivered, "Native stale pairs do not duplicate reconciled teleport events");

            game.move(player, 25, 25);
            game.tick(STEP * 2);
            check(game.countExit("arrival") == 1 && game.countExit("new-room") == 1,
                "Genuine exit retires arrival suppression and both occupancies");
            game.move(player, 19, 14.5f);
            game.tick(STEP * 3.5f);
            position(player, 5, 4.5f, "Destination portal activates after genuine exit and reentry");
            check(game.countEnter("arrival") == 2 && game.countExit("arrival") == 2,
                "Reentered arrival portal emits a second visit and teleport exit");
            game.tick(STEP * 6);
            position(player, 5, 4.5f, "Return arrival cannot bounce back through the original portal");
        }
    }

    private static void unsupportedMapStaysPut() {
        try (var game = new Harness()) {
            game.load(map(portal("future-map", 4, 4, 4, 4, "elsewhere", "not-local")));
            var player = game.entity(5, 5, true, false, 2);
            LOGS.clear();
            game.tick(STEP * 10);
            position(player, 5, 5, "Unsupported target map leaves the player in place");
            check(LOGS.stream().filter(log -> log.contains("MapSensorSystem") && log.contains("elsewhere")).count() == 1,
                "Unsupported map logs once per entry, not once per fixture or fixed tick");
            game.expect(new Event.Sensor.Enter("future-map", MapTriggers.SensorType.GENERAL, player));
        }
    }

    private static void missingSameMapDestinationFailsBeforeLoading() {
        try (var game = new Harness()) {
            var valid = map(sensor("retained", 4, 4, 4, 4));
            game.load(valid);
            int bodies = game.world.getBodyCount();
            var invalid = map(portal("broken", 4, 4, 4, 4, "this", "absent"));
            try {
                rejects(() -> MapTriggers.parse(invalid), "Missing THIS TELEPORT point is rejected by parsing");
                rejects(() -> game.tiled.setMap(invalid, game.mapAssets), "TiledSystem validates before replacing the live map");
                check(game.tiled.getCurrentMap() == valid && game.world.getBodyCount() == bodies,
                    "Invalid replacement preserves the previous map and native bodies");
            } finally {
                invalid.dispose();
            }
        }
    }

    private static void combatAndMapContactsCoexist() {
        try (var game = new Harness()) {
            game.load(map(sensor("feet", 2.4f, 4, 1.2f, .6f), sensor("attack-only", 4, 5, .5f, .5f)));
            var player = game.entity(2, 4, true, false, 1);
            var target = game.entity(3.2f, 4.75f, false, false, 2);
            var weapon = new CombatWeapon.Weapon("test", 10, 0,
                new Animation2D.Attack(1, .125f, 0, "attack"), "hit", BoundsType.RECT,
                new CombatWeapon.Bounds(pixels(1), 0, pixels(3), pixels(2)));
            var animation = new Animation2D("unused", "unused", Animation2D.AnimationType.IDLE,
                Animation.PlayMode.NORMAL, 1, .125f);
            player.add(new Combat(weapon)).add(animation);
            Facing.MAPPER.get(player).setDirection(Facing.Direction.RIGHT);
            Combat.MAPPER.get(player).requestAttack();
            game.combat.update(0);
            animation.setType(Animation2D.AnimationType.ATTACK);
            animation.beginAttack();
            animation.setAnimation(new Animation<>(.125f, new TextureRegion(), new TextureRegion()), Facing.Direction.RIGHT);
            game.combat.update(0);
            check(Combat.MAPPER.get(player).getSensor() != null, "Real CombatSystem creates the attack fixture");
            game.step();
            near(100, Health.MAPPER.get(target).getCurrent(), "Native callbacks defer combat damage too");
            game.sensors.update(0);
            game.combat.update(0);
            game.flush();
            near(90, Health.MAPPER.get(target).getCurrent(), "Actual attack hits survive map listener forwarding");
            game.expect(new Event.Sensor.Enter("feet", MapTriggers.SensorType.GENERAL, player));
            game.tick(STEP * 4);
            game.combat.update(0);
            near(90, Health.MAPPER.get(target).getCurrent(), "Multi-fixture target is damaged only once per attack");
            check(game.countEnter("attack-only") == 0, "Real player attack fixture cannot activate map sensors");
        }
    }

    private static void pendingAndActivePlayerRemoval() {
        for (boolean removeEntity : new boolean[]{false, true}) {
            for (boolean drainBeforeRemoval : new boolean[]{false, true}) {
                try (var game = new Harness()) {
                    game.load(map(sensor("removed", 4, 4, 4, 4)));
                    var player = game.entity(5, 5, true, false, 2);
                    game.step();
                    if (drainBeforeRemoval) { game.sensors.update(0); game.flush(); }
                    if (removeEntity) { game.engine.removeEntity(player); }
                    else { player.remove(Player.class); }
                    game.sensors.update(0);
                    game.tick(STEP * 3);
                    if (drainBeforeRemoval) {
                        game.expect(new Event.Sensor.Enter("removed", MapTriggers.SensorType.GENERAL, player),
                            new Event.Sensor.Exit("removed", MapTriggers.SensorType.GENERAL, player));
                    } else {
                        check(game.received.isEmpty(), "Removing player/entity discards pending native contacts safely");
                    }
                    check(game.world.getBodyCount() == (removeEntity ? 2 : 3),
                        "Only entity removal destroys the player's body; marker removal is data-only");
                }
            }
        }
    }

    private static void replacementAndSystemCleanup() {
        try (var game = new Harness()) {
            var player = game.entity(5, 5, true, false, 2);
            game.load(map(sensor("old", 4, 4, 4, 4), sensor("unused", 10, 10, 2, 2)));
            check(game.world.getBodyCount() == 4, "Loaded map owns two sensor bodies and one boundary body");
            game.tick(STEP * 2);
            game.expect(new Event.Sensor.Enter("old", MapTriggers.SensorType.GENERAL, player));
            game.load(map(sensor("replacement", 4, 4, 4, 4)));
            game.flush();
            check(game.world.getBodyCount() == 3, "TiledSystem replacement destroys old sensors and boundary");
            check(game.countExit("old") == 1, "Replacement emits the occupied old sensor exit once");
            game.tick(STEP * 2);
            check(game.countEnter("replacement") == 1, "Replacement installs a live native sensor");
            game.engine.removeSystem(game.sensors);
            game.flush();
            check(game.world.getBodyCount() == 2, "Removing MapSensorSystem destroys its bodies only");
            check(game.countExit("replacement") == 1, "System removal clears occupied contacts once");
            game.engine.removeSystem(game.tiled);
            game.tiled.dispose();
            game.tiled.dispose();
            check(game.world.getBodyCount() == 1,
                "Removing then disposing TiledSystem cleans its boundary safely without touching the player");
        }
        try (var game = new Harness()) {
            game.entity(5, 5, true, false, 1);
            game.sensors.setMap(MapTriggers.parse(game.own(map(sensor("pending", 4, 4, 4, 4)))));
            game.step();
            game.sensors.setMap(MapTriggers.parse(game.own(map(sensor("far-away", 20, 20, 2, 2)))));
            game.sensors.update(0);
            game.tick(STEP * 2);
            check(game.received.isEmpty(), "Direct parsed-map replacement discards queued old-body contacts");
            check(game.world.getBodyCount() == 2, "Direct parsed-map replacement does not leak sensor bodies");
            game.sensors.clearMap();
            game.sensors.clearMap();
            game.sensors.dispose();
            check(game.world.getBodyCount() == 1, "Repeated clear/dispose is safe and leaves player body intact");
        }
    }

    private static void lockedMapCleanupIsRejected() {
        try (var game = new Harness()) {
            var map = game.own(map(sensor("locked", 4, 4, 4, 4)));
            var parsed = MapTriggers.parse(map);
            game.sensors.setMap(parsed);
            game.entity(5, 5, true, false, 1);
            int[] rejected = {0};
            game.world.setContactListener(new com.badlogic.gdx.physics.box2d.ContactListener() {
                @Override public void beginContact(com.badlogic.gdx.physics.box2d.Contact contact) {
                    for (Runnable operation : List.<Runnable>of(game.sensors::clearMap, () -> game.sensors.setMap(parsed))) {
                        try { operation.run(); }
                        catch (IllegalStateException expected) { rejected[0]++; }
                    }
                    game.combat.beginContact(contact);
                }
                @Override public void endContact(com.badlogic.gdx.physics.box2d.Contact contact) { game.combat.endContact(contact); }
                @Override public void preSolve(com.badlogic.gdx.physics.box2d.Contact contact, com.badlogic.gdx.physics.box2d.Manifold manifold) { }
                @Override public void postSolve(com.badlogic.gdx.physics.box2d.Contact contact, com.badlogic.gdx.physics.box2d.ContactImpulse impulse) { }
            });
            game.step();
            check(rejected[0] == 2, "Map sensor clear and replacement reject a locked world");
            check(game.world.getBodyCount() == 2, "Rejected locked cleanup leaves both native bodies intact");
            game.sensors.update(0);
            game.flush();
            check(game.countEnter("locked") == 1, "Sensor remains functional after rejected locked cleanup");
        }
    }

    private static void initialPlayerAndDataOnlyMarkers() {
        try (var game = new Harness()) {
            var factory = new FakeFactory(game);
            game.engine.addSystem(factory);
            var area = rectangle("npc-area", "SpawnArea", 30, 30, 3, 3);
            area.getProperties().put("spawnType", "NPC");
            var playerArea = rectangle("player-area", "SpawnArea", 35, 35, 3, 3);
            playerArea.getProperties().put("spawnType", "PLAYER");
            game.load(map(point("start", "PLAYER", 6, 7), point("portal-point", "TELEPORT", 20, 20),
                point("npc-point", "NPC", 25, 25), point("item-point", "ITEM", 28, 28), area, playerArea,
                sensor("spawn-room", 4, 4, 4, 4)));
            check(factory.calls == 1 && game.engine.getEntities().size() == 1,
                "Only PLAYER SpawnPoint creates an entity; other points and all areas stay data-only");
            near(pixels(6), factory.lastPixels.x, "TiledSystem passes unscaled point X pixels to factory");
            near(pixels(7), factory.lastPixels.y, "TiledSystem passes unflipped point Y pixels to factory");
            check(game.tiled.getMapTriggers().points().size() == 4 && game.tiled.getMapTriggers().areas().size() == 2,
                "All data-only markers remain available in parsed map metadata");
            position(factory.lastPlayer, 5, 5.5f, "Initial factory spawn is centered at the PLAYER point");
            game.tick(STEP * 2);
            game.expect(new Event.Sensor.Enter("spawn-room", MapTriggers.SensorType.GENERAL, factory.lastPlayer));
            game.load(map(point("next", "PLAYER", 10, 11)));
            check(factory.calls == 2 && game.engine.getEntities().size() == 1,
                "Map replacement removes the previous map-owned player before spawning the next");
            check(game.world.getBodyCount() == 2, "Map-owned player replacement leaves one player and boundary");
        }
    }

    private static void eventDrivenPlayerSpawn() {
        try (var game = new Harness()) {
            var factory = new FakeFactory(game);
            game.engine.addSystem(factory);
            game.availableMap = game.own(map(point("initial", "PLAYER", 6, 7), sensor("initial-room", 4, 4, 4, 4)));
            game.events.emitEvent(new Event.Map.Load(game.mapAssets));
            game.tick(STEP * 2);
            check(factory.calls == 1 && game.engine.getEntities().size() == 1,
                "Queued Map.Load spawns the player exactly once across Ashley's update boundary");
            game.expect(new Event.Sensor.Enter("initial-room", MapTriggers.SensorType.GENERAL, factory.lastPlayer));
            game.engine.removeEntity(factory.lastPlayer);
            game.tick(STEP);
            game.availableMap = game.own(map(point("replacement", "PLAYER", 10, 11)));
            game.events.emitEvent(new Event.Map.Load(game.mapAssets));
            game.tick(STEP);
            check(factory.calls == 2 && game.world.getBodyCount() == 2,
                "Map.Load after external player removal does not double-destroy map-owned bodies");
        }
    }

    private static void invalidMapLoadCleansPartialBodies() {
        try (var game = new Harness()) {
            var invalid = game.own(map(sensor("partial", 4, 4, 4, 4)));
            invalid.getProperties().put("width", 0);
            rejects(() -> game.tiled.setMap(invalid, game.mapAssets), "Zero-sized map boundaries are rejected");
            check(game.tiled.getCurrentMap() == null && game.world.getBodyCount() == 0,
                "Failed geometry load cleans already-created sensor bodies and resets map ownership");
            game.load(map(sensor("valid", 4, 4, 4, 4)));
            check(game.world.getBodyCount() == 2, "A valid map can load after geometry rejection");
        }
    }

    private static float pixels(float worldUnits) { return worldUnits / GameConsts.UNIT_SCALE; }

    private static TiledMap map(MapObject... objects) {
        var map = new TiledMap();
        map.getProperties().put("orientation", "orthogonal");
        map.getProperties().put("width", 64);
        map.getProperties().put("height", 64);
        map.getProperties().put("tilewidth", 32);
        map.getProperties().put("tileheight", 32);
        var layer = new MapLayer();
        layer.setName("Trigger");
        for (var object : objects) { layer.getObjects().add(object); }
        map.getLayers().add(layer);
        return map;
    }

    private static RectangleMapObject rectangle(String name, String type, float x, float y, float width, float height) {
        var object = new RectangleMapObject(pixels(x), pixels(y), pixels(width), pixels(height));
        object.setName(name);
        object.getProperties().put("type", type);
        return object;
    }

    private static RectangleMapObject sensor(String name, float x, float y, float width, float height) {
        var object = rectangle(name, "Sensor", x, y, width, height);
        object.getProperties().put("sensorType", "GENERAL");
        return object;
    }

    private static RectangleMapObject portal(String name, float x, float y, float width, float height,
                                             String targetMap, String targetSpawn) {
        var object = rectangle(name, "Teleport", x, y, width, height);
        object.getProperties().put("targetMap", targetMap);
        object.getProperties().put("targetSpawn", targetSpawn);
        return object;
    }

    private static PointMapObject point(String name, String type, float x, float y) {
        var object = new PointMapObject(pixels(x), pixels(y));
        object.setName(name);
        object.getProperties().put("type", "SpawnPoint");
        object.getProperties().put("spawnType", type);
        return object;
    }

    private static Body body(Entity entity) { return Physic.MAPPER.get(entity).getBody(); }

    private static void position(Entity player, float x, float y, String message) {
        near(x, body(player).getPosition().x, message + " (body X)");
        near(y, body(player).getPosition().y, message + " (body Y)");
        near(x, Physic.MAPPER.get(player).getPrevPos().x, message + " (previous X)");
        near(y, Physic.MAPPER.get(player).getPrevPos().y, message + " (previous Y)");
        near(x, Transform.MAPPER.get(player).getPosition().x, message + " (transform X)");
        near(y, Transform.MAPPER.get(player).getPosition().y, message + " (transform Y)");
    }

    private static void fixture(Body body, float x, float y, boolean sensor) {
        var shape = new PolygonShape();
        try {
            shape.setAsBox(.12f, .12f, new Vector2(x, y), 0);
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.isSensor = sensor;
            definition.density = 1;
            body.createFixture(definition);
        } finally {
            shape.dispose();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0002f, message + ": expected " + expected + ", got " + actual);
    }

    private static void rejects(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }

    private static Engine bindEngine(Engine engine) {
        try {
            var field = GameCore.class.getDeclaredField("engine");
            field.setAccessible(true);
            var previous = (Engine) field.get(GameCore.INSTANCE);
            field.set(GameCore.INSTANCE, engine);
            return previous;
        } catch (ReflectiveOperationException failure) {
            throw new RuntimeException(failure);
        }
    }

    private static final class FakeFactory extends EntityFactory {
        private final Harness game;
        int calls;
        Vector2 lastPixels;
        Entity lastPlayer;

        FakeFactory(Harness game) {
            super(game.world, game.assets, new InputManager(InputMap.of()));
            this.game = game;
        }

        @Override public Entity getPlayerEntity(Vector2 pixelPosition, MapAssets assets) {
            calls++;
            lastPixels = pixelPosition.cpy();
            var origin = pixelPosition.cpy().scl(GameConsts.UNIT_SCALE).sub(1, 1.5f);
            lastPlayer = game.createEntity(origin.x, origin.y, true, false, 1);
            return lastPlayer;
        }
    }

    private static final class Harness implements AutoCloseable {
        final World world = new World(Vector2.Zero, true);
        final Engine engine = new Engine() {
            @Override public <T extends EntitySystem> T getSystem(Class<T> type) {
                // Ashley indexes exact system classes; expose the factory double under its runtime lookup key.
                if (type == EntityFactory.class) { return type.cast(super.getSystem(FakeFactory.class)); }
                return super.getSystem(type);
            }
        };
        final EventSystem events = new EventSystem();
        final CombatSystem combat = new CombatSystem(world);
        final MapSensorSystem sensors = new MapSensorSystem(world);
        final PhysicSystem physics = new PhysicSystem(world, STEP);
        TiledMap availableMap;
        final AssetService assets = new AssetService(FileHandle::new) {
            @Override public <T> T get(Class<T> type, String id) {
                if (type == TiledMap.class && availableMap != null) { return type.cast(availableMap); }
                throw new AssertionError("Unexpected asset lookup: " + type + " " + id);
            }
            @Override public void unload(Class<?> type, String id) { }
        };
        final TiledSystem tiled = new TiledSystem(assets, world);
        final MapAssets mapAssets = new MapAssets(Asset.Map.of("native-test", "unused.tmx"), "",
            Asset.Atlas.of("unused", "unused.atlas"), Map.of(), Map.of());
        final List<Event.Sensor> received = new ArrayList<>();
        final List<TiledMap> maps = new ArrayList<>();
        final Engine previousEngine;
        int unlockedDeliveries;

        Harness() {
            previousEngine = bindEngine(engine);
            engine.addSystem(events);
            engine.addSystem(combat); // The only native ContactListener installed by this harness.
            engine.addSystem(sensors);
            engine.addSystem(physics);
            engine.addSystem(tiled);
            events.subscribe(Event.Sensor.Enter.class, this::receive);
            events.subscribe(Event.Sensor.Exit.class, this::receive);
            events.update(0);
        }

        private void receive(Event.Sensor event) {
            check(!world.isLocked(), "Sensor event must not be dispatched inside a locked native contact callback");
            // Prove subscribers can perform native mutations, not merely inspect the lock flag.
            var temporary = world.createBody(new BodyDef());
            world.destroyBody(temporary);
            unlockedDeliveries++;
            received.add(event);
        }

        TiledMap own(TiledMap map) { maps.add(map); return map; }
        void load(TiledMap map) { tiled.setMap(own(map), mapAssets); }
        void step() { world.step(STEP, 6, 2); }
        void flush() { events.update(0); }
        void tick(float delta) { engine.update(delta); flush(); }
        void move(Entity player, float x, float y) {
            body(player).setTransform(x, y, 0);
            body(player).setAwake(true);
        }
        long countEnter(String name) {
            return received.stream().filter(event -> event instanceof Event.Sensor.Enter enter && enter.name().equals(name)).count();
        }
        long countExit(String name) {
            return received.stream().filter(event -> event instanceof Event.Sensor.Exit exit && exit.name().equals(name)).count();
        }
        void expect(Event.Sensor... expected) {
            check(received.equals(List.of(expected)), "Typed sensor payloads/order: expected "
                + List.of(expected) + ", got " + received);
        }
        Entity entity(float x, float y, boolean player, boolean sensor, int fixtures) {
            var entity = createEntity(x, y, player, sensor, fixtures);
            engine.addEntity(entity);
            return entity;
        }
        Entity createEntity(float x, float y, boolean player, boolean sensor, int fixtures) {
            var entity = new Entity();
            var definition = new BodyDef();
            definition.type = BodyDef.BodyType.DynamicBody;
            definition.position.set(x, y);
            var body = world.createBody(definition);
            body.setUserData(entity);
            for (int i = 0; i < fixtures; i++) {
                fixture(body, fixtures == 1 ? 1 : .7f + i * .6f, .25f, sensor);
            }
            entity.add(new Physic(body, new Vector2(x, y)))
                .add(new Transform(new Vector2(x, y), 0, new Vector2(2, 3), new Vector2(1, 1), 0, 0))
                .add(new Facing(Facing.Direction.LEFT)).add(new Health(100));
            if (player) { entity.add(new Player()); }
            return entity;
        }

        @Override public void close() {
            try {
                if (engine.getSystem(TiledSystem.class) != null) { engine.removeSystem(tiled); }
                if (engine.getSystem(MapSensorSystem.class) != null) { engine.removeSystem(sensors); }
                engine.removeSystem(combat);
                engine.removeAllEntities();
                engine.removeSystem(physics);
            } finally {
                world.dispose();
                maps.forEach(TiledMap::dispose);
                assets.dispose();
                bindEngine(previousEngine);
            }
        }
    }
}
