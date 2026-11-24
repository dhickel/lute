package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.gameplay.HoardRun;

import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Adapts one map-scoped run to the simulation clock, combat events and unlocked map placement. */
public final class HoardSystem extends EntitySystem implements EventListener {
    private HoardRun run;
    private final Consumer<Event.Player.KilledEnemy> onKill = event -> {
        // The bus drains on the next update, possibly after named travel or hide/show.
        if (run != null && getEngine().getSystem(TiledSystem.class).owns(event.enemy())) { run.enemyKilled(); }
    };

    static MapTriggers.SpawnArea arena(String mapId, MapTriggers triggers) {
        if (!"hoard_mode".equals(mapId)) { return null; }
        var area = triggers.requireEnemyArea("SpawnArea1", "agroWanderingHoard");
        if (area.polygon() == null) { throw new IllegalArgumentException("hoard_mode requires a polygon for SpawnArea1"); }
        return area;
    }

    void bind(TiledSystem tiled, MapTriggers.SpawnArea area, RandomGenerator random) {
        if (area == null) { run = null; return; }
        run = new HoardRun(random, tiled.playerHealth()::restore, amount -> {
            for (int i = 0; i < amount; i++) {
                if (tiled.trySpawnAreaEnemy(area, random) == null) { break; }
            }
        }, clockNight());
    }

    void playerDied(Entity player) {
        if (run != null && getEngine().getSystem(TiledSystem.class).owns(player)) { run.end(); }
    }

    public boolean isActive() { return run != null; }
    HoardRun run() { return run; }

    boolean clockNight() {
        return HoardRun.isNight(getEngine().getSystem(WorldTimeSystem.class).getMinuteOfDay());
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
        if (!run.isGameOver()) { run.advance(interval, clockNight()); }
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
