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
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.ManifestLoader;

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
        require(run != null && run.round() == 1 && enemies.size() == 6, "five wave enemies plus authored archer at arena mount");
        var clock = engine.getSystem(WorldTimeSystem.class);
        require(GameplayHud.statusText((int) clock.getMinuteOfDay(), run).equals("06:00   Round 1   3:00 left   Kills 0   Score 0"), "arena HUD");
        var area = tiled.getMapTriggers().requireEnemyArea("SpawnArea1", "agroWanderingHoard");
        Entity survivor = authoredArcher();
        for (var enemy : enemies) {
            if (enemy == survivor) { continue; }
            var transform = Transform.MAPPER.get(enemy);
            require(area.polygon().contains(transform.centerX(), transform.centerY())
                && Health.MAPPER.get(enemy).getCurrent() == 50 && Health.MAPPER.get(enemy).getMaximum() == 50
                && ActorAi.MAPPER.get(enemy).behaviorGraphKey().equals("wander_attack"), "authored polygon enemy");
        }
        var health = Health.MAPPER.get(player());
        require(health.getCurrent() == 1000 && health.getMaximum() == 1000, "arena HP");
        health.damage(101);
        Health.MAPPER.get(survivor).damage(7);

        GAME.render(); // supplies the renderer with its session and draws the initial daylight
        var renderer = engine.getSystem(RenderSystem.class);
        require(!hoard.clockNight(), "06:00 is daylight");
        double daylight = brightness();
        double secondsPerHour = clock.getDayLengthMinutes() * 60 / 24;
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
        require(enemies.size() > 6 && enemies.size() <= 12 && health.getCurrent() == 899,
            "one or two smaller daylight reinforcements without healing");
        physics.update(140.02f);
        require(run.round() == 2 && health.getCurrent() == 1000 && enemies.contains(survivor, true)
            && Health.MAPPER.get(survivor).getCurrent() == 43, "round heals only player and keeps survivors");

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
            "the same real killing blow feeds death, kills and round-scaled score");
        require(GameplayHud.statusText(720, run).endsWith("Kills 1   Score 6"), "live HUD credit");

        engine.getSystem(AwarenessSystem.class).setProcessing(true);
        verifyTerminalRun(tiled, hoard);
        remountArena(tiled, hoard);
        require(!enemies.contains(survivor, true), "old runtime enemies are gone");
        verifyLethalContactAtRollover(hoard);
        var dyingArcher = authoredArcher();
        Health.MAPPER.get(dyingArcher).damage(50);
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
        normal.damage(1);
        require(tiled.requestTravel(player(), "hoard_mode", "PlayerSpawn"), "reenter arena");
        tiled.applyPendingTravel();
        bus.update(0);
        require(hoard.run() != priorRun && hoard.run().round() == 1 && hoard.run().score() == 0 && enemies.size() == 6,
            "new run ignores late kills from prior arena");
        // This lethal frame *does* step physics and would have started round two and healed.
        var lastSecond = hoard.run();
        lastSecond.advance(179.999, false);
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
        require(Health.MAPPER.get(player()) == normal && normal.getCurrent() == normal.getMaximum() - 1,
            "arena healing does not touch ordinary session HP");

        renderer.setProcessing(false);
        engine.getSystem(AwarenessSystem.class).setProcessing(false);
        int split = reinforcementHp(tiled, hoard, false);
        int batched = reinforcementHp(tiled, hoard, true);
        System.out.printf("HoardArenaTest: reinforcement HP after two physics ticks: split=%d, batched=%d%n", split, batched);
        require(split < 50 && batched == split, "one real sword hit regardless of physics frame batching");
    }

    private int reinforcementHp(TiledSystem tiled, HoardSystem hoard, boolean batched) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var map = contactArena();
        var damageRandom = MathUtils.random;
        try {
            tiled.setMap(map, ManifestLoader.parseMapManifest("hoard_mode"));
            var area = HoardSystem.arena("hoard_mode", tiled.getMapTriggers());
            // Every placement tries (25,25); the first solid feet block the rest of the batch.
            hoard.bind(tiled, area, new Random(1) {
                @Override public float nextFloat() { return .5f; }
                @Override public int nextInt(int origin, int bound) {
                    if (origin == 20 && bound == 41) { return 20; }
                    require(origin == 25 && bound == 51, "reinforcement percentage roll");
                    return 50;
                }
            });
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
                require(enemies.size() == 1 && Health.MAPPER.get(enemies.first()).getCurrent() == 50,
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
            return hp;
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
        var area = new PolygonMapObject(new float[]{640, 640, 960, 640, 960, 960, 640, 960});
        area.setName("SpawnArea1");
        area.getProperties().put("type", "SpawnArea");
        area.getProperties().put("spawnType", "ENEMY");
        area.getProperties().put("spawnKey", "brainless_undead");
        area.getProperties().put("behaviorKey", "agroWanderingHoard");
        area.getProperties().put("facing", "DOWN");
        triggers.getObjects().add(area);
        return map;
    }

    private void verifyLethalContactAtRollover(HoardSystem hoard) {
        var engine = GAME.getEngine();
        var physics = engine.getSystem(PhysicSystem.class);
        var awareness = engine.getSystem(AwarenessSystem.class);
        var enemies = engine.getEntitiesFor(Family.all(ActorAi.class).get());
        var attacker = enemies.get(BowAim.MAPPER.has(enemies.first()) ? 1 : 0);
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
        run.advance(179.999, false);
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
        String finalHud = "12:00   Game Over   Round 2   0:01 left   Kills 1   Score 6";
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
        require(fresh != previous && !fresh.isGameOver() && fresh.round() == 1 && fresh.remainingSeconds() == 180
            && fresh.kills() == 0 && fresh.score() == 0 && enemies.size() == 6, "fresh arena counters and opening batch");
        authoredArcher();
        require(Health.MAPPER.get(player()).getCurrent() == 1000 && Controller.MAPPER.has(player())
            && Physic.MAPPER.get(player()).getBody().isActive(), "fresh live, controllable arena player");
    }

    private Entity authoredArcher() {
        var archers = GAME.getEngine().getEntitiesFor(Family.all(ActorAi.class, BowAim.class).get());
        require(archers.size() == 1, "exactly one authored archer");
        var archer = archers.first();
        require(ActorAi.MAPPER.get(archer).behaviorGraphKey().equals("wander_archer")
            && Loadout.MAPPER.get(archer).getWeaponId().equals("basic_bow")
            && Health.MAPPER.get(archer).getCurrent() == 50, "fresh live bow archer");
        return archer;
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
