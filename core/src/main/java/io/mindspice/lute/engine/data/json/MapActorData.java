package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public sealed interface MapActorData permits MapActorData.Npc, MapActorData.Enemy {
    AnimationData animation();
    PlayerData.LogicalSize logicalSize();
    CharacterBounds collisionBounds();
    CharacterBounds hitboxBounds();
    DetectionBounds detectionBounds();
    int maxHealth();
    String weapon();

    record Npc(
        @JsonProperty("move_speed") float moveSpeed,
        @JsonProperty("max_health") int maxHealth,
        PlayerData.Loadout loadout,
        AnimationData animation,
        PlayerData.Sounds sounds,
        @JsonProperty("logical_size") PlayerData.LogicalSize logicalSize,
        @JsonProperty("collision_bounds") CharacterBounds collisionBounds,
        @JsonProperty("hitbox_bounds") CharacterBounds hitboxBounds,
        @JsonProperty("detection_bounds") DetectionBounds detectionBounds
    ) implements MapActorData {
        public Npc {
            validate(moveSpeed, maxHealth, animation, sounds, logicalSize, collisionBounds, hitboxBounds, detectionBounds);
            if (loadout == null) { throw new IllegalArgumentException("NPC loadout is required"); }
        }

        @Override public String weapon() { return loadout.weapon(); }
    }

    record Enemy(
        @JsonProperty("move_speed") float moveSpeed,
        @JsonProperty("max_health") int maxHealth,
        @JsonProperty(value = "weapon", required = true) String weapon,
        AnimationData animation,
        PlayerData.Sounds sounds,
        @JsonProperty("logical_size") PlayerData.LogicalSize logicalSize,
        @JsonProperty("collision_bounds") CharacterBounds collisionBounds,
        @JsonProperty("hitbox_bounds") CharacterBounds hitboxBounds,
        @JsonProperty("detection_bounds") DetectionBounds detectionBounds
    ) implements MapActorData {
        public Enemy {
            validate(moveSpeed, maxHealth, animation, sounds, logicalSize, collisionBounds, hitboxBounds, detectionBounds);
            if (weapon != null && weapon.isBlank()) { throw new IllegalArgumentException("Enemy weapon must not be blank"); }
        }
    }

    /** Circular detection bounds centered on the feet, in logical sprite pixels. */
    record DetectionBounds(@JsonProperty(value = "radius", required = true) float radius) {
        public DetectionBounds {
            if (!Float.isFinite(radius) || radius <= 0f) {
                throw new IllegalArgumentException("Detection radius must be finite and positive");
            }
        }
    }

    private static void validate(float speed, int health, AnimationData animation, PlayerData.Sounds sounds,
                                 PlayerData.LogicalSize size, CharacterBounds feet, CharacterBounds hitbox,
                                 DetectionBounds detection) {
        if (!Float.isFinite(speed) || speed <= 0f || health <= 0) {
            throw new IllegalArgumentException("Actor movement speed and health must be positive and finite");
        }
        if (animation == null || animation.atlasKey().isBlank() || sounds == null || detection == null) {
            throw new IllegalArgumentException("Actor animation with a sprite prefix, sounds, and detection bounds are required");
        }
        PlayerData.validateBounds(size, feet, hitbox);
    }
}
