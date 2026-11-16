package io.mindspice.lute.engine.assets;

import io.mindspice.lute.engine.data.json.WeaponsData;

import java.util.List;

public record WeaponAssets(AttackSounds primary, AttackSounds secondary) {
    public record AttackSounds(Asset.Sound attack, Asset.Sound hit) { }

    public static WeaponAssets of(String weaponId, WeaponsData definitions) {
        var attacks = definitions.requireWeapon(weaponId).attackStats();
        return new WeaponAssets(sounds(weaponId, "primary", attacks.primary().sounds()),
            sounds(weaponId, "secondary", attacks.secondary().sounds()));
    }

    private static AttackSounds sounds(String weaponId, String slot, WeaponsData.WeaponData.Sounds sounds) {
        String prefix = "weapon/" + weaponId + "/" + slot;
        return new AttackSounds(Asset.Sound.of(prefix + "/attack", sounds.attack()),
            Asset.Sound.of(prefix + "/hit", sounds.hit()));
    }

    public List<Asset<?>> all() { return List.of(primary.attack(), primary.hit(), secondary.attack(), secondary.hit()); }
}
