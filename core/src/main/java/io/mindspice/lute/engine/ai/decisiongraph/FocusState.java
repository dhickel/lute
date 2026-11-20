package io.mindspice.lute.engine.ai.decisiongraph;

import io.mindspice.lute.engine.components.Facing;

/** Scratch data for one traversal; no target entity is retained. */
public final class FocusState {
    private Facing.Direction seenDirection;

    public Facing.Direction getSeenDirection() { return seenDirection; }

    public void setSeenDirection(Facing.Direction direction) { seenDirection = direction; }
}
