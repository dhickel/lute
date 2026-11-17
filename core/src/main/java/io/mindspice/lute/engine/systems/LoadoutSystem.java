package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.mindspice.lute.engine.components.Combat;
import io.mindspice.lute.engine.components.Loadout;

public final class LoadoutSystem extends EntitySystem {
    public boolean equip(Entity entity, Loadout.Equipment equipment) {
        if (!canEquip(entity)) { return false; }
        Loadout.MAPPER.get(entity).setEquipment(equipment);
        return true;
    }

    public boolean equipWeapon(Entity entity, String id) {
        if (!canEquip(entity)) { return false; }
        Loadout.MAPPER.get(entity).setWeapon(id);
        return true;
    }

    private boolean canEquip(Entity entity) {
        var combat = Combat.MAPPER.get(entity);
        return combat == null || (!combat.isAttacking() && !combat.hasAttackRequest());
    }
}
