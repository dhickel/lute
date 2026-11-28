package io.mindspice.lute.engine.behaviorgraph;

import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.singletons.GameConsts;

final class ActorDecisions {
    private ActorDecisions() { }

    static Decision<FocusState> healthAtMost(float fraction) {
        return (entity, _) -> {
            var health = Health.MAPPER.get(entity);
            return health.getCurrent() <= health.getMaximum() * fraction;
        };
    }

    static Decision<FocusState> targetWithinPixels(float pixels) {
        return (_, state) -> {
            float range = pixels * GameConsts.UNIT_SCALE;
            return state.targetOffset() != null && state.targetOffset().len2() <= range * range;
        };
    }

    static Decision<FocusState> hasPrimaryMelee() {
        return (entity, _) -> Loadout.MAPPER.get(entity).getWeapon().primary().collisionType() == CollisionType.MELEE;
    }

    static Decision<FocusState> attackReady(Combat.AttackSlot slot) {
        return (entity, _) -> !Combat.MAPPER.get(entity).isCoolingDown(Loadout.MAPPER.get(entity).getWeapon().id(), slot);
    }
}
