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
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.screen.GameScreen;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.systems.CombatSystem;
import io.mindspice.lute.engine.systems.EventSystem;
import io.mindspice.lute.engine.systems.TiledSystem;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        config.setWindowedMode(320, 240);
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
        var player = player();
        atPoint(player, "PlayerSpawn");
        var actualActors = GAME.getEngine().getEntitiesFor(Family.all(CompositeCharacter.class).exclude(Player.class).get());
        require(actualActors.size() == 2, "Actual startup map owns both actors");
        var oldNpc = actualActors.first();
        var oldEnemy = actualActors.get(1);
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
            require(body(newNpc) != oldNpcBody && body(newNpc).getFixtureList().size == 2
                && Health.MAPPER.get(newNpc) != oldNpcHealth && Health.MAPPER.get(newNpc).getCurrent() == 100f,
                "Map replacement recreates the NPC body, fixtures and health");
            require(body(newEnemy) != oldEnemyBody && body(newEnemy).getFixtureList().size == 2
                && Health.MAPPER.get(newEnemy) != oldEnemyHealth && Health.MAPPER.get(newEnemy).getCurrent() == 100f,
                "Map replacement recreates the enemy body, fixtures and health");
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

        var entry = ManifestLoader.parseMapManifest(A);
        bind(new GameScreen(entry));
        screens.loadScreen("travel", screen);
        screens.switchScreens("travel");
        var changes = new ArrayList<String>();
        GAME.getEngine().getSystem(EventSystem.class).subscribe(Event.Map.Changed.class, event -> changes.add(event.mapId()));
        atPoint(player(), "PlayerSpawn");
        frame(); // Boot inside the self portal; the rebuilt entity must not trigger it again.
        var start = player();
        atPoint(start, "PlayerSpawn");
        for (int i = 0; i < 5; i++) { frame(); }
        require(player() == start && changes.equals(List.of(A, A)), "Only boot and one explicit reload at arrival");

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
        enter("self");
        var rebuilt = player();
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
        input.keyDown(Input.Keys.D); frame();
        require(body(rebuilt).getLinearVelocity().x > 0, "Player is still controllable after failed travel");
        input.keyUp(Input.Keys.D); frame();

        exitPortals();
        input.keyDown(Input.Keys.SHIFT_LEFT); frame();
        input.keyUp(Input.Keys.SHIFT_LEFT);
        var oldAttack = Combat.MAPPER.get(rebuilt);
        for (int i = 0; i < 120 && oldAttack.getSensor() == null; i++) { frame(); }
        require(oldAttack.isAttacking() && oldAttack.getSensor() != null, "Depart with an active melee fixture");
        enter("other");
        var visitor = player();
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
        enter("local");
        require(player() == visitor && body(visitor) == visitorBody, "THIS retains player and body identity");
        atPoint(visitor, "local-landing");

        screens.switchScreens("travel");
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
        bind(new GameScreen(entry));
        screens.loadScreen("fresh", screen);
        screens.switchScreens("fresh");
        var fresh = player();
        require(Health.MAPPER.get(fresh) != health && Health.MAPPER.get(fresh).getCurrent() == GAME.getGameData().playerData().maxHealth()
            && Loadout.MAPPER.get(fresh).getWeaponId().equals(GAME.getGameData().playerData().loadout().weapon())
            && Facing.MAPPER.get(fresh).getDirection() == Facing.Direction.RIGHT, "A fresh session seeds the JSON defaults");
    }

    private void bind(GameScreen next) throws ReflectiveOperationException {
        screen = next;
        tiled = GAME.getEngine().getSystem(TiledSystem.class);
        world = (World) field(GameScreen.class, "physicWorld").get(screen);
    }

    private void enter(String name) {
        var object = (RectangleMapObject) tiled.getCurrentMap().getLayers().get("Triggers").getObjects().get(name);
        var center = object.getRectangle().getCenter(new Vector2()).scl(GameConsts.UNIT_SCALE);
        move(center);
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
