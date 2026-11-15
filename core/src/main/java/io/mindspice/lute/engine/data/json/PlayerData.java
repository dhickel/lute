package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    @JsonAlias("equipped_weapon") String equippedWeapon,
    PlayerAnimation animation
) {
    public PlayerData {
        if (!Float.isFinite(moveSpeed) || moveSpeed <= 0f || maxHealth <= 0
            || equippedWeapon == null || equippedWeapon.isBlank() || animation == null) {
            throw new IllegalArgumentException("Invalid player movement, health, or equipped weapon");
        }
    }

    public record PlayerAnimation(@JsonProperty("frame_interval") float frameInterval) {
        public PlayerAnimation {
            if (!Float.isFinite(frameInterval) || frameInterval <= 0) {
                throw new IllegalArgumentException("Player frame_interval must be positive and finite");
            }
        }
    }
}
