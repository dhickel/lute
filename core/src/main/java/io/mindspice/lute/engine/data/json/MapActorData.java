package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.mindspice.lute.engine.behaviorgraph.BehaviorGraphs;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public sealed interface MapActorData permits MapActorData.Npc, MapActorData.Enemy {
    AnimationData animation();
    PlayerData.LogicalSize logicalSize();
    CollisionData collision();
    CharacterBounds hitboxBounds();
    float detectionRadius();
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
            validate(name, moveSpeed, maxHealth, animation, sounds, logicalSize, collision, hitboxBounds,
                detectionBounds != null, behaviorGraph);
            if (loadout == null) { throw new IllegalArgumentException("NPC loadout is required"); }
        }

        @Override public String weapon() { return loadout.weapon(); }
        @Override public float detectionRadius() { return detectionBounds.radius(); }
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
        @JsonProperty("detection_bounds") EnemyDetectionBounds detectionBounds,
        @JsonProperty("behavior_graph") String behaviorGraph,
        @JsonProperty(value = "infight_chance", required = true)
        @JsonDeserialize(using = InfightChanceReader.class) Integer infightChance
    ) implements MapActorData {
        public Enemy {
            validate(name, moveSpeed, maxHealth, animation, sounds, logicalSize, collision, hitboxBounds,
                detectionBounds != null, behaviorGraph);
            if (weapon != null && weapon.isBlank()) { throw new IllegalArgumentException("Enemy weapon must not be blank"); }
            if (infightChance == null || infightChance < 0 || infightChance > 100) {
                throw new IllegalArgumentException("Enemy infight_chance must be an integer from 0 to 100");
            }
        }

        @Override public float detectionRadius() { return detectionBounds.radius(); }
    }

    final class InfightChanceReader extends ValueDeserializer<Integer> {
        @Override public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
                throw new IllegalArgumentException("Enemy infight_chance must be a JSON integer");
            }
            return parser.getIntValue();
        }
    }

    /** Circular detection bounds centered on the feet, in logical sprite pixels. */
    record DetectionBounds(@JsonProperty(value = "radius", required = true) float radius) {
        public DetectionBounds { validateDetectionRadius(radius); }
    }

    record EnemyDetectionBounds(
        @JsonProperty(value = "radius", required = true) float radius,
        @JsonProperty(value = "shape", required = true) Shape shape
    ) {
        public EnemyDetectionBounds {
            validateDetectionRadius(radius);
            if (shape == null) { throw new IllegalArgumentException("Enemy detection shape is required"); }
        }

        public enum Shape {
            FULL_CIRCLE, FORWARD_SEMICIRCLE;

            @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
            public static Shape fromJson(JsonNode node) {
                if (!node.isString()) { throw new IllegalArgumentException("Enemy detection shape must be a string"); }
                return valueOf(node.stringValue());
            }
        }
    }

    private static void validateDetectionRadius(float radius) {
        if (!Float.isFinite(radius) || radius <= 0f) {
            throw new IllegalArgumentException("Detection radius must be finite and positive");
        }
    }

    private static void validate(String name, float speed, int maxHealth, AnimationData animation,
                                 PlayerData.Sounds sounds, PlayerData.LogicalSize size, CollisionData feet,
                                 CharacterBounds hitbox, boolean hasDetectionBounds, String behaviorGraph) {
        BehaviorGraphs.requireKey(behaviorGraph);
        if (name == null || name.isBlank()) { throw new IllegalArgumentException("Actor name must not be blank"); }
        if (maxHealth <= 0) { throw new IllegalArgumentException("Actor max_health must be positive"); }
        if (!Float.isFinite(speed) || speed <= 0f) {
            throw new IllegalArgumentException("Actor movement speed must be positive and finite");
        }
        if (animation == null || animation.atlasKey().isBlank() || sounds == null || !hasDetectionBounds) {
            throw new IllegalArgumentException("Actor animation with a sprite prefix, sounds, and detection bounds are required");
        }
        PlayerData.validateBounds(size, feet, hitbox);
    }
}
