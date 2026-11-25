package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.Optional;

public record ObjectData(Optional<AnimationData> animation, Optional<CollisionData> collision,
                         Optional<EmissiveData> emissive, @JsonProperty("atlas_key") Optional<String> atlasKey,
                         Optional<Pickup> pickup) {
    public enum Kind { HEAL, WEAPON_BAG }
    public record Pickup(Kind type, Integer amount) {
        public Pickup {
            Objects.requireNonNull(type, "pickup type");
            if (type == Kind.HEAL ? amount == null || amount <= 0 : amount != null) {
                throw new IllegalArgumentException("Only a heal requires a positive amount");
            }
        }
    }
    public ObjectData {
        if (animation.map(AnimationData::atlasKey).filter(String::isBlank).isPresent()
            || atlasKey.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("Object atlas key cannot be blank");
        }
    }
}
