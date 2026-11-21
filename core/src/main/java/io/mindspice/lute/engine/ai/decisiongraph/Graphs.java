package io.mindspice.lute.engine.ai.decisiongraph;

import io.mindspice.lute.engine.components.Combat.AttackSlot;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Graphs {
    private static final Map<String, Function<Decision, RootNode>> BUILDERS = Map.of(
        "wander", _ -> wandering(),
        "wander_attack", Graphs::wanderingAttacker
    );

    private Graphs() { }

    public static void requireKey(String key) {
        if (key == null || !BUILDERS.containsKey(key)) {
            throw new IllegalArgumentException("Missing or unknown decision_graph: " + key);
        }
    }

    public static Map<String, RootNode> build(Decision seesPlayer) {
        return BUILDERS.entrySet().stream().collect(Collectors.toUnmodifiableMap(
            Map.Entry::getKey, entry -> entry.getValue().apply(seesPlayer)));
    }

    private static RootNode wandering() {
        var root = new RootNode();
        root.addEdgeNode(ActionNode.of("Wander", Action.wander()));
        return root;
    }

    private static RootNode wanderingAttacker(Decision seesPlayer) {
        var root = new RootNode();
        var visible = DecisionNode.of("Visible player", List.of(seesPlayer));
        var inRange = DecisionNode.of("Player in melee range", List.of(
            Decision.hasPrimaryMelee(), Decision.targetWithinPixels(32f)));
        var stopAndFace = Action.stop().andThen(Action.faceTarget());
        var ready = DecisionNode.of("Primary attack ready", List.of(Decision.attackReady(AttackSlot.PRIMARY)));
        ready.addEdgeNode(ActionNode.of("Attack", stopAndFace.andThen(Action.attack(AttackSlot.PRIMARY))));
        inRange.addEdgeNode(ready);
        inRange.addEdgeNode(ActionNode.of("Wait for cooldown", stopAndFace));
        visible.addEdgeNode(inRange);
        visible.addEdgeNode(ActionNode.of("Follow player", Action.followTarget()));
        root.addEdgeNode(visible);
        root.addEdgeNode(ActionNode.of("Wander", Action.wander()));
        return root;
    }
}
