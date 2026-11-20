package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.physics.box2d.World;
import io.mindspice.lute.engine.components.Emissive;
import io.mindspice.lute.engine.components.Physic;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.singletons.GameConsts.NightAmbient;

/** Renderer-owned scratch. Each disk adds to the same light map, but never shares a stencil mask. */
final class NightLighting {
    private final NightOcclusion occlusion = new NightOcclusion();
    private final NightLightMesh mesh;
    private NightLightMap map;
    private NightAmbient ambient;
    private float red, green, blue;

    NightLighting(int width, int height, RenderState state) {
        map = new NightLightMap(width, height, state);
        try { mesh = new NightLightMesh(state); }
        catch (RuntimeException | Error failure) {
            map.dispose(state);
            throw failure;
        }
    }

    void render(World world, Iterable<Entity> emitters, NightAmbient settings, double seconds,
                OrthographicCamera camera, int[] viewport, RenderState state) {
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
                var position = transform.getPosition();
                var size = transform.getSize();
                var scale = transform.getScaling();
                float x = position.x + size.x * scale.x * .5f, y = position.y + size.y * scale.y * .5f;
                double wave = light.wave(seconds);
                float radius = light.radius(wave);
                if (!camera.frustum.sphereInFrustum(x, y, 0f, radius)) { continue; }
                // Past the farthest visible point geometry can neither cast onto nor receive a visible shadow.
                // This also keeps a very large authored radius from exhausting float raster precision in a tiny view.
                double toCamera = Math.hypot((double) x - camera.position.x, (double) y - camera.position.y);
                float maskRadius = Math.min(radius, Math.nextUp((float) (toCamera + viewHalfDiagonal)));
                var physic = Physic.MAPPER.get(entity);
                if (occlusion.collect(world, physic == null ? null : physic.getBody(), x, y, maskRadius, pixelSize)) {
                    addLight(camera, light, x, y, radius, wave);
                }
            }

            state.restoreTargets(); // The caller can be another FBO, not necessarily the window.
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            gl.glDisable(GL20.GL_STENCIL_TEST);
            gl.glColorMask(true, true, true, false);
            gl.glEnable(GL20.GL_BLEND);
            gl.glBlendFuncSeparate(GL20.GL_DST_COLOR, GL20.GL_ZERO, GL20.GL_ZERO, GL20.GL_ONE);
            map.bindColor();
            mesh.composite();
            gl.glColorMask(true, true, true, true);
        } finally { mesh.end(); }
    }

    private void addLight(OrthographicCamera camera, Emissive light, float x, float y, float radius, double wave) {
        var gl = Gdx.gl;
        if (occlusion.triangles.size == 0) {
            gl.glDisable(GL20.GL_STENCIL_TEST);
        } else {
            gl.glEnable(GL20.GL_STENCIL_TEST);
            gl.glStencilMask(0xff);
            gl.glClearStencil(0);
            gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
            gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
            gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
            gl.glColorMask(false, false, false, false);
            mesh.mask(camera.combined, occlusion.triangles);
            gl.glStencilMask(0);
            gl.glStencilFunc(GL20.GL_EQUAL, 0, 0xff);
            gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
        }
        gl.glColorMask(true, true, true, false);
        gl.glEnable(GL20.GL_BLEND);
        gl.glBlendFunc(GL20.GL_ONE, GL20.GL_ONE);
        mesh.light(camera.invProjectionView, x, y, radius, light, wave);
    }

    void dispose(RenderState state) {
        mesh.dispose(state);
        map.dispose(state);
    }
}
