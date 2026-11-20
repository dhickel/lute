package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/** A color target with its own eight stencil bits. The window does not need stencil. */
final class NightLightMap {
    final int width, height;
    private int framebuffer, stencil, texture;

    NightLightMap(int width, int height, RenderState state) {
        this.width = width;
        this.height = height;
        var gl = Gdx.gl;
        int limit = Math.min(state.integer(GL20.GL_MAX_TEXTURE_SIZE), state.integer(GL20.GL_MAX_RENDERBUFFER_SIZE));
        if (width > limit || height > limit) {
            throw new IllegalStateException("Night light map " + width + "x" + height + " exceeds GL target limit " + limit);
        }
        try {
            framebuffer = gl.glGenFramebuffer();
            stencil = gl.glGenRenderbuffer();
            texture = gl.glGenTexture();
            if (framebuffer == 0 || stencil == 0 || texture == 0) {
                throw new IllegalStateException("Cannot allocate night light map attachments");
            }
            gl.glBindTexture(GL20.GL_TEXTURE_2D, texture);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MIN_FILTER, GL20.GL_NEAREST);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_MAG_FILTER, GL20.GL_NEAREST);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_S, GL20.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL20.GL_TEXTURE_2D, GL20.GL_TEXTURE_WRAP_T, GL20.GL_CLAMP_TO_EDGE);
            state.prepareTextureUpload();
            gl.glTexImage2D(GL20.GL_TEXTURE_2D, 0, GL20.GL_RGBA, width, height, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, null);
            gl.glBindRenderbuffer(GL20.GL_RENDERBUFFER, stencil);
            gl.glRenderbufferStorage(GL20.GL_RENDERBUFFER, GL20.GL_STENCIL_INDEX8, width, height);
            gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
            gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, texture, 0);
            gl.glFramebufferRenderbuffer(GL20.GL_FRAMEBUFFER, GL20.GL_STENCIL_ATTACHMENT, GL20.GL_RENDERBUFFER, stencil);
            int status = gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER);
            if (status != GL20.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Incomplete night light map " + width + "x" + height + ": 0x" + Integer.toHexString(status));
            }
        } catch (RuntimeException | Error failure) {
            dispose(state);
            throw failure;
        }
    }

    void begin() {
        Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, framebuffer);
        Gdx.gl.glViewport(0, 0, width, height);
    }

    void bindColor() { Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, texture); }

    void dispose(RenderState state) {
        state.releasedTarget(framebuffer, texture);
        state.releasedRenderbuffer(stencil);
        if (framebuffer != 0) { Gdx.gl.glDeleteFramebuffer(framebuffer); }
        if (stencil != 0) { Gdx.gl.glDeleteRenderbuffer(stencil); }
        if (texture != 0) { Gdx.gl.glDeleteTexture(texture); }
    }
}
