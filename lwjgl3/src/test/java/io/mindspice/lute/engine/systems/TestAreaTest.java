package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.controllers.ControllerManagerStub;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.screen.ScreenManager;
import io.mindspice.lute.gameplay.HoardRun;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** Exercises Test Area through the real menu, map, AI, combat, and remount paths. */
public final class TestAreaTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;

    public static void main(String[] args) throws Exception {
        Controllers.preferredManager = ControllerManagerStub.class.getName();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(640, 400);
        config.setInitialVisible(false);
        config.disableAudio(true);
        var preferences = Files.createTempDirectory("lute-test-area-");
        try {
            config.setPreferencesConfig(preferences.toString(), com.badlogic.gdx.Files.FileType.Absolute);
            new Lwjgl3Application(new TestAreaTest(), config);
            if (failure != null) { throw new AssertionError("Test Area integration", failure); }
        } finally {
            try (var files = Files.newDirectoryStream(preferences)) {
                for (var file : files) { Files.delete(file); }
            }
            Files.delete(preferences);
        }
        System.out.println("TestAreaTest: menu, waves, combat, pause, remount, cleanup and scored Hoard isolation passed");
    }

    @Override public void create() {
        try { exercise(); }
        catch (Throwable error) { failure = error; }
        finally { GAME.dispose(); Gdx.app.exit(); }
    }

    private void exercise() throws ReflectiveOperationException {
        GAME.create();
        var scores = Gdx.app.getPreferences("lute-hoard-scores-v1");
        var initialScores = Map.copyOf(scores.get());
        var screenField = GameCore.class.getDeclaredField("screenManager");
        screenField.setAccessible(true);
        var screens = (ScreenManager) screenField.get(GAME);
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        for (int i = 0; i < 3; i++) { tap(Input.Keys.DOWN); }
        tap(Input.Keys.ENTER); // Test Area is after Key Bindings, with no gate or name entry.
        var area = waitForArena(TestAreaSystem.class);
        var engine = GAME.getEngine();
        require(engine.getSystem(HoardSystem.class) == null, "scored Hoard rules are not installed");
        var tiled = engine.getSystem(TiledSystem.class);
        var player = engine.getEntitiesFor(Family.all(Player.class).get()).first();
        var cycle = GAME.getGameData().weaponsData().weapons().keySet().stream().sorted().toList();
        require(Controller.MAPPER.get(player).getWeaponCycle().equals(cycle), "player cycles every weapon in stable ID order");
        require("basic_axe".equals(Loadout.MAPPER.get(player).getWeaponId()), "Test Area starts with the axe equipped");
        require(Health.MAPPER.get(player).getCurrent() == HoardRun.PLAYER_HEALTH, "starts with standard arena HP");
        var world = Physic.MAPPER.get(player).getBody().getWorld();
        var mountBodies = world.getBodyCount();

        var actors = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        require(actors.size() == 0 && area.stamina() == 1d, "Test Area starts with no enemies and full stamina");
        var clock = engine.getSystem(WorldTimeSystem.class);
        double before = clock.getTotalInGameSeconds();
        clock.update(1f);
        require(clock.getTotalInGameSeconds() == before, "Test Area keeps arena daylight and freezes session time");
        int firstWave = ticksFor(15);
        int secondWave = ticksFor(30);
        stepPhysics(firstWave - 1);
        require(actors.size() == 0 && area.statusText().equals("Test Area   Next wave in 1s"), "no early wave; new HUD label");
        String countdown = area.statusText();
        long steps = engine.getSystem(PhysicSystem.class).completedSteps();
        GAME.pause();
        for (int i = 0; i < 60; i++) { screens.render(1f / 30f); }
        require(area.statusText().equals(countdown) && engine.getSystem(PhysicSystem.class).completedSteps() == steps
            && actors.size() == 0, "pause freezes wave time and physics");
        GAME.resume();
        stepPhysics(1);
        require(actors.size() == 10, "first wave has ten enemies: " + actors.size());
        var victim = actors.get(0);
        var attacker = actors.get(1);
        var originalAi = ActorAi.MAPPER.get(victim);
        // The mode must override even a guaranteed authored chance.
        victim.add(new ActorAi(originalAi.behaviorGraphKey(), originalAi.detectionRadius(), 100));
        var attackerBody = Physic.MAPPER.get(attacker).getBody();
        var attackerStart = attackerBody.getPosition().cpy();
        var neighbor = Physic.MAPPER.get(victim).getBody().getPosition().cpy().add(1f, 0f);
        attackerBody.setTransform(neighbor, 0f);
        var awareness = engine.getSystem(AwarenessSystem.class);
        awareness.retaliateAfterDamage(victim, attacker);
        awareness.retaliateAfterArrow(victim, attacker);
        require(ActorAi.MAPPER.get(victim).retaliationTarget() == null, "Test Area disables enemy retaliation even at an authored chance of 100");
        victim.add(originalAi);
        attackerBody.setTransform(attackerStart, 0f);
        var center = TestAreaSystem.centerZone(tiled.getMapTriggers()).bounds();
        engine.getSystem(AwarenessSystem.class).update(1f / 30f);
        for (var actor : actors) {
            require(actor.getComponent(ArenaGuard.class) == null
                && "brainless_undead".equals(Animation2D.MAPPER.get(actor).getAtlasKey()), "only the authored swarm enemies spawn");
            require(center.contains(AwarenessSystem.feet(actor)), "enemies spawn in the center zone");
            require(ActorAi.MAPPER.get(actor).mode() == ActorAi.Mode.ENGAGE, "swarm pursues the player");
        }

        stepPhysics(secondWave - firstWave - 1);
        require(actors.size() == 10, "no second wave before 30 seconds");
        stepPhysics(1);
        require(actors.size() == 20, "second wave adds ten without a scored run");
        boolean swarmAttacked = false;
        for (int i = 0; i < 600 && !swarmAttacked; i++) {
            engine.update(GameConsts.PHYSIC_INTERVAL);
            for (var actor : actors) {
                if (Combat.MAPPER.get(actor).getAttackId() > 0) { swarmAttacked = true; break; }
            }
        }
        require(swarmAttacked, "the swarm reaches attack range and swings at the player without player input");

        tiled.hideMap();
        require(!area.isActive() && actors.size() == 0 && world.getBodyCount() == 0 && engine.getEntities().size() == 0,
            "unmount releases the entire map, bodies and mode state");
        require(!GAME.getAssetService().isRegistered(ManifestLoader.parseMapManifest("hoard_mode").mapAsset()),
            "unmount releases the map asset");
        tiled.showMap(ManifestLoader.parseMapManifest("hoard_mode"));
        var remountedPlayer = engine.getEntitiesFor(Family.all(Player.class).get()).first();
        require(remountedPlayer != player
            && "basic_axe".equals(Loadout.MAPPER.get(remountedPlayer).getWeaponId()),
            "remount rebuilds the axe-equipped player");
        require(area.isActive() && actors.size() == 0 && area.stamina() == 1d && world.getBodyCount() == mountBodies,
            "remount resets entities and stamina without leaking bodies");
        require(area.statusText().endsWith("15s"), "new instance has a fresh countdown");
        verifyAxeSpin(remountedPlayer, "basic_axe", List.of(Facing.Direction.RIGHT, Facing.Direction.DOWN,
            Facing.Direction.LEFT, Facing.Direction.UP, Facing.Direction.RIGHT));
        verifyAxeSpin(remountedPlayer, "black_axe", List.of(Facing.Direction.UP, Facing.Direction.RIGHT,
            Facing.Direction.DOWN, Facing.Direction.LEFT, Facing.Direction.UP));
        verifyAxePush(remountedPlayer);
        stepPhysics(firstWave);
        require(actors.size() == 10, "new instance spawns its own wave");
        awareness.update(GameConsts.PHYSIC_INTERVAL);
        for (var actor : actors) {
            require(ActorAi.MAPPER.get(actor).mode() == ActorAi.Mode.ENGAGE,
                "remounted swarm acquires the new player, not the removed entity");
        }
        verifySprint(remountedPlayer, area);
        verifyCollateral(actors.get(0), actors.get(1));

        var hp = Health.MAPPER.get(remountedPlayer);
        hp.damage(hp.getCurrent());
        screens.render(GameConsts.PHYSIC_INTERVAL);
        require(area.statusText().equals("Test Area   Game Over") && scores.get().equals(initialScores),
            "Test Area death stays unscored and does not switch to Hoard results");
        int terminalActors = actors.size();
        stepPhysics(ticksFor(16));
        require(actors.size() == terminalActors, "waves stop after death");

        screens.unloadScreen("GameScreen");
        screens.switchScreens("Menu");
        require(engine.getSystems().size() == 0 && engine.getEntities().size() == 0,
            "disposal detaches Test Area systems and entities");
        for (int i = 0; i < 3; i++) { tap(Input.Keys.UP); } // Test Area -> Hoard.
        tap(Input.Keys.ENTER);
        var menuInput = Gdx.input.getInputProcessor();
        menuInput.keyDown(Input.Keys.T);
        menuInput.keyTyped('T');
        menuInput.keyUp(Input.Keys.T);
        tap(Input.Keys.ENTER);
        var hoard = waitForArena(HoardSystem.class);
        var hoardPlayer = engine.getSystem(TiledSystem.class).activePlayer();
        String normalWeapon = GAME.getGameData().playerData().loadout().weapon();
        var normalCycle = java.util.stream.Stream.of(normalWeapon, "basic_sword", "basic_bow").distinct().toList();
        require(engine.getSystem(TestAreaSystem.class) == null && hoard.run().round() == 1 && hoard.run().score() == 0
            && actors.size() == 10 && normalWeapon.equals(Loadout.MAPPER.get(hoardPlayer).getWeaponId())
            && Controller.MAPPER.get(hoardPlayer).getWeaponCycle().equals(normalCycle),
            "normal Hoard starts its own scored wave with the normal inventory");
        require(scores.get().equals(initialScores), "Test Area did not leave a score behind");
        var hoardVictim = actors.get(0);
        var hoardAttacker = actors.get(1);
        var ai = ActorAi.MAPPER.get(hoardVictim);
        hoardVictim.add(new ActorAi(ai.behaviorGraphKey(), ai.detectionRadius(), 100));
        Physic.MAPPER.get(hoardVictim).getBody().setTransform(-100, -100, 0f);
        Physic.MAPPER.get(hoardAttacker).getBody().setTransform(-99, -100, 0f);
        engine.getSystem(AwarenessSystem.class).retaliateAfterDamage(hoardVictim, hoardAttacker);
        require(ActorAi.MAPPER.get(hoardVictim).retaliationTarget() == hoardAttacker,
            "normal Hoard still honors guaranteed enemy retaliation");
    }

    private static <T extends ArenaSystem> T waitForArena(Class<T> type) {
        for (int i = 0; i < 300; i++) {
            GAME.render();
            var arena = GAME.getEngine().getSystem(type);
            if (arena != null && arena.isActive()) { return arena; }
        }
        throw new AssertionError("Menu did not launch " + type.getSimpleName());
    }

    private static int ticksFor(int seconds) {
        return (int) Math.ceil(seconds / (double) GameConsts.PHYSIC_INTERVAL);
    }

    private static void stepPhysics(int ticks) {
        var physics = GAME.getEngine().getSystem(PhysicSystem.class);
        float dt = GameConsts.PHYSIC_INTERVAL;
        for (int i = 0; i < ticks; i++) { physics.update(dt); }
    }

    private static void verifySprint(com.badlogic.ashley.core.Entity player, TestAreaSystem area) {
        var engine = GAME.getEngine();
        var pads = Controllers.getControllers();
        int unused = ControllerMapping.UNDEFINED;
        var mapping = new ControllerMapping(0, unused, unused, unused, unused, 7, unused, unused, unused, unused,
            unused, unused, unused, unused, unused, unused, unused, unused, unused, unused) { };
        boolean[] held = {false};
        var padType = com.badlogic.gdx.controllers.Controller.class;
        var pad = (com.badlogic.gdx.controllers.Controller) Proxy.newProxyInstance(padType.getClassLoader(),
            new Class<?>[]{padType}, (proxy, method, args) -> switch (method.getName()) {
                case "getMapping" -> mapping;
                case "isConnected" -> true;
                case "getAxis" -> 0f;
                case "getButton" -> (int) args[0] == 7 && held[0];
                default -> throw new AssertionError("Unexpected controller call: " + method.getName());
            });
        pads.add(pad);
        try {
            GAME.getInputManager().updateGamepad();
            held[0] = true;
            GAME.getInputManager().updateGamepad();
            Move.MAPPER.get(player).getDirection().set(1, 1);
            float dt = GameConsts.PHYSIC_INTERVAL;
            engine.getSystem(PhysicMoveSystem.class).update(dt);
            engine.getSystem(PhysicSystem.class).update(dt);
            require(area.stamina() < 1d, "Test Area controller sprint consumes stamina while moving");
        } finally {
            pads.removeValue(pad, true);
            GAME.getInputManager().updateGamepad();
        }
    }

    private static void verifyAxeSpin(com.badlogic.ashley.core.Entity player, String weapon, List<Facing.Direction> expected) {
        var engine = GAME.getEngine();
        var facing = Facing.MAPPER.get(player);
        var animation = Animation2D.MAPPER.get(player);
        var combat = Combat.MAPPER.get(player);
        var loadouts = engine.getSystem(LoadoutSystem.class);
        require(loadouts.equipWeapon(player, weapon), "equip axe for spin");
        var axe = Loadout.MAPPER.get(player).getWeapon();
        var secondary = axe.secondary();
        var anchor = Transform.MAPPER.get(player).getSize().cpy().scl(.5f);
        facing.setDirection(expected.getFirst());
        combat.requestAttack(Combat.AttackSlot.SECONDARY);
        var visited = new ArrayList<Facing.Direction>();
        int previousFrame = -1;
        boolean coveredFrameZero = false;
        for (int i = 0; i < 120; i++) {
            engine.update(GameConsts.PHYSIC_INTERVAL);
            if (!combat.isAttacking()) { break; }
            var direction = facing.getDirection();
            if (visited.isEmpty() || visited.getLast() != direction) { visited.add(direction); }
            int frame = animation.getFrameIndex();
            require(frame >= previousFrame && animation.getAction().equals(secondary.animation().action()),
                "turning preserves the secondary frame sequence");
            var region = (AtlasRegion) Graphic.MAPPER.get(player).getRegion();
            require(region.name.equals(animation.regionKey(secondary.animation().action(), direction)),
                "rendered frame is drawn from the matching direction");
            require(region == animation.getAnimation().getKeyFrames()[frame], "renderer uses the current frame index");
            require(combat.getSensor() != null && combat.getSensor().isSensor(),
                "axe hitbox is active on every spin tick, including during turns");
            var shape = (PolygonShape) combat.getSensor().getShape();
            var vertices = secondary.vertices(anchor, direction);
            require(shape.getVertexCount() == vertices.length, "spin uses its own hitbox shape");
            var vertex = new Vector2();
            for (int j = 0; j < shape.getVertexCount(); j++) {
                shape.getVertex(j, vertex);
                boolean matches = false;
                for (var expectedVertex : vertices) { matches |= vertex.epsilonEquals(expectedVertex, .0001f); }
                require(matches, "spin uses its own authored hitbox bounds and facing offset");
            }
            coveredFrameZero |= frame == 0;
            previousFrame = frame;
        }
        require(visited.equals(expected), "one clockwise turn, back to the starting direction: " + visited);
        require(coveredFrameZero && !combat.isAttacking() && combat.getSensor() == null,
            "spin is live from frame zero and clears the sensor when it ends");
        require(loadouts.equipWeapon(player, "basic_axe"), "restore Test Area starting axe");
    }

    private static void verifyAxePush(com.badlogic.ashley.core.Entity player) {
        var engine = GAME.getEngine();
        var body = Physic.MAPPER.get(player).getBody();
        var original = body.getPosition().cpy();
        float scale = GameConsts.UNIT_SCALE;
        body.setTransform(-100, -100, 0);
        var enemy = engine.getSystem(EntityFactory.class).getMapActor(
            body.getPosition().cpy().scl(1f / scale).add(42, 0), Actor.Role.ENEMY, "brainless_undead", Facing.Direction.LEFT);
        enemy.remove(ActorAi.class);
        engine.addEntity(enemy);
        try {
            var victimBody = Physic.MAPPER.get(enemy).getBody();
            float start = victimBody.getPosition().x;
            var combat = Combat.MAPPER.get(player);
            combat.tickCooldown(2f);
            Facing.MAPPER.get(player).setDirection(Facing.Direction.RIGHT);
            combat.requestAttack(Combat.AttackSlot.SECONDARY);
            engine.update(GameConsts.PHYSIC_INTERVAL);
            for (int i = 0; i < 120 && combat.isAttacking(); i++) { engine.update(GameConsts.PHYSIC_INTERVAL); }
            float pushed = (victimBody.getPosition().x - start) / scale;
            require(pushed > 1f && pushed < 30f && victimBody.getLinearVelocity().isZero(.0001f),
                "spin pushes the enemy outward without residual knockback: " + pushed);
            var feedback = HealthFeedback.MAPPER.get(enemy);
            require(feedback != null && feedback.numbers.size() == 1, "turning hitbox hits the enemy only once");
        } finally {
            engine.removeEntity(enemy);
            body.setTransform(original, 0);
        }
    }

    private static void verifyCollateral(com.badlogic.ashley.core.Entity attacker, com.badlogic.ashley.core.Entity victim) {
        var engine = GAME.getEngine();
        var awareness = engine.getSystem(AwarenessSystem.class);
        var ai = ActorAi.MAPPER.get(victim);
        victim.add(new ActorAi(ai.behaviorGraphKey(), ai.detectionRadius(), 100));
        Physic.MAPPER.get(attacker).getBody().setTransform(-100, -100, 0f);
        Physic.MAPPER.get(victim).getBody().setTransform(-100 + 36 * GameConsts.UNIT_SCALE, -100, 0f);
        Move.MAPPER.get(attacker).getDirection().setZero();
        Move.MAPPER.get(victim).getDirection().setZero();
        Facing.MAPPER.get(attacker).setDirection(Facing.Direction.RIGHT);
        var combat = Combat.MAPPER.get(attacker);
        var health = Health.MAPPER.get(victim);
        int before = health.getCurrent();
        awareness.setProcessing(false);
        try {
            combat.requestAttack(Combat.AttackSlot.PRIMARY, victim);
            for (int i = 0; i < 120 && health.getCurrent() == before; i++) { engine.update(GameConsts.PHYSIC_INTERVAL); }
        } finally {
            awareness.setProcessing(true);
        }
        require(health.isAlive() && health.getCurrent() < before, "production melee still deals nonlethal collateral damage: " + before + " -> " + health.getCurrent());
        require(ActorAi.MAPPER.get(victim).retaliationTarget() == null, "collateral hit does not provoke infighting");
    }

    private static void tap(int key) {
        var input = Gdx.input.getInputProcessor();
        input.keyDown(key);
        input.keyUp(key);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
