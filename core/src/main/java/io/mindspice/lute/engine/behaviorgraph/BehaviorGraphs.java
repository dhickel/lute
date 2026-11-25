package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.ActorAi.Mode;
import io.mindspice.lute.engine.components.BowAim;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Combat.AttackSlot;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;

import java.util.List;
import java.util.Map;

public final class BehaviorGraphs {
    public static final String ARCHER = "wander_archer";
    private static final RootNode WANDER = actionGraph("Wander", Action.wander());
    private static final Map<Mode, RootNode> PURSUIT = Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, engage(),
        Mode.INVESTIGATE, actionGraph("Investigate last seen position", Action.followTarget()));
    private static final Map<String, Map<Mode, RootNode>> PROFILES = Map.of(
        "wander", Map.of(Mode.WANDER, WANDER), "wander_attack", PURSUIT,
        ARCHER, Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, archer(), Mode.INVESTIGATE, PURSUIT.get(Mode.INVESTIGATE))
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
        return ai == null || ai.mode() != Mode.ENGAGE || !ARCHER.equals(ai.behaviorGraphKey())
            || combat.getActiveAttack().collisionType() != CollisionType.PROJECTILE;
    }

    private static Decision primaryDue() {
        var ready = Decision.attackReady(AttackSlot.PRIMARY);
        return (entity, state) -> {
            var ai = ActorAi.MAPPER.get(entity);
            if (Combat.MAPPER.get(entity).isAttacking() || !ready.eval(entity, state)) {
                ai.clearOpportunity();
                return false;
            }
            return ai.decisionDue(state.visibleTarget(), Loadout.MAPPER.get(entity).getWeapon(), state.deltaTime());
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
        var ready = DecisionNode.of("Bow opportunity", List.of(primaryDue()));
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
        root.addEdgeNode(PURSUIT.get(Mode.ENGAGE));
        return root;
    }

    private static RootNode engage() {
        var root = new RootNode();
        var inRange = DecisionNode.of("Player in melee range", List.of(
            Decision.hasPrimaryMelee(), Decision.targetWithinPixels(32f)));
        var stopAndFace = Action.stop().andThen(Action.faceTarget());
        var ready = DecisionNode.of("Primary opportunity", List.of(primaryDue()));
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
