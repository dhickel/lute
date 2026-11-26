package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

public record WeaponsData(String atlas, Map<String, WeaponData> weapons) {
    public WeaponsData {
        if (atlas == null || atlas.isBlank() || "NONE".equals(atlas)) {
            throw new IllegalArgumentException("A common weapon atlas is required");
        }
        weapons = Map.copyOf(Objects.requireNonNull(weapons, "weapons"));
        if (!weapons.containsKey("unarmed")) {
            throw new IllegalArgumentException("An explicit unarmed definition is required");
        }
        weapons.forEach((id, weapon) -> {
            if (id.isBlank()) { throw new IllegalArgumentException("Weapon ID is required"); }
            boolean unarmed = id.equals("unarmed");
            if (unarmed != weapon.atlasKey().equals("NONE")) {
                throw new IllegalArgumentException("Only unarmed uses atlas_key NONE");
            }
            var primary = weapon.attackStats().primary();
            var secondary = weapon.attackStats().secondary();
            if (!secondary.animation().atlasKey().equals("jab") || secondary.collisionType() != WeaponData.CollisionType.MELEE
                || (unarmed && (!primary.animation().atlasKey().equals("jab")
                    || primary.collisionType() != WeaponData.CollisionType.MELEE))) {
                throw new IllegalArgumentException("Secondary and unarmed attacks must be melee jabs");
            }
        });
    }

    public WeaponData requireWeapon(String id) {
        var weapon = weapons.get(id);
        if (weapon == null) { throw new IllegalArgumentException("Unknown weapon: " + id); }
        return weapon;
    }

    public record WeaponData(@JsonProperty("attack_stats") AttackStats attackStats,
                             @JsonProperty("atlas_key") String atlasKey) {
        public WeaponData {
            Objects.requireNonNull(attackStats, "attack_stats");
            if (atlasKey == null || atlasKey.isBlank()) {
                throw new IllegalArgumentException("Weapon atlas_key is required");
            }
        }

        public record AttackStats(AttackData primary, AttackData secondary) {
            public AttackStats {
                Objects.requireNonNull(primary, "attack_stats.primary");
                Objects.requireNonNull(secondary, "attack_stats.secondary");
            }
        }

        public record AttackData(AnimationData animation, Bounds bounds, Stats stats, Sounds sounds,
                                 @JsonProperty("collision_type") CollisionType collisionType) {
            public AttackData {
                Objects.requireNonNull(sounds, "sounds");
                Objects.requireNonNull(animation, "animation");
                Objects.requireNonNull(bounds, "bounds");
                Objects.requireNonNull(stats, "stats");
                Objects.requireNonNull(collisionType, "collision_type");
                String action = animation.atlasKey();
                if (!(action.equals("slash") || action.equals("jab") || action.equals("shot"))
                    || (action.equals("shot") != (collisionType == CollisionType.PROJECTILE))) {
                    throw new IllegalArgumentException("Attack action must be melee slash/jab or projectile shot");
                }
            }
        }

        public record Stats(@JsonProperty(value = "damage", required = true) AttackDamage damage,
                            @JsonProperty(value = "cost", required = true) int cost,
                            @JsonProperty(value = "hit_frame", required = true) int hitFrame,
                            @JsonProperty(value = "cooldown_seconds", required = true) float cooldownSeconds) {
            public Stats {
                Objects.requireNonNull(damage, "damage");
                if (cost < 0 || hitFrame < 0) {
                    throw new IllegalArgumentException("Weapon cost and hit_frame must be nonnegative");
                }
                if (!Float.isFinite(cooldownSeconds) || cooldownSeconds <= 0f) {
                    throw new IllegalArgumentException("Weapon cooldown_seconds must be finite and positive");
                }
            }
        }

        public record Sounds(String attack, String hit) {
            public Sounds {
                if (attack == null || attack.isBlank() || hit == null || hit.isBlank()) {
                    throw new IllegalArgumentException("Weapon attack and hit sound paths are required");
                }
                for (String path : new String[]{attack, hit}) {
                    if (path.startsWith("/") || path.contains("\\") || path.contains(":")
                        || java.util.Arrays.stream(path.split("/", -1))
                            .anyMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."))) {
                        throw new IllegalArgumentException("Weapon sounds must be relative audio file paths");
                    }
                }
            }
        }

        public record Bounds(BoundsType type,
                             @JsonProperty(value = "x1", required = true) float x1,
                             @JsonProperty(value = "y1", required = true) float y1,
                             @JsonProperty(value = "x2", required = true) float x2,
                             @JsonProperty(value = "y2", required = true) float y2,
                             @JsonProperty(value = "offset_up", required = true) Offset offsetUp,
                             @JsonProperty(value = "offset_down", required = true) Offset offsetDown,
                             @JsonProperty(value = "offset_left", required = true) Offset offsetLeft,
                             @JsonProperty(value = "offset_right", required = true) Offset offsetRight) {
            public Bounds {
                Objects.requireNonNull(type, "bounds.type");
                Objects.requireNonNull(offsetUp, "bounds.offset_up");
                Objects.requireNonNull(offsetDown, "bounds.offset_down");
                Objects.requireNonNull(offsetLeft, "bounds.offset_left");
                Objects.requireNonNull(offsetRight, "bounds.offset_right");
                if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)
                    || x2 <= x1 || y2 <= y1) {
                    throw new IllegalArgumentException("Weapon bounds must be finite with x2 > x1 and y2 > y1");
                }
            }
        }
        public record Offset(@JsonProperty(value = "x", required = true) float x,
                             @JsonProperty(value = "y", required = true) float y) {
            public Offset {
                if (!Float.isFinite(x) || !Float.isFinite(y)) {
                    throw new IllegalArgumentException("Weapon bounds offsets must be finite");
                }
            }
        }

        public enum BoundsType { SEMICIRCLE, TRIANGLE, RECT }
        public enum CollisionType { MELEE, PROJECTILE }
    }
}
