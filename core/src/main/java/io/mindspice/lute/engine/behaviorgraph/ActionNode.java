package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;

public final class ActionNode<C> extends Node<C> {
    private final Action<C> action;

    public ActionNode(String name, Action<C> action) {
        super(NodeType.ACTION, name);
        this.action = action;
    }

    public static <C> ActionNode<C> of(String name, Action<C> action) {
        return new ActionNode<>(name, action);
    }

    @Override
    public boolean travel(Entity self, C context) {
        action.perform(self, context);
        return true;
    }
}
