package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.gameplay.HoardRun;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Adapts one map-scoped run to the simulation clock, combat events and map placement. */
public final class HoardSystem extends EntitySystem implements EventListener {
    static final List<String> WAVE_ROSTER = List.of("brainless_undead", "undead_archer", "bone_soldier",
        "cultist", "cultist_archer", "viper", "reaper", "raider");

    static List<String> unlockedRoster(int round) {
        if (round < 5) { return WAVE_ROSTER.subList(0, 2); }
        if (round < 10) { return WAVE_ROSTER.subList(0, 3); }
        if (round < 20) { return WAVE_ROSTER.subList(0, 5); } // Both Cultists join at round 10.
        if (round < 25) { return WAVE_ROSTER.subList(0, 6); }
        if (round < 30) { return WAVE_ROSTER.subList(0, 7); }
        return WAVE_ROSTER;
    }
    // Dredge is a center guard, not a zone enemy; its independent base bounty is five points.
    private static final int DREDGE_POINTS = 5;
    private record Bounty(int points) implements Component { }
    // Placement must not consume random draws used for waves or kill drops.
    private final RandomGenerator roundHealthRandom = RandomGenerator.getDefault();
    private RandomGenerator dropRandom;
    private HoardRun run;
    private final Consumer<Event.Player.KilledEnemy> onKill = event -> {
        // The bus drains on the next update, possibly after named travel or hide/show.
        if (run == null || run.isGameOver()) { return; }
        var tiled = getEngine().getSystem(TiledSystem.class);
        var enemy = event.enemy();
        if (event.player() != tiled.activePlayer() || !tiled.owns(enemy)) { return; }
        var bounty = enemy.getComponent(Bounty.class);
        if (bounty == null) { return; }
        run.enemyKilled(bounty.points());
        if (dropRandom.nextInt(10) == 0) { drop(tiled, feet(enemy), "health_pack_1"); }
    };

    static List<MapTriggers.SpawnArea> arena(String mapId, MapTriggers triggers) {
        if (!"hoard_mode".equals(mapId)) { return null; }
        dredgePoint(triggers);
        healthArea(triggers);
        var areas = new ArrayList<MapTriggers.SpawnArea>();
        for (int i = 1; i <= 4; i++) {
            var area = triggers.requireEnemyArea("SpawnArea" + i, "agroWanderingHoard");
            if (area.polygon() == null || !"brainless_undead".equals(area.spawnKey())) {
                throw new IllegalArgumentException(area.name() + " requires a brainless_undead polygon");
            }
            areas.add(area);
        }
        return List.copyOf(areas);
    }

    private static MapTriggers.SpawnPoint dredgePoint(MapTriggers triggers) {
        var points = triggers.points().stream().filter(p -> "idle_dredge".equals(p.behaviorKey())).toList();
        if (points.size() != 1 || points.getFirst().type() != MapTriggers.SpawnType.ENEMY
            || !"dredge".equals(points.getFirst().spawnKey())) {
            throw new IllegalArgumentException("Arena requires one idle_dredge ENEMY point with spawnKey dredge");
        }
        return points.getFirst();
    }

    private static MapTriggers.SpawnArea healthArea(MapTriggers triggers) {
        var areas = triggers.areas().stream().filter(a -> "round_health_spawn".equals(a.behaviorKey())).toList();
        if (areas.size() != 1 || areas.getFirst().type() != MapTriggers.SpawnType.ITEM
            || !"health_pack_2".equals(areas.getFirst().spawnKey())) {
            throw new IllegalArgumentException("Arena requires one round_health_spawn ITEM ellipse with spawnKey health_pack_2");
        }
        return areas.getFirst();
    }

