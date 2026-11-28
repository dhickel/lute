package io.mindspice.lute.engine.behaviorgraph;


import com.badlogic.ashley.core.Entity;

public non-sealed class RootNode<C> extends Node<C> {

    public RootNode() {
        super(NodeType.ROOT, "ROOT");
    }

    @Override
    public boolean travel(Entity self, C context) {
        for (var edge : edgeNodes) {
            if (edge.travel(self, context)) {
                return true;
            }
        } // TODO add fallback logic
        return  false;
    }

}
