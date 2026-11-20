package io.mindspice.lute.engine.systems;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.FloatArray;
import io.mindspice.lute.engine.components.Emissive;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/** A clip-space quad and a bounded streaming region for world-space shadow triangles. */
final class NightLightMesh {
    private static final int MASK = 0, LIGHT = 1, COMPOSITE = 2;
    private static final float[] QUAD = {-1, -1, 1, -1, -1, 1, 1, 1};
    private static final String VERTEX = """
        attribute vec2 a_xy;
        uniform int u_pass;
        uniform mat4 u_camera;
        varying vec2 v_xy;
        void main() {
            vec4 p = vec4(a_xy, 0.0, 1.0);
            gl_Position = u_pass == 0 ? u_camera * p : p;
            v_xy = u_pass == 1 ? (u_camera * p).xy : (a_xy + 1.0) * 0.5;
        }
        """;
    private static final String FRAGMENT = """
        #ifdef GL_ES
        precision highp float;
        #endif
        uniform int u_pass;
        uniform vec3 u_disk;
        uniform vec3 u_color;
        uniform sampler2D u_lightmap;
        varying vec2 v_xy;
        void main() {
            if (u_pass == 2) {
                gl_FragColor = texture2D(u_lightmap, v_xy);
            } else if (u_pass == 1) {
                float distanceRatio = length((v_xy - u_disk.xy) / u_disk.z);
                gl_FragColor = vec4(u_color * (1.0 - smoothstep(0.0, 1.0, distanceRatio)), 1.0);
            } else {
                gl_FragColor = vec4(0.0);
            }
        }
        """;

    private final FloatBuffer stream = BufferUtils.newFloatBuffer(6 * 512);
    private final IntBuffer status = BufferUtils.newIntBuffer(16);
    private final int[] vao = {0};
    private int program, vbo;
    private final int pass, camera, disk, color;
    private boolean positionWasEnabled;

    NightLightMesh(RenderState state) {
        var gl = Gdx.gl;
        int vertex = 0, fragment = 0;
        try {
            vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
            program = gl.glCreateProgram();
            if (program == 0) { throw new IllegalStateException("Cannot create night light program"); }
            gl.glAttachShader(program, vertex);
            gl.glAttachShader(program, fragment);
            gl.glBindAttribLocation(program, 0, "a_xy");
            gl.glLinkProgram(program);
            gl.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
            if (status.get(0) == 0) { throw new IllegalStateException("Night light link: " + gl.glGetProgramInfoLog(program)); }
            pass = gl.glGetUniformLocation(program, "u_pass");
            camera = gl.glGetUniformLocation(program, "u_camera");
            disk = gl.glGetUniformLocation(program, "u_disk");
            color = gl.glGetUniformLocation(program, "u_color");
            gl.glUseProgram(program);
            gl.glUniform1i(gl.glGetUniformLocation(program, "u_lightmap"), 0);

            if (Gdx.gl30 != null) {
                Gdx.gl30.glGenVertexArrays(1, vao, 0);
                if (vao[0] == 0) { throw new IllegalStateException("Cannot create night light vertex array"); }
            }
            vbo = gl.glGenBuffer();
            if (vbo == 0) { throw new IllegalStateException("Cannot create night light vertex buffer"); }
            gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);
            int bytes = (QUAD.length + stream.capacity()) * Float.BYTES;
            gl.glBufferData(GL20.GL_ARRAY_BUFFER, bytes, null, GL20.GL_STREAM_DRAW);
            gl.glGetBufferParameteriv(GL20.GL_ARRAY_BUFFER, GL20.GL_BUFFER_SIZE, status);
            if (status.get(0) != bytes) { throw new IllegalStateException("Cannot allocate night light vertex storage"); }
            stream.put(QUAD).flip();
            gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, 0, QUAD.length * Float.BYTES, stream);
        } catch (RuntimeException | Error failure) {
            dispose(state);
            throw failure;
        } finally {
            if (vertex != 0) { gl.glDeleteShader(vertex); }
            if (fragment != 0) { gl.glDeleteShader(fragment); }
        }
    }

    private int compile(int kind, String source) {
        var gl = Gdx.gl;
        int shader = gl.glCreateShader(kind);
        if (shader == 0) { throw new IllegalStateException("Cannot create night light shader"); }
        try {
            gl.glShaderSource(shader, source);
            gl.glCompileShader(shader);
            gl.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
            if (status.get(0) == 0) { throw new IllegalStateException("Night light shader: " + gl.glGetShaderInfoLog(shader)); }
            return shader;
        } catch (RuntimeException | Error failure) {
            gl.glDeleteShader(shader);
            throw failure;
        }
    }

    void bind() {
        var gl = Gdx.gl;
        gl.glUseProgram(program);
        if (Gdx.gl30 != null) { Gdx.gl30.glBindVertexArray(vao[0]); }
        gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, vbo);
        // On desktop GL20 the enclosing render scope isolates the layout as well as the enable bit.
        gl.glGetVertexAttribiv(0, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, status);
        positionWasEnabled = status.get(0) != 0;
        gl.glVertexAttribPointer(0, 2, GL20.GL_FLOAT, false, 2 * Float.BYTES, 0);
        gl.glEnableVertexAttribArray(0);
    }

    void end() {
        if (!positionWasEnabled) { Gdx.gl.glDisableVertexAttribArray(0); }
        if (Gdx.gl30 != null) { Gdx.gl30.glBindVertexArray(0); }
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
    }

    void mask(Matrix4 projection, FloatArray triangles) {
        var gl = Gdx.gl;
        gl.glUniform1i(pass, MASK);
        gl.glUniformMatrix4fv(camera, 1, false, projection.val, 0);
        for (int offset = 0; offset < triangles.size; offset += stream.capacity()) {
            int count = Math.min(stream.capacity(), triangles.size - offset);
            stream.clear().put(triangles.items, offset, count).flip();
            gl.glBufferSubData(GL20.GL_ARRAY_BUFFER, QUAD.length * Float.BYTES, count * Float.BYTES, stream);
            gl.glDrawArrays(GL20.GL_TRIANGLES, 4, count / 2);
        }
    }

    void light(Matrix4 inverseProjection, float x, float y, float radius, Emissive light, double wave) {
        var gl = Gdx.gl;
        gl.glUniform1i(pass, LIGHT);
        gl.glUniformMatrix4fv(camera, 1, false, inverseProjection.val, 0);
        gl.glUniform3f(disk, x, y, radius);
        float intensity = light.intensity(wave);
        gl.glUniform3f(color, light.red() * intensity, light.green() * intensity, light.blue() * intensity);
        gl.glDrawArrays(GL20.GL_TRIANGLE_STRIP, 0, 4);
    }

    void composite() {
        Gdx.gl.glUniform1i(pass, COMPOSITE);
        Gdx.gl.glDrawArrays(GL20.GL_TRIANGLE_STRIP, 0, 4);
    }

    void dispose(RenderState state) {
        state.releasedShader(program);
        state.releasedArrayBuffer(vbo);
        state.releasedVertexArray(vao[0]);
        if (program != 0) { Gdx.gl.glDeleteProgram(program); }
        if (vbo != 0) { Gdx.gl.glDeleteBuffer(vbo); }
        if (vao[0] != 0) { Gdx.gl30.glDeleteVertexArrays(1, vao, 0); }
    }
}
