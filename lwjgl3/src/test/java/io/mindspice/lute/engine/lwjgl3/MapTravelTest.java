package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationLogger;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.assets.ObjectAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.data.json.DataLoader;
import io.mindspice.lute.engine.data.json.GameData;
import io.mindspice.lute.engine.data.json.ObjectData;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.AwarenessSystem;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.systems.CombatSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.systems.EntityFactory;
import io.mindspice.lute.engine.systems.PhysicDebugRenderSystem;
import io.mindspice.lute.engine.systems.TriggerDebugRenderSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.systems.WorldTimeSystem;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.tiled.ManifestLoader;
import io.mindspice.lute.engine.tiled.MapTriggers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.mindspice.lute.engine.lwjgl3.GameScreenLifecycleTest.night;
import static io.mindspice.lute.engine.lwjgl3.GameScreenLifecycleTest.seconds;

public final class MapTravelTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static final String A = "travel_test_a", B = "travel_test_b";
    private static Throwable failure;
    private static int checks;
    private boolean initialized;
    private GameScreen screen;
    private TiledSystem tiled;
    private World world;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Map travel regression");
        // Resolve the 12-pixel authored light radius with enough framebuffer samples to test its pulse.
        config.setWindowedMode(640, 480);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new MapTravelTest(), config);
        if (failure != null) { throw new AssertionError("Map travel regression failed", failure); }
        System.out.println("MapTravelTest: " + checks + " real GL and Box2D checks passed");
    }

    @Override public void create() {
        try { runTests(); }
        catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private static void checkSensor(Entity entity, Actor.Role role, float pixels) {
        var actor = Actor.MAPPER.get(entity);
        var body = body(entity);
        require(actor.role() == role && body.getType() == BodyDef.BodyType.DynamicBody,
            "Map actor has its authored role and a dynamic body");
        for (var fixture : body.getFixtureList()) {
            if (fixture.isSensor() && fixture.getShape() instanceof CircleShape circle) {
                require(fixture.getFilterData().maskBits == 0 && circle.getRadius() == pixels * GameConsts.UNIT_SCALE
                    && circle.getPosition().equals(new Vector2(actor.feetX(), actor.feetY())),
                    "Attached circle uses the authored pixel radius and physical feet center without contacts");
                return;
            }
        }
        throw new AssertionError("Missing attached awareness circle for " + role);
    }

    private void runTests() throws ReflectiveOperationException {
        GAME.create();
        initialized = true;
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.app.setLogLevel(Application.LOG_ERROR);
        var screens = (ScreenManager) field(GameCore.class, "screenManager").get(GAME);
        var loaded = (Map<?, ?>) field(ScreenManager.class, "loadedScreens").get(screens);
        bind((GameScreen) loaded.get("GameScreen"));
        for (int i = 0; i < 200 && tiled.getCurrentMap() == null; i++) { GAME.render(); }
        require(tiled.getCurrentMap() != null, "Boot finishes loading the actual map");
        var time = GAME.getEngine().getSystem(WorldTimeSystem.class);
        checkCampfire();
        var player = player();
        atPoint(player, "PlayerSpawn");
        var actualActors = GAME.getEngine().getEntitiesFor(Family.all(CompositeCharacter.class).exclude(Player.class).get());
        require(actualActors.size() == 2, "Actual startup map owns both actors");
        var oldNpc = actualActors.first();
        var oldEnemy = actualActors.get(1);
        require(Facing.MAPPER.get(player).getDirection() == Facing.Direction.DOWN
            && Facing.MAPPER.get(oldNpc).getDirection() == Facing.Direction.LEFT
            && Animation2D.MAPPER.get(oldNpc).getFdirection() == Facing.Direction.LEFT,
            "Player inherits the Tiled facing and the NPC override initializes both facing and animation");
        var awareness = GAME.getEngine().getSystem(AwarenessSystem.class);
        var gameData = GAME.getGameData();
        var npc = gameData.actorsData().npc().get("basic_npc");
        var enemy = gameData.actorsData().enemies().get("brainless_undead");
        checkSensor(oldNpc, Actor.Role.NPC, npc.detectionBounds().radius());
        checkSensor(oldEnemy, Actor.Role.ENEMY, enemy.detectionBounds().radius());
        require(Health.MAPPER.get(oldNpc).getMaximum() == npc.maxHealth()
            && Health.MAPPER.get(oldEnemy).getMaximum() == enemy.maxHealth(), "Map actors use authored health");
        require(Actor.MAPPER.get(player).role() == Actor.Role.PLAYER && body(player).getFixtureList().size == 2,
            "Player is detectable without owning a third fixture");
        require(ActorAi.MAPPER.get(oldNpc).graphKey().equals(npc.decisionGraph())
            && ActorAi.MAPPER.get(oldEnemy).graphKey().equals(enemy.decisionGraph()), "Spawned actors use their authored graph keys");
        verifyActorAi(oldNpc, oldEnemy, player, awareness);
        var oldNpcBody = body(oldNpc);
        var oldEnemyBody = body(oldEnemy);
        var oldNpcHealth = Health.MAPPER.get(oldNpc);
        var oldEnemyHealth = Health.MAPPER.get(oldEnemy);
        oldNpcHealth.damage(1);
        oldEnemyHealth.damage(1);
        var initialMap = tiled.getCurrentMap();
        var initialBody = body(player);
        var initialTriggers = tiled.getMapTriggers();
        var oldObjects = new ArrayList<Entity>();
        var entities = GAME.getEngine().getEntities();
        entities.forEach(entity -> { if (entity != player) { oldObjects.add(entity); } });
        int initialBodies = world.getBodyCount();
        var actual = tiled.getMapTriggers().sensors().stream().filter(sensor -> sensor.name().equals("Teleport")).findFirst().orElseThrow();
        enter("Teleport");
        if (actual.targetMap().equals("test_tiled")) {
            require(player() != player && body(player()) != initialBody && tiled.getCurrentMap() == initialMap,
                "Actual sample explicit ID rebuilds player and physics, reusing loaded asset data");
            require(tiled.getMapTriggers() != initialTriggers && !oldObjects.isEmpty()
                && oldObjects.stream().noneMatch(entity -> entities.contains(entity, true))
                && entities.size() == oldObjects.size() + 1 && world.getBodyCount() == initialBodies,
                "Actual map rebuilds triggers, environmental entities and bodies");
            require(actualActors.size() == 2 && !actualActors.contains(oldNpc, true) && !actualActors.contains(oldEnemy, true),
                "Actual map replacement removes both old actors and recreates both");
            require(actualActors.first() != actualActors.get(1)
                && Loadout.MAPPER.get(actualActors.first()) != Loadout.MAPPER.get(oldNpc)
                && Loadout.MAPPER.get(actualActors.get(1)) != Loadout.MAPPER.get(oldEnemy),
                "Actor loadouts are fresh rather than session-owned");
            var newNpc = actualActors.first();
            var newEnemy = actualActors.get(1);
            require(body(newNpc) != oldNpcBody && body(newNpc).getFixtureList().size == 3
                && Health.MAPPER.get(newNpc) != oldNpcHealth && Health.MAPPER.get(newNpc).getCurrent() == 100f,
                "Map replacement recreates the NPC body, fixtures and health");
            require(body(newEnemy) != oldEnemyBody && body(newEnemy).getFixtureList().size == 3
                && Health.MAPPER.get(newEnemy) != oldEnemyHealth && Health.MAPPER.get(newEnemy).getCurrent() == 100f,
                "Map replacement recreates the enemy body, fixtures and health");
            require(awareness.inRange(oldEnemy, Actor.Role.PLAYER).isEmpty(), "Retired enemy cannot query after travel");
            checkSensor(newNpc, Actor.Role.NPC, npc.detectionBounds().radius());
            checkSensor(newEnemy, Actor.Role.ENEMY, enemy.detectionBounds().radius());
            atPoint(player(), actual.targetSpawn());
            System.out.println("MapTravelTest: actual test_tiled/" + actual.targetSpawn() + " rebuilt");
        } else {
            require("THIS".equalsIgnoreCase(actual.targetMap()) && player() == player && body(player()) == initialBody,
                "Legacy sample THIS remains in-place");
            atPoint(player(), actual.targetSpawn());
        }
        screens.unloadScreen("GameScreen");
        require(actualActors.size() == 0, "Disposing the actual map removes both actors");
        require(!GAME.getAssetService().isLoaded(TiledMap.class, "test_tiled"), "Disposal releases the starting map");
        require(GAME.getAssetService().isLoaded(TextureAtlas.class, ObjectAssets.OBJECTS.id()),
            "Removing the map does not unload the shared objects atlas");

        installLightFixtures();
        var entry = ManifestLoader.parseMapManifest(A);
        var startupAmbient = GameConsts.NIGHT_AMBIENT;
        var startupLayers = GameConsts.DEBUG_LAYERS;
        try {
            GameConsts.NIGHT_AMBIENT = new GameConsts.NightAmbient("#0000FF", .25f);
            // Inspect world pixels without diagnostic overlays on the synthetic travel maps.
            GameConsts.DEBUG_LAYERS = new GameConsts.DebugLayers(false, false, false, false);
            bind(new GameScreen(entry));
        } finally {
            GameConsts.NIGHT_AMBIENT = startupAmbient;
            GameConsts.DEBUG_LAYERS = startupLayers;
        }
        require(GAME.getEngine().getSystem(PhysicDebugRenderSystem.class) == null
            && GAME.getEngine().getSystem(TriggerDebugRenderSystem.class) == null, "Disabled debug systems are not installed");
        screens.loadScreen("travel", screen);
        screens.switchScreens("travel");
        time = GAME.getEngine().getSystem(WorldTimeSystem.class);
        var changes = new ArrayList<String>();
        GAME.getEngine().getSystem(EventSystem.class).subscribe(Event.Map.Changed.class, event -> changes.add(event.mapId()));
        atPoint(player(), "PlayerSpawn");
        frame(); // Boot inside the self portal; the rebuilt entity must not trigger it again.
        var start = player();
        atPoint(start, "PlayerSpawn");
        for (int i = 0; i < 5; i++) { frame(); }
        require(player() == start && changes.equals(List.of(A, A)), "Only boot and one explicit reload at arrival");

        checkLightMount(false);
        var sourceLights = lights();
        var sourceFixtures = fixtures();
        var redCenter = center("red");
        int daylight = pixel(redCenter);
        require((daylight >>> 8) == 0xffffff, "White static art is unmodified in the initial daylight frame");
        Gdx.input.getInputProcessor().keyDown(Input.Keys.N);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.N);
        screen.render(0);
        checkRedLight(redCenter);
        int lit = pixel(redCenter);

        var health = Health.MAPPER.get(start);
        health.damage(23);
        var facing = Facing.MAPPER.get(start);
        facing.setDirection(Facing.Direction.LEFT);
        var loadout = Loadout.MAPPER.get(start);
        loadout.setWeapon("basic_bow");
        var head = GAME.getAssetService().get(TextureAtlas.class, CharacterAssets.HEAD.id());
        for (var region : toList(head)) {
            if (region.name.startsWith("basic/")) {
                head.addRegion(region.name.replaceFirst("basic/", "travel-test/"), region).index = region.index;
            }
        }
        var equipment = new Loadout.Equipment("travel-test", "basic", "basic", "basic");
        loadout.setEquipment(equipment);

        var sourceMap = tiled.getCurrentMap();
        int sourceBodies = world.getBodyCount();
        exitPortals();
        var input = Gdx.input.getInputProcessor();
        input.keyDown(Input.Keys.SPACE); screen.render(0f);
        input.keyUp(Input.Keys.SPACE); screen.render(0f);
        input.keyDown(Input.Keys.SPACE); input.keyDown(Input.Keys.W); screen.render(0f);
        require(!BowAim.MAPPER.get(start).isAiming() && arrowCount() == 1
            && Combat.MAPPER.get(start).isCoolingDown("basic_bow", Combat.AttackSlot.PRIMARY),
            "Depart with held inputs, a live arrow and an active cooldown; immediate redraw is blocked");
        var oldBody = body(start);
        var beforeSelfTravel = time.getTotalInGameSeconds();
        enterNamed("self");
        var rebuilt = player();
        require(java.util.Collections.disjoint(sourceLights, lights()) && java.util.Collections.disjoint(sourceFixtures, fixtures()),
            "No previous emitters or fixtures in the first frame of a named same-ID rebuild");
        checkRedLight(redCenter);
        sourceLights = lights();
        sourceFixtures = fixtures();
        require(GAME.getEngine().getSystem(WorldTimeSystem.class) == time
            && time.getTotalInGameSeconds() > beforeSelfTravel,
            "Named travel retains the session world-time system and accumulated time");
        require(rebuilt != start && body(rebuilt) != oldBody && tiled.getCurrentMap() == sourceMap,
            "Reentry reloads the same logical map and creates fresh runtime entities and bodies");
        require(world.getBodyCount() == sourceBodies, "Self reload leaves no extra bodies");
        atPoint(rebuilt, "PlayerSpawn");
        require(Health.MAPPER.get(rebuilt) == health && health.getCurrent() == health.getMaximum() - 23,
            "Health is the same live component with unchanged current and maximum");
        require(Facing.MAPPER.get(rebuilt) == facing && facing.getDirection() == Facing.Direction.LEFT, "Facing survives");
        require(Loadout.MAPPER.get(rebuilt) == loadout && loadout.getEquipment().equals(equipment)
            && loadout.getWeaponId().equals("basic_bow"), "Live loadout preserves equipped gear and weapon");
        require(arrowCount() == 0 && !BowAim.MAPPER.get(rebuilt).isAiming(), "Travel discards arrows and aiming");
        require(!Combat.MAPPER.get(rebuilt).isAttacking() && !Combat.MAPPER.get(rebuilt).hasAttackRequest()
            && !Combat.MAPPER.get(rebuilt).isCoolingDown("basic_bow", Combat.AttackSlot.PRIMARY),
            "Fresh combat state includes a cleared cooldown");
        input.keyDown(Input.Keys.W); input.keyDown(Input.Keys.SPACE); frame();
        input.keyUp(Input.Keys.W); input.keyUp(Input.Keys.SPACE); frame();
        atPoint(rebuilt, "PlayerSpawn");
        require(body(rebuilt).getLinearVelocity().isZero() && Controller.MAPPER.get(rebuilt).getHeldDirection().isZero()
            && !BowAim.MAPPER.get(rebuilt).isAiming() && arrowCount() == 0, "Held input and release do not cross arrival");

        for (String invalid : List.of("unknown", "bad-area", "bad-npc", "bad-item", "bad-missing")) {
            enter(invalid);
            frame(); frame();
            require(player() == rebuilt && tiled.getCurrentMap() == sourceMap && world.getBodyCount() == sourceBodies,
                "Invalid destination preserves the live map and player: " + invalid);
            require(!GAME.getAssetService().isLoaded(TiledMap.class, B)
                && !GAME.getAssetService().isLoaded(TextureAtlas.class, B) && !GAME.getAssetService().isLoaded(Sound.class, "TRAVEL_B"),
                "Invalid destination releases the candidate assets: " + invalid);
        }
        verifyStagedFailure(sourceMap, rebuilt, sourceBodies, redCenter);
        input.keyDown(Input.Keys.D); frame();
        require(body(rebuilt).getLinearVelocity().x > 0, "Player is still controllable after failed travel");
        input.keyUp(Input.Keys.D); frame();

        exitPortals();
        input.keyDown(Input.Keys.SHIFT_LEFT); frame();
        input.keyUp(Input.Keys.SHIFT_LEFT);
        var oldAttack = Combat.MAPPER.get(rebuilt);
        for (int i = 0; i < 120 && oldAttack.getSensor() == null; i++) { frame(); }
        require(oldAttack.isAttacking() && oldAttack.getSensor() != null, "Depart with an active melee fixture");
        enterNamed("other");
        var visitor = player();
        require(java.util.Collections.disjoint(sourceLights, lights()) && java.util.Collections.disjoint(sourceFixtures, fixtures()),
            "No source emitter or fixture remains on the first destination frame");
        checkLightMount(true);
        int panel = pixel(redCenter);
        require((panel >>> 16) == 0 && Math.abs((panel >>> 8 & 255) - 64) <= 2,
            "White panel where the retired red light and solid stood shows only the captured ambient: " + Integer.toHexString(panel));
        checkRedLight(center("red"));
        System.out.printf("MapTravelTest: same world point RGBA: A/day=%08x, A/night=%08x, B/first night frame=%08x%n",
            daylight, lit, panel);
        require(visitor != rebuilt && tiled.getCurrentMap() != sourceMap, "Independent manifest ID changes runtime map and player");
        require(!oldAttack.isAttacking() && oldAttack.getSensor() == null && !Combat.MAPPER.get(visitor).isAttacking()
            && body(visitor).getFixtureList().size == 2, "Travel destroys the outgoing active melee fixture");
        require(!GAME.getAssetService().isLoaded(TiledMap.class, A) && !GAME.getAssetService().isLoaded(TextureAtlas.class, A)
            && !GAME.getAssetService().isLoaded(Music.class, "TRAVEL_A"), "Old map, atlas and music are released");
        require(GAME.getAssetService().isLoaded(TiledMap.class, B) && GAME.getAssetService().isLoaded(Sound.class, "TRAVEL_B"),
            "Independent map assets are loaded");
        atPoint(visitor, "arrival");
        for (int i = 0; i < 5; i++) { frame(); }
        require(player() == visitor, "Overlapping return portal waits for an exit and reentry");
        var visitorBody = body(visitor);
        var visitorLights = lights();
        var beforeLocal = seconds(screen);
        enter("local");
        require(night(screen) && seconds(screen) == beforeLocal + 2d * GameConsts.PHYSIC_INTERVAL
                && lights().equals(visitorLights), "THIS teleport keeps pulse time, mode and mounted light identities");
        require(player() == visitor && body(visitor) == visitorBody, "THIS retains player and body identity");
        atPoint(visitor, "local-landing");
        checkRedLight(center("red"));

        var beforeHideShow = time.getTotalInGameSeconds();
        var beforeRemount = seconds(screen);
        screens.switchScreens("travel");
        require(night(screen) && seconds(screen) == beforeRemount && java.util.Collections.disjoint(visitorLights, lights()),
            "Hide/show rebuilds the lights without resetting phase or mode");
        screen.render(0f);
        checkRedLight(center("red"));
        require(GAME.getEngine().getSystem(WorldTimeSystem.class) == time
            && time.getTotalInGameSeconds() == beforeHideShow,
            "Hide/show retains world time without advancing while hidden");
        require(player() != visitor && Health.MAPPER.get(player()) == health && Loadout.MAPPER.get(player()) == loadout,
            "Hide/show remounts the current map and retains live player state");
        atPoint(player(), "arrival");
        enter("local"); // Exit the arrival portal again before returning.
        enter("back");
        atPoint(player(), "PlayerSpawn");
        require(GAME.getAssetService().isLoaded(TiledMap.class, A) && !GAME.getAssetService().isLoaded(Sound.class, "TRAVEL_B"),
            "Travel back loads the original manifest and releases B-owned sound");
        require(Health.MAPPER.get(player()) == health && world.getBodyCount() == sourceBodies, "Returning preserves state and balances body ownership");

        screens.unloadScreen("travel");
        require(lights().isEmpty(), "Screen teardown retires every mounted emitter");
        bind(new GameScreen(entry));
        screens.loadScreen("fresh", screen);
        screens.switchScreens("fresh");
        var fresh = player();
        require(Health.MAPPER.get(fresh) != health && Health.MAPPER.get(fresh).getCurrent() == GAME.getGameData().playerData().maxHealth()
            && Loadout.MAPPER.get(fresh).getWeaponId().equals(GAME.getGameData().playerData().loadout().weapon())
            && Facing.MAPPER.get(fresh).getDirection() == Facing.Direction.UP, "A fresh session uses JSON defaults and the starting marker's facing");
        require(!night(screen) && seconds(screen) == 0d && lights().size() == 4,
            "New screen has a fresh day session and only its own map lights");
        var freshTime = GAME.getEngine().getSystem(WorldTimeSystem.class);
        require(freshTime != time && freshTime.getTotalInGameSeconds() == 21_600,
            "A fresh GameScreen starts a fresh world-time session");

        screens.unloadScreen("fresh");
        bind(new GameScreen(ManifestLoader.parseMapManifest(B)));
        screens.loadScreen("playerless", screen);
        var valid = GAME.getGameData();
        try {
            var invalid = new HashMap<>(valid.objectsData());
            invalid.remove("wisp");
            setObjects(invalid);
            try {
                screens.switchScreens("playerless");
                throw new AssertionError("Invalid object should fail the initial mount");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("wisp"), "Show fails during object preparation");
            }
            input.keyDown(Input.Keys.N);
            screen.render(.02f);
            require(!night(screen) && seconds(screen) == 0d && world.getBodyCount() == 0 && lights().isEmpty(),
                "Failed show starts neither the session nor its mounted lights and bodies");
        } finally { field(GameCore.class, "gameData").set(GAME, valid); }
        screens.switchScreens("playerless");
        input.keyDown(Input.Keys.N); screen.render(0f);
        require(!night(screen), "A key held from the failed show cannot toggle on retry");
        input.keyUp(Input.Keys.N);
        require(GAME.getEngine().getEntitiesFor(Family.all(Player.class).get()).size() == 0,
            "Direct entry without a PLAYER marker mounts the map without a player");
        input.keyDown(Input.Keys.N); input.keyUp(Input.Keys.N); frame();
        require(night(screen) && seconds(screen) == GameConsts.PHYSIC_INTERVAL, "Night input works without a player");
    }

    private void installLightFixtures() throws ReflectiveOperationException {
        var objects = new HashMap<>(GAME.getGameData().objectsData());
        try (var file = Gdx.files.classpath("fixtures/travel-lights.json").read()) {
            objects.putAll(DataLoader.parseObjectDefinitions(file));
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        setObjects(objects);
        var atlas = GAME.getAssetService().get(TextureAtlas.class, ObjectAssets.OBJECTS.id());
        var white = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        try {
            white.setColor(1, 1, 1, 1);
            white.fill();
            var texture = new Texture(white);
            // addRegion transfers this texture to the shared objects atlas, which owns disposal.
            for (String key : List.of("red-lamp", "blue-lamp", "panel")) { atlas.addRegion(key, texture, 0, 0, 32, 32); }
        } finally { white.dispose(); }
    }

    private void verifyActorAi(Entity npc, Entity enemy, Entity player, AwarenessSystem awareness) {
        var npcPosition = body(npc).getPosition().cpy();
        var enemyPosition = body(enemy).getPosition().cpy();
        var playerPosition = body(player).getPosition().cpy();
        var combat = Combat.MAPPER.get(enemy);
        // Use empty space to verify actor steering and weapon damage independent of fixture placement on the demo map.
        body(npc).setTransform(-110, -100, 0);
        body(enemy).setTransform(-100, -100, 0);
        body(player).setTransform(-90, -100, 0);
        for (int i = 0; i < 24; i++) { screen.render(1f / 60f); }
        require(!body(npc).getPosition().epsilonEquals(-110, -100, .01f)
            && !body(enemy).getPosition().epsilonEquals(-100, -100, .01f)
            && combat.getAttackId() == 0, "Both actors physically wander without attacking out of range");
        body(enemy).setTransform(-100, -100, 0);
        body(player).setTransform(-100 + 28f * GameConsts.UNIT_SCALE, -100, 0);
        var originalHealth = Health.MAPPER.get(player);
        var testHealth = new Health(originalHealth.getMaximum());
        player.add(testHealth);
        try {
            screen.render(1f / 60f);
            require(awareness.inRange(enemy, Actor.Role.PLAYER).equals(List.of(player))
                && Facing.MAPPER.get(enemy).getDirection() == Facing.Direction.RIGHT && combat.isAttacking(),
                "Live graph faces the player and starts combat in range");
            for (int i = 0; i < 5; i++) { screen.render(1f / 60f); }
            require(testHealth.getCurrent() == testHealth.getMaximum() - Loadout.MAPPER.get(enemy).getWeapon().primary().damage()
                && body(enemy).getLinearVelocity().isZero(), "Enemy deals one weapon hit while standing still");
            body(player).setTransform(-90, -100, 0);
            for (int i = 0; i < 90 && combat.isAttacking(); i++) { screen.render(1f / 60f); }
            screen.render(1f / 60f);
            require(!combat.isAttacking() && !body(enemy).getLinearVelocity().isZero(),
                "Enemy resumes wandering when the player leaves range");
        } finally {
            player.add(originalHealth);
            body(npc).setTransform(npcPosition, 0);
            body(enemy).setTransform(enemyPosition, 0);
            body(player).setTransform(playerPosition, 0);
        }
    }

    private static void setObjects(Map<String, ObjectData> definitions) throws ReflectiveOperationException {
        var data = GAME.getGameData();
        field(GameCore.class, "gameData").set(GAME, new GameData(data.playerData(), data.weaponsData(),
            data.projectilesData(), data.actorsData(), definitions));
    }

    private Vector2 center(String marker) {
        var point = tiled.getMapTriggers().points().stream().filter(p -> p.name().equals(marker)).findFirst().orElseThrow();
        return point.position().cpy().scl(GameConsts.UNIT_SCALE);
    }

    private static Set<Entity> lights() {
        var result = new HashSet<Entity>();
        GAME.getEngine().getEntitiesFor(Family.all(Emissive.class).get()).forEach(result::add);
        return result;
    }

    private Set<Fixture> fixtures() {
        var nativeFixtures = new Array<Fixture>();
        world.getFixtures(nativeFixtures);
        var result = new HashSet<Fixture>();
        nativeFixtures.forEach(result::add);
        return result;
    }

    private void checkLightMount(boolean otherMap) {
        // The same four definitions swap ITEM/OBJECT routes on B. No hand-added components or bodies.
        var entities = GAME.getEngine().getEntitiesFor(Family.all(Transform.class, Graphic.class).get());
        require(lights().size() == 4, "Exactly four emitters from this mount");
        for (var point : tiled.getMapTriggers().points()) {
            if (point.type() != MapTriggers.SpawnType.ITEM && point.type() != MapTriggers.SpawnType.OBJECT) { continue; }
            var definition = GAME.getGameData().objectsData().get(point.spawnKey());
            Entity spawned = null;
            var center = center(point.name());
            for (var entity : entities) {
                var t = Transform.MAPPER.get(entity);
                if (t.getPosition().cpy().mulAdd(t.getSize(), .5f).epsilonEquals(center, .0001f)) {
                    require(spawned == null, "Only one sprite at " + point.name());
                    spawned = entity;
                }
            }
            require(spawned != null, "Art centered on the authored " + point.type() + " point: " + point.name());
            require(Physic.MAPPER.has(spawned) == definition.collision().isPresent()
                && Animation2D.MAPPER.has(spawned) == definition.animation().isPresent(), "Independent collision/animation: " + point.name());
            float angle = switch (point.facing()) { case UP -> 180f; case LEFT -> -90f; case DOWN -> 0f; case RIGHT -> 90f; };
            require(Transform.MAPPER.get(spawned).getRotationDeg() == angle, "Facing rotates the centered art: " + point.name());
            if (Physic.MAPPER.has(spawned)) {
                require(body(spawned).getPosition().epsilonEquals(center) && !body(spawned).getFixtureList().first().isSensor(),
                    "Light's own solid rotates about spawn center: " + point.name());
            }
            var light = Emissive.MAPPER.get(spawned);
            require((light != null) == definition.emissive().isPresent(), "Null emissive leaves ordinary art: " + point.name());
            if (light != null) {
                var data = definition.emissive().orElseThrow();
                require(light.radius(0d) == data.radiusPixels() * GameConsts.UNIT_SCALE
                    && light.intensity(0d) == data.intensity(), "Factory converts pixel radius once: " + point.name());
            }
        }
        require(tiled.getMapTriggers().points().stream().anyMatch(p -> p.name().equals("panel")) == otherMap,
            "Ambient-only panel belongs only to B");
    }

    private static int pixel(Vector2 position) {
        var window = GAME.getViewport().project(position.cpy());
        var sample = Pixmap.createFromFrameBuffer(HdpiUtils.toBackBufferX((int) window.x), HdpiUtils.toBackBufferY((int) window.y), 1, 1);
        try { return sample.getPixel(0, 0); }
        finally { sample.dispose(); }
    }

    private void checkRedLight(Vector2 center) throws ReflectiveOperationException {
        int peak = pixel(center);
        int left = pixel(center.cpy().sub(.31f, 0)) >>> 24;
        int right = pixel(center.cpy().add(.31f, 0)) >>> 24;
        // The center pixel can be half a raster pixel off-axis. Compare to the independently authored pulse.
        double expectedRed = 255 * .6 * (1 + .5 * Math.sin(seconds(screen) * 4 * Math.PI));
        require(Math.abs((peak >>> 24) - expectedRed) < 16 && (peak >>> 16 & 255) < 3 && Math.abs((peak >>> 8 & 255) - 64) <= 2,
            "Frame follows the session phase on blue ambient; expected red " + expectedRed + ", got " + Integer.toHexString(peak));
        require((peak >>> 24) > left + 35 && (peak >>> 24) > right + 35,
            "Peak stays centered, not at the facing-rotated offset fixture: " + left + "/" + (peak >>> 24) + "/" + right);
    }

    private void verifyStagedFailure(TiledMap sourceMap, Entity player, int bodies, Vector2 redCenter)
            throws ReflectiveOperationException {
        var original = GAME.getGameData();
        var corrupt = new HashMap<>(original.objectsData());
        // The last B marker has valid static art and collision, but its authored pixel radius underflows in world units.
        corrupt.put("panel", corrupt.get("underflow-panel"));
        var sourceLights = lights();
        var sourceFixtures = fixtures();
        var previousLogger = Gdx.app.getApplicationLogger();
        var errors = new ArrayList<Throwable>();
        setObjects(corrupt);
        try {
            Gdx.app.setApplicationLogger(new Lwjgl3ApplicationLogger() {
                @Override public void error(String tag, String message, Throwable error) {
                    if (tag.equals("TiledSystem") && message.contains(B + "/arrival")) { errors.add(error); }
                    super.error(tag, message, error);
                }
            });
            enter("other");
        } finally {
            Gdx.app.setApplicationLogger(previousLogger);
            field(GameCore.class, "gameData").set(GAME, original);
        }
        require(errors.size() == 1 && errors.getFirst() instanceof IllegalArgumentException
                && errors.getFirst().getMessage().contains("world radius"),
            "Destination reaches the conversion failure after staging earlier solid emitters: " + errors);
        screen.render(0f);
        require(tiled.getCurrentMap() == sourceMap && player() == player && lights().equals(sourceLights) && night(screen),
            "Failed prepare preserves the previous map and all mounted lights");
        require(world.getBodyCount() == bodies && fixtures().equals(sourceFixtures),
            "Neither failed conversion nor earlier staged entities leave native bodies or fixtures");
        require(!GAME.getAssetService().isLoaded(TiledMap.class, B), "Candidate map released on staged failure");
        System.out.printf("MapTravelTest: failed prepare retained %d emitters, %d bodies and %d fixtures%n",
            sourceLights.size(), bodies, sourceFixtures.size());
        checkRedLight(redCenter);
    }

    private void bind(GameScreen next) throws ReflectiveOperationException {
        screen = next;
        tiled = GAME.getEngine().getSystem(TiledSystem.class);
        world = (World) field(GameScreen.class, "physicWorld").get(screen);
    }

    private void moveToPortal(String name) {
        var object = (RectangleMapObject) tiled.getCurrentMap().getLayers().get("Triggers").getObjects().get(name);
        var center = object.getRectangle().getCenter(new Vector2()).scl(GameConsts.UNIT_SCALE);
        move(center);
    }

    private void enterNamed(String name) throws ReflectiveOperationException {
        var outgoing = player();
        double before = seconds(screen);
        moveToPortal(name);
        int ticks = 0;
        while (ticks < 2 && player() == outgoing) { frame(); ticks++; }
        require(player() != outgoing, "Contact installs a new runtime: " + name);
        screen.render(0f); // First destination frame; no further simulation tick.
        require(night(screen) && seconds(screen) == before + ticks * (double) GameConsts.PHYSIC_INTERVAL,
            "First frame retains mode and continuous session time: " + name);
    }

    private void enter(String name) {
        moveToPortal(name);
        frame();
        frame(); // A newly overlapping Box2D pair can first report contact on the next tick.
    }

    private void exitPortals() { move(new Vector2(3, 15)); frame(); frame(); }

    private void move(Vector2 center) {
        var player = player();
        body(player).setTransform(center.mulAdd(Transform.MAPPER.get(player).getSize(), -.5f), 0);
        body(player).setLinearVelocity(0, 0);
        body(player).setAwake(true);
    }

    private void checkCampfire() {
        var point = tiled.getMapTriggers().points().stream().filter(p -> p.name().equals("FireSpawn")).findFirst().orElseThrow();
        var center = point.position().cpy().scl(GameConsts.UNIT_SCALE);
        var engine = GAME.getEngine();
        var campfire = engine.getEntitiesFor(Family.all(Graphic.class, Animation2D.class, Physic.class).get());
        Entity fire = null;
        for (var entity : campfire) {
            if (ObjectAssets.OBJECTS.id().equals(Animation2D.MAPPER.get(entity).getAtlasId())) { fire = entity; }
        }
        require(fire != null && body(fire).getPosition().epsilonEquals(center), "Campfire body is centered on FireSpawn");
        var light = Emissive.MAPPER.get(fire);
        var authored = GAME.getGameData().objectsData().get(point.spawnKey()).emissive().orElseThrow();
        require(light != null && light.radius(0d) == authored.radiusPixels() * GameConsts.UNIT_SCALE
                && light.intensity(0d) == authored.intensity(), "Real FireSpawn carries the configured world-unit emitter");
        var transform = Transform.MAPPER.get(fire);
        require(transform.getRotationDeg() == 0f && transform.getSize().equals(new Vector2(1, 1)),
            "DOWN preserves original 32x32 art orientation and size");
        var fixture = body(fire).getFixtureList().first();
        require(body(fire).getType() == BodyDef.BodyType.StaticBody && !fixture.isSensor()
            && fixture.testPoint(center.x, center.y - .25f) && !fixture.testPoint(center), "Campfire has a solid base ellipse");
        screen.render(0f);
        var first = Graphic.MAPPER.get(fire).getRegion();
        for (int i = 0; i < 12; i++) { frame(); }
        require(Graphic.MAPPER.get(fire).getRegion() != first && Animation2D.MAPPER.get(fire).getAnimation().getKeyFrames().length == 6,
            "Campfire advances through its six frames");
        require(transform.getPosition().epsilonEquals(center.cpy().sub(.5f, .5f)), "Physics steps preserve the centered sprite");
        var factory = engine.getSystem(EntityFactory.class);
        var rotated = factory.getSpawnedObject(point.position(), point.spawnKey(), Facing.Direction.RIGHT);
        engine.addEntity(rotated);
        try {
            var rotatedFixture = body(rotated).getFixtureList().first();
            require(Transform.MAPPER.get(rotated).getRotationDeg() == 90f
                && rotatedFixture.testPoint(center.x + .25f, center.y)
                && !rotatedFixture.testPoint(center.x, center.y - .25f), "RIGHT rotates the sprite and base ellipse around the point");
        } finally { engine.removeEntity(rotated); }
    }

    private void frame() { screen.render(GameConsts.PHYSIC_INTERVAL); }

    private void atPoint(Entity player, String name) {
        var point = tiled.getMapTriggers().points().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow();
        var transform = Transform.MAPPER.get(player);
        var expected = point.position().cpy().scl(GameConsts.UNIT_SCALE).mulAdd(transform.getSize(), -.5f);
        require(expected.epsilonEquals(body(player).getPosition(), .001f) && expected.epsilonEquals(transform.getPosition(), .001f)
            && expected.epsilonEquals(Physic.MAPPER.get(player).getPrevPos(), .001f), "Body and interpolation centered at " + name);
    }

    private int arrowCount() throws ReflectiveOperationException {
        return ((List<?>) field(CombatSystem.class, "arrows").get(GAME.getEngine().getSystem(CombatSystem.class))).size();
    }

    private static List<TextureAtlas.AtlasRegion> toList(TextureAtlas atlas) {
        var frames = new ArrayList<TextureAtlas.AtlasRegion>();
        atlas.getRegions().forEach(frames::add);
        return frames;
    }

    private static Entity player() {
        var players = GAME.getEngine().getEntitiesFor(Family.all(Player.class).get());
        require(players.size() == 1, "Exactly one player");
        return players.first();
    }

    private static Body body(Entity player) { return Physic.MAPPER.get(player).getBody(); }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }

    @Override public void dispose() {
        if (initialized) {
            try { GAME.dispose(); }
            catch (Throwable error) {
                if (failure == null) { failure = error; }
                else { failure.addSuppressed(error); }
            }
        }
    }
}
