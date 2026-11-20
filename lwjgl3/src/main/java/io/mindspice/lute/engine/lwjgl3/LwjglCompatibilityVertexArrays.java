package io.mindspice.lute.engine.lwjgl3;

import io.mindspice.lute.engine.systems.CompatibilityVertexArrays;
import org.lwjgl.opengl.AMDDrawBuffersBlend;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.ARBSamplerObjects;
import org.lwjgl.opengl.EXTDrawBuffers2;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.ARBInstancedArrays.glVertexAttribDivisorARB;
import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;

/** Use the native desktop capabilities, not the version of the active libGDX wrapper. */
public final class LwjglCompatibilityVertexArrays implements CompatibilityVertexArrays {
    private final boolean coreIndexed = GL.getCapabilities().OpenGL30;
    private final boolean hasArrays = coreIndexed || GL.getCapabilities().GL_ARB_vertex_array_object;
    private final int drawBuffers = coreIndexed || GL.getCapabilities().GL_EXT_draw_buffers2 ? glGetInteger(GL_MAX_DRAW_BUFFERS) : 1;
    private final boolean indexedBlend = GL.getCapabilities().OpenGL40 || GL.getCapabilities().GL_ARB_draw_buffers_blend
        || GL.getCapabilities().GL_AMD_draw_buffers_blend;
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

    @Override public boolean supportsSamplers() {
        return GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;
    }

    @Override public void bindUnitZeroSampler(int sampler) {
        ARBSamplerObjects.glBindSampler(0, sampler); // unsuffixed on both core and ARB paths
    }

    private record BlendMode(int rgb, int alpha, int src, int dst, int srcAlpha, int dstAlpha) {
        void restore(int index) {
            if (GL.getCapabilities().OpenGL40) {
                GL40.glBlendEquationSeparatei(index, rgb, alpha);
                GL40.glBlendFuncSeparatei(index, src, dst, srcAlpha, dstAlpha);
            } else if (GL.getCapabilities().GL_ARB_draw_buffers_blend) {
                ARBDrawBuffersBlend.glBlendEquationSeparateiARB(index, rgb, alpha);
                ARBDrawBuffersBlend.glBlendFuncSeparateiARB(index, src, dst, srcAlpha, dstAlpha);
            } else {
                AMDDrawBuffersBlend.glBlendEquationSeparateIndexedAMD(index, rgb, alpha);
                AMDDrawBuffersBlend.glBlendFuncSeparateIndexedAMD(index, src, dst, srcAlpha, dstAlpha);
            }
        }
    }

    private int indexed(int key, int index) {
        return coreIndexed ? GL30.glGetIntegeri(key, index) : EXTDrawBuffers2.glGetIntegerIndexedEXT(key, index);
    }

    private record Slot(int[] mask, boolean blend, BlendMode mode) {
        void restore(int index, boolean core) {
            if (core) {
                GL30.glColorMaski(index, mask[0] != 0, mask[1] != 0, mask[2] != 0, mask[3] != 0);
                if (blend) { GL30.glEnablei(GL_BLEND, index); } else { GL30.glDisablei(GL_BLEND, index); }
            } else {
                EXTDrawBuffers2.glColorMaskIndexedEXT(index, mask[0] != 0, mask[1] != 0, mask[2] != 0, mask[3] != 0);
                if (blend) { EXTDrawBuffers2.glEnableIndexedEXT(GL_BLEND, index); }
                else { EXTDrawBuffers2.glDisableIndexedEXT(GL_BLEND, index); }
            }
            if (mode != null) { mode.restore(index); }
        }
    }

    @Override public void setDrawBuffers(IntBuffer routes) { glDrawBuffers(routes); }

    @Override public Runnable captureAdditionalDrawBuffers() {
        if (drawBuffers <= 1) { return null; }
        // Blend and color-write state is context-wide, including slots not routed to an attachment right now.
        var slots = new Slot[drawBuffers - 1];
        for (int i = 1; i < drawBuffers; i++) {
            var mask = new int[4];
            boolean blend;
            if (coreIndexed) {
                GL30.glGetIntegeri_v(GL_COLOR_WRITEMASK, i, mask);
                blend = GL30.glIsEnabledi(GL_BLEND, i);
            } else {
                EXTDrawBuffers2.glGetIntegerIndexedvEXT(GL_COLOR_WRITEMASK, i, mask);
                blend = EXTDrawBuffers2.glIsEnabledIndexedEXT(GL_BLEND, i);
            }
            var mode = indexedBlend ? new BlendMode(indexed(GL_BLEND_EQUATION_RGB, i), indexed(GL_BLEND_EQUATION_ALPHA, i),
                indexed(GL_BLEND_SRC_RGB, i), indexed(GL_BLEND_DST_RGB, i), indexed(GL_BLEND_SRC_ALPHA, i), indexed(GL_BLEND_DST_ALPHA, i)) : null;
            slots[i - 1] = new Slot(mask, blend, mode);
        }
        return () -> {
            for (int i = 1; i < drawBuffers; i++) { slots[i - 1].restore(i, coreIndexed); }
        };
    }

    @Override public void dispose() {
        if (array != 0) { glDeleteVertexArrays(array); array = 0; }
    }
}
