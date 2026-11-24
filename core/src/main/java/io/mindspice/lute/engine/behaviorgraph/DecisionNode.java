package io.mindspice.lute.engine.behaviorgraph;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.badlogic.ashley.core.Entity;

public final class DecisionNode extends Node {
    List<Decision> decisions;

    public DecisionNode(String name, List<Decision> decisions) {
        super(NodeType.DECISION, name);
        this.decisions = decisions;
    }

    public static DecisionNode of(String name, List<Decision> decisions) {
        return new DecisionNode(name, decisions);
    }

    @Override
    public boolean travel(Entity entity, FocusState state) {
        for (var decision : decisions) {
            var decided = decision.eval(entity, state);
            // if any decision fails return
            if (!decided) { return false; }
        }

        for (Node node : edgeNodes) {
            if (node.travel(entity, state)) { return true; }
        }
        return false;
    }
}
