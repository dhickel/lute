package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ObjectData(
    @JsonProperty(value = "animation", required = true) AnimationData animation,
    @JsonProperty(required = true) CollisionData collision
) {
    public ObjectData {
        if (animation != null && animation.atlasKey().isBlank()) {
            throw new IllegalArgumentException("Object animation requires an atlas_key");
        }
    }
}
