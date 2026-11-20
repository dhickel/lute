package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.singletons.GameConsts.NightAmbient;

/** Renderer-owned scratch. Each disk adds to the same RGB map with its own visibility mask. */
final class NightLighting {
    private final NightOcclusion occlusion = new NightOcclusion();
    private final NightLightMesh mesh;
    private NightLightMap map;
    private NightAmbient ambient;
    private float red, green, blue;

    NightLighting(int width, int height, RenderState state) {
        var version = Gdx.graphics.getGLVersion();
        if (version.getType() == GLVersion.Type.GLES && !version.isVersionEqualToOrHigher(3, 0)
            && !Gdx.graphics.supportsExtension("GL_EXT_blend_minmax")) {
            throw new IllegalStateException("Night shadows need GL_EXT_blend_minmax on GLES2");
        }
        map = new NightLightMap(width, height, state);
        try { mesh = new NightLightMesh(state); }
        catch (RuntimeException | Error failure) {
            map.dispose(state);
            throw failure;
        }
    }

    void render(World world, Iterable<Entity> emitters, NightAmbient settings, double seconds,
                OrthographicCamera camera, int[] viewport, RenderState state, boolean rawMap) {
        var gl = Gdx.gl;
        if (map.width != viewport[2] || map.height != viewport[3]) {
            var replacement = new NightLightMap(viewport[2], viewport[3], state);
            map.dispose(state);
            map = replacement;
        }
        if (ambient != settings) {
            int rgb = Integer.parseInt(settings.tint(), 1, 7, 16);
            float brightness = settings.brightness();
            red = (rgb >> 16) / 255f * brightness;
            green = (rgb >> 8 & 255) / 255f * brightness;
            blue = (rgb & 255) / 255f * brightness;
            ambient = settings;
        }

        map.begin();
        gl.glClearColor(red, green, blue, 1f);
        gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        mesh.bind();
        try {
            float pixelSize = camera.zoom * Math.min(camera.viewportWidth / map.width, camera.viewportHeight / map.height);
            double viewHalfDiagonal = .5d * camera.zoom * Math.hypot(camera.viewportWidth, camera.viewportHeight);
            for (Entity entity : emitters) {
                var light = Emissive.MAPPER.get(entity);
                var transform = Transform.MAPPER.get(entity);
                float x = transform.centerX(), y = transform.centerY();
                double wave = light.wave(seconds);
                float radius = light.radius(wave);
                if (!camera.frustum.sphereInFrustum(x, y, 0f, radius)) { continue; }
                // Past the farthest visible point geometry can neither cast onto nor receive a visible shadow.
                // This also keeps a very large authored radius from exhausting float raster precision in a tiny view.
                double toCamera = Math.hypot((double) x - camera.position.x, (double) y - camera.position.y);
                float maskRadius = Math.min(radius, Math.nextUp((float) (toCamera + viewHalfDiagonal)));
                double maxTail = Math.min(24d * GameConsts.UNIT_SCALE, .25d * radius);
                var physic = Physic.MAPPER.get(entity);
                if (occlusion.collect(world, physic == null ? null : physic.getBody(), x, y, maskRadius, pixelSize, maxTail)) {
                    addLight(camera, light, x, y, radius, wave);
                }
            }

            state.restoreTargets(); // The caller can be another FBO, not necessarily the window.
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            gl.glColorMask(true, true, true, rawMap);
            if (rawMap) {
                gl.glDisable(GL20.GL_BLEND);
            } else {
                gl.glEnable(GL20.GL_BLEND);
                gl.glBlendFuncSeparate(GL20.GL_DST_COLOR, GL20.GL_ZERO, GL20.GL_ZERO, GL20.GL_ONE);
            }
            map.bindColor();
            mesh.composite();
            gl.glColorMask(true, true, true, true);
        } finally { mesh.end(); }
    }

    private void addLight(OrthographicCamera camera, Emissive light, float x, float y, float radius, double wave) {
        var gl = Gdx.gl;
        gl.glEnable(GL20.GL_BLEND);
        boolean masked = occlusion.triangles.size != 0;
        if (masked) {
            // Clear just visibility to one, leaving ambient and all earlier lights intact.
            gl.glColorMask(false, false, false, true);
            gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            gl.glBlendEquation(GL30.GL_MIN); // overlapping pieces keep the darkest, never accumulate
            mesh.mask(camera.combined, occlusion.triangles);
            gl.glBlendEquation(GL20.GL_FUNC_ADD);
        }
        gl.glColorMask(true, true, true, false);
        gl.glBlendFunc(masked ? GL20.GL_DST_ALPHA : GL20.GL_ONE, GL20.GL_ONE);
        mesh.light(camera.invProjectionView, x, y, radius, light, wave, masked);
    }

    void dispose(RenderState state) {
        mesh.dispose(state);
        map.dispose(state);
    }
}
