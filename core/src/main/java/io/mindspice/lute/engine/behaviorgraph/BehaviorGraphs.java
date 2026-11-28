package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.Actor;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.ActorAi.Mode;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Combat.AttackSlot;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Physic;
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
    public static final Supplier<RootNode<FocusState>> BASE_MELEE = BehaviorGraphs::engage;
    public static final Supplier<RootNode<FocusState>> BASE_ARCHER = BehaviorGraphs::archer;
    private static final RootNode<FocusState> WANDER = actionGraph("Wander", ActorActions.wander());
    private static final Map<Mode, RootNode<FocusState>> PURSUIT = Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, BASE_MELEE.get(),
        Mode.INVESTIGATE, actionGraph("Investigate last seen position", ActorActions.followTarget()));
    private static final Map<String, Map<Mode, RootNode<FocusState>>> PROFILES = Map.of(
        "wander", Map.of(Mode.WANDER, WANDER), "wander_attack", PURSUIT, "wander_lunge", PURSUIT,
        ARCHER, Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, BASE_ARCHER.get(), Mode.INVESTIGATE, PURSUIT.get(Mode.INVESTIGATE))
    );

    private BehaviorGraphs() { }

    public static void requireKey(String key) {
        if (key == null || !PROFILES.containsKey(key)) {
            throw new IllegalArgumentException("Missing or unknown behavior_graph: " + key);
        }
    }

    public static Map<Mode, RootNode<FocusState>> forKey(String key) { return PROFILES.get(key); }

    public static RootNode<FocusState> pursuit(Mode mode) { return PURSUIT.get(mode); }

    private static RootNode<FocusState> actionGraph(String name, Action<FocusState> action) {
        var root = new RootNode<FocusState>();
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

    private static Decision<FocusState> attackDue(AttackSlot slot, DoubleSupplier delaySeconds) {
        var ready = ActorDecisions.attackReady(slot);
        return (entity, state) -> {
            var ai = ActorAi.MAPPER.get(entity);
            if (Combat.MAPPER.get(entity).isAttacking() || !ready.eval(entity, state)) {
                ai.clearOpportunity();
                return false;
            }
            return ai.decisionDue(state.visibleTarget(), Loadout.MAPPER.get(entity).getWeapon(), slot, state.deltaTime(), delaySeconds);
        };
    }

    private static RootNode<FocusState> archer() {
        var root = new RootNode<FocusState>();
        var bow = DecisionNode.<FocusState>of("Primary projectile weapon", List.of((entity, _) ->
            Loadout.MAPPER.get(entity).getWeapon().primary().collisionType() == CollisionType.PROJECTILE));
        var face = ActorActions.faceTarget();
        Action<FocusState> track = (entity, state) -> {
            var aim = BowAim.MAPPER.get(entity);
            if (!Combat.MAPPER.get(entity).isAttacking() && (aim == null || !aim.isLocked())) { face.perform(entity, state); }
        };
        var plant = ActorActions.stop().andThen(track);
        var ready = DecisionNode.of("Bow opportunity", List.of(attackDue(AttackSlot.PRIMARY, ARCHER_DELAY)));
        ready.addEdgeNode(ActionNode.of("Plant and draw", plant.andThen(ActorActions.attack(AttackSlot.PRIMARY))));
        bow.addEdgeNode(ready);
        var near = DecisionNode.<FocusState>of("Within half detection radius", List.of((entity, state) -> {
            float range = ActorAi.MAPPER.get(entity).detectionRadius() * .5f;
            return state.targetOffset().len2() <= range * range;
        }));
        near.addEdgeNode(ActionNode.of("Hold position", plant));
        bow.addEdgeNode(near);
        bow.addEdgeNode(ActionNode.of("Approach between shots", ActorActions.followTarget()));
        root.addEdgeNode(bow);
        root.addEdgeNode(BASE_MELEE.get());
        return root;
    }

    private static Decision<FocusState> secondaryInRange(Decision<FocusState> primaryInRange) {
        var meleeRange = ActorDecisions.targetWithinPixels(PRIMARY_MELEE_RANGE);
        var lungeRange = ActorDecisions.targetWithinPixels(LUNGE_COMMIT_RANGE);
        return (entity, state) -> {
            if (Actor.MAPPER.get(entity).role() != Actor.Role.ENEMY) { return false; }
            var target = state.visibleTarget();
            if (target == null || !Health.MAPPER.get(target).isAlive()) { return false; }
            return switch (Loadout.MAPPER.get(entity).getWeapon().secondary().animKey()) {
                case TWIRL -> meleeRange.eval(entity, state);
                case LUNGE -> lungeRange.eval(entity, state) && (!primaryInRange.eval(entity, state)
                    || Physic.MAPPER.get(target).getBody().getLinearVelocity().dot(state.targetOffset()) > 0f);
                default -> false;
            };
        };
    }

    private static RootNode<FocusState> engage() {
        var root = new RootNode<FocusState>();
        var primaryMelee = ActorDecisions.hasPrimaryMelee();
        var meleeRange = ActorDecisions.targetWithinPixels(PRIMARY_MELEE_RANGE);
        Decision<FocusState> primaryInRange = (entity, state) -> primaryMelee.eval(entity, state) && meleeRange.eval(entity, state);
        var stopAndFace = ActorActions.stop().andThen(ActorActions.faceTarget());
        Action<FocusState> clearOpportunity = (entity, _) -> ActorAi.MAPPER.get(entity).clearOpportunity();
        // The plant action keeps a pending secondary from falling through and resetting its delay to primary.
        var secondary = DecisionNode.of("Eligible melee secondary", List.of(secondaryInRange(primaryInRange),
            ActorDecisions.attackReady(AttackSlot.SECONDARY)));
        var secondaryReady = DecisionNode.of("Secondary opportunity", List.of(attackDue(AttackSlot.SECONDARY, MELEE_DELAY)));
        secondaryReady.addEdgeNode(ActionNode.of("Attack",
            stopAndFace.andThen(clearOpportunity).andThen(ActorActions.attack(AttackSlot.SECONDARY))));
        secondary.addEdgeNode(secondaryReady);
        secondary.addEdgeNode(ActionNode.of("Plant feet for secondary", stopAndFace));
        root.addEdgeNode(secondary);

        var inRange = DecisionNode.of("Player in melee range", List.of(primaryInRange));
        var ready = DecisionNode.of("Primary opportunity", List.of(attackDue(AttackSlot.PRIMARY, MELEE_DELAY)));
        ready.addEdgeNode(ActionNode.of("Attack", stopAndFace.andThen(ActorActions.attack(AttackSlot.PRIMARY))));
        inRange.addEdgeNode(ready);
        var planted = ActorDecisions.targetWithinPixels(24f);
        var step = DecisionNode.of("Room to step in", List.of(ActorDecisions.attackReady(AttackSlot.PRIMARY),
            (entity, state) -> !planted.eval(entity, state)));
        step.addEdgeNode(ActionNode.of("Close to striking distance", ActorActions.followTarget()));
        inRange.addEdgeNode(step);
        inRange.addEdgeNode(ActionNode.of("Plant feet", stopAndFace));
        root.addEdgeNode(inRange);
        root.addEdgeNode(ActionNode.of("Follow player", clearOpportunity.andThen(ActorActions.followTarget())));
        return root;
    }
}
