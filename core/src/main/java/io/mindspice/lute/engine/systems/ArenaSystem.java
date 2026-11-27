package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.data.json.ObjectData;
import io.mindspice.lute.engine.events.Event;
import io.mindspice.lute.engine.events.EventListener;
import io.mindspice.lute.engine.input.InputManager;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.MapTriggers;
import io.mindspice.lute.gameplay.HoardRun;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/** Shared arena mechanics; the screen supplies the rules for the visit. */
public abstract class ArenaSystem extends EntitySystem implements EventListener {
    interface MapRules {
        List<MapTriggers.SpawnArea> enemyAreas();
        default boolean includeAuthoredEnemyAreas() { return true; }
        default List<String> extraEnemyKeys(MapTriggers.SpawnArea area) { return List.of(); }
        default boolean spawnPoint(MapTriggers.SpawnPoint point) { return true; }
        default Component pointEnemyComponent(String key) { return null; }
        Controller setupLoadout(Loadout loadout);
        void validate(EntityFactory factory, Entity player, BiFunction<String, Facing.Direction, Rectangle> enemyFootprint);
        void start(TiledSystem tiled, RandomGenerator random);
    }

    private static final String KILL_HEALTH_KEY = "health_pack_1";
    private static final float SPRINT_MULTIPLIER = 1.6f;
    private static final float SPRINT_RAMP_UP_SECONDS = .25f;
    private static final float SPRINT_RAMP_DOWN_SECONDS = .10f;
    private static final double SPRINT_SECONDS = 2d;
    private static final double REFILL_PER_SECOND = 0.7d / 6d;
    private static final double REFILL_DELAY_SECONDS = 2d;

    private final InputManager input;
    private TiledSystem tiled;
    private RandomGenerator dropRandom;
    private double stamina = 1d;
    private double refillDelay;
    private long exhaustedPress;
    private long sprintPress;
    private float sprintBoost;

    private final Consumer<Event.Player.KilledEnemy> onKill = event -> {
        // Events may drain after travel or remount. Only credit this visit's actors.
        if (isActive() && !isGameOver() && event.player() == tiled.activePlayer()
            && tiled.owns(event.enemy()) && creditKill(event.enemy()) && dropRandom.nextInt(10) == 0) {
            drop(tiled, AwarenessSystem.feet(event.enemy()), KILL_HEALTH_KEY);
        }
    };

    protected ArenaSystem(InputManager input) { this.input = input; }

    abstract MapRules rulesFor(String mapId, MapTriggers triggers);
    abstract void clearRun();
    abstract boolean isGameOver();
    abstract void endRun();
    abstract void advance(TiledSystem tiled, float interval);
    abstract boolean creditKill(Entity enemy);
    abstract String statusText();

    public AwarenessSystem createAwareness(World world) { return new AwarenessSystem(world, true); }

    final void bind(TiledSystem tiled, MapRules rules, RandomGenerator random) {
        this.tiled = rules == null ? null : tiled;
        dropRandom = rules == null ? null : random;
        stamina = 1d;
        refillDelay = 0d;
        exhaustedPress = 0;
        clearSprintMomentum();
        clearRun();
        if (rules != null) { rules.start(tiled, random); }
    }

    public final boolean isActive() { return tiled != null; }

    final void actorDied(Entity actor) {
        if (!isActive() || !tiled.owns(actor)) { return; }
        if (Actor.MAPPER.get(actor).role() == Actor.Role.PLAYER) { endRun(); }
        else { enemyDied(tiled, actor); }
    }

    void enemyDied(TiledSystem tiled, Entity actor) { }

    static Health newPlayerHealth() { return new Health(HoardRun.PLAYER_HEALTH); }

    static void validateKillDrop(EntityFactory factory) {
        factory.validatePickupSpawn(KILL_HEALTH_KEY, ObjectData.Kind.HEAL);
    }

    final void drop(TiledSystem tiled, Vector2 world, String key) {
        var pixels = world.cpy().scl(1f / GameConsts.UNIT_SCALE);
        tiled.addArenaEntity(getEngine().getSystem(EntityFactory.class).getSpawnedObject(pixels, key, Facing.Direction.DOWN));
    }

    double stamina() { return stamina; }

    public void clearSprintMomentum() {
        sprintPress = 0;
        sprintBoost = 0f;
    }

    long beforeSprintStep(float interval, boolean wasBoosted) {
        if (!isActive() || isGameOver()) { return 0; }
        var player = tiled.activePlayer();
        if (!Health.MAPPER.get(player).isAlive() || !Controller.MAPPER.has(player)) { return 0; }
        if (CombatSystem.isLunging(Combat.MAPPER.get(player))) {
            clearSprintMomentum();
            return 0;
        }

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
        var player = tiled.activePlayer();
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
                refillDelay = REFILL_DELAY_SECONDS;
            } else {
                refill(interval);
            }
            getEngine().getSystem(AnimationSystem.class).advanceSprintWalk(player, interval * sprintBoost);
        } else {
            // Don't bank a full-speed start by holding B against a wall.
            clearSprintMomentum();
            refill(interval);
        }
    }

    private void refill(float interval) {
        double refillTime = Math.max(0d, interval - refillDelay);
        refillDelay = Math.max(0d, refillDelay - interval);
        stamina = Math.min(1d, stamina + refillTime * REFILL_PER_SECOND);
    }

    // Hits resolve at this tick's positions, before a wave spawns or a pickup heals.
    final void afterPhysicsStep(float interval) {
        if (!isActive() || isGameOver()) { return; }
        var engine = getEngine();
        var casualties = engine.getSystem(CombatSystem.class).resolveArenaStepHits(tiled::ownsArrivingEnemy);
        var death = engine.getSystem(DeathSystem.class);
        for (var victim : casualties) { death.beginContactDeath(victim); }
        if (!isGameOver()) {
            tiled.collectPickups();
            advance(tiled, interval);
        }
    }

    @Override public final void addedToEngine(Engine engine) {
        eventSubscriber.accept(Event.Player.KilledEnemy.class, onKill);
    }

    @Override public final void removedFromEngine(Engine engine) {
        eventUnSubscriber.accept(Event.Player.KilledEnemy.class, onKill);
        bind(null, null, null);
    }
}
