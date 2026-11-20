package io.mindspice.lute.engine.lwjgl3;

import io.mindspice.lute.engine.systems.CompatibilityVertexArrays;
import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.ARBInstancedArrays.glVertexAttribDivisorARB;
import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.GL_MAX_VERTEX_ATTRIBS;
import static org.lwjgl.opengl.GL20.glDisableVertexAttribArray;

/** Called only on the desktop GL20 path. Keep SpriteBatch and night draws off the caller's layout. */
public final class LwjglCompatibilityVertexArrays implements CompatibilityVertexArrays {
    private final boolean hasArrays = GL.getCapabilities().OpenGL30 || GL.getCapabilities().GL_ARB_vertex_array_object;
    private int array;

    @Override public int bind() {
        if (hasArrays) {
            int previous = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            if (array == 0) {
                array = glGenVertexArrays();
                if (array == 0) { throw new IllegalStateException("Cannot allocate renderer vertex array"); }
            }
            glBindVertexArray(array);
            return previous;
        }
        // Pre-VAO GL2: the compatibility client stack includes generic attribute pointers,
        // per-attribute VBO bindings and enables (including actual client-memory pointers).
        if (glGetInteger(GL_CLIENT_ATTRIB_STACK_DEPTH) == glGetInteger(GL_MAX_CLIENT_ATTRIB_STACK_DEPTH)) {
            throw new IllegalStateException("Cannot save renderer vertex input: client attribute stack full");
        }
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        int count = glGetInteger(GL_MAX_VERTEX_ATTRIBS);
        for (int i = 0; i < count; i++) {
            glDisableVertexAttribArray(i);
            if (GL.getCapabilities().GL_ARB_instanced_arrays) { glVertexAttribDivisorARB(i, 0); }
        }
        return 0;
    }

    @Override public void restore(int previousArray) {
        if (hasArrays) { glBindVertexArray(previousArray); }
        else { glPopClientAttrib(); }
    }

    @Override public void dispose() {
        if (array != 0) { glDeleteVertexArrays(array); array = 0; }
    }
}
