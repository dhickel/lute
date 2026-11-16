package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

/** Selects offscreen composition for any character, independently of player control. */
public final class CompositeCharacter implements Component {
    public static final ComponentMapper<CompositeCharacter> MAPPER = ComponentMapper.getFor(CompositeCharacter.class);
}
