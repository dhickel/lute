package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Shared animation fields; keep Tiled's AnimatedPhysicObject properties in sync. */
public record AnimationData(@JsonProperty(value = "speed", required = true) float speed,
                            @JsonProperty(value = "atlas_key", required = true) String atlasKey,
                            @JsonProperty(value = "sound_frame", required = true) int soundFrame,
                            @JsonProperty(value = "frame_interval", required = true) float frameInterval) {
    public AnimationData {
        if (!Float.isFinite(speed) || speed <= 0 || atlasKey == null
            || soundFrame < 0 || !Float.isFinite(frameInterval) || frameInterval <= 0) {
            throw new IllegalArgumentException("Invalid animation speed, atlas_key, sound_frame or frame_interval");
        }
    }
}
