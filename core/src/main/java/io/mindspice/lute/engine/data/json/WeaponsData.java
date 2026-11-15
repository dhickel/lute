package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public record WeaponsData(String atlas, Map<String, WeaponData> weapons) {
    public WeaponsData {
        atlas = atlas == null ? "NONE" : atlas;
        if (atlas.isBlank()) { throw new IllegalArgumentException("Weapon atlas must be a path or NONE"); }
        weapons = Map.copyOf(Objects.requireNonNull(weapons, "weapons"));
    }

    public WeaponData requireWeapon(String id) {
        var weapon = weapons.get(id);
        if (weapon == null) { throw new IllegalArgumentException("Unknown weapon: " + id); }
        if (!"NONE".equals(atlas) || !"NONE".equals(weapon.animation().atlasKey())) {
            throw new IllegalArgumentException("Separate weapon atlas rendering is not supported yet: " + id);
        }
        return weapon;
    }

    public record WeaponData(Stats stats, WeaponAnimation animation, Sounds sounds, Bounds bounds) {
        public WeaponData {
            Objects.requireNonNull(stats, "stats");
            Objects.requireNonNull(animation, "animation");
            Objects.requireNonNull(sounds, "sounds");
            Objects.requireNonNull(bounds, "bounds");
        }

        public record Stats(int damage, int cost, @JsonProperty("hit_frame") int hitFrame) {
            public Stats {
                if (damage <= 0 || cost < 0 || hitFrame < 0) {
                    throw new IllegalArgumentException("Weapon damage must be positive; cost and hit_frame must be nonnegative");
                }
            }
        }

        public record WeaponAnimation(float speed, @JsonProperty("atlas_key") String atlasKey,
                                      @JsonProperty("sound_frame") int soundFrame) {
            public WeaponAnimation {
                atlasKey = atlasKey == null ? "NONE" : atlasKey;
                if (!Float.isFinite(speed) || speed <= 0 || soundFrame < 0 || atlasKey.isBlank()) {
                    throw new IllegalArgumentException("Invalid weapon animation speed, atlas_key or sound_frame");
                }
            }
        }

        public record Sounds(String attack, String hit) {
            public Sounds {
                if (attack == null || attack.isBlank() || hit == null || hit.isBlank()) {
                    throw new IllegalArgumentException("Weapon attack and hit sound paths are required");
                }
            }
        }

        public record Bounds(BoundsType type, float x1, float y1, float x2, float y2) {
            public Bounds {
                Objects.requireNonNull(type, "bounds.type");
                if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)
                    || x2 <= x1 || y2 <= y1) {
                    throw new IllegalArgumentException("Weapon bounds must be finite with x2 > x1 and y2 > y1");
                }
            }
        }
        public enum BoundsType { SEMICIRCLE, TRIANGLE, RECT }
    }
}
