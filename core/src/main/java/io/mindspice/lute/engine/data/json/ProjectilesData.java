package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record ProjectilesData(Map<String, Projectile> projectiles) {
    public ProjectilesData {
        if (projectiles == null || projectiles.get("iron_arrow") == null) {
            throw new IllegalArgumentException("Missing projectile definition: iron_arrow");
        }
        for (var entry : projectiles.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid projectile definition: " + entry.getKey());
            }
        }
        projectiles = Map.copyOf(projectiles);
    }

    public Projectile ironArrow() {
        return projectiles.get("iron_arrow");
    }

    /** Speed is in world units per second, lifetime in seconds, and tip coordinates in sprite pixels. */
    public record Projectile(@JsonProperty("atlas_key") String atlasKey, float speed, float lifetime,
                             @JsonProperty("collision_bounds") CollisionBounds collisionBounds,
                             @JsonProperty("arc_height") float arcHeight,
                             @JsonProperty(value = "tip_x", required = true) float tipX,
                             @JsonProperty(value = "tip_y", required = true) float tipY,
                             @JsonProperty(value = "art_degrees", required = true) float artDegrees) {
        public Projectile {
            if (atlasKey == null || atlasKey.isBlank() || !positive(speed) || !positive(lifetime)
                || collisionBounds == null || !Float.isFinite(arcHeight) || arcHeight < 0
                || !Float.isFinite(tipX) || tipX < 0 || !Float.isFinite(tipY) || tipY < 0
                || !Float.isFinite(artDegrees)) {
                throw new IllegalArgumentException("Invalid projectile flight or sprite geometry: " + atlasKey);
            }
        }
    }

    /** Tip-relative triangle dimensions in pixels: length behind the tip and full width at the base. */
    public record CollisionBounds(float length, float width) {
        public CollisionBounds {
            if (!positive(length) || !positive(width)) {
                throw new IllegalArgumentException("Projectile collision length and width must be positive and finite");
            }
        }
    }

    private static boolean positive(float n) { return n > 0 && Float.isFinite(n); }
}
