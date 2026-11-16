package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

import java.util.Map;
import java.util.function.Consumer;

public final class Loadout implements Component {
    public static final ComponentMapper<Loadout> MAPPER = ComponentMapper.getFor(Loadout.class);
    public record Equipment(String head, String body, String hands, String feet) { }

    private Equipment equipment;
    private final Combat combat;
    private final Map<String, CombatWeapon.Weapon> weapons;
    private final Consumer<Equipment> validateEquipment;

    public Loadout(Equipment equipment, Combat combat, Map<String, CombatWeapon.Weapon> weapons,
                   Consumer<Equipment> validateEquipment) {
        this.equipment = equipment;
        this.combat = combat;
        this.weapons = Map.copyOf(weapons);
        this.validateEquipment = validateEquipment;
        validateEquipment.accept(equipment);
    }

    public Equipment getEquipment() { return equipment; }
    public String getWeaponId() {
        String id = combat.getCurrentWeapon().id();
        return id.equals("unarmed") ? null : id;
    }

    public boolean equip(Equipment equipment) {
        if (combat.isAttacking() || combat.hasAttackRequest()) { return false; }
        validateEquipment.accept(equipment);
        this.equipment = equipment;
        return true;
    }

    public boolean equipWeapon(String id) {
        if (combat.isAttacking() || combat.hasAttackRequest()) { return false; }
        var weapon = weapons.get(id == null ? "unarmed" : id);
        if (weapon == null) { throw new IllegalArgumentException("Unknown weapon: " + id); }
        return combat.setWeapon(weapon);
    }
}
