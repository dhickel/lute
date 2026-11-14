package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.Map;

public record WeaponsData(
    Map<String, WeaponData> weapons
) {

    public record WeaponData(
        int damage,
        @JsonAlias("attack_speed") float attackSpeed,
        @JsonAlias("attack_duration") float attackDuration,
        @JsonAlias("hit_delay") float hitDelay,
        @JsonAlias("bounds_type") BoundsType boundsType,
        Bounds bounds
    ) {
        public record Bounds(float x1, float y1, float x2, float y2) { }
        public enum BoundsType {SEMICIRCLE, TRIANGLE, RECT}
    }
}
