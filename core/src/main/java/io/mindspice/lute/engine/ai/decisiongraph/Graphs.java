package io.mindspice.lute.engine.ai.decisiongraph;

import io.mindspice.lute.engine.components.ActorAi.Mode;
import io.mindspice.lute.engine.components.Combat.AttackSlot;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;

import java.util.List;
import java.util.Map;

public final class Graphs {
    public static final String ARCHER = "wander_archer";
    private static final RootNode WANDER = actionGraph("Wander", Action.wander());
    private static final Map<Mode, RootNode> PURSUIT = Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, engage(),
        Mode.INVESTIGATE, actionGraph("Investigate last seen position", Action.followTarget()));
    private static final Map<String, Map<Mode, RootNode>> PROFILES = Map.of(
        "wander", Map.of(Mode.WANDER, WANDER), "wander_attack", PURSUIT,
        ARCHER, Map.of(Mode.WANDER, WANDER, Mode.ENGAGE, archer(), Mode.INVESTIGATE, PURSUIT.get(Mode.INVESTIGATE))
    );

    private Graphs() { }

    public static void requireKey(String key) {
        if (key == null || !PROFILES.containsKey(key)) {
            throw new IllegalArgumentException("Missing or unknown decision_graph: " + key);
        }
    }

    public static Map<Mode, RootNode> forKey(String key) { return PROFILES.get(key); }

    public static RootNode pursuit(Mode mode) { return PURSUIT.get(mode); }

    private static RootNode actionGraph(String name, Action action) {
        var root = new RootNode();
        root.addEdgeNode(ActionNode.of(name, action));
        return root;
    }

    private static RootNode archer() {
        var root = new RootNode();
        var bow = DecisionNode.of("Primary projectile weapon", List.of((entity, _) ->
            Loadout.MAPPER.get(entity).getWeapon().primary().collisionType() == CollisionType.PROJECTILE));
        bow.addEdgeNode(ActionNode.of("Aim at visible target", Action.stop().andThen(Action.faceTarget())
            .andThen(Action.attack(AttackSlot.PRIMARY))));
        root.addEdgeNode(bow);
        root.addEdgeNode(PURSUIT.get(Mode.ENGAGE));
        return root;
    }

    private static RootNode engage() {
        var root = new RootNode();
        var inRange = DecisionNode.of("Player in melee range", List.of(
            Decision.hasPrimaryMelee(), Decision.targetWithinPixels(32f)));
        var stopAndFace = Action.stop().andThen(Action.faceTarget());
        var ready = DecisionNode.of("Primary attack ready", List.of(Decision.attackReady(AttackSlot.PRIMARY)));
        ready.addEdgeNode(ActionNode.of("Attack", stopAndFace.andThen(Action.attack(AttackSlot.PRIMARY))));
        inRange.addEdgeNode(ready);
        inRange.addEdgeNode(ActionNode.of("Wait for cooldown", stopAndFace));
        root.addEdgeNode(inRange);
        root.addEdgeNode(ActionNode.of("Follow player", Action.followTarget()));
        return root;
    }
}
