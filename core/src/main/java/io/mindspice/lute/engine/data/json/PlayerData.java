package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.List;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    Loadout loadout,
    AnimationData animation,
    Sounds sounds,
    @JsonProperty("logical_size") LogicalSize logicalSize,
    @JsonProperty(required = true) CollisionData collision,
    @JsonProperty("hitbox_bounds") CharacterBounds hitboxBounds
) {
    public PlayerData {
        if (!Float.isFinite(moveSpeed) || moveSpeed <= 0f || maxHealth <= 0
            || loadout == null || animation == null) {
            throw new IllegalArgumentException("Invalid player movement, health, loadout, or animation");
        }
        if (!animation.atlasKey().isEmpty()) {
            throw new IllegalArgumentException("Player animation atlas_key must be empty for the flat character atlas");
        }
        if (sounds == null) { throw new IllegalArgumentException("Player sounds are required"); }
        validateBounds(logicalSize, collision, hitboxBounds);
    }

    static void validateBounds(LogicalSize logicalSize, CollisionData collision, CharacterBounds hitboxBounds) {
        if (logicalSize == null || collision == null || hitboxBounds == null) {
            throw new IllegalArgumentException("Character logical size, collision bounds and hitbox bounds are required");
        }
        for (var bounds : new CharacterBounds[]{collision.bounds(), hitboxBounds}) {
            if (bounds.x2() > logicalSize.width() || bounds.y2() > logicalSize.height()) {
                throw new IllegalArgumentException("Character bounds must fit the logical frame");
            }
        }
    }

    public record LogicalSize(
        @JsonProperty(value = "width", required = true) float width,
        @JsonProperty(value = "height", required = true) float height
    ) {
        public LogicalSize {
            if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Logical sprite dimensions must be finite and positive");
            }
        }
    }

    public record Loadout(String head, String body, String hands, String feet, String weapon,
                          @JsonProperty("weapon_cycle") List<String> weaponCycle) {
        public Loadout {
            for (String id : new String[]{head, body, hands, feet, weapon}) {
                if (id != null && id.isBlank()) {
                    throw new IllegalArgumentException("Equipped item IDs must not be blank");
                }
            }
            weaponCycle = weaponCycle == null ? List.of() : weaponCycle;
            if (weaponCycle.stream().anyMatch(id -> id == null || id.isBlank())
                || new HashSet<>(weaponCycle).size() != weaponCycle.size()) {
                throw new IllegalArgumentException("weapon_cycle must contain distinct, nonblank weapon IDs");
            }
            weaponCycle = List.copyOf(weaponCycle);
        }
    }

    public record Sounds(String move, String damage) {
        public Sounds {
            if (move == null || move.isBlank() || damage == null || damage.isBlank()) {
                throw new IllegalArgumentException("Player move and damage sounds are required");
            }
        }
    }
}
