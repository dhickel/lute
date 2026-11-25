package io.mindspice.lute.engine.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Ellipse;
import com.badlogic.gdx.math.Vector2;

public record ArenaGuard(Ellipse territory, Vector2 home) implements Component { }
