package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    @JsonAlias("equipped_weapon") String equippedWeapon,
    AnimationData animation,
    Sounds sounds
) {
    public PlayerData {
        if (!Float.isFinite(moveSpeed) || moveSpeed <= 0f || maxHealth <= 0
            || equippedWeapon == null || equippedWeapon.isBlank() || animation == null) {
            throw new IllegalArgumentException("Invalid player movement, health, or equipped weapon");
        }
        if (sounds == null) { throw new IllegalArgumentException("Player sounds are required"); }
    }

    public record Sounds(String move, String damage) {
        public Sounds {
            if (move == null || move.isBlank() || damage == null || damage.isBlank()) {
                throw new IllegalArgumentException("Player move and damage sounds are required");
            }
        }
    }
}
