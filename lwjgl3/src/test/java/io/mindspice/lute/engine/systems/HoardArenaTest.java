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
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.objects.PointMapObject;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.math.MathUtils;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.input.InputAction;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Real generated arena, Box2D, normal screen lifecycle and both rendering paths. */
public final class HoardArenaTest extends ApplicationAdapter {
    private static final GameCore GAME = GameCore.INSTANCE;
    private static Throwable failure;
    private boolean initialized;

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setWindowedMode(640, 400);
        config.setInitialVisible(false);
        config.disableAudio(true);
        new Lwjgl3Application(new HoardArenaTest(), config);
        if (failure != null) { throw new AssertionError("Hoard arena", failure); }
        System.out.println("HoardArenaTest: real arena, batches, HP, lighting, game over, corpse expiry, pause and remount passed");
    }

    @Override public void create() {
        try { exercise(); }
        catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void exercise() {
        GAME.create();
        initialized = true;
        GAME.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.app.setLogLevel(Application.LOG_ERROR);
        var engine = GAME.getEngine();
        var tiled = engine.getSystem(TiledSystem.class);
        for (int i = 0; i < 200 && tiled.getCurrentMap() == null; i++) { GAME.render(); }
        var hoard = engine.getSystem(HoardSystem.class);
        var run = hoard.run();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        require(run != null && run.round() == 1 && enemies.size() == 5, "five wave enemies at arena mount");
        var clock = engine.getSystem(WorldTimeSystem.class);
        require(GameplayHud.statusText((int) clock.getMinuteOfDay(), run).equals("06:00   Round 1   2:00 left   Kills 0   Score 0"), "arena HUD");
        var areas = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        Entity survivor = enemies.first();
        for (var enemy : enemies) {
            var transform = Transform.MAPPER.get(enemy);
            require(areas.getFirst().polygon().contains(transform.centerX(), transform.centerY()),
                "opening enemy is in SpawnArea1");
            require(Health.MAPPER.get(enemy).getCurrent() == 50 && Health.MAPPER.get(enemy).getMaximum() == 50
                && ActorAi.MAPPER.get(enemy).behaviorGraphKey().equals(BowAim.MAPPER.has(enemy) ? "wander_archer" : "wander_attack"),
                "wave enemy has its own behavior inside the polygon");
        }
        var health = Health.MAPPER.get(player());
        require(health.getCurrent() == 1000 && health.getMaximum() == 1000, "arena HP");
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
        require(!hoard.clockNight(), "06:00 is daylight");
        double daylight = brightness();
        double secondsPerHour = clock.getDayLengthMinutes() * 60 / 24 / 4;
        clock.update((float) (16 * secondsPerHour)); // 06:00 -> 22:00
        renderer.update(0f);
        require(hoard.clockNight(), "22:00 is overnight");
        double dark = brightness();
        Gdx.input.getInputProcessor().keyDown(Input.Keys.N);
        Gdx.input.getInputProcessor().keyUp(Input.Keys.N);
        GAME.render();
        double afterToggle = brightness();
        clock.update((float) (14 * secondsPerHour)); // 22:00 -> noon
        renderer.update(0f);
        require(!hoard.clockNight() && Math.min(daylight, brightness()) > Math.max(dark, afterToggle) * 1.15,
            "N leaves the arena in clock-driven night until sunrise");
        GAME.pause();
        int frozen = run.remainingSeconds();
        double frozenClock = clock.getTotalInGameSeconds();
        for (int i = 0; i < 120; i++) { GAME.render(); }
        require(run.remainingSeconds() == frozen && clock.getTotalInGameSeconds() == frozenClock, "paused simulation");
        GAME.resume();

        // Advance real Box2D fixed ticks without rendering thousands of intermediate frames.
        var physics = engine.getSystem(PhysicSystem.class);
        physics.update(40.01f);
        require(enemies.size() > 5 && enemies.size() <= 11 && health.getCurrent() == 899,
            "one or two smaller daylight reinforcements without healing");
        physics.update(80.02f);
        require(run.round() == 2 && health.getCurrent() == 899 && enemies.contains(survivor, true)
            && Health.MAPPER.get(survivor).getCurrent() == 43, "round drops a pack instead of healing and keeps survivors");

        require(pickups.size() == 1 && pickups.first().getComponent(Pickup.class).rule().amount() == 500, "one round reward");
        var center = Physic.MAPPER.get(pickups.first()).getBody().getPosition().cpy();
        var ellipse = tiled.getMapTriggers().areas().stream().filter(a -> "round_health_spawn".equals(a.behaviorKey())).findFirst().orElseThrow();
        require(center.epsilonEquals(ellipse.bounds().getCenter(new com.badlogic.gdx.math.Vector2()), .0001f), "round pack at authored center");
        var playerActor = Actor.MAPPER.get(player());
        Physic.MAPPER.get(player()).getBody().setTransform(center.x - playerActor.feetX(), center.y - playerActor.feetY(), 0);
        Physic.MAPPER.get(player()).getBody().setAwake(true);
        health.restore();
        physics.update(.04f);
        require(pickups.size() == 1, "full HP leaves pack untouched");
        health.damage(101);
        physics.update(.04f);
        require(pickups.size() == 0 && health.getCurrent() == 1000, "continued contact collects and caps round heal");

        // Isolate a genuine, lethal sword contact from wandering and the authored scenery.
        engine.getSystem(AwarenessSystem.class).setProcessing(false);
        Physic.MAPPER.get(player()).getBody().setTransform(-20, -20, 0);
        Physic.MAPPER.get(survivor).getBody().setTransform(-19.15f, -20, 0);
        Loadout.MAPPER.get(player()).setWeapon("basic_sword");
        Facing.MAPPER.get(player()).setDirection(Facing.Direction.RIGHT);
        Health.MAPPER.get(survivor).damage(42);
        Combat.MAPPER.get(player()).requestAttack(Combat.AttackSlot.PRIMARY);
        for (int i = 0; i < 60 && run.score() == 0; i++) { engine.update(1f / 60); }
        require(!Health.MAPPER.get(survivor).isAlive() && run.kills() == 1 && run.score() == 5,
            "the same real killing blow feeds death, kills and flat zone score");
        require(GameplayHud.statusText(720, run).endsWith("Kills 1   Score 5"), "live HUD credit");

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
        require(hoard.run() != priorRun && hoard.run().round() == 1 && hoard.run().score() == 0 && enemies.size() == 5,
            "new run ignores late kills from prior arena");
        before = clock.getTotalInGameSeconds();
        clock.update(1f);
        require(Math.abs(clock.getTotalInGameSeconds() - before - 4 * ordinaryIncrement) < .001
            && hoard.run().remainingSeconds() == 120, "only the world clock runs at 4x");
        verifyVisitInventory(ordinaryLoadout);
        // This lethal frame does step physics and would have started round two and dropped a pack.
        var lastSecond = hoard.run();
        lastSecond.advance(119.999, false);
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
        verifyZoneRosters(tiled, hoard);

        renderer.setProcessing(false);
        engine.getSystem(AwarenessSystem.class).setProcessing(false);
        int split = reinforcementHp(tiled, hoard, false);
        int batched = reinforcementHp(tiled, hoard, true);
        System.out.printf("HoardArenaTest: reinforcement HP after two physics ticks: split=%d, batched=%d%n", split, batched);
        require(split > 0 && batched == split, "one real sword hit regardless of physics frame batching");
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

    private void verifyZoneRosters(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var bus = engine.getSystem(EventSystem.class);
        var areas = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        var marker = tiled.getMapTriggers().points().stream().filter(p -> p.name().equals("Dredge")).findFirst().orElseThrow();
        var expected = List.of(List.of("brainless_undead", "undead_archer"), List.of("bone_soldier", "cultist"),
            List.of("cultist_archer", "viper"), List.of("reaper", "raider"));
        var observed = new HashSet<String>();
        hoard.bind(tiled, areas, new Random(537) {
            @Override public int nextInt(int bound) { return bound == 10 ? 9 : super.nextInt(bound); }
        });
        for (int round = 1; round <= 19; round++) {
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
                    require(enemy.getComponent(ArenaGuard.class) != null
                        && Math.abs(transform.centerX() / GameConsts.UNIT_SCALE - marker.position().x) < .01f
                        && Math.abs(transform.centerY() / GameConsts.UNIT_SCALE - marker.position().y) < .01f,
                        "Dredge spawns at the authored point");
                    points = 5;
                } else {
                    int zone = 0;
                    while (zone < areas.size() && !areas.get(zone).polygon().contains(transform.centerX(), transform.centerY())) { zone++; }
                    require(zone < areas.size() && round >= 1 + 5 * zone && expected.get(zone).contains(key),
                        "round " + round + " uses only unlocked zone rosters: " + key);
                    if (key.equals("viper")) { require(BowAim.MAPPER.has(enemy), "Viper can use its authored bow"); }
                    points = 5 * (zone + 1);
                }
                if (observed.add(key)) {
                    var run = hoard.run();
                    long prior = run.score();
                    bus.emitEvent(new Event.Player.KilledEnemy(player(), enemy));
                    bus.update(0f);
                    require(run.score() - prior == points, "flat bounty for origin of " + key);
                }
            }
            require(bosses == (round % 5 == 0 ? 1 : 0), "one Dredge every fifth round only");
            while (enemies.size() > 0) {
                var enemy = enemies.first();
                tiled.retireCorpse(enemy);
                engine.removeEntity(enemy);
            }
            hoard.run().advance(120, false);
        }
        require(observed.size() == 9, "all eight non-Dredges and Dredge appeared: " + observed);
    }

    private int reinforcementHp(TiledSystem tiled, HoardSystem hoard, boolean batched) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var map = contactArena();
        var damageRandom = MathUtils.random;
        try {
            tiled.setMap(map, ManifestLoader.parseMapManifest("hoard_mode"));
            require(hoard.run().round() == 1 && enemies.size() == 0,
                "fresh mount starts with only the undersized SpawnArea1 unlocked");
            var area = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
            // Start at the first undersized area. Only area four can fit one reinforcement.
            hoard.bind(tiled, area, new Random(1) {
                @Override public int nextInt(int bound) { return 0; }
                @Override public float nextFloat() { return .5f; }
                @Override public int nextInt(int origin, int bound) {
                    if (origin == 20 && bound == 41) { return 20; }
                    require(origin == 25 && bound == 51, "reinforcement percentage roll");
                    return 50;
                }
            });
            hoard.run().advance(15 * 120 - 1, false);
            require(enemies.size() == 3, "only the three center Dredges spawn through round 15");
            var boss = enemies.first();
            var guard = boss.getComponent(ArenaGuard.class);
            require(guard != null, "milestone spawned a guard");
            var playerBody = Physic.MAPPER.get(player()).getBody();
            var original = playerBody.getPosition().cpy();
            var actor = Actor.MAPPER.get(player());
            var zone = guard.territory();
            playerBody.setTransform(zone.x - actor.feetX(), zone.y - actor.feetY(), 0);
            var awareness = engine.getSystem(AwarenessSystem.class);
            awareness.processEntity(boss, .01f);
            require(ActorAi.MAPPER.get(boss).mode() == ActorAi.Mode.ENGAGE, "player inside ellipse is acquired");
            playerBody.setTransform(zone.x - zone.width / 2 - actor.feetX(), zone.y - zone.height / 2 - actor.feetY(), 0);
            awareness.processEntity(boss, .01f);
            require(ActorAi.MAPPER.get(boss).mode() != ActorAi.Mode.ENGAGE && Move.MAPPER.get(boss).getDirection().isZero(),
                "corner of ellipse bounding box is outside; guard at home waits");
            var bossBody = Physic.MAPPER.get(boss).getBody();
            bossBody.setTransform(guard.home().x + 1, guard.home().y, 0);
            awareness.processEntity(boss, .01f);
            require(Move.MAPPER.get(boss).getDirection().x < 0, "guard returns to its own spawn");
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
            hoard.run().advance(1, false);
            require(enemies.size() == 4, "round 16 opening retries reach newly unlocked SpawnArea4");
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
            float dt = GameConsts.PHYSIC_INTERVAL;
            for (int i = 0; i < 60 && attack.getSensor() == null; i++) { engine.update(dt); }
            require(attack.getSensor() != null, "armed before the reinforcement tick");
            hoard.run().advance(20 - dt / 2, false);
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
                require(DamageFeedback.MAPPER.get(enemy).numbers.size() == 1, "one damage application per sword swing");
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
        boolean[] archer = {true, true, false, true, false, false, false, true};
        var area = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
        hoard.bind(tiled, area, new Random(37) {
            int slot;
            int drops;
            @Override public int nextInt(int bound) { return bound == 10 ? (drops++ == 0 ? 9 : 0) : super.nextInt(bound); }
            @Override public boolean nextBoolean() { return slot < archer.length ? archer[slot++] : super.nextBoolean(); }
            @Override public int nextInt(int origin, int bound) { return bound - 1; }
        });
        require(enemies.size() == start + 5, "five scripted opening slots");
        hoard.run().advance(40, false);
        require(enemies.size() == start + archer.length, "three scripted reinforcement slots");
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
        require(hoard.run().kills() == 2 && hoard.run().score() == 10, "both wave types earn round-one credit");
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
        body.setTransform(position, 0);
        return victim;
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
        run.advance(run.remainingSeconds() - .001, false);
        int waveSize = enemies.size();
        physics.setProcessing(true);
        engine.update(GameConsts.PHYSIC_INTERVAL);
        awareness.setProcessing(true);
        require(!health.isAlive() && run.isGameOver() && run.round() == 1 && run.remainingSeconds() == 1
            && enemies.size() == waveSize, "lethal sword contact beats the same tick's heal and opening batch");
        require(Animation2D.MAPPER.get(player).getType() == Animation2D.AnimationType.DEATH
            && !Physic.MAPPER.get(player).getBody().isActive(), "death is visible without another frame");
    }

    private void verifyTerminalRun(TiledSystem tiled, HoardSystem hoard) {
        var engine = GAME.getEngine();
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var run = hoard.run();
        run.advance(run.remainingSeconds() - 1, hoard.clockNight());
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
        String finalHud = "Game Over   Score 5   Round 2   Alive 239s";
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
            && fresh.kills() == 0 && fresh.score() == 0 && enemies.size() == 5, "fresh arena counters and opening batch");
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
