package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.input.InputManager;
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
    static List<String> spawnTickets(List<String> roster) {
        var tickets = new ArrayList<String>();
        for (var key : roster) {
            tickets.add(key);
            switch (key) {
                case "undead_archer", "cultist_archer", "viper" -> { }
                default -> tickets.add(key);
            }
        }
        return tickets;
    }

    private static final int DREDGE_BASE_XP = 100;
    private static final float SPRINT_MULTIPLIER = 1.6f;
    private static final float SPRINT_RAMP_UP_SECONDS = .25f;
    private static final float SPRINT_RAMP_DOWN_SECONDS = .10f;
    private static final double SPRINT_SECONDS = 2d;
    private static final double REFILL_PER_SECOND = 0.7d / 6d;
    private record Bounty(int baseXp) implements Component { }

    private static int waveBaseXp(String key) {
        return switch (key) {
            case "brainless_undead", "undead_archer" -> 5;
            case "bone_soldier" -> 10;
            case "cultist", "cultist_archer" -> 15;
            case "viper" -> 20;
            case "reaper" -> 25;
            case "raider" -> 30;
            default -> throw new IllegalArgumentException("Unknown Hoard enemy: " + key);
        };
    }
    // Placement must not consume random draws used for waves or kill drops.
    private final RandomGenerator roundHealthRandom = RandomGenerator.getDefault();
    private RandomGenerator dropRandom;
    private HoardRun run;
    private final InputManager input;
    private double stamina = 1d;
    private long exhaustedPress;
    private long sprintPress;
    private float sprintBoost;
    private final Consumer<Event.Player.KilledEnemy> onKill = event -> {
        // The bus drains on the next update, possibly after named travel or hide/show.
        if (run == null || run.isGameOver()) { return; }
        var tiled = getEngine().getSystem(TiledSystem.class);
        var enemy = event.enemy();
        if (event.player() != tiled.activePlayer() || !tiled.owns(enemy)) { return; }
        var bounty = enemy.getComponent(Bounty.class);
        if (bounty == null) { return; }
        run.enemyKilled(bounty.baseXp());
        if (dropRandom.nextInt(10) == 0) { drop(tiled, feet(enemy), "health_pack_1"); }
    };

    public HoardSystem(InputManager input) {
        this.input = input;
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

    void bind(TiledSystem tiled, List<MapTriggers.SpawnArea> areas, RandomGenerator random) {
        stamina = 1d;
        exhaustedPress = 0;
        clearSprintMomentum();
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
                int baseHealth = Health.MAPPER.get(boss).getMaximum();
                boss.add(new Health((int) Math.min(Integer.MAX_VALUE, HoardRun.scaleForRound(baseHealth, round))));
                boss.add(new ArenaGuard(Physic.MAPPER.get(boss).getBody().getPosition().cpy()));
                boss.add(new Bounty(DREDGE_BASE_XP));
                tiled.addArenaEntity(boss);
            }
        }, (amount, round) -> {
            var tickets = spawnTickets(unlockedRoster(round));
            int count = areas.size();
            int next = random.nextInt(count);
            for (int i = 0; i < amount; i++) {
                String key = tickets.get(random.nextInt(tickets.size()));
                int baseXp = waveBaseXp(key);
                for (int attempt = 0; attempt < count; attempt++) {
                    int zone = next;
                    next = (next + 1) % count;
                    var enemy = tiled.trySpawnAreaEnemy(areas.get(zone), key, random);
                    if (enemy != null) { enemy.add(new Bounty(baseXp)); break; }
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

    double stamina() { return stamina; }

    public void clearSprintMomentum() {
        sprintPress = 0;
        sprintBoost = 0f;
    }

    long beforeSprintStep(float interval, boolean wasBoosted) {
        if (run == null || run.isGameOver()) { return 0; }
        var player = getEngine().getSystem(TiledSystem.class).activePlayer();
        if (!Health.MAPPER.get(player).isAlive() || !Controller.MAPPER.has(player)) { return 0; }

        var move = Move.MAPPER.get(player);
        var aim = BowAim.MAPPER.get(player);
        long press = input.arenaSprintPress();
        if (move.isRooted() || move.getDirection().isZero() || (aim != null && aim.isAiming())) {
            clearSprintMomentum();
        } else if (press != 0 && press != exhaustedPress && stamina > 0d) {
            sprintPress = press;
            // A sliver of stamina buys only its share of this tick's acceleration.
            float poweredTime = (float) Math.min(interval, stamina * SPRINT_SECONDS);
            sprintBoost = Math.min(1f, sprintBoost + poweredTime / SPRINT_RAMP_UP_SECONDS);
        } else {
            sprintBoost = Math.max(0f, sprintBoost - interval / SPRINT_RAMP_DOWN_SECONDS);
        }
        if (sprintBoost > .000001f) {
            float multiplier = 1f + (SPRINT_MULTIPLIER - 1f) * sprintBoost;
            getEngine().getSystem(PhysicMoveSystem.class).applyMovement(player, multiplier);
            return sprintPress;
        }
        clearSprintMomentum();
        // Ordinary movement is already applied once per frame. Restore it only when
        // leaving a boosted tick in this batch, then let contacts carry velocity forward.
        if (wasBoosted) { getEngine().getSystem(PhysicMoveSystem.class).applyMovement(player, 1f); }
        refill(interval);
        return 0;
    }

    // Measure the actual step before sensor teleports can change the player's position.
    void finishSprintStep(float interval, long boostedPress) {
        var player = getEngine().getSystem(TiledSystem.class).activePlayer();
        var physics = Physic.MAPPER.get(player);
        var before = physics.getPrevPos();
        var after = physics.getBody().getPosition();
        var direction = Move.MAPPER.get(player).getDirection();
        float progress = direction.dot(after.x - before.x, after.y - before.y);
        if (progress > 0.00001f) {
            // The release tail still costs stamina while it provides extra movement.
            if (stamina > 0d) {
                stamina = Math.max(0d, stamina - interval / SPRINT_SECONDS);
                if (stamina == 0d) { exhaustedPress = boostedPress; }
            }
            getEngine().getSystem(AnimationSystem.class).advanceSprintWalk(player, interval * sprintBoost);
        } else {
            // Don't bank a full-speed start by holding B against a wall.
            clearSprintMomentum();
            refill(interval);
        }
    }

    private void refill(float interval) {
        stamina = Math.min(1d, stamina + interval * REFILL_PER_SECOND);
    }

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
