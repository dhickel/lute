package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import io.mindspice.lute.engine.data.json.EmissiveData;

/** Immutable light in world units. No per-light clock to reset on map travel. */
public final class Emissive implements Component {
    public static final ComponentMapper<Emissive> MAPPER = ComponentMapper.getFor(Emissive.class);
    private final float red, green, blue, radius, intensity;
    private final EmissiveData.Pulse pulse;

    public Emissive(EmissiveData data, float unitScale) {
        radius = data.worldRadius(unitScale);
        intensity = data.intensity();
        pulse = data.pulse().orElse(null);
        int rgb = Integer.parseInt(data.color(), 1, 7, 16);
        red = (rgb >> 16) / 255f;
        green = (rgb >> 8 & 255) / 255f;
        blue = (rgb & 255) / 255f;
    }

    public float red() { return red; }
    public float green() { return green; }
    public float blue() { return blue; }

    public double wave(double seconds) {
        if (pulse == null) { return 0d; }
        double period = pulse.periodSeconds();
        return Math.sin(((seconds % period) / period) * (2d * Math.PI));
    }

    public float radius(double wave) {
        return pulse == null ? radius : (float) (radius * (1d + pulse.radiusStrength() * wave));
    }

    public float intensity(double wave) {
        return pulse == null ? intensity : (float) (intensity * (1d + pulse.intensityStrength() * wave));
    }
}
