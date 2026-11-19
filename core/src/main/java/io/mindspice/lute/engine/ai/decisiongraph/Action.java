package io.mindspice.lute.engine.ai.decisiongraph;

import com.badlogic.ashley.core.Entity;

import java.util.List;
import java.util.Objects;


@FunctionalInterface
public interface Action {

    void perform(Entity entity, FocusState state);

    default Action andThen(Action nextAction) {
        Objects.requireNonNull(nextAction);
        return (entity, state) -> {
            this.perform(entity, state);
            nextAction.perform(entity, state);
        };
    }


    default Action multiAction(List<Action> actions) {
        return actions.stream()
            .reduce(Action::andThen)
            .orElseThrow(() -> new IllegalArgumentException("Must include one action"));
    }
}
