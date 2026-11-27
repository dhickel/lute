package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.ActorAi.Mode;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Combat.AttackSlot;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.data.json.AttackAnimKey;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class BehaviorGraphs {
    public static final String ARCHER = "wander_archer";
    private static final float PRIMARY_MELEE_RANGE = 32f;
    private static final float LUNGE_COMMIT_RANGE = 80f;
    private static final DoubleSupplier MELEE_DELAY = () -> ThreadLocalRandom.current().nextFloat(.025f, .10f);
    private static final DoubleSupplier ARCHER_DELAY = () -> ThreadLocalRandom.current().nextFloat(.15f, .30f);
    public static final Supplier<RootNode> BASE_MELEE = BehaviorGraphs::engage;
    public static final Supplier<RootNode> BASE_ARCHER = BehaviorGraphs::archer;
    private static final RootNode WANDER = actionGraph("Wander", Action.wander());
    private static final Map<Mode, RootNode> PURSUIT = Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, BASE_MELEE.get(),
        Mode.INVESTIGATE, actionGraph("Investigate last seen position", Action.followTarget()));
    private static final Map<String, Map<Mode, RootNode>> PROFILES = Map.of(
        "wander", Map.of(Mode.WANDER, WANDER), "wander_attack", PURSUIT,
        "wander_lunge", Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, lungeEngage(), Mode.INVESTIGATE, PURSUIT.get(Mode.INVESTIGATE)),
        ARCHER, Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, BASE_ARCHER.get(), Mode.INVESTIGATE, PURSUIT.get(Mode.INVESTIGATE))
    );

    private BehaviorGraphs() { }

    public static void requireKey(String key) {
        if (key == null || !PROFILES.containsKey(key)) {
            throw new IllegalArgumentException("Missing or unknown behavior_graph: " + key);
        }
    }

    public static Map<Mode, RootNode> forKey(String key) { return PROFILES.get(key); }

    public static RootNode pursuit(Mode mode) { return PURSUIT.get(mode); }

    private static RootNode actionGraph(String name, Action action) {
        var root = new RootNode();
        root.addEdgeNode(ActionNode.of(name, action));
        return root;
    }

    /** An engaging archer can reposition (but not draw) during projectile recovery. */
    public static boolean attackLocksMovement(Entity entity) {
        var combat = Combat.MAPPER.get(entity);
        var ai = ActorAi.MAPPER.get(entity);
        if (ai != null && ai.recovering()) { return true; }
        if (combat == null || !combat.isAttacking()) { return false; }
        return ai == null || ai.mode() != Mode.ENGAGE || !BowAim.MAPPER.has(entity)
            || combat.getActiveAttack().collisionType() != CollisionType.PROJECTILE;
    }

    private static Decision attackDue(AttackSlot slot, DoubleSupplier delaySeconds) {
        var ready = Decision.attackReady(slot);
        return (entity, state) -> {
            var ai = ActorAi.MAPPER.get(entity);
            if (Combat.MAPPER.get(entity).isAttacking() || !ready.eval(entity, state)) {
                ai.clearOpportunity();
                return false;
            }
            return ai.decisionDue(state.visibleTarget(), Loadout.MAPPER.get(entity).getWeapon(), slot, state.deltaTime(), delaySeconds);
        };
    }

    private static RootNode archer() {
        var root = new RootNode();
        var bow = DecisionNode.of("Primary projectile weapon", List.of((entity, _) ->
            Loadout.MAPPER.get(entity).getWeapon().primary().collisionType() == CollisionType.PROJECTILE));
        var face = Action.faceTarget();
        Action track = (entity, state) -> {
            var aim = BowAim.MAPPER.get(entity);
            if (!Combat.MAPPER.get(entity).isAttacking() && (aim == null || !aim.isLocked())) { face.perform(entity, state); }
        };
        var plant = Action.stop().andThen(track);
        var ready = DecisionNode.of("Bow opportunity", List.of(attackDue(AttackSlot.PRIMARY, ARCHER_DELAY)));
        ready.addEdgeNode(ActionNode.of("Plant and draw", plant.andThen(Action.attack(AttackSlot.PRIMARY))));
        bow.addEdgeNode(ready);
        var near = DecisionNode.of("Within half detection radius", List.of((entity, state) -> {
            float range = ActorAi.MAPPER.get(entity).detectionRadius() * .5f;
            return state.targetOffset().len2() <= range * range;
        }));
        near.addEdgeNode(ActionNode.of("Hold position", plant));
        bow.addEdgeNode(near);
        bow.addEdgeNode(ActionNode.of("Approach between shots", Action.followTarget()));
        root.addEdgeNode(bow);
        root.addEdgeNode(BASE_MELEE.get());
        return root;
    }

    private static RootNode lungeEngage() {
        var root = new RootNode();
        var meleeRange = Decision.targetWithinPixels(PRIMARY_MELEE_RANGE);
        var lunge = DecisionNode.of("Visible target to close on", List.of(
            (entity, _) -> Loadout.MAPPER.get(entity).getWeapon().secondary().animKey() == AttackAnimKey.LUNGE,
            Decision.targetWithinPixels(LUNGE_COMMIT_RANGE),
            (entity, state) -> {
                var target = state.visibleTarget();
                if (target == null || !Health.MAPPER.get(target).isAlive()) { return false; }
                return !meleeRange.eval(entity, state)
                    || Physic.MAPPER.get(target).getBody().getLinearVelocity().dot(state.targetOffset()) > 0f;
            },
            Decision.attackReady(AttackSlot.SECONDARY)));
        var plant = Action.stop().andThen(Action.faceTarget());
        var ready = DecisionNode.of("Secondary opportunity", List.of(attackDue(AttackSlot.SECONDARY, MELEE_DELAY)));
        ready.addEdgeNode(ActionNode.of("Lunge", plant.andThen(Action.attack(AttackSlot.SECONDARY))));
        lunge.addEdgeNode(ready);
        lunge.addEdgeNode(ActionNode.of("Plant feet for lunge", plant));
        root.addEdgeNode(lunge);
        root.addEdgeNode(BASE_MELEE.get());
        return root;
    }

    private static RootNode engage() {
        var root = new RootNode();
        var inRange = DecisionNode.of("Player in melee range", List.of(Decision.hasPrimaryMelee(),
            Decision.targetWithinPixels(PRIMARY_MELEE_RANGE)));
        var stopAndFace = Action.stop().andThen(Action.faceTarget());
        var ready = DecisionNode.of("Primary opportunity", List.of(attackDue(AttackSlot.PRIMARY, MELEE_DELAY)));
        ready.addEdgeNode(ActionNode.of("Attack", stopAndFace.andThen(Action.attack(AttackSlot.PRIMARY))));
        inRange.addEdgeNode(ready);
        var planted = Decision.targetWithinPixels(24f);
        var step = DecisionNode.of("Room to step in", List.of(Decision.attackReady(AttackSlot.PRIMARY),
            (entity, state) -> !planted.eval(entity, state)));
        step.addEdgeNode(ActionNode.of("Close to striking distance", Action.followTarget()));
        inRange.addEdgeNode(step);
        inRange.addEdgeNode(ActionNode.of("Plant feet", stopAndFace));
        root.addEdgeNode(inRange);
        Action disengage = (entity, _) -> ActorAi.MAPPER.get(entity).clearOpportunity();
        root.addEdgeNode(ActionNode.of("Follow player", disengage.andThen(Action.followTarget())));
        return root;
    }
}
