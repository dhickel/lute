package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;

public sealed interface MapActorData permits MapActorData.Npc, MapActorData.Enemy {
    AnimationData animation();
    PlayerData.LogicalSize logicalSize();
    CollisionData collision();
    CharacterBounds hitboxBounds();
    DetectionBounds detectionBounds();
    float moveSpeed();
    String name();
    int maxHealth();
    String behaviorGraph();
    String weapon();

    record Npc(
        String name,
        @JsonProperty("move_speed") float moveSpeed,
        @JsonProperty("max_health") int maxHealth,
        PlayerData.Loadout loadout,
        AnimationData animation,
        PlayerData.Sounds sounds,
        @JsonProperty("logical_size") PlayerData.LogicalSize logicalSize,
        @JsonProperty(required = true) CollisionData collision,
        @JsonProperty("hitbox_bounds") CharacterBounds hitboxBounds,
        @JsonProperty("detection_bounds") DetectionBounds detectionBounds,
        @JsonProperty("behavior_graph") String behaviorGraph
    ) implements MapActorData {
        public Npc {
            validate(name, moveSpeed, maxHealth, animation, sounds, logicalSize, collision, hitboxBounds, detectionBounds, behaviorGraph);
            if (loadout == null) { throw new IllegalArgumentException("NPC loadout is required"); }
        }

        @Override public String weapon() { return loadout.weapon(); }
    }

    record Enemy(
        String name,
        @JsonProperty("move_speed") float moveSpeed,
        @JsonProperty("max_health") int maxHealth,
        @JsonProperty(value = "weapon", required = true) String weapon,
        AnimationData animation,
        PlayerData.Sounds sounds,
        @JsonProperty("logical_size") PlayerData.LogicalSize logicalSize,
        @JsonProperty(required = true) CollisionData collision,
        @JsonProperty("hitbox_bounds") CharacterBounds hitboxBounds,
        @JsonProperty("detection_bounds") DetectionBounds detectionBounds,
        @JsonProperty("behavior_graph") String behaviorGraph
    ) implements MapActorData {
        public Enemy {
            validate(name, moveSpeed, maxHealth, animation, sounds, logicalSize, collision, hitboxBounds, detectionBounds, behaviorGraph);
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

    private static void validate(String name, float speed, int maxHealth, AnimationData animation,
                                 PlayerData.Sounds sounds, PlayerData.LogicalSize size, CollisionData feet,
                                 CharacterBounds hitbox, DetectionBounds detection, String behaviorGraph) {
        BehaviorGraphs.requireKey(behaviorGraph);
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Actor name must not be blank"); }
        if (maxHealth <= 0) { throw new IllegalArgumentException("Actor max_health must be positive"); }
        if (!Float.isFinite(speed) || speed <= 0f) {
            throw new IllegalArgumentException("Actor movement speed must be positive and finite");
        }
        if (animation == null || animation.atlasKey().isBlank() || sounds == null || detection == null) {
            throw new IllegalArgumentException("Actor animation with a sprite prefix, sounds, and detection bounds are required");
        }
        PlayerData.validateBounds(size, feet, hitbox);
    }
}
