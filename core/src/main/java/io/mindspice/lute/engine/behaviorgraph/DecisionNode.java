package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;
import java.util.List;

public final class DecisionNode<C> extends Node<C> {
    private final List<Decision<C>> decisions;

    public DecisionNode(String name, List<Decision<C>> decisions) {
        super(NodeType.DECISION, name);
        this.decisions = decisions;
    }

    public static <C> DecisionNode<C> of(String name, List<Decision<C>> decisions) {
        return new DecisionNode<>(name, decisions);
    }

    @Override
    public boolean travel(Entity self, C context) {
        for (var decision : decisions) {
            if (!decision.eval(self, context)) { return false; }
        }
        for (var node : edgeNodes) {
            if (node.travel(self, context)) { return true; }
        }
        return false;
    }
}
