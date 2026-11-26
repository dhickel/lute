package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;

/** Marks actors whose opponents are selected by the isolated Hoard hammer demo. */
public record HoardDemoCombatant(Side side) implements Component {
    public enum Side { DREDGE, SWARM }
}