    void bind(TiledSystem tiled, List<MapTriggers.SpawnArea> areas, RandomGenerator random) {
        if (areas == null) { run = null; return; }
        dropRandom = random;
        var triggers = tiled.getMapTriggers();
        var marker = dredgePoint(triggers);
        var center = healthArea(triggers);
        var box = center.bounds();
        var dredgeSpawn = marker.position().cpy().scl(GameConsts.UNIT_SCALE);
        run = new HoardRun(random, round -> {
            if (round > 1) {
                drop(tiled, sampleRoundHealth(box, dredgeSpawn, roundHealthRandom), center.spawnKey());
            }
            if (round % 5 == 0) {
                var boss = getEngine().getSystem(EntityFactory.class).getMapActor(marker.position(), Actor.Role.ENEMY,
                    marker.spawnKey(), marker.facing());
                boss.add(new ArenaGuard(Physic.MAPPER.get(boss).getBody().getPosition().cpy()));
                boss.add(new Bounty(DREDGE_POINTS));
                tiled.addArenaEntity(boss);
            }
        }, (amount, round) -> {
            var roster = unlockedRoster(round);
            int count = areas.size();
            int next = random.nextInt(count);
            for (int i = 0; i < amount; i++) {
                String key = roster.get(random.nextInt(roster.size()));
                for (int attempt = 0; attempt < count; attempt++) {
                    int zone = next;
                    next = (next + 1) % count;
                    var enemy = tiled.trySpawnAreaEnemy(areas.get(zone), key, random);
                    if (enemy != null) { enemy.add(new Bounty(5 * (zone + 1))); break; }
                }
            }
        });
    }

    static Vector2 sampleRoundHealth(Rectangle bounds, Vector2 dredgeSpawn, RandomGenerator random) {
        float rx = bounds.width / 2, ry = bounds.height / 2;
        float cx = bounds.x + rx, cy = bounds.y + ry;
        // Keep a one-pixel gap from the marker, capped so a tiny ellipse still has room.
        float clearance = Math.min(GameConsts.UNIT_SCALE, Math.max(rx, ry) / 2);
        while (true) {
            // Rejection from a uniform square gives a uniform disk, then scale to the ellipse.
            float x = 2 * random.nextFloat() - 1, y = 2 * random.nextFloat() - 1;
            if (x * x + y * y >= 1) { continue; }
            float worldX = cx + x * rx, worldY = cy + y * ry;
            if (dredgeSpawn.dst2(worldX, worldY) > clearance * clearance) {
                return new Vector2(worldX, worldY);
            }
        }
    }

    void dredgeDied(Entity enemy) {
        var tiled = getEngine().getSystem(TiledSystem.class);
        if (isActive() && tiled.owns(enemy)) { drop(tiled, feet(enemy), "weapon_bag"); }
    }

    private static Vector2 feet(Entity enemy) {
        var actor = Actor.MAPPER.get(enemy);
        return Physic.MAPPER.get(enemy).getBody().getWorldPoint(new Vector2(actor.feetX(), actor.feetY()));
    }

    private void drop(TiledSystem tiled, Vector2 world, String key) {
        var pixels = world.cpy().scl(1f / GameConsts.UNIT_SCALE);
        tiled.addArenaEntity(getEngine().getSystem(EntityFactory.class).getSpawnedObject(pixels, key, Facing.Direction.DOWN));
    }

    void playerDied(Entity player) {
        if (run != null && getEngine().getSystem(TiledSystem.class).owns(player)) { run.end(); }
    }

    public boolean isActive() { return run != null; }
    HoardRun run() { return run; }

    // PhysicSystem has committed this tick; fixture queries and new bodies are safe here.
    void afterPhysicsStep(float interval) {
        if (run == null || run.isGameOver()) { return; }
        // Resolve this tick's contacts before a batch or heal. Arrows and attack playback
        // already had their frame update; neither should advance again on a fixed tick.
        var engine = getEngine();
        var killed = engine.getSystem(CombatSystem.class).applyPendingHits(engine.getSystem(TiledSystem.class));
        for (var victim : killed) {
            // A newly spawned casualty may not be in DeathSystem's Ashley family yet.
            var death = engine.getSystem(DeathSystem.class);
            if (death.getFamily().matches(victim)) { death.processEntity(victim, 0f); }
        }
        if (!run.isGameOver()) {
            engine.getSystem(TiledSystem.class).collectPickups();
            run.advance(interval);
        }
    }

    @Override
    public void addedToEngine(Engine engine) {
        eventSubscriber.accept(Event.Player.KilledEnemy.class, onKill);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        eventUnSubscriber.accept(Event.Player.KilledEnemy.class, onKill);
        run = null;
    }
}
