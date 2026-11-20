package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.utils.Disposable;

import java.nio.IntBuffer;

/** Desktop state not fully exposed by the libGDX wrappers, including compatibility vertex input. */
public interface CompatibilityVertexArrays extends Disposable {
    int bind();
    void restore(int previousArray);

    boolean supportsSamplers();
    void bindUnitZeroSampler(int sampler);

    /** glDrawBuffers for desktop contexts exposed through the libGDX GL20 wrapper. */
    void setDrawBuffers(IntBuffer routes);

    /** Restore slots beyond zero after libGDX's non-indexed color and blend setters. */
    Runnable captureAdditionalDrawBuffers();
}
