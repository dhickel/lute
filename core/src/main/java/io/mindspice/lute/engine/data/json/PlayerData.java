package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PlayerData(
    String name,
    @JsonAlias("move_speed") float moveSpeed,
    @JsonAlias("max_health") int maxHealth,
    @JsonAlias("equipped_weapon") String equippedWeapon
) {
}
