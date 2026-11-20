package io.mindspice.lute.engine.lwjgl3;

import org.lwjgl.opengl.GL;

import static org.lwjgl.opengl.ARBInstancedArrays.GL_VERTEX_ATTRIB_ARRAY_DIVISOR_ARB;
import static org.lwjgl.opengl.ARBInstancedArrays.glVertexAttribDivisorARB;
import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL30C.*;

/** Native desktop caller; its VAO is real even when libGDX is using its GL20 wrapper. */
final class CallerVertexInput implements AutoCloseable {
    private final boolean hasVao = GL.getCapabilities().OpenGL30 || GL.getCapabilities().GL_ARB_vertex_array_object;
    private final boolean hasDivisor = GL.getCapabilities().GL_ARB_instanced_arrays;
    private final int previousVao = hasVao ? glGetInteger(GL_VERTEX_ARRAY_BINDING) : 0;
    private final int previousArray = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int vao = hasVao ? glGenVertexArrays() : 0;
    private final int[] sources = {glGenBuffers(), glGenBuffers(), glGenBuffers()};
    private final int array = glGenBuffers(), index = glGenBuffers();

    private record Attribute(int size, int type, int normalized, int stride, long offset, int source, int enabled) {
        static Attribute read(int i) {
            return new Attribute(glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_SIZE),
                glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_TYPE), glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_NORMALIZED),
                glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_STRIDE), glGetVertexAttribPointer(i, GL_VERTEX_ATTRIB_ARRAY_POINTER),
                glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), glGetVertexAttribi(i, GL_VERTEX_ATTRIB_ARRAY_ENABLED));
        }
    }

    // Neither the array nor the index binding is any of the buffers captured by glVertexAttribPointer.
    private final Attribute[] attributes = {
        new Attribute(3, GL_SHORT, 1, 24, 6, sources[0], 1),
        new Attribute(4, GL_UNSIGNED_BYTE, 1, 16, 8, sources[1], 0),
        new Attribute(2, GL_FLOAT, 0, 32, 12, sources[2], 1)
    };

    CallerVertexInput() {
        if (hasVao) { glBindVertexArray(vao); }
        else { glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT); }
        for (int i = 0; i < attributes.length; i++) {
            var a = attributes[i];
            glBindBuffer(GL_ARRAY_BUFFER, a.source);
            glBufferData(GL_ARRAY_BUFFER, 1024, GL_STATIC_DRAW);
            glVertexAttribPointer(i, a.size, a.type, a.normalized != 0, a.stride, a.offset);
            if (a.enabled != 0) { glEnableVertexAttribArray(i); } else { glDisableVertexAttribArray(i); }
        }
        if (hasDivisor) { glVertexAttribDivisorARB(0, 3); }
        glBindBuffer(GL_ARRAY_BUFFER, array);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, index);
        assertIntact("native caller setup");
    }

    void assertIntact(String where) {
        // Query without rebinding anything; binding the expected VAO here would hide a lost binding.
        if (hasVao) { check(where, "VAO", vao, glGetInteger(GL_VERTEX_ARRAY_BINDING)); }
        check(where, "array buffer", array, glGetInteger(GL_ARRAY_BUFFER_BINDING));
        check(where, "element buffer", index, glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING));
        for (int i = 0; i < attributes.length; i++) { check(where, "attribute " + i, attributes[i], Attribute.read(i)); }
        if (hasDivisor) { check(where, "attribute 0 divisor", 3, glGetVertexAttribi(0, GL_VERTEX_ATTRIB_ARRAY_DIVISOR_ARB)); }
        check(where, "GL error", GL_NO_ERROR, glGetError());
    }

    private static void check(String where, String key, Object expected, Object actual) {
        if (!expected.equals(actual)) { throw new AssertionError(where + ": " + key + " expected " + expected + ", got " + actual); }
    }

    @Override public void close() {
        if (hasVao) {
            glBindVertexArray(previousVao);
            glDeleteVertexArrays(vao);
        } else { glPopClientAttrib(); }
        glBindBuffer(GL_ARRAY_BUFFER, previousArray);
        glDeleteBuffers(sources);
        glDeleteBuffers(array);
        glDeleteBuffers(index);
    }
}
