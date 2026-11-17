package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** State touched by the offscreen and world sprite passes. */
final class RenderState {
    private final IntBuffer integers = BufferUtils.newIntBuffer(16);
    private final FloatBuffer floats = BufferUtils.newFloatBuffer(4);
    private final ByteBuffer booleans = BufferUtils.newByteBuffer(4);
    private int framebuffer = integer(GL20.GL_FRAMEBUFFER_BINDING);
    private int readFramebuffer = Gdx.gl30 == null ? framebuffer : integer(GL30.GL_READ_FRAMEBUFFER_BINDING);
    private final int renderbuffer = integer(GL20.GL_RENDERBUFFER_BINDING);
    private final int[] viewport = vector(GL20.GL_VIEWPORT);
    private final float[] clearColor = clearColor();
    private final boolean blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
    private final boolean scissor = Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST);
    private final boolean depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
    private final boolean stencil = Gdx.gl.glIsEnabled(GL20.GL_STENCIL_TEST);
    private final boolean cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
    private final boolean depthMask = bool(GL20.GL_DEPTH_WRITEMASK);
    private final boolean[] colorMask = colorMask();
    private final int src = integer(GL20.GL_BLEND_SRC_RGB), dst = integer(GL20.GL_BLEND_DST_RGB);
    private final int srcAlpha = integer(GL20.GL_BLEND_SRC_ALPHA), dstAlpha = integer(GL20.GL_BLEND_DST_ALPHA);
    private final int equation = integer(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = integer(GL20.GL_BLEND_EQUATION_ALPHA);
    private int program = integer(GL20.GL_CURRENT_PROGRAM);
    private final int arrayBuffer = integer(GL20.GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final int vertexArray = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int activeTexture = integer(GL20.GL_ACTIVE_TEXTURE);
    private int texture;

    RenderState() {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        texture = integer(GL20.GL_TEXTURE_BINDING_2D);
    }

    int integer(int key) {
        integers.clear();
        Gdx.gl.glGetIntegerv(key, integers);
        return integers.get(0);
    }

    private int[] vector(int key) {
        integers.clear();
        Gdx.gl.glGetIntegerv(key, integers);
        return new int[]{integers.get(0), integers.get(1), integers.get(2), integers.get(3)};
    }

    private float[] clearColor() {
        floats.clear();
        Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, floats);
        return new float[]{floats.get(0), floats.get(1), floats.get(2), floats.get(3)};
    }

    private boolean bool(int key) {
        booleans.clear();
        Gdx.gl.glGetBooleanv(key, booleans);
        return booleans.get(0) != 0;
    }

    private boolean[] colorMask() {
        booleans.clear();
        Gdx.gl.glGetBooleanv(GL20.GL_COLOR_WRITEMASK, booleans);
        return new boolean[]{booleans.get(0) != 0, booleans.get(1) != 0, booleans.get(2) != 0, booleans.get(3) != 0};
    }

    static void prepare() {
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
    }

    void releasedTarget(int framebuffer, int texture) {
        if (this.framebuffer == framebuffer) { this.framebuffer = 0; }
        if (readFramebuffer == framebuffer) { readFramebuffer = 0; }
        if (this.texture == texture) { this.texture = 0; }
    }

    void releasedShader(int program) {
        if (this.program == program) { this.program = 0; }
    }

    void restore() {
        if (Gdx.gl30 == null) { Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer); }
        else {
            Gdx.gl30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            Gdx.gl30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        }
        Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbuffer);
        Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        Gdx.gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        Gdx.gl.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        Gdx.gl.glDepthMask(depthMask);
        Gdx.gl.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        Gdx.gl.glBlendEquationSeparate(equation, equationAlpha);
        enabled(GL20.GL_BLEND, blend);
        enabled(GL20.GL_SCISSOR_TEST, scissor);
        enabled(GL20.GL_DEPTH_TEST, depth);
        enabled(GL20.GL_STENCIL_TEST, stencil);
        enabled(GL20.GL_CULL_FACE, cull);
        Gdx.gl.glUseProgram(program);
        if (Gdx.gl30 != null) { Gdx.gl30.glBindVertexArray(vertexArray); }
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
        Gdx.gl.glActiveTexture(activeTexture);
    }

    private static void enabled(int capability, boolean enabled) {
        if (enabled) { Gdx.gl.glEnable(capability); }
        else { Gdx.gl.glDisable(capability); }
    }
}
