package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.Map;

public record ProjectilesData(Map<String, Projectile> projectiles) {
    public ProjectilesData {
        if (projectiles == null || projectiles.isEmpty()) {
            throw new IllegalArgumentException("Missing projectile definitions");
        }
        for (var entry : projectiles.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue() == null) {
                throw new IllegalArgumentException("Invalid projectile definition: " + entry.getKey());
            }
        }
        projectiles = Map.copyOf(projectiles);
    }

    public static Projectile loadIronArrow() {
        try (var input = ProjectilesData.class.getResourceAsStream("/projectiles.json")) {
            if (input == null) { throw new IllegalStateException("Missing projectiles.json"); }
            var arrow = DataLoader.mapper.readValue(input, ProjectilesData.class).projectiles().get("iron_arrow");
            if (arrow == null) { throw new IllegalStateException("Missing projectile definition: iron_arrow"); }
            return arrow;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read projectiles.json", e);
        }
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
