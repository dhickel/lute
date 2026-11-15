package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Shared animation fields; keep Tiled's AnimatedPhysicObject properties in sync. */
public record AnimationData(float speed, @JsonProperty("atlas_key") String atlasKey,
                            @JsonProperty("sound_frame") int soundFrame,
                            @JsonProperty("frame_interval") float frameInterval) {
    public AnimationData {
        atlasKey = atlasKey == null ? "NONE" : atlasKey;
        if (!Float.isFinite(speed) || speed <= 0 || atlasKey.isBlank()
            || soundFrame < 0 || !Float.isFinite(frameInterval) || frameInterval <= 0) {
            throw new IllegalArgumentException("Invalid animation speed, atlas_key, sound_frame or frame_interval");
        }
    }
}
