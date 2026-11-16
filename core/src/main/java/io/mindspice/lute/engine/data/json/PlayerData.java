package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    Loadout loadout,
    AnimationData animation,
    Sounds sounds
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
    }

    public record Loadout(String head, String body, String hands, String feet,
                          @JsonProperty(value = "weapon", required = true) String weapon) {
        public Loadout {
            if (head == null || head.isBlank() || body == null || body.isBlank()
                || hands == null || hands.isBlank() || feet == null || feet.isBlank()
                || (weapon != null && weapon.isBlank())) {
                throw new IllegalArgumentException("Equipment IDs are required; weapon may be null for unarmed");
            }
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
