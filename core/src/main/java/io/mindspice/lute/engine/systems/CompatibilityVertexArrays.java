package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.utils.Disposable;

/** Isolates desktop compatibility vertex input, which libGDX's GL20 API cannot save or switch. */
public interface CompatibilityVertexArrays extends Disposable {
    int bind();
    void restore(int previousArray);
}
