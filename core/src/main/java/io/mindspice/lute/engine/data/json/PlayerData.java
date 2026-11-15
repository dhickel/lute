package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    @JsonAlias("equipped_weapon") String equippedWeapon,
    AnimationData animation,
    Map<String, String> sounds
) {
    public PlayerData {
        if (!Float.isFinite(moveSpeed) || moveSpeed <= 0f || maxHealth <= 0
            || equippedWeapon == null || equippedWeapon.isBlank() || animation == null) {
            throw new IllegalArgumentException("Invalid player movement, health, or equipped weapon");
        }
        if (sounds == null || sounds.get("move") == null || sounds.get("move").isBlank()
            || sounds.get("damage") == null || sounds.get("damage").isBlank()) {
            throw new IllegalArgumentException("Player move and damage sounds are required");
        }
        sounds = Map.copyOf(sounds);
    }
}
