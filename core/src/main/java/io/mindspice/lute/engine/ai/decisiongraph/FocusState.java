package io.mindspice.lute.engine.ai.decisiongraph;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;

/** Scratch data for one traversal. Only a live sighting can supply an attack target. */
public final class FocusState {
    private final float deltaTime;
    private Vector2 targetOffset;
    private Entity visibleTarget;

    public FocusState(float deltaTime) { this.deltaTime = deltaTime; }

    public float deltaTime() { return deltaTime; }
    public Vector2 targetOffset() { return targetOffset; }
    public void setTargetOffset(Vector2 targetOffset) { this.targetOffset = targetOffset; }

    public Entity visibleTarget() { return visibleTarget; }
    public void setVisibleTarget(Entity target) { visibleTarget = target; }
}
