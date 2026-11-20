package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

/** The awareness point is the center of the solid feet fixture, in body-local world units. */
public record Actor(Role role, float feetX, float feetY) implements Component {
    public static final ComponentMapper<Actor> MAPPER = ComponentMapper.getFor(Actor.class);

    public enum Role { PLAYER, NPC, ENEMY }
}
