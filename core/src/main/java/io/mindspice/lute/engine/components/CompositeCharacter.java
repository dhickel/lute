package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;

/** Selects offscreen composition for any character, independently of player control. */
public final class CompositeCharacter implements Component {
    public static final ComponentMapper<CompositeCharacter> MAPPER = ComponentMapper.getFor(CompositeCharacter.class);
    private final int canvasWidth;
    private final int canvasHeight;

    public CompositeCharacter(int canvasWidth, int canvasHeight) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    public int canvasWidth() { return canvasWidth; }
    public int canvasHeight() { return canvasHeight; }
}
