package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;

public final class ActionNode extends Node {
    private final Action action;

    public ActionNode(String name, Action action) {
        super(NodeType.ACTION, name);
        this.action = action;
    }

    public static ActionNode of(String name, Action action) {
        return new ActionNode(name, action);
    }

    @Override
    public boolean travel(Entity entity, FocusState state) {
        action.perform(entity, state);
        return true;
    }
}
