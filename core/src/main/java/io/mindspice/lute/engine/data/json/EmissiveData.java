package io.mindspice.lute.engine.data.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Optional;

public record EmissiveData(String color, @JsonProperty("radius_pixels") float radiusPixels,
                           float intensity, Optional<Pulse> pulse) {
    public EmissiveData {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("emissive.color must be #RRGGBB");
        }
        if (!(intensity > 0f && intensity <= 1f)) {
            throw new IllegalArgumentException("emissive.intensity must be in (0, 1]");
        }
        checkExtrema(radiusPixels, pulse.map(Pulse::radiusStrength).orElse(0f), "radius_pixels");
        checkExtrema(intensity, pulse.map(Pulse::intensityStrength).orElse(0f), "intensity");
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    private static EmissiveData parse(JsonNode node) {
        if (!node.isObject() || node.size() != 4 || !node.required("color").isString()) {
            throw new IllegalArgumentException("Expected emissive color, radius_pixels, intensity and pulse");
        }
        var radius = positiveNumber(node, "radius_pixels");
        var intensity = positiveNumber(node, "intensity");
        if (intensity.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("emissive.intensity must be at most 1");
        }
        var pulse = node.required("pulse");
        return new EmissiveData(node.get("color").stringValue(), radius.floatValue(), intensity.floatValue(),
            pulse.isNull() ? Optional.empty() : Optional.of(Pulse.parse(pulse)));
    }

    public record Pulse(@JsonProperty("period_seconds") double periodSeconds,
                        @JsonProperty("radius_strength") float radiusStrength,
                        @JsonProperty("intensity_strength") float intensityStrength) {
        public Pulse {
            if (!Double.isFinite(periodSeconds) || periodSeconds <= 0d) {
                throw new IllegalArgumentException("pulse.period_seconds must be finite and positive");
            }
            if (!(radiusStrength >= 0f && radiusStrength < 1f && intensityStrength >= 0f && intensityStrength < 1f)) {
                throw new IllegalArgumentException("pulse strengths must be in [0, 1)");
            }
        }

        private static Pulse parse(JsonNode node) {
            if (!node.isObject() || node.size() != 3) {
                throw new IllegalArgumentException("Expected pulse period_seconds, radius_strength and intensity_strength");
            }
            return new Pulse(positiveNumber(node, "period_seconds").doubleValue(),
                strength(node, "radius_strength"), strength(node, "intensity_strength"));
        }

        private static float strength(JsonNode node, String key) {
            var value = number(node, key);
            if (value.signum() < 0 || value.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException(key + " must be in [0, 1)");
            }
            float strength = value.floatValue();
            if (value.signum() > 0 && strength == 0f) {
                throw new IllegalArgumentException(key + " is too small to represent");
            }
            return strength;
        }
    }

    public float worldRadius(float unitScale) {
        float radius = radiusPixels * unitScale;
        checkExtrema(radius, pulse.map(Pulse::radiusStrength).orElse(0f), "world radius");
        return radius;
    }

    private static void checkExtrema(float base, float strength, String key) {
        float trough = (float) (base * (1d - strength));
        float peak = (float) (base * (1d + strength));
        if (trough <= 0f || !Float.isFinite(peak)) {
            throw new IllegalArgumentException(key + " and its pulsed extrema must remain finite and positive");
        }
    }

    private static BigDecimal positiveNumber(JsonNode node, String key) {
        var value = number(node, key);
        if (value.signum() <= 0) { throw new IllegalArgumentException(key + " must be positive"); }
        return value;
    }

    private static BigDecimal number(JsonNode node, String key) {
        var value = node.required(key);
        if (!value.isNumber()) { throw new IllegalArgumentException(key + " must be a JSON number"); }
        return value.decimalValue();
    }
}
