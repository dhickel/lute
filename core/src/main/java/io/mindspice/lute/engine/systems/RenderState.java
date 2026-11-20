package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ServiceLoader;

/** State touched by the offscreen and world sprite passes. */
final class RenderState {
    // Render-thread LIFO scopes: a compositor resize/disposal can also retire a handle
    // saved by the enclosing world pass. Forget it there before GL can recycle the name.
    private static RenderState current;
    private final RenderState enclosing = current;
    private final IntBuffer integers = BufferUtils.newIntBuffer(16);
    private final FloatBuffer floats = BufferUtils.newFloatBuffer(4);
    private final ByteBuffer booleans = BufferUtils.newByteBuffer(4);
    private int framebuffer = integer(GL20.GL_FRAMEBUFFER_BINDING);
    // The desktop GL20 backend also exposes split target bindings on GL3+ drivers.
    private final boolean splitTargets = Gdx.gl30 != null || (Gdx.graphics.getGLVersion().getType() == GLVersion.Type.OpenGL
        && (Gdx.graphics.getGLVersion().isVersionEqualToOrHigher(3, 0)
            || Gdx.graphics.supportsExtension("GL_ARB_framebuffer_object")
            || Gdx.graphics.supportsExtension("GL_EXT_framebuffer_blit")));
    private int readFramebuffer = splitTargets ? integer(GL30.GL_READ_FRAMEBUFFER_BINDING) : framebuffer;
    private final int renderbuffer = integer(GL20.GL_RENDERBUFFER_BINDING);
    private final int[] viewport = currentViewport();
    private final int[] scissorBox = vector(GL20.GL_SCISSOR_BOX);
    private final int unpackAlignment = integer(GL20.GL_UNPACK_ALIGNMENT);
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
    private int arrayBuffer = integer(GL20.GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private int vertexArray = Gdx.gl30 == null ? 0 : integer(GL30.GL_VERTEX_ARRAY_BINDING);
    private final int activeTexture = integer(GL20.GL_ACTIVE_TEXTURE);
    private int texture;
    private final CompatibilityVertexArrays desktop;
    private final Runnable restoreAdditionalDrawBuffers;
    private IntBuffer callerDrawRoutes;
    private int savedCompatArray;
    private boolean compatArrayBound;
    private int sampler;
    private boolean unpackPrepared, unpackSubimage;
    private int unpackBuffer, rowLength, skipRows, skipPixels;

    RenderState(CompatibilityVertexArrays desktop) {
        this.desktop = desktop;
        restoreAdditionalDrawBuffers = desktop == null ? null : desktop.captureAdditionalDrawBuffers();
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        texture = integer(GL20.GL_TEXTURE_BINDING_2D);
        current = this;
    }

    int integer(int key) {
        integers.clear();
        Gdx.gl.glGetIntegerv(key, integers);
        return integers.get(0);
    }

    int[] currentViewport() { return vector(GL20.GL_VIEWPORT); }

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

    static CompatibilityVertexArrays desktopBindings() {
        if (Gdx.graphics.getGLVersion().getType() != GLVersion.Type.OpenGL) { return null; }
        return ServiceLoader.load(CompatibilityVertexArrays.class).findFirst().orElseThrow(
            () -> new IllegalStateException("Desktop renderer needs its GL state adapter"));
    }

    void prepare() {
        if (desktop != null && Gdx.gl30 == null) {
            savedCompatArray = desktop.bind();
            compatArrayBound = true;
        }
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        // A caller sampler overrides even the lightmap texture's explicit, non-mipmapped filter.
        if (desktop != null ? desktop.supportsSamplers() : Gdx.gl30 != null) {
            sampler = integer(GL30.GL_SAMPLER_BINDING);
            if (sampler != 0) { bindUnitZeroSampler(0); }
        }
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glColorMask(true, true, true, true);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
    }

    /** glClear writes every routed output. Keep only the caller's slot-zero destination for gameplay. */
    void isolateOutputZero() {
        if (desktop == null && Gdx.gl30 == null) { return; } // Single output in baseline GLES2.
        for (int last = integer(GL30.GL_MAX_DRAW_BUFFERS) - 1; last > 0; last--) {
            if (integer(GL30.GL_DRAW_BUFFER0 + last) == GL20.GL_NONE) { continue; }
            var routes = BufferUtils.newIntBuffer(last + 1);
            for (int i = 0; i <= last; i++) { routes.put(integer(GL30.GL_DRAW_BUFFER0 + i)); }
            callerDrawRoutes = routes.flip();
            integers.clear();
            integers.put(callerDrawRoutes.get(0)).flip();
            setDrawBuffers(integers);
            return;
        }
    }

    private void setDrawBuffers(IntBuffer routes) {
        if (Gdx.gl30 != null) { Gdx.gl30.glDrawBuffers(routes.remaining(), routes); }
        else { desktop.setDrawBuffers(routes); }
    }

    /** TexImage2D uses client memory (including null for storage), never an offset into caller buffers. */
    void prepareTextureUpload() {
        if (unpackPrepared) { return; }
        var version = Gdx.graphics.getGLVersion();
        boolean desktop = version.getType() == GLVersion.Type.OpenGL;
        if (Gdx.gl30 != null || (desktop && (version.isVersionEqualToOrHigher(2, 1)
            || Gdx.graphics.supportsExtension("GL_ARB_pixel_buffer_object") || Gdx.graphics.supportsExtension("GL_EXT_pixel_buffer_object")))) {
            unpackBuffer = integer(GL30.GL_PIXEL_UNPACK_BUFFER_BINDING);
            if (unpackBuffer != 0) { Gdx.gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, 0); }
        }
        // Pixmap uploads (the crosshair) also need tightly packed rows starting at pixel zero.
        unpackSubimage = desktop || Gdx.gl30 != null || Gdx.graphics.supportsExtension("GL_EXT_unpack_subimage");
        if (unpackSubimage) {
            rowLength = integer(GL30.GL_UNPACK_ROW_LENGTH);
            skipRows = integer(GL30.GL_UNPACK_SKIP_ROWS);
            skipPixels = integer(GL30.GL_UNPACK_SKIP_PIXELS);
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, 0);
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, 0);
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, 0);
        }
        unpackPrepared = true;
    }

    void releasedTarget(int framebuffer, int texture) {
        if (this.framebuffer == framebuffer) { this.framebuffer = 0; callerDrawRoutes = null; }
        if (readFramebuffer == framebuffer) { readFramebuffer = 0; }
        if (this.texture == texture) { this.texture = 0; }
        if (enclosing != null) { enclosing.releasedTarget(framebuffer, texture); }
    }

    void releasedTexture(int texture) {
        if (this.texture == texture) { this.texture = 0; }
        if (enclosing != null) { enclosing.releasedTexture(texture); }
    }

    void releasedShader(int program) {
        if (this.program == program) { this.program = 0; }
        if (enclosing != null) { enclosing.releasedShader(program); }
    }

    void releasedArrayBuffer(int handle) { if (arrayBuffer == handle) { arrayBuffer = 0; } }
    void releasedVertexArray(int handle) { if (vertexArray == handle) { vertexArray = 0; } }

    void restoreTargets() {
        if (splitTargets) {
            Gdx.gl.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            Gdx.gl.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        } else { Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer); }
    }

    void restore() {
        current = enclosing;
        restoreTargets();
        // Routing belongs to the draw FBO. Rebind it first and only restore on final exit, not before modulation.
        if (callerDrawRoutes != null) { setDrawBuffers(callerDrawRoutes); }
        Gdx.gl.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        if (unpackBuffer != 0) { Gdx.gl.glBindBuffer(GL30.GL_PIXEL_UNPACK_BUFFER, unpackBuffer); }
        if (unpackSubimage) {
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_ROW_LENGTH, rowLength);
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_ROWS, skipRows);
            Gdx.gl.glPixelStorei(GL30.GL_UNPACK_SKIP_PIXELS, skipPixels);
        }
        Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, unpackAlignment);
        Gdx.gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, renderbuffer);
        Gdx.gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        Gdx.gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        Gdx.gl.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        Gdx.gl.glDepthMask(depthMask);
        Gdx.gl.glBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        Gdx.gl.glBlendEquationSeparate(equation, equationAlpha);
        enabled(GL20.GL_BLEND, blend);
        // The non-indexed setters above apply slot zero's values to *all* draw buffers.
        if (restoreAdditionalDrawBuffers != null) { restoreAdditionalDrawBuffers.run(); }
        enabled(GL20.GL_SCISSOR_TEST, scissor);
        enabled(GL20.GL_DEPTH_TEST, depth);
        enabled(GL20.GL_STENCIL_TEST, stencil);
        enabled(GL20.GL_CULL_FACE, cull);
        Gdx.gl.glUseProgram(program);
        if (compatArrayBound) { desktop.restore(savedCompatArray); }
        if (Gdx.gl30 != null) { Gdx.gl30.glBindVertexArray(vertexArray); }
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
        if (sampler != 0) { bindUnitZeroSampler(sampler); }
        Gdx.gl.glActiveTexture(activeTexture);
    }

    private void bindUnitZeroSampler(int name) {
        if (desktop != null) { desktop.bindUnitZeroSampler(name); }
        else { Gdx.gl30.glBindSampler(0, name); }
    }

    private static void enabled(int capability, boolean enabled) {
        if (enabled) { Gdx.gl.glEnable(capability); }
        else { Gdx.gl.glDisable(capability); }
    }
}
