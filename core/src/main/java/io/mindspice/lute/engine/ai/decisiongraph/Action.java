package io.mindspice.lute.engine.ai.decisiongraph;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Move;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;


@FunctionalInterface
public interface Action {

    void perform(Entity entity, FocusState state);

    static Action stop() {
        return (entity, _) -> {
            Move.MAPPER.get(entity).getDirection().setZero();
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action wander() {
        return (entity, state) -> {
            var ai = ActorAi.MAPPER.get(entity);
            if (!ai.advanceWander(state.deltaTime())) { return; }
            var random = ThreadLocalRandom.current();
            var direction = Move.MAPPER.get(entity).getDirection();
            switch (random.nextInt(4)) {
                case 0 -> direction.set(1, 0);
                case 1 -> direction.set(-1, 0);
                case 2 -> direction.set(0, 1);
                case 3 -> direction.set(0, -1);
            }
            ai.resetWander(random.nextFloat(.35f, .8f));
        };
    }

    static Action faceTarget() {
        return (entity, state) -> {
            var offset = state.targetOffset();
            if (offset != null) { Facing.MAPPER.get(entity).faceAlong(offset); }
        };
    }

    static Action followTarget() {
        return (entity, state) -> {
            Move.MAPPER.get(entity).getDirection().set(state.targetOffset());
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action flee() {
        return (entity, state) -> {
            var offset = state.targetOffset();
            if (offset == null || offset.isZero()) { return; }
            Move.MAPPER.get(entity).getDirection().set(offset).scl(-1f);
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action attack(Combat.AttackSlot slot) {
        return (entity, _) -> Combat.MAPPER.get(entity).requestAttack(slot);
    }

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
