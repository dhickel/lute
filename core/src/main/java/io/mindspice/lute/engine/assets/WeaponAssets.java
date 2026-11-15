package io.mindspice.lute.engine.assets;

import io.mindspice.lute.engine.data.json.WeaponsData;

import java.util.List;

public record WeaponAssets(
    Asset.Sound attack,
    Asset.Sound hit
) {
    public static WeaponAssets of(String weaponId, WeaponsData definitions) {
        var sounds = definitions.requireWeapon(weaponId).sounds();

        return new WeaponAssets(
            Asset.Sound.of("weapon/" + weaponId + "/attack", sounds.attack()),
            Asset.Sound.of("weapon/" + weaponId + "/hit", sounds.hit()));
    }

    public List<Asset<?>> all() { return List.of(attack, hit); }
}
