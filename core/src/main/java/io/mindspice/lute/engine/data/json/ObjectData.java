package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;

public record ObjectData(
    @JsonProperty(required = true) Optional<AnimationData> animation,
    @JsonProperty(required = true) Optional<CollisionData> collision,
    @JsonProperty(required = true) Optional<EmissiveData> emissive
) {
    public ObjectData {
        Objects.requireNonNull(collision, "collision");
        Objects.requireNonNull(emissive, "emissive");
        if (animation.isPresent() && animation.get().atlasKey().isBlank()) {
            throw new IllegalArgumentException("Object animation requires an atlas_key");
        }
    }
}
