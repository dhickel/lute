package io.mindspice.lute.engine.behaviorgraph;

import io.mindspice.lute.engine.components.ActorAi;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Facing;
import io.mindspice.lute.engine.components.Move;

import java.util.concurrent.ThreadLocalRandom;

public final class ActorActions {
    private ActorActions() { }

    public static Action<FocusState> stop() {
        return (entity, _) -> {
            Move.MAPPER.get(entity).getDirection().setZero();
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action<FocusState> wander() {
        return (entity, state) -> {
            var ai = ActorAi.MAPPER.get(entity);
            if (!ai.advanceWander(state.deltaTime())) { return; }
            var random = ThreadLocalRandom.current();
            var direction = Move.MAPPER.get(entity).getDirection();
            if (direction.isZero()) {
                switch (random.nextInt(4)) {
                    case 0 -> direction.set(1, 0);
                    case 1 -> direction.set(-1, 0);
                    case 2 -> direction.set(0, 1);
                    case 3 -> direction.set(0, -1);
                }
            } else {
                direction.setZero();
            }
            ai.resetWander(ActorAi.wanderPhaseSeconds());
        };
    }

    static Action<FocusState> faceTarget() {
        return (entity, state) -> {
            var offset = state.targetOffset();
            if (offset != null) { Facing.MAPPER.get(entity).faceAlong(offset); }
        };
    }

    public static Action<FocusState> followTarget() {
        return (entity, state) -> {
            Move.MAPPER.get(entity).getDirection().set(state.targetOffset());
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action<FocusState> flee() {
        return (entity, state) -> {
            var offset = state.targetOffset();
            if (offset == null || offset.isZero()) { return; }
            Move.MAPPER.get(entity).getDirection().set(offset).scl(-1f);
            ActorAi.MAPPER.get(entity).resetWander(0f);
        };
    }

    static Action<FocusState> attack(Combat.AttackSlot slot) {
        return (entity, state) -> {
            if (state.visibleTarget() != null) { Combat.MAPPER.get(entity).requestAttack(slot, state.visibleTarget()); }
        };
    }
}
