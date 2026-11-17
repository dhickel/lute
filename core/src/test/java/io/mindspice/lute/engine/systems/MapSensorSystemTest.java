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
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Box2D;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
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
import io.mindspice.lute.engine.components.Hurtbox;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.data.json.CharacterBounds;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Player;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.BoundsType;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.Offset;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.input.InputMap;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.FixedTmxMapLoader;
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
                if ((method.getName().equals("log") || method.getName().equals("error")
                    || method.getName().equals("debug")) && values != null) {
                    LOGS.add(values[0] + ": " + values[1]);
                }
                return null;
            });
        try {
            var events = new EventSystem();
            var player = new Entity();
            LOGS.clear();
            events.emitEvent(new Event.Sensor.Enter("TestSensor", MapTriggers.SensorType.GENERAL, player));
            events.emitEvent(new Event.Sensor.Exit("TestSensor", MapTriggers.SensorType.GENERAL, player));
            events.update(0);
            check(LOGS.stream().anyMatch(log -> log.startsWith("Sensor: Enter[") && log.contains("TestSensor")),
                "Sensor entry is logged without subscribers");
            check(LOGS.stream().anyMatch(log -> log.startsWith("Sensor: Exit[") && log.contains("TestSensor")),
                "Sensor exit is logged without subscribers");
            nativeContactsAndFixtureDeduplication();
            onlyPlayerPhysicalFixtures();
            characterFeetAndHurtbox();
            characterTeleportAndRemoval();
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
            tiledNativeShapesAndRotation();
            tiledCompoundSeams();
            tiledTeleportShapeUnions();
            tiledTeleportPolygonSkin();
            compoundFixtureDestructionAndCleanup();
            teleportArrivalFixtureDestroyedBeforeContactDiscovery();
            nativeOverlapTolerance();
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

    private static void characterFeetAndHurtbox() {
        float u = GameConsts.UNIT_SCALE;
        try (var game = new Harness()) {
            game.load(map(sensor("character-feet", 5 + 28 * u, 5 + 7 * u, 8 * u, 4 * u),
                sensor("upper-only", 5 + 28 * u, 5 + 36 * u, 8 * u, 8 * u),
                portal("upper-portal", 5 + 28 * u, 5 + 36 * u, 8 * u, 8 * u, "THIS", "unused"),
                point("unused", "TELEPORT", 20, 20)));
            var player = game.character(5, 5);
            game.tick(STEP * 3);
            position(player, 5, 5, "Hurtbox-only portal never teleports the player");
            game.expect(new Event.Sensor.Enter("character-feet", MapTriggers.SensorType.GENERAL, player));
            Fixture physical = null, hurtbox = null;
            for (var fixture : body(player).getFixtureList()) {
                if (fixture.getUserData() == Hurtbox.INSTANCE) { hurtbox = fixture; }
                else { physical = fixture; }
            }
            check(physical != null && hurtbox != null, "Character map test uses both real ellipse fixtures");
            body(player).destroyFixture(physical);
            game.tick(STEP * 3);
            check(game.countExit("character-feet") == 1, "Removing feet exits even while persistent hurtbox overlaps detector");
            check(game.countEnter("upper-only") == 0 && game.countEnter("upper-portal") == 0,
                "Hurtbox never contributes ordinary or teleport map occupancy");
            body(player).destroyFixture(hurtbox);
            game.engine.removeEntity(player);
            game.tick(STEP * 2);
            check(game.received.size() == 2, "Fixture and entity cleanup cannot duplicate map exits");
        }
    }

    private static void characterTeleportAndRemoval() {
        float u = GameConsts.UNIT_SCALE;
        try (var game = new Harness()) {
            game.load(map(portal("feet-departure", 5 + 28 * u, 5 + 7 * u, 8 * u, 4 * u, "THIS", "landing"),
                sensor("landing-feet", 19 + 28 * u, 15 + 7 * u, 8 * u, 4 * u),
                portal("landing-portal", 19 + 28 * u, 15 + 7 * u, 8 * u, 4 * u, "THIS", "return"),
                sensor("landing-upper", 19 + 28 * u, 15 + 36 * u, 8 * u, 8 * u),
                portal("landing-upper-portal", 19 + 28 * u, 15 + 36 * u, 8 * u, 8 * u, "THIS", "return"),
                point("landing", "TELEPORT", 20, 16), point("return", "TELEPORT", 6, 6)));
            var player = game.character(5, 5);
            game.tick(STEP * 3);
            position(player, 19, 15, "Teleport centers the configured 64px logical area, not the 128px canvas");
            check(game.countEnter("feet-departure") == 1 && game.countExit("feet-departure") == 1,
                "Feet portal emits one complete departure visit");
            check(game.countEnter("landing-feet") == 1 && game.countEnter("landing-portal") == 1,
                "Teleport reconciliation sees physical feet at destination");
            check(game.countEnter("landing-upper") == 0 && game.countEnter("landing-upper-portal") == 0,
                "Teleport reconciliation excludes hurtbox-only overlaps");
            game.tick(STEP * 5);
            position(player, 19, 15, "Feet arrival suppression survives native contacts without hurtbox contribution");
            game.engine.removeEntity(player);
            game.tick(STEP * 3);
            check(game.countExit("landing-feet") == 1 && game.countExit("landing-portal") == 1,
                "Removing a teleported character destroys both fixtures and exits once");
            check(game.world.getBodyCount() == 6, "Character removal leaves only five map detectors and boundary");
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
            var attack = new CombatWeapon.Attack("test", 10, 0,
                new Animation2D.Attack("jab", 1, .125f, 0, "attack"), "hit", BoundsType.RECT,
                new CombatWeapon.Bounds(pixels(1), 0, pixels(3), pixels(2),
                    new Offset(0, 0), new Offset(0, 0), new Offset(0, 0), new Offset(0, 0)),
                io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType.MELEE);
            var weapon = new CombatWeapon.Weapon("test", "melee/test", attack, attack);
            var animation = new Animation2D("unused", "unused", Animation2D.AnimationType.IDLE,
                Animation.PlayMode.NORMAL, 1, .125f);
            player.add(new Combat()).add(animation)
                .add(new Loadout(new Loadout.Equipment("basic", "basic", "basic", "basic"), "test",
                    Map.of("test", weapon), equipment -> {}));
            Facing.MAPPER.get(player).setDirection(Facing.Direction.RIGHT);
            Combat.MAPPER.get(player).requestAttack(Combat.AttackSlot.PRIMARY);
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

    private static void tiledNativeShapesAndRotation() {
        for (String type : List.of("Sensor", "Teleport")) {
            // Expected vertices are independent world-space values, not copied from parsed geometry.
            verifyTiledShape(type, "rectangle", "", 8, 4, 0,
                new float[]{10, 16, 18, 16, 18, 20, 10, 20}, 14, 18, 19, 18, 4);
            verifyTiledShape(type, "rotated-rectangle", "", 8, 4, 90,
                new float[]{6, 20, 6, 12, 10, 12, 10, 20}, 8, 16, 14, 18, 4);
            verifyTiledShape(type, "circle", "<ellipse/>", 4, 4, 0,
                null, 12, 18, 10.2f, 16.2f, 0);
            verifyTiledShape(type, "rotated-circle", "<ellipse/>", 4, 4, 90,
                null, 8, 18, 12, 18, 0);
            verifyTiledShape(type, "ellipse", "<ellipse/>", 8, 4, 0,
                null, 14, 18, 17.7f, 19.7f, 8);
            verifyTiledShape(type, "rotated-ellipse", "<ellipse/>", 8, 4, 90,
                new float[]{8, 12, 6.5857863f, 13.171573f, 6, 16, 6.5857863f, 18.828426f,
                    8, 20, 9.414214f, 18.828426f, 10, 16, 9.414214f, 13.171573f},
                8, 16, 14, 18, 8);
            verifyTiledShape(type, "rotated-polygon", polygon(0, 0, 8, 0, 0, 4), 0, 0, 90,
                new float[]{10, 20, 10, 12, 6, 20}, 9, 18, 12, 19, 3);
            verifyTiledShape(type, "concave", concavePolygon(), 0, 0, 0,
                null, 11, 15, 15, 15, -1);
            verifyTiledShape(type, "twelve-vertices", twelveVertexPolygon(), 0, 0, 0,
                null, 14, 16, 10.2f, 12.2f, -1);
        }
    }

    private static void verifyTiledShape(String type, String name, String shape, float width, float height,
                                          float rotation, float[] expectedVertices, float insideX, float insideY,
                                          float outsideX, float outsideY, int vertexCount) {
        try (var game = new Harness()) {
            game.load(xmlMap(detectorXml(name, type, 10, 20, width, height, rotation, shape, "elsewhere", "unused")));
            var detector = detectorBodies(game).getFirst();
            var fixtures = detector.getFixtureList();
            check(detectorBodies(game).size() == 1, type + " " + name + " owns one detector body");
            if (vertexCount < 0) { check(fixtures.size > 1, name + " decomposes into multiple solid fixtures"); }
            else { check(fixtures.size == 1, type + " " + name + " creates exactly one native fixture"); }
            for (var fixture : fixtures) {
                check(fixture.isSensor(), type + " " + name + " is a non-blocking native sensor");
                if (vertexCount == 0) {
                    check(fixture.getShape() instanceof CircleShape, name + " uses native CircleShape");
                    var circle = (CircleShape) fixture.getShape();
                    near(2, circle.getRadius(), name + " native circle radius");
                    var center = detector.getWorldPoint(circle.getPosition());
                    near(insideX, center.x, name + " native circle center X");
                    near(insideY, center.y, name + " native circle center Y");
                } else {
                    check(fixture.getShape() instanceof PolygonShape, name + " is solid PolygonShape, never ChainShape");
                    var polygon = (PolygonShape) fixture.getShape();
                    check(polygon.getVertexCount() >= 3 && polygon.getVertexCount() <= 8,
                        name + " respects native convex vertex limits");
                    if (vertexCount > 0) { check(polygon.getVertexCount() == vertexCount, name + " fixed vertex count"); }
                    if (expectedVertices != null) { expectVertices(fixture, expectedVertices, name); }
                }
            }
            var player = game.shapePlayer(insideX, insideY, true, 1, 0);
            game.tick(STEP * 2);
            check(game.countEnter(name) == 1, type + " " + name + " detects a player wholly inside its filled interior");
            game.move(player, outsideX, outsideY);
            game.tick(STEP * 2);
            check(game.countExit(name) == 1, type + " " + name + " excludes empty notch/corner or unrotated location");
            game.move(player, insideX, insideY);
            game.tick(STEP * 2);
            check(game.countEnter(name) == 2 && game.countExit(name) == 1, type + " " + name + " reenters once");
        }
    }

    private static void tiledCompoundSeams() {
        for (String type : List.of("Sensor", "Teleport")) {
            for (String shape : List.of(concavePolygon(), twelveVertexPolygon())) {
                for (int playerFixtures : new int[]{1, 2}) {
                    try (var game = new Harness()) {
                        game.load(xmlMap(detectorXml("compound", type, 10, 20, 0, 0, 0,
                            shape, "elsewhere", "unused")));
                        var detector = detectorBodies(game).getFirst();
                        var seam = internalSeam(detector);
                        var player = game.shapePlayer(seam[0].x, seam[0].y, true, playerFixtures, 0);
                        game.tick(STEP * 2);
                        check(touchingDetectorFixtures(game, detector, player) >= 2,
                            "A single physical player fixture really straddles two native detector fixtures");
                        check(game.countEnter("compound") == 1, "All detector/player fixture pairs form one logical entry");
                        game.move(player, seam[1].x, seam[1].y);
                        game.tick(STEP * 2);
                        check(touchingDetectorFixtures(game, detector, player) == 1,
                            "Moving off the seam loses one detector contact while retaining another");
                        check(game.countExit("compound") == 0, "Losing one detector pair cannot exit the logical union");
                        // Cross directly between disjoint fixture interiors in one fixed tick.
                        game.move(player, seam[2].x, seam[2].y);
                        game.tick(STEP);
                        check(touchingDetectorFixtures(game, detector, player) == 1,
                            "Same-tick seam crossing finishes in the other fixture interior");
                        check(game.countEnter("compound") == 1 && game.countExit("compound") == 0,
                            "Batched native end/begin across a seam does not exit/reenter");
                        game.move(player, 30, 30);
                        game.tick(STEP * 2);
                        check(game.countExit("compound") == 1, "Leaving every compound fixture exits exactly once");
                        game.move(player, seam[0].x, seam[0].y);
                        game.tick(STEP * 2);
                        check(game.countEnter("compound") == 2, "Compound reentry creates one new visit");
                    }
                }
            }
        }
    }

    private static void tiledTeleportShapeUnions() {
        for (boolean circlePlayer : new boolean[]{false, true}) {
            for (boolean landInside : new boolean[]{false, true}) {
                for (String kind : List.of("circle", "ellipse", "polygon", "compound", "many-vertices")) {
                    try (var game = new Harness()) {
                        String shape = switch (kind) {
                            case "circle", "ellipse" -> "<ellipse/>";
                            case "polygon" -> polygon(0, 0, 8, 0, 0, 8);
                            case "many-vertices" -> twelveVertexPolygon();
                            default -> concavePolygon();
                        };
                        float width = kind.equals("circle") || kind.equals("ellipse") ? 8 : 0;
                        float height = kind.equals("circle") ? 8 : kind.equals("ellipse") ? 4 : 0;
                        var inside = switch (kind) {
                            case "circle", "many-vertices" -> new Vector2(24, 26);
                            case "ellipse" -> new Vector2(24, 28);
                            case "polygon" -> new Vector2(22, 28);
                            default -> new Vector2(21, 25);
                        };
                        var outside = switch (kind) {
                            case "circle", "many-vertices" -> new Vector2(20.3f, 22.3f);
                            case "ellipse" -> new Vector2(20.3f, 26.3f);
                            case "polygon" -> new Vector2(26, 24);
                            default -> new Vector2(25, 25);
                        };
                        var landing = landInside ? inside : outside;
                        String departureShape = kind.equals("many-vertices") ? twelveVertexPolygon() : concavePolygon();
                        game.load(xmlMap(
                            detectorXml("departure-room", "Sensor", 2, 10, 0, 0, 0, departureShape, null, null),
                            detectorXml("departure", "Teleport", 2, 10, 0, 0, 0, departureShape, "THIS", "landing"),
                            detectorXml("arrival-room", "Sensor", 20, 30, width, height, 0, shape, null, null),
                            detectorXml("arrival", "Teleport", 20, 30, width, height, 0, shape, "THIS", "return"),
                            spawnXml("landing", landing.x + 1, landing.y + 1.5f), spawnXml("return", 4, 7.5f)));
                        var player = game.shapePlayer(3, 6, circlePlayer, 2, .7f);
                        var original = body(player);
                        game.tick(STEP * 2);
                        position(player, landing.x, landing.y, kind + " exact shape-union arrival");
                        check(body(player) == original, "Shape teleport retains the native player body");
                        near(.7f, original.getAngle(), "Teleport preserves a rotated physical player's angle");
                        check(game.countEnter("departure-room") == 1 && game.countExit("departure-room") == 1,
                            "Teleport out of a compound preserves generic enter/exit exactly once");
                        check(game.countEnter("departure") == 1 && game.countExit("departure") == 1,
                            "Teleport out of a compound retires every old fixture pair");
                        check(game.countEnter("arrival-room") == (landInside ? 1 : 0)
                            && game.countEnter("arrival") == (landInside ? 1 : 0),
                            kind + " reconciliation uses actual fixture union, not its AABB");
                        game.tick(STEP * 8);
                        position(player, landing.x, landing.y, kind + " arrival does not bounce or use stale source contacts");
                        check(game.countExit("arrival") == 0, "Suppressed native arrivals remain one continuous visit");
                        if (landInside) {
                            game.move(player, 35, 35);
                            game.tick(STEP * 2);
                            check(game.countExit("arrival") == 1 && game.countExit("arrival-room") == 1,
                                "Genuine shape exit clears arrival suppression and generic occupancy");
                        }
                        game.move(player, inside.x, inside.y);
                        game.tick(STEP * 2);
                        position(player, 3, 6, kind + " arrival activates on genuine entry/reentry");
                        check(game.countEnter("arrival") == (landInside ? 2 : 1)
                            && game.countExit("arrival") == (landInside ? 2 : 1),
                            "Arrival activation emits one typed enter/exit for each actual visit");
                        check(game.countEnter("arrival-room") == (landInside ? 2 : 1)
                            && game.countExit("arrival-room") == (landInside ? 2 : 1),
                            "Generic events survive both shape arrival and departure");
                        game.tick(STEP * 5);
                        position(player, 3, 6, "Compound return arrival stays suppressed until a real exit");
                        check(game.countEnter("departure-room") == 2 && game.countExit("departure-room") == 1,
                            "Returning to compound immediately restores generic occupancy without duplicates");
                    }
                }
            }
        }
    }

    private static void tiledTeleportPolygonSkin() {
        for (boolean circlePlayer : new boolean[]{false, true}) {
            try (var game = new Harness()) {
                // The player outline is just outside the rectangle, but within native polygon skin.
                float landingX = circlePlayer ? 28.085f : 28.135f;
                game.load(xmlMap(
                    detectorXml("departure", "Teleport", 2, 10, 4, 8, 0, "", "THIS", "landing"),
                    detectorXml("arrival-room", "Sensor", 20, 30, 8, 8, 0, "", null, null),
                    detectorXml("arrival", "Teleport", 20, 30, 8, 8, 0, "", "THIS", "return"),
                    spawnXml("landing", landingX + 1, 27.5f), spawnXml("return", 4, 7.5f)));
                var player = game.shapePlayer(3, 6, circlePlayer, 1, 0);
                game.tick(STEP * 2);
                position(player, landingX, 26, "Polygon-skin arrival is reconciled without bouncing");
                check(game.countEnter("arrival-room") == 1 && game.countEnter("arrival") == 1,
                    "Immediate arrival overlap includes native polygon skin for circle and polygon players");
                game.tick(STEP * 5);
                check(game.countEnter("arrival-room") == 1 && game.countExit("arrival-room") == 0
                    && game.countEnter("arrival") == 1 && game.countExit("arrival") == 0,
                    "Native skin contacts match reconciled arrivals without duplicate events");
                position(player, landingX, 26, "Skin-only arrival stays suppressed across native steps");
                game.move(player, landingX + .1f, 26);
                game.tick(STEP * 2);
                check(game.countExit("arrival") == 1 && game.countExit("arrival-room") == 1,
                    "Moving beyond polygon skin genuinely exits both arrival detectors");
                game.move(player, landingX, 26);
                game.tick(STEP * 2);
                position(player, 3, 6, "Reentering only the native polygon skin activates the portal");
            }
        }
    }

    private static void compoundFixtureDestructionAndCleanup() {
        for (boolean drainFirst : new boolean[]{false, true}) {
            try (var game = new Harness()) {
                game.load(xmlMap(detectorXml("compound", "Sensor", 10, 20, 0, 0, 0,
                    concavePolygon(), null, null)));
                var detector = detectorBodies(game).getFirst();
                var seam = internalSeam(detector)[0];
                var player = game.shapePlayer(seam.x, seam.y, true, 2, 0);
                game.step();
                if (drainFirst) { game.sensors.update(0); game.flush(); }
                var body = body(player);
                body.destroyFixture(body.getFixtureList().first());
                game.sensors.update(0);
                game.flush();
                check(game.countEnter("compound") == 1 && game.countExit("compound") == 0,
                    "Destroyed player fixture cannot invalidate the surviving compound contacts");
                body.destroyFixture(body.getFixtureList().first());
                game.sensors.update(0);
                game.flush();
                check(game.countExit("compound") == 1, "Destroying the last physical fixture exits compound once");
                circleFixture(body, 0, 0, .08f);
                body.setAwake(true);
                game.tick(STEP * 2);
                check(game.countEnter("compound") == 2, "New physical fixture can reenter after destruction");
                int before = game.world.getBodyCount();
                game.sensors.clearMap();
                game.sensors.clearMap();
                game.flush();
                check(game.world.getBodyCount() == before - 1, "Compound cleanup destroys one body and all of its fixtures");
                check(game.countExit("compound") == 2, "Repeated compound cleanup emits only one final exit");
                game.tick(STEP * 2);
                check(game.countEnter("compound") == 2 && game.countExit("compound") == 2,
                    "Destroyed compound callbacks do not resurrect occupancy");
            }
        }
    }

    private static void teleportArrivalFixtureDestroyedBeforeContactDiscovery() {
        for (boolean reuseBeforeDrain : new boolean[]{false, true}) {
            try (var game = new Harness()) {
                game.load(xmlMap(
                    detectorXml("departure", "Teleport", 2, 10, 0, 0, 0, concavePolygon(), "THIS", "landing"),
                    detectorXml("arrival-room", "Sensor", 20, 30, 0, 0, 0, concavePolygon(), null, null),
                    detectorXml("arrival", "Teleport", 20, 30, 0, 0, 0, concavePolygon(), "THIS", "return"),
                    spawnXml("landing", 22, 26.5f), spawnXml("return", 4, 7.5f)));
                var player = game.shapePlayer(3, 6, true, 1, 0);
                game.step();
                game.sensors.update(0);
                game.flush();
                position(player, 21, 25, "Single source step reconciles a compound arrival before native discovery");
                check(game.countEnter("arrival") == 1 && game.countEnter("arrival-room") == 1,
                    "Compound arrival immediately enters portal and generic sensor");
                var body = body(player);
                body.destroyFixture(body.getFixtureList().first());
                if (reuseBeforeDrain) {
                    circleFixture(body, 0, 0, .08f);
                    game.move(player, 35, 35);
                }
                // No native destination contact ever existed, hence no native destination end callback.
                game.sensors.update(0);
                game.flush();
                check(game.countExit("arrival") == 1 && game.countExit("arrival-room") == 1,
                    "Destroying an undiscovered arrival fixture retires occupancy without an end callback");
                if (!reuseBeforeDrain) { circleFixture(body, 0, 0, .08f); }
                game.move(player, 35, 35);
                game.tick(STEP * 2);
                game.move(player, 21, 25);
                game.tick(STEP * 2);
                position(player, 3, 6, "Replacement physical fixture reenters and activates the arrival portal");
                check(game.countEnter("arrival") == 2 && game.countExit("arrival") == 2
                    && game.countEnter("arrival-room") == 2 && game.countExit("arrival-room") == 2,
                    "Replacement fixtures, including pooled wrappers, preserve exactly one new logical visit");
            }
        }
    }

    private static void nativeOverlapTolerance() {
        for (String type : List.of("Sensor", "Teleport")) {
            try (var game = new Harness()) {
                game.load(xmlMap(detectorXml("tangent", type, 9, 11, 2, 2, 0, "<ellipse/>", "elsewhere", "unused")));
                var player = game.shapePlayer(Math.nextUp(12f), 10, true, 0, 0);
                circleFixture(body(player), 0, 0, 1);
                game.step();
                check(touchingDetectorFixtures(game, detectorBodies(game).getFirst(), player) == 1,
                    "Native sensor sees the tiny positive distance beyond summed circle radii");
                game.sensors.update(0);
                game.flush();
                check(game.countEnter("tangent") == 1, "Java occupancy accepts native overlap tolerance");
                game.move(player, 11.5f, 10);
                game.tick(STEP * 2);
                check(game.countEnter("tangent") == 1 && game.countExit("tangent") == 0,
                    "Moving inward without a replacement native begin retains one logical entry");
                game.move(player, 14, 10);
                game.tick(STEP * 2);
                check(game.countExit("tangent") == 1, "Moving beyond native tolerance exits once");
            }
        }
    }

    private static List<Body> detectorBodies(Harness game) {
        var bodies = new Array<Body>();
        game.world.getBodies(bodies);
        var result = new ArrayList<Body>();
        for (var body : bodies) {
            if (body.getType() == BodyDef.BodyType.StaticBody && body.getFixtureList().size > 0
                && body.getFixtureList().first().isSensor()) { result.add(body); }
        }
        return result;
    }

    private static float[] vertices(Fixture fixture) {
        var shape = (PolygonShape) fixture.getShape();
        var result = new float[shape.getVertexCount() * 2];
        var point = new Vector2();
        for (int i = 0; i < shape.getVertexCount(); i++) {
            shape.getVertex(i, point);
            point.set(fixture.getBody().getWorldPoint(point));
            result[i * 2] = point.x;
            result[i * 2 + 1] = point.y;
        }
        return result;
    }

    private static void expectVertices(Fixture fixture, float[] expected, String label) {
        var actual = vertices(fixture);
        check(actual.length == expected.length, label + " expected native vertex count");
        for (int i = 0; i < expected.length; i += 2) {
            boolean found = false;
            for (int j = 0; j < actual.length; j += 2) {
                if (Math.abs(expected[i] - actual[j]) < .0002f && Math.abs(expected[i + 1] - actual[j + 1]) < .0002f) {
                    found = true;
                    break;
                }
            }
            check(found, label + " contains expected rotated world vertex " + expected[i] + "," + expected[i + 1]);
        }
    }

    private static Vector2[] internalSeam(Body detector) {
        // Find a shared edge with room on both sides; decomposition order is deliberately not contractual.
        for (var first : detector.getFixtureList()) {
            var a = vertices(first);
            for (var second : detector.getFixtureList()) {
                if (first == second) { continue; }
                var b = vertices(second);
                for (int i = 0; i < a.length; i += 2) {
                    int nextA = (i + 2) % a.length;
                    for (int j = 0; j < b.length; j += 2) {
                        int nextB = (j + 2) % b.length;
                        if (Vector2.dst2(a[i], a[i + 1], b[nextB], b[nextB + 1]) > .000001f
                            || Vector2.dst2(a[nextA], a[nextA + 1], b[j], b[j + 1]) > .000001f) { continue; }
                        var middle = new Vector2((a[i] + a[nextA]) / 2, (a[i + 1] + a[nextA + 1]) / 2);
                        var normal = new Vector2(a[nextA] - a[i], a[nextA + 1] - a[i + 1]).nor().rotate90(1).scl(.3f);
                        var sideA = middle.cpy().add(normal);
                        var sideB = middle.cpy().sub(normal);
                        if (Intersector.isPointInPolygon(a, 0, a.length, sideA.x, sideA.y)
                            && Intersector.isPointInPolygon(b, 0, b.length, sideB.x, sideB.y)) {
                            return new Vector2[]{middle, sideA, sideB};
                        }
                    }
                }
            }
        }
        throw new AssertionError("Compound fixture geometry must contain an internal shared edge");
    }

    private static int touchingDetectorFixtures(Harness game, Body detector, Entity player) {
        var touching = new java.util.HashSet<Fixture>();
        for (var contact : game.world.getContactList()) {
            if (!contact.isTouching()) { continue; }
            if (contact.getFixtureA().getBody() == detector && contact.getFixtureB().getBody() == body(player)) {
                touching.add(contact.getFixtureA());
            } else if (contact.getFixtureB().getBody() == detector && contact.getFixtureA().getBody() == body(player)) {
                touching.add(contact.getFixtureB());
            }
        }
        return touching.size();
    }

    private static String polygon(float... worldOffsets) {
        var points = new StringBuilder();
        for (int i = 0; i < worldOffsets.length; i += 2) {
            if (i > 0) { points.append(' '); }
            points.append(pixels(worldOffsets[i])).append(',').append(pixels(worldOffsets[i + 1]));
        }
        return "<polygon points=\"" + points + "\"/>";
    }

    private static String concavePolygon() { return polygon(0, 0, 8, 0, 8, 2, 2, 2, 2, 8, 0, 8); }

    private static String twelveVertexPolygon() {
        return polygon(8, 4, 7.464102f, 6, 6, 7.464102f, 4, 8, 2, 7.464102f, .535898f, 6,
            0, 4, .535898f, 2, 2, .535898f, 4, 0, 6, .535898f, 7.464102f, 2);
    }

    private static String detectorXml(String name, String type, float anchorX, float anchorY,
                                      float width, float height, float rotation, String shape,
                                      String targetMap, String targetSpawn) {
        String properties = type.equals("Teleport")
            ? "<property name=\"targetMap\" value=\"" + targetMap + "\"/><property name=\"targetSpawn\" value=\"" + targetSpawn + "\"/>"
            : "<property name=\"sensorType\" value=\"GENERAL\"/>";
        return "<object name=\"" + name + "\" type=\"" + type + "\" x=\"" + pixels(anchorX)
            + "\" y=\"" + (2048 - pixels(anchorY)) + "\" width=\"" + pixels(width) + "\" height=\"" + pixels(height)
            + "\" rotation=\"" + rotation + "\"><properties>" + properties + "</properties>" + shape + "</object>";
    }

    private static String spawnXml(String name, float x, float y) {
        return "<object name=\"" + name + "\" type=\"SpawnPoint\" x=\"" + pixels(x) + "\" y=\"" + (2048 - pixels(y))
            + "\"><properties><property name=\"spawnType\" value=\"TELEPORT\"/></properties><point/></object>";
    }

    private static TiledMap xmlMap(String... objects) { return new XmlLoader().loadObjects(String.join("", objects)); }

    private static final class XmlLoader extends FixedTmxMapLoader {
        XmlLoader() { super(FileHandle::new); }

        TiledMap loadObjects(String objects) {
            root = xml.parse("<map orientation=\"orthogonal\" width=\"64\" height=\"64\" tilewidth=\"32\" tileheight=\"32\">"
                + "<objectgroup name=\"Triggers\">" + objects + "</objectgroup></map>");
            return loadTiledMap(new FileHandle("native-sensor-test.tmx"), new BaseTiledMapLoader.Parameters(),
                path -> { throw new AssertionError("Geometry-only XML must not request textures: " + path); });
        }
    }

    private static void circleFixture(Body body, float x, float y, float radius) {
        var shape = new CircleShape();
        try {
            shape.setPosition(new Vector2(x, y));
            shape.setRadius(radius);
            var definition = new FixtureDef();
            definition.shape = shape;
            definition.density = 1;
            body.createFixture(definition);
        } finally { shape.dispose(); }
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
        Entity character(float x, float y) {
            var entity = createEntity(x, y, true, false, 0);
            entity.add(new Transform(new Vector2(x, y), 0,
                new Vector2(64, 64).scl(GameConsts.UNIT_SCALE), new Vector2(1, 1), 0, 0));
            CharacterFixtures.ellipse(body(entity), new CharacterBounds(20, 4, 44, 14), false);
            CharacterFixtures.ellipse(body(entity), new CharacterBounds(16, 6, 48, 56), true).setUserData(Hurtbox.INSTANCE);
            engine.addEntity(entity);
            return entity;
        }
        Entity shapePlayer(float x, float y, boolean circle, int fixtures, float angle) {
            var entity = createEntity(x, y, true, false, 0);
            var body = body(entity);
            for (int i = 0; i < fixtures; i++) {
                if (circle) { circleFixture(body, 0, i * .02f, .08f); }
                else {
                    var shape = new PolygonShape();
                    try {
                        shape.setAsBox(.12f, .06f, new Vector2(0, i * .02f), 0);
                        var definition = new FixtureDef();
                        definition.shape = shape;
                        definition.density = 1;
                        body.createFixture(definition);
                    } finally { shape.dispose(); }
                }
            }
            body.setTransform(x, y, angle);
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
