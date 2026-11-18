package io.mindspice.lute.engine.assets;

import java.util.List;

public final class CharacterAssets {
    public static final Asset.Atlas CHARACTER = Asset.Atlas.of("character", "character.atlas");
    public static final Asset.Atlas ENEMIES = Asset.Atlas.of("enemies", "enemies.atlas");
    public static final Asset.Atlas HEAD = Asset.Atlas.of("head", "head.atlas");
    public static final Asset.Atlas BODY = Asset.Atlas.of("body", "body.atlas");
    public static final Asset.Atlas HANDS = Asset.Atlas.of("hands", "hands.atlas");
    public static final Asset.Atlas FEET = Asset.Atlas.of("feet", "feet.atlas");
    public static final Asset.Atlas WEAPONS = Asset.Atlas.of("weapons", "weapons.atlas");

    private CharacterAssets() { }

    public static List<Asset<?>> all(String weaponAtlas) {
        return List.of(CHARACTER, ENEMIES, HEAD, BODY, HANDS, FEET, Asset.Atlas.of(WEAPONS.id(), weaponAtlas));
    }
}
