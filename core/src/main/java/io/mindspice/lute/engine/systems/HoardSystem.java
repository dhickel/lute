package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.data.json.ObjectData;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.gameplay.HoardRun;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;

/** Adapts one map-scoped run to the simulation clock, combat events and map placement. */
public final class HoardSystem extends ArenaSystem {
    record RosterEntry(String key, int unlockRound, int weight, int baseXp) { }

    static final List<RosterEntry> WAVE_ROSTER = List.of(
        new RosterEntry("brainless_undead", 1, 2, 5),
        new RosterEntry("undead_archer", 1, 1, 5),
        new RosterEntry("bone_soldier", 5, 2, 10),
        new RosterEntry("cultist", 10, 2, 15),
        new RosterEntry("cultist_archer", 10, 1, 15),
        new RosterEntry("viper", 20, 1, 20),
        new RosterEntry("reaper", 25, 2, 25),
        new RosterEntry("raider", 30, 2, 30)
    );

    static List<RosterEntry> spawnTickets(int round) {
        var tickets = new ArrayList<RosterEntry>();
        for (var enemy : WAVE_ROSTER) {
            if (enemy.unlockRound() > round) { break; }
            for (int i = 0; i < enemy.weight(); i++) { tickets.add(enemy); }
        }
        return tickets;
    }

    private static final List<String> STARTER_WEAPONS = List.of("basic_sword", "basic_bow");

    static List<String> starterWeaponCycle(String equippedWeapon) {
        var cycle = new LinkedHashSet<>(List.of(equippedWeapon));
        cycle.addAll(STARTER_WEAPONS);
        return List.copyOf(cycle);
    }

    private static final String WEAPON_BAG_KEY = "weapon_bag";
    private static final int DREDGE_BASE_XP = 100;
    private record Bounty(int baseXp) implements Component { }

    static Component rosterBounty(String key) {
        for (var enemy : WAVE_ROSTER) {
            if (enemy.key().equals(key)) { return new Bounty(enemy.baseXp()); }
        }
        throw new IllegalArgumentException("Unsupported Hoard enemy spawnKey: " + key);
    }
    // Placement must not consume random draws used for waves or kill drops.
    private final RandomGenerator roundHealthRandom = RandomGenerator.getDefault();
    private HoardRun run;

    public HoardSystem(InputManager input) { super(input); }

    @Override boolean creditKill(Entity enemy) {
        var bounty = enemy.getComponent(Bounty.class);
        if (bounty == null) { return false; }
        run.enemyKilled(bounty.baseXp());
        return true;
    }

    @Override MapRules rulesFor(String mapId, MapTriggers triggers) {
        var areas = arena(mapId, triggers);
        return areas == null ? null : arenaRules(areas, triggers);
    }

    private MapRules arenaRules(List<MapTriggers.SpawnArea> areas, MapTriggers triggers) {
        var roster = WAVE_ROSTER.stream().map(RosterEntry::key).toList();
        return new MapRules() {
            @Override public List<MapTriggers.SpawnArea> enemyAreas() { return areas; }
            @Override public List<String> extraEnemyKeys(MapTriggers.SpawnArea area) { return roster; }
            @Override public boolean spawnPoint(MapTriggers.SpawnPoint point) {
                return !"idle_dredge".equals(point.behaviorKey());
            }
            @Override public Component pointEnemyComponent(String key) { return rosterBounty(key); }
            @Override public Controller setupLoadout(Loadout loadout) {
                return Controller.forArena(starterWeaponCycle(loadout.getWeapon().id()));
            }
            @Override public void validate(EntityFactory factory, Entity player,
                                           BiFunction<String, Facing.Direction, Rectangle> enemyFootprint) {
                validateDelayedSpawns(factory, triggers, player, enemyFootprint);
            }
            @Override public void start(TiledSystem tiled, RandomGenerator random) {
                startRun(tiled, areas, triggers, random);
            }
        };
    }

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

    static void validateDelayedSpawns(EntityFactory factory, MapTriggers triggers, Entity player,
                                      BiFunction<String, Facing.Direction, Rectangle> enemyFootprint) {
        var boss = dredgePoint(triggers);
        enemyFootprint.apply(boss.spawnKey(), boss.facing());
        factory.validatePickupSpawn(healthArea(triggers).spawnKey(), ObjectData.Kind.HEAL);
        validateKillDrop(factory);
        factory.validatePickupSpawn(WEAPON_BAG_KEY, ObjectData.Kind.WEAPON_BAG);
        // Factory staging already validates weapon art. Check that the arena cycle resolves too.
        var weapons = GameCore.INSTANCE.getGameData().weaponsData();
        for (var weapon : Controller.MAPPER.get(player).getWeaponCycle()) {
            weapons.requireWeapon(weapon);
        }
    }

    void bind(TiledSystem tiled, List<MapTriggers.SpawnArea> areas, RandomGenerator random) {
        bind(tiled, areas == null ? null : arenaRules(areas, tiled.getMapTriggers()), random);
    }

    private void startRun(TiledSystem tiled, List<MapTriggers.SpawnArea> areas, MapTriggers triggers, RandomGenerator random) {
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
                int baseHealth = Health.MAPPER.get(boss).getMaximum();
                boss.add(new Health((int) Math.min(Integer.MAX_VALUE, HoardRun.scaleForRound(baseHealth, round))));
                boss.add(new ArenaGuard(Physic.MAPPER.get(boss).getBody().getPosition().cpy()));
                boss.add(new Bounty(DREDGE_BASE_XP));
                tiled.addArenaEntity(boss);
            }
        }, (amount, round) -> {
            var tickets = spawnTickets(round);
            int count = areas.size();
            int next = random.nextInt(count);
            for (int i = 0; i < amount; i++) {
                var entry = tickets.get(random.nextInt(tickets.size()));
                for (int attempt = 0; attempt < count; attempt++) {
                    int zone = next;
                    next = (next + 1) % count;
                    var enemy = tiled.trySpawnAreaEnemy(areas.get(zone), entry.key(), random);
                    if (enemy != null) { enemy.add(new Bounty(entry.baseXp())); break; }
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

    @Override void enemyDied(TiledSystem tiled, Entity actor) {
        if (actor.getComponent(ArenaGuard.class) != null) { drop(tiled, AwarenessSystem.feet(actor), WEAPON_BAG_KEY); }
    }

    @Override void clearRun() { run = null; }
    @Override boolean isGameOver() { return run.isGameOver(); }
    @Override void endRun() { run.end(); }
    @Override void advance(TiledSystem tiled, float interval) { run.advance(interval); }
    @Override String statusText() { return GameplayHud.hoardStatus(run); }
    public HoardRun run() { return run; }
}
