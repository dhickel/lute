package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

/** Ignores facing for sight checks; range and solid cover still apply. */
public final class FullCircleVision implements Component {
    public static final ComponentMapper<FullCircleVision> MAPPER = ComponentMapper.getFor(FullCircleVision.class);
}
