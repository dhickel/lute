package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.components.Controller;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.tiled.MapTriggers;

import java.util.List;
import java.util.function.BiFunction;
import java.util.random.RandomGenerator;

/** Unscored, map-scoped combat practice using the production simulation. */
public final class TestAreaSystem extends ArenaSystem {
    private static final int WAVE_SIZE = 10;
    private static final double WAVE_SECONDS = 15d;
    private record Combatant(Entity player) implements Component { }

    private MapTriggers.SpawnArea center;
    private RandomGenerator spawnRandom;
    private double waveTime;
    private boolean ended;

    public TestAreaSystem(InputManager input) { super(input); }

    @Override MapRules rulesFor(String mapId, MapTriggers triggers) {
        if (!"hoard_mode".equals(mapId)) { return null; }
        var zone = centerZone(triggers);
        var weapons = GameCore.INSTANCE.getGameData().weaponsData().weapons().keySet().stream().sorted().toList();
        return new MapRules() {
            @Override public List<MapTriggers.SpawnArea> enemyAreas() { return List.of(zone); }
            @Override public boolean includeAuthoredEnemyAreas() { return false; }
            @Override public boolean spawnPoint(MapTriggers.SpawnPoint point) {
                return point.type() != MapTriggers.SpawnType.ENEMY;
            }
            @Override public Controller setupLoadout(Loadout loadout) {
                loadout.setWeapon("basic_axe");
                return Controller.forArenaInventory(weapons);
            }
            @Override public void validate(EntityFactory factory, Entity player,
                                           BiFunction<String, Facing.Direction, Rectangle> enemyFootprint) {
                validateKillDrop(factory);
            }
            @Override public void start(TiledSystem tiled, RandomGenerator random) {
                center = zone;
                spawnRandom = random;
            }
        };
    }

    static MapTriggers.SpawnArea centerZone(MapTriggers triggers) {
        var areas = triggers.areas().stream().filter(a -> "round_health_spawn".equals(a.behaviorKey())).toList();
        if (areas.size() != 1 || areas.getFirst().type() != MapTriggers.SpawnType.ITEM
            || !"health_pack_2".equals(areas.getFirst().spawnKey())) {
            throw new IllegalArgumentException("Test Area requires the arena's central round_health_spawn ITEM ellipse");
        }
        return new MapTriggers.SpawnArea(new MapTriggers.SpawnInfo("TestAreaCenter", MapTriggers.SpawnType.ENEMY),
            areas.getFirst().bounds(), null, null, "brainless_undead", Facing.Direction.DOWN);
    }

    @Override public AwarenessSystem createAwareness(World world) {
        return new AwarenessSystem(world, false, RandomGenerator.getDefault(), actor -> {
            var combatant = actor.getComponent(Combatant.class);
            return combatant == null ? null : combatant.player();
        });
    }

    @Override void advance(TiledSystem tiled, float interval) {
        waveTime += interval;
        while (waveTime >= WAVE_SECONDS) {
            waveTime -= WAVE_SECONDS;
            var combatant = new Combatant(tiled.activePlayer());
            for (int i = 0; i < WAVE_SIZE; i++) {
                var enemy = tiled.trySpawnAreaEnemy(center, spawnRandom);
                if (enemy != null) { enemy.add(combatant); }
            }
        }
    }

    @Override void clearRun() {
        center = null;
        spawnRandom = null;
        waveTime = 0d;
        ended = false;
    }

    @Override boolean creditKill(Entity enemy) { return enemy.getComponent(Combatant.class) != null; }
    @Override boolean isGameOver() { return ended; }
    @Override void endRun() { ended = true; }

    @Override String statusText() {
        if (ended) { return "Test Area   Game Over"; }
        return "Test Area   Next wave in " + (int) Math.ceil(WAVE_SECONDS - waveTime) + "s";
    }
}
