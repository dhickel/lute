package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

/** Keeps the demo swarm focused on the player from every spawn zone. */
public record HoardDemoCombatant(Entity player) implements Component { }
