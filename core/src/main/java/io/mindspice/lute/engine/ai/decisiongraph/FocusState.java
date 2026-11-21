package io.mindspice.lute.engine.ai.decisiongraph;

import com.badlogic.gdx.math.Vector2;

/** Scratch data for one traversal; no target entity is retained. */
public final class FocusState {
    private final float deltaTime;
    private Vector2 targetOffset;

    public FocusState(float deltaTime) { this.deltaTime = deltaTime; }

    public float deltaTime() { return deltaTime; }
    public Vector2 targetOffset() { return targetOffset; }
    public void setTargetOffset(Vector2 targetOffset) { this.targetOffset = targetOffset; }
}
