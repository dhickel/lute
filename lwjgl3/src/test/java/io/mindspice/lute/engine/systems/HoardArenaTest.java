package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.HdpiUtils;
import com.badlogic.gdx.controllers.ControllerManagerStub;
import com.badlogic.gdx.controllers.ControllerMapping;
import com.badlogic.gdx.controllers.Controllers;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Real generated arena, Box2D, normal screen lifecycle and both rendering paths. */
public final class HoardArenaTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;
    private boolean initialized;

    public static void main(String[] args) {
        Controllers.preferredManager = ControllerManagerStub.class.getName();
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(640, 400);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new HoardArenaTest(), config);
        if (failure != null) { throw new AssertionError("Hoard arena", failure); }
        System.out.println("HoardArenaTest: arena, baseline walk, sprint movement, animation, stamina and HUD, Dredge lunge, batches, HP, lighting, death, pause and remount passed");
    }

    @Override public void create() {
        try { exercise(); }
        catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void exercise() {
        verifyRoundHealthSampling();
        GAME.create();
        initialized = true;
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.app.setLogLevel(Application.LOG_ERROR);
        var engine = GAME.getEngine();
        var tiled = engine.getSystem(TiledSystem.class);
        for (int i = 0; i < 200 && tiled.getCurrentMap() == null; i++) { GAME.render(); }
        var hoard = engine.getSystem(HoardSystem.class);
        verifySprint(tiled, hoard);
        var run = hoard.run();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        require(run != null && run.round() == 1 && enemies.size() == 10, "ten wave enemies at arena mount");
        var clock = engine.getSystem(WorldTimeSystem.class);
        require(GameplayHud.statusText((int) clock.getMinuteOfDay(), run).equals("Round 1   2:00 left   Kills 0   Score 0"), "arena HUD");
        var areas = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        Entity survivor = enemies.first();
        var openingAreas = new HashSet<Integer>();
        for (var enemy : enemies) {
            int area = spawnArea(areas, enemy);
            require(area < areas.size(), "opening enemy is inside an authored spawn area");
            openingAreas.add(area);
            require(enemy.getComponent(DredgeLunge.class) == null, "ordinary waves have no Dredge lunge");
            require(Health.MAPPER.get(enemy).getCurrent() == 50 && Health.MAPPER.get(enemy).getMaximum() == 50
                && ActorAi.MAPPER.get(enemy).behaviorGraphKey().equals(BowAim.MAPPER.has(enemy) ? "wander_archer" : "wander_attack"),
                "wave enemy has its own behavior inside the polygon");
        }
        require(openingAreas.size() == 4, "all four areas are used in the opening batch");
        var health = Health.MAPPER.get(player());
        require(health.getCurrent() == 1000 && health.getMaximum() == 1000, "arena HP");
        require(HealthFeedback.MAPPER.get(player()) == null, "arena spawn does not show healing text");
        var pickups = engine.getEntitiesFor(Family.all(Pickup.class).get());
        require(pickups.size() == 0, "no initial pack");
        var equippedId = Loadout.MAPPER.get(player()).getWeapon().id();
        var starterCycle = new java.util.LinkedHashSet<>(java.util.List.of(equippedId, "basic_sword", "basic_bow"));
        require(Controller.MAPPER.get(player()).getWeaponCycle().equals(java.util.List.copyOf(starterCycle)),
            "visit inventory starts with the basic sword and bow, without duplicating the equipped weapon");
        health.damage(101);
        Health.MAPPER.get(survivor).damage(7);

        GAME.render(); // supplies the renderer with its session and draws the initial daylight
        var renderer = engine.getSystem(RenderSystem.class);
        double daylight = brightness();
        double arenaClock = clock.getTotalInGameSeconds();
        clock.update(2_400f);
        Gdx.input.getInputProcessor().keyDown(Input.Keys.N);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.N);
        GAME.render();
        renderer.update(0f);
        require(clock.getTotalInGameSeconds() == arenaClock && brightness() > daylight / 1.15,
            "Hoard freezes world time and stays in daylight even after pressing N");
        GAME.pause();
        int frozen = run.remainingSeconds();
        double frozenClock = clock.getTotalInGameSeconds();
        for (int i = 0; i < 120; i++) { GAME.render(); }
        require(run.remainingSeconds() == frozen && clock.getTotalInGameSeconds() == frozenClock, "paused simulation");
        GAME.resume();

        // Advance real Box2D fixed ticks without rendering thousands of intermediate frames.
        var physics = engine.getSystem(PhysicSystem.class);
        physics.update(50.01f);
        require(enemies.size() >= 14 && enemies.size() <= 18 && health.getCurrent() == 899,
            "one daylight refresh of four to eight enemies without healing");
        physics.update(70.02f);
        require(run.round() == 2 && health.getCurrent() == 899 && enemies.contains(survivor, true)
            && Health.MAPPER.get(survivor).getCurrent() == 43, "round drops a pack instead of healing and keeps survivors");

        require(pickups.size() == 1 && pickups.first().getComponent(Pickup.class).rule().amount() == 250, "one 250 HP round reward");
        var center = Physic.MAPPER.get(pickups.first()).getBody().getPosition().cpy();
        var ellipse = tiled.getMapTriggers().areas().stream().filter(a -> "round_health_spawn".equals(a.behaviorKey())).findFirst().orElseThrow();
        var dredgeMarker = tiled.getMapTriggers().points().stream().filter(p -> "idle_dredge".equals(p.behaviorKey()))
            .findFirst().orElseThrow();
        require(ellipseRadiusSquared(ellipse.bounds(), center) < 1.00001f, "round pack is inside the authored ellipse");
        require(center.dst(dredgeMarker.position().cpy().scl(GameConsts.UNIT_SCALE)) >= GameConsts.UNIT_SCALE - .0001f,
            "round pack is clear of the Dredge spawn point");
        var playerActor = Actor.MAPPER.get(player());
        Physic.MAPPER.get(player()).getBody().setTransform(center.x - playerActor.feetX(), center.y - playerActor.feetY(), 0);
        Physic.MAPPER.get(player()).getBody().setAwake(true);
        health.restore();
        physics.update(.04f);
        require(pickups.size() == 1 && HealthFeedback.MAPPER.get(player()) == null,
            "reset and full-HP contact do not show healing text or consume the pack");
        health.damage(101);
        physics.update(.04f);
        require(pickups.size() == 0 && health.getCurrent() == 1000, "continued contact collects and caps round heal");
        requireHealMarker(player(), 101);

        // Isolate a genuine, lethal sword contact from wandering and the authored scenery.
        engine.getSystem(AwarenessSystem.class).setProcessing(false);
        Physic.MAPPER.get(player()).getBody().setTransform(-20, -20, 0);
        Physic.MAPPER.get(survivor).getBody().setTransform(-19.15f, -20, 0);
        Loadout.MAPPER.get(player()).setWeapon("basic_sword");
        Facing.MAPPER.get(player()).setDirection(Facing.Direction.RIGHT);
        Health.MAPPER.get(survivor).damage(42);
        Combat.MAPPER.get(player()).requestAttack(Combat.AttackSlot.PRIMARY);
        for (int i = 0; i < 60 && run.score() == 0; i++) { engine.update(1f / 60); }
        require(!Health.MAPPER.get(survivor).isAlive() && run.kills() == 1 && run.score() == 6,
            "the same real killing blow feeds death, kills and round-two XP for a round-one enemy");
        require(GameplayHud.statusText(720, run).endsWith("Kills 1   Score 6"), "live HUD credit");

        engine.getSystem(AwarenessSystem.class).setProcessing(true);
        verifyTerminalRun(tiled, hoard);
        remountArena(tiled, hoard);
        require(!enemies.contains(survivor, true), "old runtime enemies are gone");
        var dyingEnemy = enemies.first();
        var melee = verifyMixedWaves(tiled, hoard);
        verifyLethalContactAtRollover(hoard, melee);
        Health.MAPPER.get(dyingEnemy).damage(50);
        engine.getSystem(DeathSystem.class).update(0f);
        remountArena(tiled, hoard);
        var bus = engine.getSystem(EventSystem.class);
        var priorRun = hoard.run();
        var priorEnemy = enemies.first();
        bus.emitEvent(new Event.Player.KilledEnemy(player(), priorEnemy)); // delayed until after named reentry
        require(tiled.requestTravel(player(), "test_tiled", "PlayerSpawn"), "leave arena");
        tiled.applyPendingTravel();
        require(hoard.run() == null && !enemies.contains(priorEnemy, true)
            && Health.MAPPER.get(player()).getMaximum() == GAME.getGameData().playerData().maxHealth(), "normal map and HP restored");
        var normal = Health.MAPPER.get(player());
        var ordinaryLoadout = Loadout.MAPPER.get(player());
        require(Controller.MAPPER.get(player()).getWeaponCycle().equals(GAME.getGameData().playerData().loadout().weaponCycle()),
            "ordinary weapon cycle is still authored");
        double before = clock.getTotalInGameSeconds();
        clock.update(1f);
        double ordinaryIncrement = clock.getTotalInGameSeconds() - before;
        normal.damage(1);
        require(tiled.requestTravel(player(), "hoard_mode", "PlayerSpawn"), "reenter arena");
        tiled.applyPendingTravel();
        bus.update(0);
        require(hoard.run() != priorRun && hoard.run().round() == 1 && hoard.run().score() == 0 && enemies.size() == 10,
            "new run ignores late kills from prior arena");
        before = clock.getTotalInGameSeconds();
        clock.update(1f);
        require(ordinaryIncrement > 0 && clock.getTotalInGameSeconds() == before
            && hoard.run().remainingSeconds() == 120, "world time runs only outside Hoard; rounds start at 120 seconds");
        verifyVisitInventory(ordinaryLoadout);
        // This lethal frame does step physics and would have started round two and dropped a pack.
        var lastSecond = hoard.run();
        lastSecond.advance(119.999);
        int waveSize = enemies.size();
        var dyingPlayer = player();
        var dyingHealth = Health.MAPPER.get(dyingPlayer);
        dyingHealth.damage(dyingHealth.getCurrent());
        engine.update(1f / 30);
        require(lastSecond.isGameOver() && lastSecond.round() == 1 && lastSecond.remainingSeconds() == 1
            && !dyingHealth.isAlive() && enemies.size() == waveSize, "death beats a due physics rollover");
        remountArena(tiled, hoard); // Unmount while the death pose is still visible.
        require(player() != dyingPlayer && !engine.getEntities().contains(dyingPlayer, true), "no hidden arena corpse");
        tiled.requestTravel(player(), "test_tiled", "PlayerSpawn");
        tiled.applyPendingTravel();
        require(Health.MAPPER.get(player()) == normal && normal.getCurrent() == normal.getMaximum() - 1
            && Loadout.MAPPER.get(player()) == ordinaryLoadout, "arena health and equipment do not leak to ordinary maps");
        tiled.requestTravel(player(), "hoard_mode", "PlayerSpawn");
        tiled.applyPendingTravel();
        verifySharedRoster(tiled, hoard);

        renderer.setProcessing(false);
        engine.getSystem(AwarenessSystem.class).setProcessing(false);
        int split = reinforcementHp(tiled, hoard, false);
        int batched = reinforcementHp(tiled, hoard, true);
        System.out.printf("HoardArenaTest: reinforcement HP after two physics ticks: split=%d, batched=%d%n", split, batched);
        require(split > 0 && batched == split, "one real sword hit regardless of physics frame batching");
    }

    private static void verifyRoundHealthSampling() {
        var bounds = new Rectangle(4, 7, 12, 8);
        var marker = bounds.getCenter(new Vector2());
        var scripted = new Random(0) {
            private final float[] draws = {0, 0, .5f, .5f, .75f, .5f};
            private int index;
            @Override public float nextFloat() { return draws[index++]; }
        };
        require(HoardSystem.sampleRoundHealth(bounds, marker, scripted).equals(new Vector2(13, 11)),
            "reject rectangle corners and the exact Dredge spawn, then use the next valid sample");

        var seeded = new Random(537);
        double radiusSum = 0;
        int samples = 4096;
        for (int i = 0; i < samples; i++) {
            var point = HoardSystem.sampleRoundHealth(bounds, marker, seeded);
            float radiusSquared = ellipseRadiusSquared(bounds, point);
            require(radiusSquared < 1 && point.dst(marker) > GameConsts.UNIT_SCALE, "sample stays in the allowed area");
            radiusSum += radiusSquared;
        }
        require(Math.abs(radiusSum / samples - .5) < .02, "area-uniform samples have mean normalized squared radius near 1/2");
    }

    private static float ellipseRadiusSquared(Rectangle bounds, Vector2 point) {
        float x = 2 * (point.x - bounds.x) / bounds.width - 1;
        float y = 2 * (point.y - bounds.y) / bounds.height - 1;
        return x * x + y * y;
    }

    private void verifyVisitInventory(Loadout ordinary) {
        var engine = GAME.getEngine();
        var actor = player();
        var loadout = Loadout.MAPPER.get(actor);
        var controller = Controller.MAPPER.get(actor);
        var initial = loadout.getWeapon();
        require(loadout != ordinary && initial == ordinary.getWeapon(), "arena starts with a separate loadout and the session weapon");
        for (String id : GAME.getGameData().weaponsData().weapons().keySet()) {
            controller.collectWeapon(id);
            controller.collectWeapon(id);
        }
        var cycle = controller.getWeaponCycle();
        require(cycle.size() == GAME.getGameData().weaponsData().weapons().size() && new HashSet<>(cycle).size() == cycle.size(),
            "every distinct weapon fits and duplicate pickups do not grow the cycle");
        var controllerSystem = engine.getSystem(ControllerSystem.class);
        for (int i = 1; i <= cycle.size(); i++) {
            if (i == 1) { Gdx.input.getInputProcessor().keyDown(Input.Keys.R); }
            else { controller.getInputListener().offer(InputAction.CYCLE_ARENA_WEAPON, 1); }
            controllerSystem.processEntity(actor, 0f);
            if (i == 1) { Gdx.input.getInputProcessor().keyUp(Input.Keys.R); }
            require(loadout.getWeapon().id().equals(cycle.get(i % cycle.size())) && ordinary.getWeapon() == initial,
                "R and the Y action cycle in order without changing ordinary equipment");
        }
        Gdx.input.getInputProcessor().keyDown(Input.Keys.R);
        controllerSystem.processEntity(actor, 0f);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.R);
        require(loadout.getWeapon() != initial, "visit ends with a different weapon equipped");
    }

    private static int spawnArea(List<MapTriggers.SpawnArea> areas, Entity enemy) {
        var transform = Transform.MAPPER.get(enemy);
        int area = 0;
        while (area < areas.size() && !areas.get(area).polygon().contains(transform.centerX(), transform.centerY())) { area++; }
        return area;
    }

    private void verifySharedRoster(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var bus = engine.getSystem(EventSystem.class);
        var areas = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        var marker = tiled.getMapTriggers().points().stream().filter(p -> p.name().equals("Dredge")).findFirst().orElseThrow();
        var expected = List.of("brainless_undead", "undead_archer", "bone_soldier", "cultist",
            "cultist_archer", "viper", "reaper", "raider");
        var unlockRounds = List.of(1, 1, 5, 10, 10, 20, 25, 30);
        var baseXp = List.of(5, 5, 10, 15, 15, 20, 25, 30);
        for (int round = 1; round <= 35; round++) {
            int currentRound = round;
            int count = (int) unlockRounds.stream().filter(unlock -> unlock <= currentRound).count();
            var roster = HoardSystem.unlockedRoster(round);
            require(roster.equals(expected.subList(0, count)), "shared pool at round " + round);
            var tickets = HoardSystem.spawnTickets(roster);
            require(roster.containsAll(tickets), "only unlocked types can be rolled");
            for (var key : roster) {
                boolean archer = List.of("undead_archer", "cultist_archer", "viper").contains(key);
                require(java.util.Collections.frequency(tickets, key) == (archer ? 1 : 2),
                    "archers receive half the spawn weight of melee at round " + round);
            }
        }
        var observed = new HashSet<String>();
        hoard.bind(tiled, areas, new Random(537) {
            @Override public int nextInt(int bound) { return bound == 10 ? 9 : super.nextInt(bound); }
        });
        for (int round = 1; round <= 35; round++) {
            int bosses = 0;
            for (var enemy : enemies) {
                var atlasKey = Animation2D.MAPPER.get(enemy).getAtlasKey();
                var weaponId = Loadout.MAPPER.get(enemy).getWeaponId();
                var key = GAME.getGameData().actorsData().enemies().entrySet().stream()
                    .filter(e -> e.getValue().animation().atlasKey().equals(atlasKey) && e.getValue().weapon().equals(weaponId))
                    .map(java.util.Map.Entry::getKey).findFirst().orElseThrow();
                var transform = Transform.MAPPER.get(enemy);
                int points;
                if (key.equals("dredge")) {
                    bosses++;
                    var health = Health.MAPPER.get(enemy);
                    require(health.getCurrent() == 300 + 15 * round && health.getMaximum() == health.getCurrent(),
                        "Dredge starts at full scaled health in round " + round);
                    require(enemy.getComponent(ArenaGuard.class) != null
                        && Math.abs(transform.centerX() / GameConsts.UNIT_SCALE - marker.position().x) < .01f
                        && Math.abs(transform.centerY() / GameConsts.UNIT_SCALE - marker.position().y) < .01f,
                        "Dredge spawns at the authored point");
                    points = 100;
                } else {
                    int zone = spawnArea(areas, enemy);
                    require(zone < areas.size() && expected.contains(key) && unlockRounds.get(expected.indexOf(key)) <= round,
                        "round " + round + " uses only the unlocked shared roster: " + key);
                    if (key.equals("viper")) { require(BowAim.MAPPER.has(enemy), "Viper can use its authored bow"); }
                    points = baseXp.get(expected.indexOf(key));
                }
                boolean firstOfType = observed.add(key);
                if (firstOfType || key.equals("dredge")) {
                    if (firstOfType) { verifyAuthoredMoveSpeed(enemy, key); }
                    if (firstOfType && !key.equals("dredge")) { verifyRosterAttack(enemy, key); }
                    var run = hoard.run();
                    long prior = run.score();
                    bus.emitEvent(new Event.Player.KilledEnemy(player(), enemy));
                    bus.update(0f);
                    require(run.score() - prior == Math.round(points * (100 + 5 * round) / 100.0),
                        "round-scaled bounty for enemy type " + key);
                }
            }
            require(bosses == (round % 5 == 0 ? 1 : 0), "one Dredge every fifth round only");
            while (enemies.size() > 0) {
                var enemy = enemies.first();
                tiled.retireCorpse(enemy);
                engine.removeEntity(enemy);
            }
            hoard.run().advance(120);
        }
        require(observed.size() == 9, "all eight non-Dredges and Dredge appeared: " + observed);
    }

    private void verifyAuthoredMoveSpeed(Entity enemy, String key) {
        float expected = GAME.getGameData().actorsData().enemies().get(key).moveSpeed();
        var move = Move.MAPPER.get(enemy);
        var body = Physic.MAPPER.get(enemy).getBody();
        try {
            move.getDirection().set(3, 4);
            GAME.getEngine().getSystem(PhysicMoveSystem.class).processEntity(enemy, 0);
            require(Math.abs(body.getLinearVelocity().len() - expected) < .0001f,
                key + " uses its authored move_speed as the Box2D velocity magnitude");
        } finally {
            move.getDirection().setZero();
            body.setLinearVelocity(0, 0);
        }
    }

    private void verifyRosterAttack(Entity enemy, String key) {
        var engine = GAME.getEngine();
        var awareness = engine.getSystem(AwarenessSystem.class);
        var enemyBody = Physic.MAPPER.get(enemy).getBody();
        var playerBody = Physic.MAPPER.get(player()).getBody();
        var home = enemyBody.getPosition().cpy();
        var original = playerBody.getPosition().cpy();
        boolean bow = BowAim.MAPPER.has(enemy);
        try {
            enemyBody.setTransform(-100, -100, 0);
            Facing.MAPPER.get(enemy).setDirection(Facing.Direction.RIGHT);
            // Archers must attack well outside melee range, melee fighters must close it.
            playerBody.setTransform(-100 + (bow ? 2f : .85f), -100, 0);
            var combat = Combat.MAPPER.get(enemy);
            awareness.processEntity(enemy, 0);
            require(!combat.hasAttackRequest(), "no instant attack from " + key);
            awareness.processEntity(enemy, .35f);
            require(combat.requestedTarget() == player(), "authored profile found an attack for " + key);
            engine.getSystem(CombatSystem.class).processEntity(enemy, 0);
            require(bow ? BowAim.MAPPER.get(enemy).isAiming() && !combat.isAttacking()
                : combat.isAttacking() && combat.getSensor() == null, "draw or committed windup for " + key);
        } finally {
            enemyBody.setTransform(home, 0);
            playerBody.setTransform(original, 0);
        }
    }

    private int reinforcementHp(TiledSystem tiled, HoardSystem hoard, boolean batched) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var map = contactArena();
        var damageRandom = MathUtils.random;
        try {
            tiled.setMap(map, ManifestLoader.parseMapManifest("hoard_mode"));
            require(hoard.run().round() == 1 && enemies.size() > 0,
                "fresh mount uses SpawnArea4 even when the first three are undersized");
            for (int i = enemies.size() - 1; i >= 0; i--) {
                var enemy = enemies.get(i);
                tiled.retireCorpse(enemy);
                engine.removeEntity(enemy);
            }
            var area = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
            // Start at the first undersized area. Only area four can fit one reinforcement.
            hoard.bind(tiled, area, new Random(1) {
                @Override public int nextInt(int bound) { return 0; }
                @Override public float nextFloat() { return .5f; }
                @Override public int nextInt(int origin, int bound) {
                    if (origin == 4 && bound == 9) { return 4; }
                    require(origin == 30 && bound == 51, "reinforcement interval roll");
                    return 30;
                }
            });
            hoard.run().advance(14 * 120);
            long roundStartStep = physics.completedSteps();
            var guards = new java.util.ArrayList<Entity>();
            for (var enemy : enemies) {
                if (enemy.getComponent(ArenaGuard.class) != null) { guards.add(enemy); }
            }
            require(guards.size() == 3, "three center Dredges spawn through round 15");
            var boss = guards.getFirst();
            var guard = boss.getComponent(ArenaGuard.class);
            require(guard != null, "milestone spawned a guard");
            var playerBody = Physic.MAPPER.get(player()).getBody();
            var original = playerBody.getPosition().cpy();
            var actor = Actor.MAPPER.get(player());
            var center = tiled.getMapTriggers().areas().stream()
                .filter(a -> "round_health_spawn".equals(a.behaviorKey())).findFirst().orElseThrow().bounds();
            verifyGuardVision(boss, guard, center.x + center.width);
            verifyGuardArrowChase(boss, guard);
            var awareness = engine.getSystem(AwarenessSystem.class);
            awareness.setProcessing(true);
            try {
                verifyGuardVolley(guards);
                verifyGuardLunge(boss);
            } finally {
                awareness.setProcessing(false);
            }
            Health.MAPPER.get(boss).damage(Integer.MAX_VALUE);
            engine.getSystem(DeathSystem.class).processEntity(boss, 0);
            var objects = engine.getEntitiesFor(Family.all(Pickup.class).get());
            var bag = java.util.stream.StreamSupport.stream(objects.spliterator(), false)
                .filter(e -> e.getComponent(Pickup.class).weapon() != null).findFirst().orElseThrow();
            var id = bag.getComponent(Pickup.class).weapon();
            require(GAME.getGameData().weaponsData().weapons().containsKey(id)
                && !id.equals("basic_bow") && !id.equals("basic_arrow"), "eligible loaded payload");
            var bagAt = Physic.MAPPER.get(bag).getBody().getPosition();
            playerBody.setTransform(bagAt.x - actor.feetX(), bagAt.y - actor.feetY(), 0);
            playerBody.setAwake(true);
            var equipped = Loadout.MAPPER.get(player()).getWeapon();
            playerBody.getWorld().step(GameConsts.PHYSIC_INTERVAL, 6, 2);
            tiled.collectPickups();
            require(!objects.contains(bag, true) && Controller.MAPPER.get(player()).getWeaponCycle().contains(id)
                && Loadout.MAPPER.get(player()).getWeapon() == equipped, "bag collected without replacing equipped weapon");
            playerBody.setTransform(original, 0);
            // The guard exercises ran real fixed ticks in round 15. Advance only the rest of that round.
            float dt = GameConsts.PHYSIC_INTERVAL;
            double guardTime = (physics.completedSteps() - roundStartStep) * (double) dt;
            hoard.run().advance(120 - guardTime + .000001);
            require(hoard.run().round() == 16, "reinforcement fixture starts at the next round boundary");
            // Empty the arena so the sword can only contact the first reinforcement.
            for (int i = enemies.size() - 1; i >= 0; i--) {
                var opening = enemies.get(i);
                tiled.retireCorpse(opening);
                engine.removeEntity(opening);
            }
            var player = player();
            Loadout.MAPPER.get(player).setWeapon("basic_sword");
            Facing.MAPPER.get(player).setDirection(Facing.Direction.RIGHT);
            var attack = Combat.MAPPER.get(player);
            physics.setProcessing(false);
            attack.requestAttack(Combat.AttackSlot.PRIMARY);
            for (int i = 0; i < 60 && attack.getSensor() == null; i++) { engine.update(dt); }
            require(attack.getSensor() != null, "armed before the reinforcement tick");
            hoard.run().advance(30 - dt / 2);
            physics.setProcessing(true);
            MathUtils.random = new Random(6); // Same primary sword damage roll in both layouts.
            if (batched) {
                engine.update(2 * dt);
            } else {
                engine.update(dt);
                require(enemies.size() == 1 && Health.MAPPER.get(enemies.first()).remainingRatio() == 1f,
                    "spawned after step one, not hit until step two");
                engine.update(dt);
            }
            require(enemies.size() == 1, "one reinforcement materialized");
            var enemy = enemies.first();
            require(tiled.owns(enemy) && !tiled.ownsArrivingEnemy(enemy), "registration hands off the map-owned enemy");
            var health = Health.MAPPER.get(enemy);
            int hp = health.getCurrent();
            if (hp != health.getMaximum()) {
                require(HealthFeedback.MAPPER.get(enemy).numbers.size() == 1, "one damage application per sword swing");
            }
            engine.update(dt);
            require(health.getCurrent() == hp, "no deferred or repeated damage on the third tick");
            return health.getMaximum() - hp;
        } finally {
            MathUtils.random = damageRandom;
            physics.setProcessing(true);
            tiled.hideMap();
            map.dispose();
            require(enemies.size() == 0 && !hoard.isActive(), "no reinforcement survives unmount");
        }
    }

    private enum LungeTrial { HIT, DODGE, COVER, FREE }

    private void verifyGuardLunge(Entity boss) {
        require(boss.getComponent(DredgeLunge.class) != null, "Hoard guard has lunge");
        float dt = GameConsts.PHYSIC_INTERVAL;
        verifyGuardSwing(boss, LungeTrial.HIT, dt);
        verifyGuardSwing(boss, LungeTrial.HIT, 2 * dt);
        verifyGuardSwing(boss, LungeTrial.DODGE, dt);
        verifyGuardSwing(boss, LungeTrial.COVER, dt);
        for (float frame : new float[]{dt / 2, dt, 2 * dt}) {
            float travel = verifyGuardSwing(boss, LungeTrial.FREE, frame);
            require(Math.abs(travel - 61.511f) < .01f, "demo lunge travel at frame delta " + frame + ": " + travel);
        }
    }

    private float verifyGuardSwing(Entity boss, LungeTrial trial, float frame) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var player = player();
        var playerBody = Physic.MAPPER.get(player).getBody();
        var bossBody = Physic.MAPPER.get(boss).getBody();
        var savedPlayer = playerBody.getPosition().cpy();
        var savedBoss = bossBody.getPosition().cpy();
        var attack = Combat.MAPPER.get(boss);
        var animation = Animation2D.MAPPER.get(boss);
        var health = Health.MAPPER.get(player);
        var ai = ActorAi.MAPPER.get(boss);
        int hp = health.getCurrent();
        float dt = GameConsts.PHYSIC_INTERVAL, scale = GameConsts.UNIT_SCALE;
        var input = Gdx.input.getInputProcessor();
        Body wall = null;
        try {
            attack.tickCooldown(1f);
            ai.recover(0f);
            ai.forgetTarget();
            bossBody.setTransform(-100, -100, 0);
            var feet = AwarenessSystem.feet(boss);
            var playerActor = Actor.MAPPER.get(player);
            playerBody.setTransform(feet.x + 76 * scale - playerActor.feetX(), feet.y - playerActor.feetY(), 0);
            physics.resetClock();
            long previousAttack = attack.getAttackId();
            for (int i = 0; i < 10 && !attack.isAttacking(); i++) { engine.update(dt); }
            require(attack.getAttackId() == previousAttack + 1 && attack.isAttacking(), "guard commits at extended range");
            require(AwarenessSystem.feet(player).x - AwarenessSystem.feet(boss).x > 60 * scale,
                "commit well outside planted range");
            var start = bossBody.getPosition().cpy();
            // Equalize the gap after the randomized approach ticks, without changing the committed direction.
            float playerY = playerBody.getPosition().y;
            playerBody.setTransform(AwarenessSystem.feet(boss).x + 76 * scale - playerActor.feetX(), playerY, 0);
            switch (trial) {
                case HIT -> { }
                case DODGE -> input.keyDown(Input.Keys.W);
                case COVER -> wall = lungeWall(bossBody, AwarenessSystem.feet(player).x - 18 * scale, feet.y);
                case FREE -> playerBody.setTransform(playerBody.getPosition().x + 100 * scale, playerBody.getPosition().y, 0);
            }
            int startup = 0, active = 0;
            boolean hammerContact = false;
            float lastStride = Float.POSITIVE_INFINITY;
            for (int i = 0; i < 120 && attack.isAttacking(); i++) {
                long step = physics.completedSteps();
                float x = bossBody.getPosition().x;
                engine.update(frame);
                int stepped = (int) (physics.completedSteps() - step);
                if (!attack.isAttacking()) { break; }
                require(attack.getAttackFacing() == Facing.Direction.RIGHT
                    && Facing.MAPPER.get(boss).getDirection() == Facing.Direction.RIGHT, "lunge direction stays committed");
                var sensor = attack.getSensor();
                require((sensor != null) == animation.hasReachedFrame(attack.getActiveAttack().hitFrame()),
                    "damage sensor starts at the authored frame");
                if (sensor == null) {
                    startup += stepped;
                    require(bossBody.getPosition().equals(start) && health.getCurrent() == hp, "Dredge plants for windup");
                } else {
                    active += stepped;
                    require(sensor.getBody() == bossBody, "active hitbox moves with Dredge");
                    if (trial == LungeTrial.DODGE) {
                        require(bossBody.getPosition().y == start.y, "sidestep cannot steer the lunge");
                    } else if (trial == LungeTrial.HIT || trial == LungeTrial.COVER) {
                        hammerContact |= overlapsHurtbox(sensor, playerBody);
                        if (trial == LungeTrial.HIT && hammerContact) {
                            require(health.getCurrent() < hp, "damage resolves on the contact tick, not on the next frame");
                        }
                    } else if (trial == LungeTrial.FREE && stepped > 0) {
                        float stride = (bossBody.getPosition().x - x) / stepped;
                        require(stride > 0 && stride < lastStride, "free lunge eases forward on each active batch");
                        lastStride = stride;
                    }
                }
            }
            require(startup == 20 && active == 12, "fixed windup and active window: " + startup + "/" + active);
            require(!attack.isAttacking() && attack.getSensor() == null && ai.recovering(), "fixture is gone in recovery");
            float travel = (bossBody.getPosition().x - start.x) / scale;
            require(travel > 35f, "Dredge closes the gap: " + travel);
            if (trial == LungeTrial.COVER) {
                require(hammerContact && AwarenessSystem.feet(boss).x < wall.getPosition().x && travel < 50f,
                    "thin wall stops the lunge and shields a player overlapping the hammer sensor");
            }
            if (trial == LungeTrial.FREE || trial == LungeTrial.DODGE) {
                require(bossBody.getLinearVelocity().isZero(), "lunge drive ends before recovery");
            }
            if (trial == LungeTrial.DODGE) {
                require(playerBody.getPosition().y - playerY > 16 * scale, "player sidesteps using live input");
            }
            var feedback = HealthFeedback.MAPPER.get(player);
            if (trial == LungeTrial.HIT) {
                require(hammerContact && health.getCurrent() < hp && feedback != null && feedback.numbers.size() == 1,
                    "stationary player takes exactly one hit: hp " + hp + " -> " + health.getCurrent()
                        + ", feedback count " + (feedback == null ? 0 : feedback.numbers.size()) + ", dt " + frame + ", travel " + travel);
            } else {
                require(health.getCurrent() == hp, trial + " takes no lunge damage");
            }
            return travel;
        } finally {
            if (trial == LungeTrial.DODGE) { input.keyUp(Input.Keys.W); }
            if (wall != null) { wall.getWorld().destroyBody(wall); }
            health.heal(hp - health.getCurrent());
            player.remove(HealthFeedback.class);
            playerBody.setLinearVelocity(0, 0);
            bossBody.setLinearVelocity(0, 0);
            playerBody.setTransform(savedPlayer, 0);
            bossBody.setTransform(savedBoss, 0);
        }
    }

    private static boolean overlapsHurtbox(Fixture hammer, Body target) {
        for (var contact : target.getWorld().getContactList()) {
            if (!contact.isTouching()) { continue; }
            var a = contact.getFixtureA();
            var b = contact.getFixtureB();
            var other = a == hammer ? b : b == hammer ? a : null;
            if (other != null && other.getBody() == target && other.getUserData() == Hurtbox.INSTANCE) { return true; }
        }
        return false;
    }

    private static Body lungeWall(Body owner, float x, float y) {
        var wall = owner.getWorld().createBody(new BodyDef());
        wall.setTransform(x, y, 0);
        var shape = new PolygonShape();
        try {
            shape.setAsBox(.08f, 4f);
            wall.createFixture(shape, 0);
        } finally { shape.dispose(); }
        return wall;
    }

    private void verifyGuardVision(Entity boss, ArenaGuard guard, float healthAreaRightEdge) {
        var engine = GAME.getEngine();
        var awareness = engine.getSystem(AwarenessSystem.class);
        var bossBody = Physic.MAPPER.get(boss).getBody();
        var playerBody = Physic.MAPPER.get(player()).getBody();
        var bossActor = Actor.MAPPER.get(boss);
        var playerActor = Actor.MAPPER.get(player());
        var bossFeet = bossBody.getWorldPoint(new Vector2(bossActor.feetX(), bossActor.feetY())).cpy();
        var playerAt = bossFeet.cpy().add(3f - playerActor.feetX(), -playerActor.feetY());
        require(playerAt.x + playerActor.feetX() > healthAreaRightEdge, "Dredge chase fixture is outside the health area");
        playerBody.setTransform(playerAt, 0);
        var facing = Facing.MAPPER.get(boss);
        facing.setDirection(Facing.Direction.LEFT);
        awareness.processEntity(boss, .01f);
        var ai = ActorAi.MAPPER.get(boss);
        var move = Move.MAPPER.get(boss).getDirection();
        require(ai.mode() == ActorAi.Mode.ENGAGE && move.x > 0, "Dredge pursues a player behind it outside the health area");
        engine.getSystem(FacingSystem.class).update(0);
        require(facing.getDirection() == Facing.Direction.RIGHT, "Dredge turns toward the pursuit direction");

        bossBody.setTransform(guard.home().x + 1, guard.home().y, 0);
        var world = bossBody.getWorld();
        var wall = world.createBody(new BodyDef());
        var shape = new PolygonShape();
        try {
            wall.setTransform(bossFeet.x + 2, bossFeet.y, 0);
            shape.setAsBox(.125f, 1f);
            wall.createFixture(shape, 0);
            awareness.processEntity(boss, .01f);
            require(ai.mode() != ActorAi.Mode.ENGAGE && move.equals(new Vector2(-1, 0)),
                "Solid cover breaks Dredge's chase and it heads home, not to the last seen point");
        } finally {
            shape.dispose();
            world.destroyBody(wall);
        }
        awareness.processEntity(boss, .01f);
        require(ai.mode() == ActorAi.Mode.ENGAGE && move.x > 0, "Dredge reacquires a visible player on the way home");
        playerBody.setTransform(playerAt.x + 10, playerAt.y, 0);
        awareness.processEntity(boss, .01f);
        require(ai.mode() != ActorAi.Mode.ENGAGE && move.x < 0, "Leaving detection range also sends Dredge home");
        bossBody.setTransform(guard.home(), 0);
        for (int i = 0; i < 5; i++) {
            awareness.processEntity(boss, 1f);
            require(move.isZero(), "Dredge waits at spawn instead of wandering");
        }
    }

    private void verifyGuardArrowChase(Entity boss, ArenaGuard guard) {
        var engine = GAME.getEngine();
        var awareness = engine.getSystem(AwarenessSystem.class);
        var combat = engine.getSystem(CombatSystem.class);
        var player = player();
        var playerBody = Physic.MAPPER.get(player).getBody();
        var bossBody = Physic.MAPPER.get(boss).getBody();
        var savedPlayer = playerBody.getPosition().cpy();
        var playerTransform = Transform.MAPPER.get(player);
        var savedTransform = playerTransform.getPosition().cpy();
        var loadout = Loadout.MAPPER.get(player);
        var savedWeapon = loadout.getWeaponId();
        var facing = Facing.MAPPER.get(player);
        var savedFacing = facing.getDirection();
        var ai = ActorAi.MAPPER.get(boss);
        var health = Health.MAPPER.get(boss);
        var bossCombat = Combat.MAPPER.get(boss);
        var move = Move.MAPPER.get(boss).getDirection();
        var world = bossBody.getWorld();
        float dt = GameConsts.PHYSIC_INTERVAL;
        try {
            bossBody.setTransform(-100, -100, 0);
            float nearX = -100 + ai.detectionRadius() - 1;
            float farX = nearX + 2;
            float feetY = -100 + Actor.MAPPER.get(boss).feetY();
            float playerY = feetY - Actor.MAPPER.get(player).feetY();
            playerBody.setLinearVelocity(0, 0);
            // This low wall blocks feet-to-feet sight but is below the shot at hurtbox height.
            var wall = world.createBody(new BodyDef());
            var shape = new PolygonShape();
            try {
                wall.setTransform(-100 + ai.detectionRadius() / 2, feetY, 0);
                shape.setAsBox(.125f, .15f);
                wall.createFixture(shape, 0);
                playerBody.setTransform(nearX, playerY, 0);
                require(awareness.inRange(boss, Actor.Role.PLAYER).contains(player), "shooter can stand in range behind cover");
                awareness.processEntity(boss, dt);
                require(ai.mode() == ActorAi.Mode.WANDER, "cover conceals the unprovoking player from the guard");

                playerBody.setTransform(farX, playerY, 0);
                require(awareness.inRange(boss, Actor.Role.PLAYER).isEmpty(), "bow fixture starts outside guard detection");
                int before = health.getCurrent();
                launchGuardArrow(combat, player, bossBody);
                for (int i = 0; i < 120 && !combat.arrows().isEmpty(); i++) { combat.update(dt); }
                require(health.getCurrent() < before, "released player arrow hits the Dredge hurtbox");
                require(ai.retaliationTarget() == player, "an unseen player beyond detection range provokes the guard");
                awareness.processEntity(boss, dt);
                require(move.x > 0 && ai.mode() == ActorAi.Mode.ENGAGE, "provoked guard pursues beyond detection range");

                playerBody.setTransform(nearX, playerY, 0);
                awareness.processEntity(boss, dt);
                require(move.x > 0 && ai.retaliationTarget() == player, "cover does not drop arrow pursuit in range");
            } finally {
                shape.dispose();
                world.destroyBody(wall);
            }
            playerBody.setTransform(farX + 1, playerY, 0);
            awareness.processEntity(boss, dt);
            require(move.x > 0 && ai.retaliationTarget() == player, "moving farther away does not drop arrow pursuit");

            boolean sensorContact = false;
            for (int i = 0; i < 240 && !touching(bossBody, playerBody, false); i++) {
                sensorContact |= touching(bossBody, playerBody, true);
                awareness.processEntity(boss, dt);
                require(ai.retaliationTarget() == player && !bossCombat.hasAttackRequest(),
                    "the guard closes the gap without requesting melee before solid contact");
                engine.getSystem(PhysicMoveSystem.class).processEntity(boss, dt);
                world.step(dt, 6, 2);
            }
            require(sensorContact && touching(bossBody, playerBody, false), "pursuit passes sensor overlap and reaches solid feet");
            require(ai.retaliationTarget() == null, "solid contact ends forced pursuit during the physics step");
            awareness.processEntity(boss, 0);
            require(!bossCombat.hasAttackRequest(), "ordinary melee starts a fresh decision after contact");
            awareness.processEntity(boss, .25f);
            require(bossCombat.requestedTarget() == player, "normal melee resumes after contact");

            // Awareness runs before Combat: a new arrow can land on the tick a melee request is due.
            bossBody.setLinearVelocity(0, 0);
            playerBody.setLinearVelocity(0, 0);
            playerBody.setTransform(bossBody.getPosition().x + .9f, playerY, 0);
            world.step(dt, 6, 2);
            require(!touching(bossBody, playerBody, false), "second shot starts in melee range without solid contact");
            combat.cancelCombat(player);
            Combat.MAPPER.get(player).tickCooldown(5);
            launchGuardArrow(combat, player, bossBody);
            int before = health.getCurrent();
            combat.update(.25f);
            require(health.getCurrent() < before && ai.retaliationTarget() == player, "second shot provokes the guard");
            require(!bossCombat.isAttacking() && !bossCombat.hasAttackRequest(),
                "arrow impact supersedes a melee request that has not committed yet");
        } finally {
            combat.cancelCombat(player);
            loadout.setWeapon(savedWeapon);
            facing.setDirection(savedFacing);
            playerTransform.getPosition().set(savedTransform);
            bossCombat.consumeAttackRequest();
            ai.forgetTarget();
            move.setZero();
            bossBody.setLinearVelocity(0, 0);
            bossBody.setTransform(guard.home(), 0);
            playerBody.setLinearVelocity(0, 0);
            playerBody.setTransform(savedPlayer, 0);
        }
    }

    private static void launchGuardArrow(CombatSystem combat, Entity player, Body target) {
        // Launch uses the render transform, which has not been updated since we teleported the player.
        Transform.MAPPER.get(player).getPosition().set(Physic.MAPPER.get(player).getBody().getPosition());
        var loadout = Loadout.MAPPER.get(player);
        loadout.setWeapon("basic_bow");
        Facing.MAPPER.get(player).setDirection(Facing.Direction.LEFT);
        var aim = BowAim.MAPPER.get(player);
        combat.drawBow(player, aim, loadout.getWeapon());
        require(aim.isAiming(), "player draws the equipped bow");
        var bounds = GAME.getGameData().actorsData().enemies().get("dredge").hitboxBounds();
        aim.lockAt(target.getWorldPoint(new Vector2(bounds.x1() + bounds.x2(), bounds.y1() + bounds.y2())
            .scl(.5f * GameConsts.UNIT_SCALE)), 0);
        combat.launch(player, aim);
    }

    private static boolean touching(Body first, Body second, boolean sensor) {
        for (var contact : first.getWorld().getContactList()) {
            if (!contact.isTouching()) { continue; }
            var a = contact.getFixtureA();
            var b = contact.getFixtureB();
            if ((a.isSensor() || b.isSensor()) == sensor && (a.getBody() == first && b.getBody() == second
                || a.getBody() == second && b.getBody() == first)) { return true; }
        }
        return false;
    }

    /** Same opening for all three real hammer guards, in separated lanes outside the map. */
    private void verifyGuardVolley(List<Entity> guards) {
        float x = -100f, y = -100f;
        var engine = GAME.getEngine();
        var awareness = engine.getSystem(AwarenessSystem.class);
        var actor = Actor.MAPPER.get(player());
        var playerBody = Physic.MAPPER.get(player()).getBody();
        var original = playerBody.getPosition().cpy();
        playerBody.setTransform(x - actor.feetX(), y - actor.feetY(), 0);
        for (int i = 0; i < guards.size(); i++) {
            var guard = guards.get(i);
            require(Loadout.MAPPER.get(guard).getWeaponId().equals("black_hammer"), "Dredge volley uses authored hammers");
            var feet = Actor.MAPPER.get(guard);
            Physic.MAPPER.get(guard).getBody().setTransform(x - feet.feetX() - 1.5f, y - feet.feetY() + (i - 1) * .6f, 0);
        }
        float dt = GameConsts.PHYSIC_INTERVAL;
        engine.getSystem(PhysicSystem.class).resetClock();
        var primary = Loadout.MAPPER.get(guards.getFirst()).getWeapon().primary();
        var animation = primary.animation();
        float windup = Math.max(.28f, primary.hitFrame() * animation.frameInterval() / animation.speed());
        // The commitment tick does not sample playback. The next tick installs frame zero.
        // Allow floating-point rounding if the authored hit lies on an exact tick boundary.
        double playbackTicks = windup / dt;
        int earliestHitOffset = 1 + (int) Math.ceil(playbackTicks - .0001);
        int latestHitOffset = 1 + (int) Math.ceil(playbackTicks + .0001);
        int[] commitTicks = new int[guards.size()];
        Arrays.fill(commitTicks, -1);
        int first = -1, last = -1, peak = 0;
        for (int tick = 0; tick < 135; tick++) {
            engine.update(dt);
            int committed = 0, active = 0;
            for (int i = 0; i < guards.size(); i++) {
                var guard = guards.get(i);
                var combat = Combat.MAPPER.get(guard);
                if (combat.getAttackId() > 0) {
                    if (commitTicks[i] < 0) { commitTicks[i] = tick; }
                    committed++;
                }
                var sensor = combat.getSensor();
                if (sensor != null) { active++; }
                if (last >= 0 && combat.isAttacking()) {
                    require(combat.getAttackFacing() == Facing.Direction.RIGHT
                        && Facing.MAPPER.get(guard).getDirection() == Facing.Direction.RIGHT
                        && Move.MAPPER.get(guard).getDirection().isZero(), "Losing sight cannot steer a committed hammer swing");
                }
                if (tick < 2) { require(combat.getAttackId() == 0, "Dredge respects melee decision delay"); }
                int sinceCommit = tick - commitTicks[i];
                if (commitTicks[i] < 0 || sinceCommit < earliestHitOffset) {
                    require(sensor == null, "Dredge keeps its authored pre-hit windup");
                } else if (sinceCommit == latestHitOffset) {
                    require(sensor != null, "Dredge reaches its authored hit frame after the player leaves");
                }
            }
            if (committed > 0 && first < 0) { first = tick; }
            if (committed == guards.size() && last < 0) {
                require(active == 0 && guards.stream().allMatch(guard -> Combat.MAPPER.get(guard).isAttacking()),
                    "All guards have committed but not hit when the player retreats");
                last = tick;
                playerBody.setTransform(x + 10f, y, 0);
                require(guards.stream().allMatch(guard -> awareness.inRange(guard, Actor.Role.PLAYER).isEmpty()),
                    "Player retreats beyond every guard's detection range");
            }
            peak = Math.max(peak, active);
        }
        require(first >= 2 && last >= first && last <= 7 && peak == 3, "Every eligible guard commits; active swings can overlap");
        for (var guard : guards) {
            var combat = Combat.MAPPER.get(guard);
            require(!combat.isAttacking() && combat.getAttackId() == 1 && !ActorAi.MAPPER.get(guard).recovering(),
                "Leaving detection range cannot cancel a whiff or start another hammer attack");
            var body = Physic.MAPPER.get(guard).getBody();
            body.setTransform(guard.getComponent(ArenaGuard.class).home(), 0);
            body.setLinearVelocity(0, 0);
        }
        playerBody.setTransform(original, 0);
    }

    private static TiledMap contactArena() {
        var map = new TiledMap();
        var props = map.getProperties();
        props.put("width", 64);
        props.put("height", 64);
        props.put("tilewidth", 32);
        props.put("tileheight", 32);
        var triggers = new MapLayer();
        triggers.setName("Triggers");
        map.getLayers().add(triggers);
        // The candidate overlaps a right-facing sword but not the player's solid feet.
        var start = new PointMapObject(23.75f * 32, 25 * 32);
        start.setName("PlayerSpawn");
        start.getProperties().put("type", "SpawnPoint");
        start.getProperties().put("spawnType", "PLAYER");
        start.getProperties().put("facing", "RIGHT");
        triggers.getObjects().add(start);
        var boss = new PointMapObject(256, 256);
        boss.setName("Dredge");
        boss.getProperties().put("type", "SpawnPoint");
        boss.getProperties().put("spawnType", "ENEMY");
        boss.getProperties().put("spawnKey", "dredge");
        boss.getProperties().put("behaviorKey", "idle_dredge");
        boss.getProperties().put("facing", "DOWN");
        triggers.getObjects().add(boss);
        var pack = new com.badlogic.gdx.maps.objects.EllipseMapObject(220, 220, 72, 72);
        pack.setName("RoundHealth");
        pack.getProperties().put("type", "SpawnArea");
        pack.getProperties().put("spawnType", "ITEM");
        pack.getProperties().put("spawnKey", "health_pack_2");
        pack.getProperties().put("behaviorKey", "round_health_spawn");
        pack.getProperties().put("facing", "DOWN");
        triggers.getObjects().add(pack);
        for (int i = 1; i <= 4; i++) {
            float end = i == 4 ? 960 : 641;
            var area = new PolygonMapObject(new float[]{640, 640, end, 640, end, end, 640, end});
            area.setName("SpawnArea" + i);
            area.getProperties().put("type", "SpawnArea");
            area.getProperties().put("spawnType", "ENEMY");
            area.getProperties().put("spawnKey", "brainless_undead");
            area.getProperties().put("behaviorKey", "agroWanderingHoard");
            area.getProperties().put("facing", "DOWN");
            triggers.getObjects().add(area);
        }
        return map;
    }

    private Entity verifyMixedWaves(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        int start = enemies.size();
        boolean[] archer = {true, true, false, true, false, false, false, true, true, false, false, true, false, true, false, true, false, true};
        var area = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        hoard.bind(tiled, area, new Random(37) {
            int slot;
            int drops;
            @Override public int nextInt(int bound) {
                return switch (bound) {
                    case 3 -> slot < archer.length ? (archer[slot++] ? 2 : 0) : super.nextInt(bound);
                    case 4 -> 0;
                    case 10 -> drops++ == 0 ? 9 : 0;
                    default -> super.nextInt(bound);
                };
            }
            @Override public int nextInt(int origin, int bound) { return bound - 1; }
        });
        require(enemies.size() == start + 10, "ten scripted opening slots");
        hoard.run().advance(50);
        require(enemies.size() == start + archer.length, "eight scripted reinforcement slots");
        for (int i = 0; i < archer.length; i++) {
            var enemy = enemies.get(start + i);
            require(tiled.owns(enemy) && BowAim.MAPPER.has(enemy) == archer[i]
                && Loadout.MAPPER.get(enemy).getWeaponId().equals(archer[i] ? "basic_bow" : "basic_sword"),
                "map-owned wave actor matches roll for slot " + i);
        }
        var bus = engine.getSystem(EventSystem.class);
        var pickups = engine.getEntitiesFor(Family.all(Pickup.class).get());
        bus.emitEvent(new Event.Player.KilledEnemy(player(), enemies.get(start)));
        bus.update(0);
        require(pickups.size() == 0, "one nonwinning 10% roll produces no pickup");
        var victim = enemies.get(start + 2);
        bus.emitEvent(new Event.Player.KilledEnemy(player(), victim));
        bus.update(0);
        require(hoard.run().kills() == 2 && hoard.run().score() == 10, "both starter types earn the same XP across spawn areas");
        require(pickups.size() == 1 && pickups.first().getComponent(Pickup.class).rule().amount() == 50,
            "the next independent, winning roll drops one 50 HP pack");
        var body = Physic.MAPPER.get(player()).getBody();
        var actor = Actor.MAPPER.get(player());
        var position = body.getPosition().cpy();
        var drop = Physic.MAPPER.get(pickups.first()).getBody().getPosition().cpy();
        var victimActor = Actor.MAPPER.get(victim);
        require(drop.epsilonEquals(Physic.MAPPER.get(victim).getBody().getWorldPoint(
            new com.badlogic.gdx.math.Vector2(victimActor.feetX(), victimActor.feetY())), .0001f), "kill drop at victim feet");
        Health.MAPPER.get(player()).damage(60);
        body.setTransform(drop.x - actor.feetX(), drop.y - actor.feetY(), 0);
        body.setAwake(true);
        body.getWorld().step(GameConsts.PHYSIC_INTERVAL, 6, 2);
        tiled.collectPickups();
        require(pickups.size() == 0 && Health.MAPPER.get(player()).getCurrent() == 990, "small pack heals exactly 50 on contact");
        requireHealMarker(player(), 50);
        body.setTransform(position, 0);
        return victim;
    }

    private static void requireHealMarker(Entity player, int amount) {
        var feedback = HealthFeedback.MAPPER.get(player);
        require(feedback != null && feedback.numbers.size() == 1, "pickup creates exactly one marker");
        var number = feedback.numbers.getFirst();
        var position = Physic.MAPPER.get(player).getBody().getPosition();
        var size = Transform.MAPPER.get(player).getSize();
        require(number.isHeal() && number.amount == amount && number.age == 0,
            "fresh marker reports actual HP gained: " + amount);
        require(number.x == position.x + size.x / 2 && number.y == position.y + size.y,
            "marker is anchored at the player's position when collected");
    }

    private void verifyLethalContactAtRollover(HoardSystem hoard, Entity attacker) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var awareness = engine.getSystem(AwarenessSystem.class);
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var player = player();
        var health = Health.MAPPER.get(player);
        var attack = Combat.MAPPER.get(attacker);
        // Arm the sword without stepping the world: its first native contact must be the rollover tick.
        awareness.setProcessing(false);
        physics.setProcessing(false);
        Physic.MAPPER.get(attacker).getBody().setTransform(-20, -20, 0);
        Physic.MAPPER.get(player).getBody().setTransform(-19.15f, -20, 0);
        Loadout.MAPPER.get(attacker).setWeapon("basic_sword");
        Facing.MAPPER.get(attacker).setDirection(Facing.Direction.RIGHT);
        health.damage(health.getCurrent() - 1);
        attack.requestAttack(Combat.AttackSlot.PRIMARY);
        for (int i = 0; i < 60 && attack.getSensor() == null; i++) { engine.update(1f / 60); }
        require(attack.getSensor() != null && health.getCurrent() == 1, "live player before sword contact");

        var run = hoard.run();
        run.advance(run.remainingSeconds() - .001);
        int waveSize = enemies.size();
        physics.setProcessing(true);
        engine.update(GameConsts.PHYSIC_INTERVAL);
        awareness.setProcessing(true);
        require(!health.isAlive() && run.isGameOver() && run.round() == 1 && run.remainingSeconds() == 1
            && enemies.size() == waveSize, "lethal sword contact beats the same tick's heal and opening batch");
        require(Animation2D.MAPPER.get(player).getType() == Animation2D.AnimationType.DEATH
            && !Physic.MAPPER.get(player).getBody().isActive(), "death is visible without another frame");
    }

    private void verifyBaselineWalk(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var locomotion = engine.getSystem(PhysicMoveSystem.class);
        var physics = engine.getSystem(PhysicSystem.class);
        var player = player();
        var body = Physic.MAPPER.get(player).getBody();
        var move = Move.MAPPER.get(player);
        float dt = GameConsts.PHYSIC_INTERVAL;
        physics.resetClock();
        // Outside the map, the player's feet can push a real wave enemy with no other contacts.
        body.setTransform(-100, -100, 0);
        body.setLinearVelocity(0, 0);
        move.getDirection().set(1, 0);
        long steps = physics.completedSteps();
        locomotion.update(0f);
        physics.update(0f);
        require(physics.completedSteps() == steps && body.getLinearVelocity().x == move.getMaxSpeed(),
            "Hoard walk sets velocity even on a frame with no fixed tick");

        var enemy = engine.getEntitiesFor(Family.all(ActorAi.class, Physic.class, Move.class).get()).first();
        var other = Physic.MAPPER.get(enemy).getBody();
        var feet = Actor.MAPPER.get(player);
        var otherFeet = Actor.MAPPER.get(enemy);
        other.setTransform(-100 + feet.feetX() - otherFeet.feetX() + .65f,
            -100 + feet.feetY() - otherFeet.feetY(), 0);
        other.setLinearVelocity(0, 0);
        Move.MAPPER.get(enemy).getDirection().setZero();
        for (int i = 0; i < 10; i++) {
            locomotion.update(dt);
            physics.update(dt);
        }
        require(body.getLinearVelocity().x < move.getMaxSpeed() - .01f && other.getLinearVelocity().x > .01f,
            "walk probe must have sustained solid enemy contact");
        steps = physics.completedSteps();
        locomotion.update(2 * dt);
        float momentum = body.getMass() * body.getLinearVelocity().x + other.getMass() * other.getLinearVelocity().x;
        physics.update(2 * dt);
        float after = body.getMass() * body.getLinearVelocity().x + other.getMass() * other.getLinearVelocity().x;
        require(physics.completedSteps() == steps + 2 && Math.abs(after - momentum) < .001f,
            "two-tick Hoard walk retains collision impulses rather than reapplying velocity");
        remountArena(tiled, hoard);
    }

    private void updateSprintFrame(float delta) {
        var engine = GAME.getEngine();
        engine.getSystem(AnimationPlayerSystem.class).update(delta);
        engine.getSystem(AnimationSystem.class).update(delta);
        engine.getSystem(PhysicMoveSystem.class).update(delta);
        engine.getSystem(PhysicSystem.class).update(delta);
    }

    private void verifySprint(TiledSystem tiled, HoardSystem hoard) {
        verifyBaselineWalk(tiled, hoard);
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var renderer = engine.getSystem(RenderSystem.class);
        var input = GAME.getInputManager();
        var pads = Controllers.getControllers();
        boolean[] held = {false};
        int unused = ControllerMapping.UNDEFINED;
        var mapping = new ControllerMapping(0, unused, unused, unused, unused, 7, unused, unused, unused, unused,
            unused, unused, unused, unused, unused, unused, unused, unused, unused, unused) { };
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
        input.updateGamepad();
        GAME.render(); // initialize the renderer's frame for direct HUD draws
        var player = player();
        var move = Move.MAPPER.get(player);
        var body = Physic.MAPPER.get(player).getBody();
        var origin = body.getPosition().cpy();
        var animation = Animation2D.MAPPER.get(player);
        float dt = GameConsts.PHYSIC_INTERVAL, walk = move.getMaxSpeed(), walkFrameTime = dt * animation.getSpeed();
        physics.resetClock();
        require(GameplayHud.staminaText(hoard.stamina()).equals("Stamina 100%"), "new arena starts full");
        verifySprintHud(renderer, false);

        Gdx.input.getInputProcessor().keyDown(Input.Keys.B);
        move.getDirection().set(1, 1);
        updateSprintFrame(0f); // Install the walk poses before measuring playback.
        updateSprintFrame(dt);
        require(Math.abs(body.getLinearVelocity().len() - walk) < .001f && hoard.stamina() == 1d, "keyboard B does not sprint");
        require(animation.getType() == Animation2D.AnimationType.WALK
            && Math.abs(animation.getStateTime() - walkFrameTime) < .00001f, "ordinary walk plays at the authored rate");
        Gdx.input.getInputProcessor().keyUp(Input.Keys.B);
        body.setTransform(origin, 0);
        held[0] = true;
        input.updateGamepad();
        move.getDirection().setZero();
        updateSprintFrame(0f);
        updateSprintFrame(dt);
        require(body.getLinearVelocity().isZero() && hoard.stamina() == 1d, "stationary hold costs nothing");
        require(animation.getType() == Animation2D.AnimationType.IDLE
            && Math.abs(animation.getStateTime() - walkFrameTime) < .00001f, "held B does not speed up idle");
        move.getDirection().set(1, 1);
        updateSprintFrame(0f);
        updateSprintFrame(dt / 2);
        require(hoard.stamina() == 1d && Math.abs(animation.getStateTime() - walkFrameTime / 2) < .00001f,
            "a frame with no fixed tick adds no sprint playback or drain");
        updateSprintFrame(dt / 2);
        require(Math.abs(body.getLinearVelocity().len() - 1.04f * walk) < .001f && move.getMaxSpeed() == walk,
            "first sprint tick adds only a small normalized boost");
        require(Math.abs(animation.getStateTime() - (1f + 1f / 15f) * walkFrameTime) < .00001f,
            "walk playback follows the current boost");
        float before = animation.getStateTime();
        long steps = physics.completedSteps();
        updateSprintFrame(2 * dt);
        require(physics.completedSteps() == steps + 2
            && Math.abs(animation.getStateTime() - before - (2f + 5f / 15f) * walkFrameTime) < .00001f
            && Graphic.MAPPER.get(player).getRegion() == animation.getAnimation().getKeyFrame(animation.getStateTime()),
            "two running ticks in one frame advance both the ramp and walk clock");
        for (int i = 4; i <= 15; i++) {
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
            require(Math.abs(body.getLinearVelocity().len() - (1f + .04f * i) * walk) < .001f,
                "held B reaches full speed only after 15 ticks");
        }
        held[0] = false;
        input.updateGamepad();
        double beforeRelease = hoard.stamina();
        for (int i = 1; i <= 6; i++) {
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
            require(Math.abs(body.getLinearVelocity().len() - (1.6f - .1f * i) * walk) < .001f,
                "release returns to normal walking speed over six ticks");
        }
        require(hoard.stamina() < beforeRelease, "release tail costs stamina rather than granting a free boost");
        held[0] = true;
        input.updateGamepad();
        updateSprintFrame(dt);
        require(Math.abs(body.getLinearVelocity().len() - 1.04f * walk) < .001f, "a fresh tap starts at minimum boost");
        held[0] = false;
        input.updateGamepad();
        updateSprintFrame(dt);
        require(Math.abs(body.getLinearVelocity().len() - walk) < .001f, "a one-tick tap cannot produce a lingering boost");
        held[0] = true;
        input.updateGamepad();
        double first = hoard.stamina();
        move.setRooted(true);
        updateSprintFrame(dt);
        require(hoard.stamina() == first && body.getLinearVelocity().isZero(), "rooted hold neither moves nor drains");
        move.setRooted(false);
        var aim = BowAim.MAPPER.get(player);
        var loadouts = engine.getSystem(LoadoutSystem.class);
        var initialWeapon = Loadout.MAPPER.get(player).getWeapon().id();
        loadouts.equipWeapon(player, "basic_bow");
        engine.getSystem(CombatSystem.class).drawBow(player, aim, Loadout.MAPPER.get(player).getWeapon());
        require(aim.isAiming(), "bow draw active");
        first = hoard.stamina();
        engine.getSystem(ControllerSystem.class).update(0f);
        updateSprintFrame(0f);
        updateSprintFrame(dt);
        require(hoard.stamina() == first && body.getLinearVelocity().isZero(), "aiming hold neither moves nor drains");
        require(animation.getType() == Animation2D.AnimationType.ATTACK
            && Math.abs(animation.getStateTime() - dt * animation.getSpeed()) < .00001f, "held B does not speed up bow draw");
        aim.cancel();
        loadouts.equipWeapon(player, initialWeapon);

        // A solid wall stops the boosted body just like the normal velocity path.
        body.setTransform(origin, 0);
        var wall = body.getWorld().createBody(new BodyDef());
        var feet = Actor.MAPPER.get(player);
        wall.setTransform(origin.x + feet.feetX() + .75f, origin.y + feet.feetY(), 0);
        var shape = new PolygonShape();
        shape.setAsBox(.05f, 1f);
        wall.createFixture(shape, 0);
        shape.dispose();
        move.getDirection().set(1, 0);
        for (int i = 0; i < 30; i++) { updateSprintFrame(dt); }
        require(body.getPosition().x + feet.feetX() < wall.getPosition().x, "sprint respects solid collision");
        double blocked = hoard.stamina();
        before = animation.getStateTime();
        updateSprintFrame(dt);
        require(hoard.stamina() >= blocked && Math.abs(animation.getStateTime() - before - walkFrameTime) < .00001f,
            "pushing against a wall without moving neither drains nor speeds up playback");
        body.getWorld().destroyBody(wall);

        int sprintTicks = 0;
        double startingStamina = hoard.stamina();
        while (hoard.stamina() > 0d && sprintTicks * dt < 2.1f) {
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
            sprintTicks++;
        }
        require(Math.abs(sprintTicks * dt - 2 * startingStamina) <= dt + .00001 && hoard.stamina() == 0d,
            "stamina exhausts at the two-second sprint rate");
        require(GameplayHud.staminaText(hoard.stamina()).equals("Stamina 0%"), "HUD reports depletion");
        verifySprintHud(renderer, true);
        for (int i = 1; i < 6; i++) {
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
            require(Math.abs(body.getLinearVelocity().len() - (1.6f - .1f * i) * walk) < .001f
                && hoard.stamina() == 0d, "exhaustion eases off through the same ramp without refilling mid-boost");
        }
        body.setTransform(origin, 0);
        before = animation.getStateTime();
        updateSprintFrame(dt);
        double perTickRegen = dt * (.7d / 6d);
        require(Math.abs(body.getLinearVelocity().len() - walk) < .001f
            && Math.abs(animation.getStateTime() - before - walkFrameTime) < .00001f
            && hoard.stamina() == 0d, "exhausted hold walks without immediate refill");
        for (int i = 6; i < 120; i++) {
            require(hoard.stamina() == 0d, "no refill before two seconds since last drain");
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
        }
        body.setTransform(origin, 0);
        updateSprintFrame(dt);
        require(Math.abs(hoard.stamina() - perTickRegen) < .0000001d,
            "refill starts at the unchanged rate after two seconds");

        held[0] = false;
        input.updateGamepad();
        held[0] = true;
        input.updateGamepad();
        float partialRunTime = (float) (2d * hoard.stamina());
        before = animation.getStateTime();
        body.setTransform(origin, 0);
        steps = physics.completedSteps();
        updateSprintFrame(2 * dt);
        require(physics.completedSteps() == steps + 2 && Math.abs(body.getLinearVelocity().len() - walk) < .001f
            && Math.abs(animation.getStateTime() - before - (2f + partialRunTime / .25f) * walkFrameTime) < .00001f
            && hoard.stamina() == 0d,
            "a partial sprint tick exhausts and restarts the delay even within a batched frame");

        int refillTicks = 1;
        while (hoard.stamina() < 1d && refillTicks * dt < 10.7f) {
            body.setTransform(origin, 0);
            updateSprintFrame(dt);
            refillTicks++;
        }
        require(refillTicks == (int) Math.ceil((2d + 6d / .7d) / dt) && hoard.stamina() == 1d
            && Math.abs(body.getLinearVelocity().len() - walk) < .001f, "full refill takes about 10.57s including the delay and still requires release");
        held[0] = false;
        input.updateGamepad();
        held[0] = true;
        input.updateGamepad();
        updateSprintFrame(dt);
        require(hoard.stamina() < 1d, "release and repress between ticks re-arms sprint");
        double partial = hoard.stamina();
        GAME.pause();
        GAME.render();
        require(hoard.stamina() == partial && input.arenaSprintPress() == 0, "pause freezes stamina and clears input");
        GAME.resume();
        input.updateGamepad();
        move.getDirection().set(1, 0);
        updateSprintFrame(dt);
        require(hoard.stamina() == partial && input.arenaSprintPress() == 0, "resume preserves the refill delay and invalidates held B");

        held[0] = false;
        input.updateGamepad();
        held[0] = true;
        input.updateGamepad();
        loadouts.equipWeapon(player, "basic_sword");
        Combat.MAPPER.get(player).requestAttack(Combat.AttackSlot.PRIMARY);
        engine.getSystem(CombatSystem.class).update(0f);
        updateSprintFrame(0f);
        before = animation.getStateTime();
        partial = hoard.stamina();
        updateSprintFrame(dt);
        require(hoard.stamina() < partial && animation.getType() == Animation2D.AnimationType.ATTACK
            && Math.abs(animation.getStateTime() - before - dt * animation.getSpeed()) < .00001f,
            "melee plays at its authored rate even while the player is sprinting");
        partial = hoard.stamina();
        Health.MAPPER.get(player).damage(1000);
        engine.getSystem(DeathSystem.class).update(0f);
        physics.update(dt);
        require(hoard.run().isGameOver() && hoard.stamina() == partial, "terminal stamina freezes at death");
        remountArena(tiled, hoard);
        input.updateGamepad();
        require(hoard.stamina() == 1d && input.arenaSprintPress() == 0, "arena remount resets stamina and invalidates held B");
        require(tiled.requestTravel(player(), "test_tiled", "PlayerSpawn"), "leave sprint arena for ordinary map");
        tiled.applyPendingTravel();
        held[0] = false;
        input.updateGamepad();
        held[0] = true;
        input.updateGamepad();
        var ordinaryPlayer = player();
        var ordinaryMove = Move.MAPPER.get(ordinaryPlayer);
        Physic.MAPPER.get(ordinaryPlayer).getBody().setTransform(-100, -100, 0);
        ordinaryMove.getDirection().set(1, 0);
        updateSprintFrame(0f);
        var ordinaryAnimation = Animation2D.MAPPER.get(ordinaryPlayer);
        updateSprintFrame(dt);
        require(!hoard.isActive() && input.arenaSprintPress() != 0 && Physic.MAPPER.get(ordinaryPlayer).getBody()
            .getLinearVelocity().len() == ordinaryMove.getMaxSpeed()
            && ordinaryAnimation.getType() == Animation2D.AnimationType.WALK
            && Math.abs(ordinaryAnimation.getStateTime() - dt * ordinaryAnimation.getSpeed()) < .00001f,
            "ordinary map ignores held B and preserves walk speed and playback");
        require(tiled.requestTravel(ordinaryPlayer, "hoard_mode", "PlayerSpawn"), "return to sprint arena");
        tiled.applyPendingTravel();
        input.updateGamepad();
        require(hoard.stamina() == 1d && input.arenaSprintPress() == 0, "named travel starts fresh without carrying held B");
        pads.removeValue(pad, true);
        input.updateGamepad();
    }

    private static void verifySprintHud(RenderSystem renderer, boolean depleted) {
        renderer.update(0f);
        var viewport = GAME.getViewport();
        int width = HdpiUtils.toBackBufferX(viewport.getScreenWidth());
        int top = HdpiUtils.toBackBufferY(viewport.getScreenY() + viewport.getScreenHeight());
        int height = HdpiUtils.toBackBufferY(28);
        var pixels = Pixmap.createFromFrameBuffer(HdpiUtils.toBackBufferX(viewport.getScreenX()), top - 2 * height, width, 2 * height);
        try {
            for (int row = 0; row < 2; row++) {
                int left = width, right = -1;
                boolean blue = false, hp = false, empty = false;
                for (int y = row * height; y < (row + 1) * height; y++) {
                    for (int x = 0; x < width; x++) {
                        int pixel = pixels.getPixel(x, y);
                        int r = pixel >>> 24, g = pixel >>> 16 & 255, b = pixel >>> 8 & 255;
                        if (Math.max(r, Math.max(g, b)) < 90) { continue; }
                        blue |= b > r + 30 && g > r + 30;
                        hp |= g > 170 && r < 30 && b < 30;
                        empty |= r > 190 && g > 70 && g < 160 && b < 105;
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
                require(right > left && Math.abs(left + right - width) <= 4, "each HUD row is independently centered");
                require(row == 1 ? blue && !hp && !empty : hp && (depleted ? empty && !blue : blue && !empty),
                    "status above, HP and visible stamina below; depleted stamina uses distinct ink: row " + row
                        + ", blue=" + blue + ", HP=" + hp + ", depleted ink=" + empty);
            }
        } finally {
            pixels.dispose();
        }
    }

    private void verifyTerminalRun(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var run = hoard.run();
        long score = run.score();
        run.advance(run.remainingSeconds() - 1);
        int waveSize = enemies.size();
        var corpse = player();
        var health = Health.MAPPER.get(corpse);
        var pose = Animation2D.MAPPER.get(corpse);
        var bus = engine.getSystem(EventSystem.class);
        // Model credit emitted during combat, after the event pass but before the lethal death pass.
        var victim = enemies.get(waveSize - 1);
        var victimHealth = Health.MAPPER.get(victim);
        victimHealth.damage(victimHealth.getCurrent());
        bus.emitEvent(new Event.Player.KilledEnemy(corpse, victim));
        health.damage(health.getCurrent());
        engine.getSystem(DeathSystem.class).update(0f);
        String finalHud = "Game Over   Score " + score + "   Round 2   Alive 239s";
        require(hoard.run() == run && run.isGameOver() && GameplayHud.statusText(720, run).equals(finalHud),
            "final score on the lethal frame, without a physics tick or event drain");
        require(pose.getType() == Animation2D.AnimationType.DEATH && pose.getFrameIndex() == 0
            && !Physic.MAPPER.get(corpse).getBody().isActive() && !Controller.MAPPER.has(corpse), "normal death presentation");
        engine.update(0f); // Deliver queued credit while the victim still belongs to this runtime.
        engine.getSystem(PhysicSystem.class).update(360.05f);
        require(enemies.size() == waveSize && !health.isAlive() && GameplayHud.statusText(720, run).equals(finalHud),
            "future rounds cannot spawn, heal, change time or revise score");

        engine.update((float) pose.getPlaybackDuration() + 4.75f);
        require(player() == corpse && Graphic.MAPPER.has(corpse)
            && pose.getFrameIndex() == pose.getAnimation().getKeyFrames().length - 1, "corpse holds its visible final frame");
        double age = pose.getDeathSeconds();
        GAME.pause();
        GAME.render();
        GAME.resume();
        require(pose.getDeathSeconds() == age && hoard.run() == run, "pause preserves the terminal run and death pose");
        engine.update(.3f);
        tiled.applyPendingRespawn();
        require(engine.getEntitiesFor(Family.all(Player.class).get()).size() == 0 && !tiled.owns(corpse)
            && !engine.getEntities().contains(corpse, true), "expired corpse is removed, not replaced");
        require(hoard.run() == run && GameplayHud.statusText(720, run).equals(finalHud)
            && GameplayHud.healthText(tiled.playerHealth()).equals("HP 0/1000"), "final HUD outlives the corpse");
    }

    private void remountArena(TiledSystem tiled, HoardSystem hoard) {
        var enemies = GAME.getEngine().getEntitiesFor(Family.all(ActorAi.class).get());
        var world = Physic.MAPPER.get(enemies.first()).getBody().getWorld();
        var previous = hoard.run();
        tiled.hideMap();
        require(hoard.run() == null && enemies.size() == 0 && world.getBodyCount() == 0, "unmount releases arena and bodies");
        tiled.showMap(ManifestLoader.parseMapManifest("hoard_mode"));
        var fresh = hoard.run();
        require(fresh != previous && !fresh.isGameOver() && fresh.round() == 1 && fresh.remainingSeconds() == 120
            && fresh.kills() == 0 && fresh.score() == 0 && enemies.size() == 10, "fresh arena counters and opening batch");
        require(Health.MAPPER.get(player()).getCurrent() == 1000 && Controller.MAPPER.has(player())
            && Physic.MAPPER.get(player()).getBody().isActive(), "fresh live, controllable arena player");
    }

    private double brightness() {
        var pixels = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        try {
            long rgb = 0;
            for (int y = pixels.getHeight() / 4; y < pixels.getHeight() * 3 / 4; y += 4) {
                for (int x = pixels.getWidth() / 4; x < pixels.getWidth() * 3 / 4; x += 4) {
                    int p = pixels.getPixel(x, y);
                    rgb += (p >>> 24) + ((p >>> 16) & 255) + ((p >>> 8) & 255);
                }
            }
            return rgb;
        } finally { pixels.dispose(); }
    }

    private static Entity player() { return GAME.getEngine().getEntitiesFor(Family.all(Player.class).get()).first(); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    @Override public void dispose() { if (initialized) { GAME.dispose(); } }
}
