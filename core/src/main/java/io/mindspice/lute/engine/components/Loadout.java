package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

import java.util.Map;
import java.util.function.Consumer;

public final class Loadout implements Component {
    public static final ComponentMapper<Loadout> MAPPER = ComponentMapper.getFor(Loadout.class);
    public record Equipment(String head, String body, String hands, String feet) { }

    private Equipment equipment;
    private CombatWeapon.Weapon weapon;
    private final Map<String, CombatWeapon.Weapon> weapons;
    private final Consumer<Equipment> validateEquipment;

    public Loadout(Equipment equipment, String weaponId, Map<String, CombatWeapon.Weapon> weapons,
                   Consumer<Equipment> validateEquipment) {
        this.weapons = Map.copyOf(weapons);
        this.validateEquipment = validateEquipment;
        setEquipment(equipment);
        setWeapon(weaponId);
    }

    public Loadout copy() { return new Loadout(equipment, weapon.id(), weapons, validateEquipment); }

    public Equipment getEquipment() { return equipment; }
    public CombatWeapon.Weapon getWeapon() { return weapon; }
    public String getWeaponId() {
        String id = weapon.id();
        return id.equals("unarmed") ? null : id;
    }

    public void setEquipment(Equipment equipment) {
        validateEquipment.accept(equipment);
        this.equipment = equipment;
    }

    public void setWeapon(String id) {
        var selected = weapons.get(id == null ? "unarmed" : id);
        if (selected == null) { throw new IllegalArgumentException("Unknown weapon: " + id); }
        weapon = selected;
    }
}
