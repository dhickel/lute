package io.mindspice.lute.engine.ai.decisiongraph;

import com.badlogic.ashley.core.Entity;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Health;
import io.mindspice.lute.engine.components.Loadout;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.CollisionType;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface Decision {
    boolean eval(Entity entity, FocusState state);

    static Decision pctChance(int chancePct) {
        return (_, _) -> ThreadLocalRandom.current().nextInt(100) < chancePct;
    }

    static Decision healthAtMost(float fraction) {
        return (entity, _) -> {
            var health = Health.MAPPER.get(entity);
            return health.getCurrent() <= health.getMaximum() * fraction;
        };
    }

    static Decision targetWithinPixels(float pixels) {
        return (_, state) -> {
            float range = pixels * GameConsts.UNIT_SCALE;
            return state.targetOffset() != null && state.targetOffset().len2() <= range * range;
        };
    }

    static Decision hasPrimaryMelee() {
        return (entity, _) -> Loadout.MAPPER.get(entity).getWeapon().primary().collisionType() == CollisionType.MELEE;
    }

    static Decision attackReady(Combat.AttackSlot slot) {
        return (entity, _) -> !Combat.MAPPER.get(entity).isCoolingDown(Loadout.MAPPER.get(entity).getWeapon().id(), slot);
    }
}
