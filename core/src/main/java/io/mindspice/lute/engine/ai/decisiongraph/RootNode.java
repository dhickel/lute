package io.mindspice.lute.engine.ai.decisiongraph;


import com.badlogic.ashley.core.Entity;

public non-sealed class RootNode extends Node {

    public RootNode() {
        super(NodeType.ROOT, "ROOT");
    }

    @Override
    public boolean travel(Entity entity, FocusState state) {
        for (Node edge : edgeNodes) {
            if (edge.travel(entity, state)) {
                return true;
            }
        } // TODO add fallback logic
        return  false;
    }

}
