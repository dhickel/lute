package io.mindspice.lute.engine.behaviorgraph;

import com.badlogic.ashley.core.Entity;

import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface Action<C> {
    void perform(Entity self, C context);

    default Action<C> andThen(Action<C> nextAction) {
        Objects.requireNonNull(nextAction);
        return (self, context) -> {
            perform(self, context);
            nextAction.perform(self, context);
        };
    }

    default Action<C> multiAction(List<Action<C>> actions) {
        return actions.stream().reduce(Action::andThen)
            .orElseThrow(() -> new IllegalArgumentException("Must include one action"));
    }
}
