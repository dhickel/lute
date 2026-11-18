package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.*;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import io.mindspice.lute.engine.GameCore;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.mindspice.lute.engine.assets.*;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.data.json.WeaponsData.WeaponData.*;
import io.mindspice.lute.engine.systems.*;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.IntBuffer;

/** Finite, real-GL regression executable; no mocked framebuffer or batch operations. */
public final class CompositeRenderTest extends ApplicationAdapter {
    private static Throwable failure;
    private static int checks;
    private final TestAssets assets = new TestAssets();
    private final List<Texture> textures = new ArrayList<>();
    private SpriteBatch batch;
    private FrameBuffer output;
    private RenderSystem renderer;
    private Engine engine;
    private final LoadoutSystem loadouts = new LoadoutSystem();
    private OrthographicCamera camera;
    private FitViewport viewport;
    private final Map<String, CombatWeapon.Weapon> weapons = Map.of(
        "sword", weapon("sword", "melee/basic_sword", "slash"),
        "bow", weapon("bow", "ranged/basic_bow", "shot"),
        "unarmed", weapon("unarmed", "NONE", "jab"));

    public static void main(String[] args) {
        var configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Composite render regression");
        configuration.setWindowedMode(256, 256);
        configuration.setInitialVisible(false);
        configuration.disableAudio(true);
        configuration.setForegroundFPS(60);
        try { new Lwjgl3Application(new CompositeRenderTest(), configuration); }
        catch (Throwable error) { throw new AssertionError("Real GL test failed; ensure DISPLAY=:0 provides an OpenGL context", error); }
        if (failure != null) { throw new AssertionError("Composite framebuffer regression failed", failure); }
        System.out.println("CompositeRenderTest: " + checks + " real GL checks passed");
    }

    @Override public void create() {
        try {
            CompositeGameTest.run();
            float previousUnit = GameConsts.UNIT_SCALE;
            try { GameConsts.UNIT_SCALE = 1; runTests(); }
            finally { GameConsts.UNIT_SCALE = previousUnit; }
        }
        catch (Throwable error) { failure = error; }
        finally { Gdx.app.exit(); }
    }

    private void runTests() {
        var definitions = List.of(CharacterAssets.CHARACTER, CharacterAssets.FEET, CharacterAssets.BODY,
            CharacterAssets.HEAD, CharacterAssets.HANDS, CharacterAssets.WEAPONS);
        for (int layer = 0; layer < definitions.size(); layer++) {
            assets.atlases.put(definitions.get(layer).id(), atlas(layer, false));
        }
        camera = new OrthographicCamera();
        viewport = new FitViewport(128, 128, camera);
        viewport.update(128, 128, true);
        batch = new SpriteBatch();
        output = new FrameBuffer(Pixmap.Format.RGBA8888, 128, 128, false);
        renderer = new RenderSystem(batch, viewport, camera, assets);
        engine = new Engine();
        engine.addSystem(loadouts);
        engine.addSystem(renderer);
        Entity a = character(16, 16, 1);
        engine.addEntity(a);
        for (var direction : Facing.Direction.values()) {
            for (String action : List.of("idle", "walk", "death", "slash", "jab", "shot")) {
                loadouts.equipWeapon(a, action.equals("shot") ? "bow" : "sword");
                for (int frame = 0; frame < 8; frame++) {
                    playback(a, action, direction, frame);
                    try (var image = renderFrame()) {
                        for (int layer = 0; layer < 6; layer++) {
                            pixel(image, 16 + layer * 8 + 2, 16 + 8, color(layer, direction.ordinal(), frame), "aligned " + action + direction + frame + "/" + layer);
                        }
                        int top = direction == Facing.Direction.UP ? 3 : 5;
                        pixel(image, 48, 48, color(top, direction.ordinal(), frame), "direction order");
                        layerPairs(image, direction, frame, true);
                        pixel(image, 17, 77, 0, "transparent hole/orientation");
                    }
                }
            }
        }
        loadouts.equipWeapon(a, null);
        for (var direction : Facing.Direction.values()) {
            for (String action : List.of("idle", "walk", "jab", "death")) {
                for (int frame = 0; frame < 8; frame++) {
                    playback(a, action, direction, frame);
                    try (var image = renderFrame()) {
                        pixel(image, 48, 48, color(3, direction.ordinal(), frame), "unarmed helmet stays above gloves");
                        layerPairs(image, direction, frame, false);
                    }
                }
            }
        }
        playback(a, "idle", Facing.Direction.DOWN, 0);
        loadouts.equipWeapon(a, "bow");
        loadouts.equip(a, new Loadout.Equipment("alternate", "alternate", "alternate", "alternate"));
        try (var image = renderFrame()) { pixel(image, 34, 24, alternate(color(2, Facing.Direction.DOWN.ordinal(), 0)), "equipment switch invalidates selection"); }
        loadouts.equip(a, new Loadout.Equipment("basic", "basic", "basic", "basic"));
        var replacement = atlas(2, true);
        assets.atlases.put(CharacterAssets.BODY.id(), replacement);
        try (var image = renderFrame()) { pixel(image, 34, 24, alternate(color(2, Facing.Direction.DOWN.ordinal(), 0)), "atlas identity replacement"); }
        assets.atlases.put(CharacterAssets.BODY.id(), atlas(2, false));

        Entity b = character(16, 20, 1);
        Graphic.MAPPER.get(b).getColor().set(0, 1, 0, 1);
        engine.addEntity(b);
        try (var image = renderFrame()) { pixel(image, 48, 50, color(5, Facing.Direction.DOWN.ordinal(), 0), "lower y character in front"); }
        Transform.MAPPER.get(b).getPosition().y = 12;
        try (var image = renderFrame()) { pixel(image, 48, 46, green(color(5, Facing.Direction.DOWN.ordinal(), 0)), "exchange y"); }
        b.add(new Transform(new Vector2(16, 20), 2, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        try (var image = renderFrame()) { pixel(image, 48, 50, green(color(5, Facing.Direction.DOWN.ordinal(), 0)), "exchange z"); }
        Entity object = ordinary(16, 18, 3, 0xff00ffff);
        engine.addEntity(object);
        try (var image = renderFrame()) { pixel(image, 48, 48, 0xff00ffff, "ordinary full sprite in front"); }
        object.add(new Transform(new Vector2(16, 18), 0, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        try (var image = renderFrame()) { pixel(image, 48, 50, green(color(5, Facing.Direction.DOWN.ordinal(), 0)), "ordinary full sprite behind"); }
        object.add(new Transform(new Vector2(16, 18), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        b.add(new Transform(new Vector2(16, 20), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        try (var image = renderFrame()) {
            pixel(image, 48, 50, color(5, Facing.Direction.DOWN.ordinal(), 0), "object between characters: front character");
            pixel(image, 50, 54, 0xff00ffff, "object between characters: rear character obscured");
            screenshot(image, "/tmp/lute-phase3-overlap.png");
        }
        engine.removeEntity(object);
        engine.removeEntity(b);
        mapPasses();
        alpha(a);
        stateRestoration();
        assetBoundary(a);
        paddedCanvasAndPivot(a);
        growthAndCull(a);
        multipageTargets();
        renderer.dispose();
        renderer.dispose();
        requireThrows(() -> renderer.update(0), "disposed renderer rejects updates");
        renderer = null;
    }

    private void layerPairs(Image image, Facing.Direction direction, int frame, boolean armed) {
        int pair = 0;
        for (int first = 0; first < 6; first++) {
            for (int second = first + 1; second < 6; second++, pair++) {
                final int winner;
                if (second == 5) { winner = armed && direction != Facing.Direction.UP ? 5 : first; }
                else if (first == 3 && second == 4) { winner = 3; } // Helmet covers gloves.
                else { winner = second; } // Base, feet and torso retain their local order.
                pixel(image, 21 + pair % 5 * 8, 70 - pair / 5 * 6,
                    color(winner, direction.ordinal(), frame), "pair " + first + "/" + second + " facing " + direction);
            }
        }
    }

    private void mapPasses() {
        var map = new TiledMap();
        var bg = tileLayer(0x112233ff);
        bg.setName("background");
        var marker = new MapLayer(); marker.setName("Objects");
        var fg = tileLayer(0x775533ff);
        fg.setName("foreground");
        map.getLayers().add(bg); map.getLayers().add(marker); map.getLayers().add(fg);
        renderer.setMap(map);
        try (var image = renderFrame()) { pixel(image, 48, 48, 0x775533ff, "foreground covers completed sprite"); }
        renderer.cleanMapLayers();
        map.getLayers().remove(fg);
        renderer.loadMapLayers(map);
        try (var image = renderFrame()) {
            pixel(image, 48, 48, color(5, Facing.Direction.DOWN.ordinal(), 0), "character covers background");
            pixel(image, 2, 2, 0x112233ff, "background visible");
        }
        renderer.cleanMapLayers();
        map.dispose();
    }

    private TiledMapTileLayer tileLayer(int rgba) {
        int count = (int) Math.ceil(128 / (64 * GameConsts.UNIT_SCALE));
        var layer = new TiledMapTileLayer(count, count, 64, 64);
        var tile = new StaticTiledMapTile(new TextureRegion(solid(rgba)));
        for (int x = 0; x < count; x++) {
            for (int y = 0; y < count; y++) { layer.setCell(x, y, new TiledMapTileLayer.Cell().setTile(tile)); }
        }
        return layer;
    }

    private void alpha(Entity a) {
        Graphic.MAPPER.get(a).getColor().set(1, 0.5f, 1, 0.5f);
        try (var image = renderFrame()) {
            // Source bottom-left patch is half-transparent red; all other layers have a hole here.
            near(image, 19, 20, 0x40000040, "premult edge plus translucent tint");
            near(image, 21, 21, 0x20004060, "two translucent layers plus tint");
            pixel(image, 17, 77, 0, "transparency remains a hole after tint");
            near(image, 48, 48, tint(color(5, Facing.Direction.DOWN.ordinal(), 0)), "premult tint alpha");
        }
        var background = ordinary(16, 16, 0, 0x00ff00ff);
        engine.addEntity(background);
        try (var image = renderFrame()) {
            near(image, 19, 20, 0x40bf00ff, "translucent edge over opaque world object");
            near(image, 21, 21, 0x209f40ff, "two translucent layers over opaque object");
            screenshot(image, "/tmp/lute-phase3-alpha.png");
        }
        engine.removeEntity(background);
        Graphic.MAPPER.get(a).getColor().set(Color.WHITE);
    }

    private void paddedCanvasAndPivot(Entity a) {
        a.add(new Transform(new Vector2(32, 32), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        int expected = color(0, Facing.Direction.DOWN.ordinal(), 0);
        try (var image = renderFrame()) {
            pixel(image, 12, 64, expected, "left overhang retains native canvas pixels");
            pixel(image, 116, 64, expected, "right overhang is not cropped to logical bounds");
            pixel(image, 64, 116, expected, "top overhang retains full canvas height");
            pixel(image, 64, 12, expected, "bottom overhang retains full canvas height");
            pixel(image, 64, 64, color(5, Facing.Direction.DOWN.ordinal(), 0), "logical center and canvas pivot coincide");
            pixel(image, 38, 64, 0, "full canvas is not naively downsampled into the logical 64px area");
            screenshot(image, "/tmp/lute-canvas-native-padding.png");
        }
        a.add(new Transform(new Vector2(24, 28), 1, new Vector2(80, 72), new Vector2(1, 1), 0, 0));
        try (var image = renderFrame()) {
            pixel(image, 12, 64, expected, "A different configured logical size changes offset, not source scale");
            pixel(image, 64, 64, color(5, Facing.Direction.DOWN.ordinal(), 0), "Future logical sizes retain the same centered canvas pivot");
        }
        a.add(new Transform(new Vector2(132, 32), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        try (var image = renderFrame()) {
            pixel(image, 112, 64, expected, "visible overhang survives while logical body is entirely offscreen");
        }
        // The bottom canvas overhang rotates into view while the scaled logical body stays outside.
        a.add(new Transform(new Vector2(132, 32), 1, new Vector2(64, 64), new Vector2(1.5f, 1), -90, 0));
        try (var image = renderFrame()) {
            pixel(image, 124, 64, expected, "rotation and nonuniform scale cull using the full visual canvas");
            screenshot(image, "/tmp/lute-canvas-overhang-rotated.png");
        }
        a.add(new Transform(new Vector2(80, 32), 1, new Vector2(64, 64), new Vector2(-1, 1), 90, 0));
        try (var image = renderFrame()) {
            pixel(image, 48, 116, expected, "negative scale and rotation share the logical pivot");
        }
        a.add(new Transform(new Vector2(16, 16), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
    }

    private void growthAndCull(Entity a) {
        var extra = new ArrayList<Entity>();
        for (int i = 0; i < 35; i++) {
            var entity = character(16, 16, 1);
            extra.add(entity); engine.addEntity(entity);
        }
        try (var image = renderFrame()) { pixel(image, 48, 48, color(5, Facing.Direction.DOWN.ordinal(), 0), "cell growth isolation"); }
        extra.forEach(engine::removeEntity);
        Transform.MAPPER.get(a).getPosition().set(200, 200);
        try (var image = renderFrame()) { pixel(image, 48, 48, 0, "empty visible state"); }
        a.add(new Transform(new Vector2(-70, 16), 1, new Vector2(64, 64), new Vector2(2, 1), 30, 0));
        try (var image = renderFrame()) {
            boolean any = false;
            for (int x = 0; x < 32; x++) { for (int y = 0; y < 100; y++) { any |= image.pixmap.getPixel(x, y) != 0; } }
            require(any, "transformed bounds intersect camera");
        }
        engine.removeEntity(a);
        try (var image = renderFrame()) { pixel(image, 48, 48, 0, "removed character clears output"); }
        output.dispose();
        output = new FrameBuffer(Pixmap.Format.RGBA8888, 192, 160, false);
        viewport.update(192, 160, true);
        a.add(new Transform(new Vector2(16, 16), 1, new Vector2(64, 64), new Vector2(1, 1), 0, 0));
        engine.addEntity(a);
        try (var image = renderFrame()) {
            pixel(image, 76, 60, color(5, Facing.Direction.DOWN.ordinal(), 0), "resized viewport renders retained composite");
            pixel(image, 0, 60, 0, "resized letterbox is untouched");
            screenshot(image, "/tmp/lute-phase3-resize.png");
        }
        a.add(new Transform(new Vector2(100, 16), 1, new Vector2(64, 64), new Vector2(-1, 1), 0, 0));
        try (var image = renderFrame()) { pixel(image, 101, 60, color(5, Facing.Direction.DOWN.ordinal(), 0), "negative scale transformed culling and rendering"); }
        engine.removeEntity(a);
        viewport.update(128, 128, true);
    }

    private void stateRestoration() {
        var projection = new Matrix4().setToOrtho2D(-3, -4, 90, 95);
        var transform = new Matrix4().translate(3, 4, 0).rotate(0, 0, 1, 17);
        var shader = SpriteBatch.createDefaultShader();
        var defaultShader = batch.getShader();
        defaultShader.bind();
        defaultShader.setUniformMatrix("u_projTrans", projection);
        defaultShader.setUniformi("u_texture", 3);
        var texture0 = solid(0x333333ff);
        var texture1 = solid(0x555555ff);
        batch.setProjectionMatrix(projection);
        batch.setTransformMatrix(transform);
        batch.setShader(shader);
        batch.setColor(0.3f, 0.4f, 0.5f, 0.6f);
        float packed = batch.getPackedColor();
        var exactColor = batch.getColor().cpy();
        batch.disableBlending();
        batch.setBlendFunctionSeparate(GL20.GL_DST_COLOR, GL20.GL_SRC_COLOR, GL20.GL_ZERO, GL20.GL_ONE);
        output.begin();
        int framebuffer = integer(GL20.GL_FRAMEBUFFER_BINDING);
        int arrayBuffer = Gdx.gl.glGenBuffer(), elementBuffer = Gdx.gl.glGenBuffer();
        Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, arrayBuffer);
        Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        shader.bind();
        int program = integer(GL20.GL_CURRENT_PROGRAM);
        texture0.bind(0);
        texture1.bind(1);
        Gdx.gl.glViewport(7, 9, 111, 113);
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 0.6f);
        Gdx.gl.glBlendFuncSeparate(GL20.GL_ONE, GL20.GL_ZERO, GL20.GL_DST_ALPHA, GL20.GL_SRC_ALPHA);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_REVERSE_SUBTRACT, GL20.GL_FUNC_SUBTRACT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(3, 5, 1, 1);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
        Gdx.gl.glEnable(GL20.GL_CULL_FACE);
        Gdx.gl.glDepthMask(false);
        Gdx.gl.glColorMask(false, true, false, true);
        try {
            renderer.update(0);
            require(integer(GL20.GL_FRAMEBUFFER_BINDING) == framebuffer, "caller framebuffer restored");
            var values = BufferUtils.newIntBuffer(4);
            Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, values);
            require(values.get(0) == 7 && values.get(1) == 9 && values.get(2) == 111 && values.get(3) == 113, "caller viewport restored");
            var floats = BufferUtils.newFloatBuffer(4);
            Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, floats);
            require(floats.get(0) == 0.2f && floats.get(1) == 0.3f && floats.get(2) == 0.4f && floats.get(3) == 0.6f, "clear color restored");
            require(integer(GL20.GL_CURRENT_PROGRAM) == program, "caller shader program restored");
            require(integer(GL20.GL_ARRAY_BUFFER_BINDING) == arrayBuffer && integer(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING) == elementBuffer, "caller buffers restored");
            require(integer(GL20.GL_ACTIVE_TEXTURE) == GL20.GL_TEXTURE1 && integer(GL20.GL_TEXTURE_BINDING_2D) == texture1.getTextureObjectHandle(), "active texture unit and its binding restored");
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            require(integer(GL20.GL_TEXTURE_BINDING_2D) == texture0.getTextureObjectHandle(), "unit zero binding restored");
            require(Gdx.gl.glIsEnabled(GL20.GL_BLEND) && Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST)
                && Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST) && Gdx.gl.glIsEnabled(GL20.GL_STENCIL_TEST)
                && Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE), "caller enables restored");
            require(integer(GL20.GL_BLEND_SRC_RGB) == GL20.GL_ONE && integer(GL20.GL_BLEND_DST_RGB) == GL20.GL_ZERO
                && integer(GL20.GL_BLEND_SRC_ALPHA) == GL20.GL_DST_ALPHA && integer(GL20.GL_BLEND_DST_ALPHA) == GL20.GL_SRC_ALPHA,
                "GL blend factors restored");
            require(integer(GL20.GL_BLEND_EQUATION_RGB) == GL20.GL_FUNC_REVERSE_SUBTRACT
                && integer(GL20.GL_BLEND_EQUATION_ALPHA) == GL20.GL_FUNC_SUBTRACT, "blend equations restored");
            var mask = BufferUtils.newByteBuffer(4);
            Gdx.gl.glGetBooleanv(GL20.GL_COLOR_WRITEMASK, mask);
            require(mask.get(0) == 0 && mask.get(1) != 0 && mask.get(2) == 0 && mask.get(3) != 0, "color mask restored");
            Gdx.gl.glGetBooleanv(GL20.GL_DEPTH_WRITEMASK, mask);
            require(mask.get(0) == 0, "depth mask restored");
            require(batch.getPackedColor() == packed && batch.getColor().equals(exactColor)
                && !batch.isBlendingEnabled() && !batch.isDrawing(), "exact batch float/packed color and blending/drawing restored");
            var uniform = BufferUtils.newFloatBuffer(16);
            Gdx.gl.glGetUniformfv(defaultShader.getHandle(), defaultShader.getUniformLocation("u_projTrans"), uniform);
            for (int i = 0; i < 16; i++) { require(uniform.get(i) == projection.val[i], "caller default shader matrix uniform preserved"); }
            var sampler = BufferUtils.newIntBuffer(1);
            Gdx.gl.glGetUniformiv(defaultShader.getHandle(), defaultShader.getUniformLocation("u_texture"), sampler);
            require(sampler.get(0) == 3, "caller default shader sampler uniform preserved");
            require(batch.getShader() == shader && Arrays.equals(batch.getProjectionMatrix().val, projection.val)
                && Arrays.equals(batch.getTransformMatrix().val, transform.val), "batch shader and matrices restored");
            require(batch.getBlendSrcFunc() == GL20.GL_DST_COLOR && batch.getBlendDstFunc() == GL20.GL_SRC_COLOR
                && batch.getBlendSrcFuncAlpha() == GL20.GL_ZERO && batch.getBlendDstFuncAlpha() == GL20.GL_ONE, "batch blend factors restored");
        } finally {
            Gdx.gl.glColorMask(true, true, true, true);
            Gdx.gl.glDepthMask(true);
            for (int capability : new int[]{GL20.GL_BLEND, GL20.GL_SCISSOR_TEST, GL20.GL_DEPTH_TEST, GL20.GL_STENCIL_TEST, GL20.GL_CULL_FACE}) {
                Gdx.gl.glDisable(capability);
            }
            Gdx.gl.glBlendEquation(GL20.GL_FUNC_ADD);
            Gdx.gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, 0);
            Gdx.gl.glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, 0);
            Gdx.gl.glDeleteBuffer(arrayBuffer);
            Gdx.gl.glDeleteBuffer(elementBuffer);
            batch.setShader(null);
            shader.dispose();
            Gdx.gl.glUseProgram(0);
            batch.setColor(Color.WHITE);
            batch.setTransformMatrix(new Matrix4());
            batch.enableBlending();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            output.end();
        }
        batch.begin();
        requireThrows(() -> renderer.update(0), "active caller batch is rejected without ending it");
        require(batch.isDrawing(), "rejected batch remains drawing");
        batch.end();
    }

    private void assetBoundary(Entity a) {
        var previous = assets.atlases.get(CharacterAssets.BODY.id());
        var bad = atlas(2, false);
        bad.getRegions().first().originalWidth = 63;
        assets.atlases.put(CharacterAssets.BODY.id(), bad);
        output.begin();
        int framebuffer = integer(GL20.GL_FRAMEBUFFER_BINDING);
        Gdx.gl.glClearColor(0.1f, 0.2f, 0.3f, 0.4f);
        try {
            requireThrows(() -> renderer.update(0), "replacement asset is revalidated");
            require(integer(GL20.GL_FRAMEBUFFER_BINDING) == framebuffer, "failure restores caller framebuffer");
            var clear = BufferUtils.newFloatBuffer(4);
            Gdx.gl.glGetFloatv(GL20.GL_COLOR_CLEAR_VALUE, clear);
            require(clear.get(0) == 0.1f && clear.get(3) == 0.4f && !batch.isDrawing(), "failure restores clear color and batch");
        } finally {
            assets.atlases.put(CharacterAssets.BODY.id(), previous);
            output.end();
        }
        try (var image = renderFrame()) { pixel(image, 48, 48, color(5, Facing.Direction.DOWN.ordinal(), 0), "valid atlas restored after rejection"); }
        var original = assets.atlases.get(CharacterAssets.CHARACTER.id());
        assets.atlases.put(CharacterAssets.CHARACTER.id(), atlas(0, true));
        try (var image = renderFrame()) { pixel(image, 18, 24, alternate(color(0, Facing.Direction.DOWN.ordinal(), 0)), "base identity replacement invalidates cache"); }
        assets.atlases.put(CharacterAssets.CHARACTER.id(), original);
        var originalWeapons = assets.atlases.get(CharacterAssets.WEAPONS.id());
        assets.atlases.put(CharacterAssets.WEAPONS.id(), atlas(5, true));
        try (var image = renderFrame()) { pixel(image, 48, 48, alternate(color(5, Facing.Direction.DOWN.ordinal(), 0)), "weapon identity replacement invalidates cached pages"); }
        assets.atlases.put(CharacterAssets.WEAPONS.id(), originalWeapons);
        // Graphic's old base frame is deliberately stale: only the shared clock selects all source layers.
        require(Graphic.MAPPER.get(a).getRegion() != null, "composite ignores the old fullsprite graphic region");
    }

    private void multipageTargets() {
        GL20 previous = Gdx.gl, previous20 = Gdx.gl20;
        int[] maximum = {260}, draws = {0};
        var sourceBinds = new ArrayList<Integer>();
        var sourceIds = new ArrayList<Integer>();
        for (var definition : List.of(CharacterAssets.CHARACTER, CharacterAssets.FEET, CharacterAssets.BODY,
            CharacterAssets.HEAD, CharacterAssets.HANDS, CharacterAssets.WEAPONS)) {
            String prefix = definition.equals(CharacterAssets.CHARACTER) ? "" : definition.equals(CharacterAssets.WEAPONS) ? "melee/basic_sword/" : "basic/";
            sourceIds.add(assets.atlases.get(definition.id()).findRegion(prefix + "idle_down", 0).getTexture().getTextureObjectHandle());
        }
        // Instrument the real driver; cap the reported budget so the multi-target path is
        // executable without allocating a maximum-size texture. Every GL draw/read stays real.
        var instrumented = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class<?>[]{GL20.class}, (proxy, method, args) -> {
            try {
                Object result = method.invoke(previous, args);
                if (method.getName().equals("glGetIntegerv") && (int) args[0] == GL20.GL_MAX_TEXTURE_SIZE) {
                    ((IntBuffer) args[1]).put(0, maximum[0]);
                }
                if (method.getName().equals("glDrawElements")) { draws[0]++; }
                if (method.getName().equals("glBindTexture") && sourceIds.contains((int) args[1])) { sourceBinds.add((int) args[1]); }
                return result;
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
        Gdx.gl = instrumented;
        Gdx.gl20 = instrumented;
        CharacterCompositor cells = null;
        var characters = new ArrayList<Entity>();
        try {
            Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
            Gdx.gl.glBindTexture(GL20.GL_TEXTURE_2D, 0);
            cells = new CharacterCompositor(assets);
            for (int i = 0; i < 10; i++) { characters.add(character(16, 16, 1)); }
            cells.compose(List.of(characters.getFirst()), camera);
            Texture smallTarget = cells.region(characters.getFirst()).getTexture();
            draws[0] = 0;
            sourceBinds.clear();
            cells.compose(characters, camera);
            // The driver may immediately reuse the deleted GLuint for another target.
            require(smallTarget.getTextureObjectHandle() == 0, "Growing 128px cells releases the smaller framebuffer texture");
            var targetTextures = new HashSet<Texture>();
            for (var entity : characters) { targetTextures.add(cells.region(entity).getTexture()); }
            require(targetTextures.size() == 3, "capacity exceeding texture budget allocates multiple targets");
            require(draws[0] == 18, "six source-page batches per target, not six draws per character: " + draws[0]);
            var expectedBinds = new ArrayList<Integer>();
            // All characters face down: base, feet, body, gloves, helmet, then weapon.
            // Each rank must finish across all three targets before the next rank begins.
            for (int layer : new int[]{0, 1, 2, 4, 3, 5}) {
                for (int target = 0; target < 3; target++) { expectedBinds.add(sourceIds.get(layer)); }
            }
            require(sourceBinds.equals(expectedBinds), "source pages grouped across all targets in dependency order: " + sourceBinds);
            for (int i = 0; i < characters.size(); i++) {
                playback(characters.get(i), "idle", i % 2 == 0 ? Facing.Direction.DOWN : Facing.Direction.UP, i % 8);
            }
            cells.compose(characters, camera);
            for (int i = 0; i < characters.size(); i++) {
                var region = cells.region(characters.get(i));
                output.begin();
                try {
                    Gdx.gl.glClearColor(0, 0, 0, 0);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    Gdx.gl.glViewport(0, 0, output.getWidth(), output.getHeight());
                    batch.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, output.getWidth(), output.getHeight()));
                    batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
                    batch.begin();
                    require(region.getRegionWidth() == 128 && region.getRegionHeight() == 128,
                        "Every isolated cell retains the full 128px canvas");
                    batch.draw(region, 0, 0);
                    batch.end();
                    try (var image = new Image(Pixmap.createFromFrameBuffer(0, 0, 128, 128))) {
                        var direction = i % 2 == 0 ? Facing.Direction.DOWN : Facing.Direction.UP;
                        pixel(image, 64, 64, color(direction == Facing.Direction.UP ? 3 : 5, direction.ordinal(), i % 8), "distinct cell frame/orientation " + i);
                        pixel(image, 1, 126, 0, "no adjacent cell leak " + i);
                        pixel(image, 12, 64, color(0, direction.ordinal(), i % 8), "cell preserves native overhang " + i);
                    }
                    Gdx.gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, region.getTexture().getTextureObjectHandle(), 0);
                    int x = region.getRegionX() - 1;
                    int y = Math.round(Math.min(region.getV(), region.getV2()) * region.getTexture().getHeight()) - 1;
                    try (var gutter = new Image(Pixmap.createFromFrameBuffer(x, y, 130, 130))) {
                        for (int edge = 0; edge < 130; edge++) {
                            pixel(gutter, edge, 0, 0, "bottom cell gutter " + i);
                            pixel(gutter, edge, 129, 0, "top cell gutter " + i);
                            pixel(gutter, 0, edge, 0, "left cell gutter " + i);
                            pixel(gutter, 129, edge, 0, "right cell gutter " + i);
                        }
                    }
                } finally {
                    Gdx.gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER, GL20.GL_COLOR_ATTACHMENT0, GL20.GL_TEXTURE_2D, output.getColorBufferTexture().getTextureObjectHandle(), 0);
                    output.end();
                }
            }
            cells.compose(List.of(), camera);
            require(cells.region(characters.getFirst()) == null, "empty composition invalidates completed references");
            maximum[0] = 129;
            var finalCells = cells;
            requireThrows(() -> finalCells.compose(characters, camera), "unsupported texture budget fails clearly");
            var targetHandles = targetTextures.stream().map(Texture::getTextureObjectHandle).toList();
            cells.dispose();
            cells.dispose();
            for (int handle : targetHandles) { require(!Gdx.gl.glIsTexture(handle), "disposed target GL texture released"); }
            requireThrows(() -> finalCells.compose(characters, camera), "disposed compositor rejects reuse");
        } finally {
            if (cells != null) { cells.dispose(); }
            Gdx.gl = previous;
            Gdx.gl20 = previous20;
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private static int integer(int key) {
        var value = BufferUtils.newIntBuffer(4);
        Gdx.gl.glGetIntegerv(key, value);
        return value.get(0);
    }

    private Entity ordinary(float x, float y, int z, int color) {
        return new Entity().add(new Transform(new Vector2(x, y), z, new Vector2(64, 64), new Vector2(1, 1), 0, 0))
            .add(new Graphic(new TextureRegion(solid(color)), Color.WHITE.cpy()));
    }

    private Entity character(float x, float y, int z) {
        var entity = ordinary(x, y, z, 0);
        entity.add(new CompositeCharacter(128, 128))
            .add(new Loadout(new Loadout.Equipment("basic", "basic", "basic", "basic"), "sword", weapons, equipment -> {}));
        require(Combat.MAPPER.get(entity) == null, "Rendering and loadout switching do not require combat state");
        var animation = new Animation2D(CharacterAssets.CHARACTER.id(), "", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, 0.1f);
        entity.add(animation);
        playback(entity, "idle", Facing.Direction.DOWN, 0);
        return entity;
    }

    private void playback(Entity entity, String action, Facing.Direction direction, int frame) {
        var animation = Animation2D.MAPPER.get(entity);
        if (action.equals("idle")) { animation.setType(Animation2D.AnimationType.IDLE); }
        else if (action.equals("walk")) { animation.setType(Animation2D.AnimationType.WALK); }
        else {
            animation.configureAttack(new Animation2D.Attack(action, 1, 0.1f, 0, ""));
            animation.setType(Animation2D.AnimationType.ATTACK);
        }
        animation.setAnimation(new Animation<>(0.1f, assets.atlases.get(CharacterAssets.CHARACTER.id()).findRegions(action + "_" + direction.atlasKey), Animation.PlayMode.LOOP), direction);
        animation.incAndGetStateTime(frame * 0.1f + 0.001f);
    }

    private TextureAtlas atlas(int layer, boolean changed) {
        var atlas = new TextureAtlas();
        // Each frame is its own real texture page; successive frames and directions change pages.
        for (var direction : Facing.Direction.values()) {
            for (int frame = 0; frame < 8; frame++) {
                int rgba = color(layer, direction.ordinal(), frame);
                Texture texture = layerTexture(layer, changed ? alternate(rgba) : rgba);
                for (String action : List.of("idle", "walk", "death", "slash", "jab", "shot")) {
                    if (layer == 0) { add(atlas, action + "_" + direction.atlasKey, frame, texture); }
                    else if (layer == 5) {
                        if (!action.equals("shot")) { add(atlas, "melee/basic_sword/" + action + "_" + direction.atlasKey, frame, texture); }
                        if (!action.equals("slash")) { add(atlas, "ranged/basic_bow/" + action + "_" + direction.atlasKey, frame, texture); }
                    } else {
                        add(atlas, "basic/" + action + "_" + direction.atlasKey, frame, texture);
                    }
                }
                if (layer > 0 && layer < 5) {
                    Texture other = layerTexture(layer, alternate(rgba));
                    for (String action : List.of("idle", "walk", "death", "slash", "jab", "shot")) {
                        add(atlas, "alternate/" + action + "_" + direction.atlasKey, frame, other);
                    }
                }
            }
        }
        return atlas;
    }

    private void add(TextureAtlas atlas, String name, int index, Texture texture) {
        atlas.addRegion(name, new TextureRegion(texture)).index = index;
    }

    private Texture layerTexture(int layer, int rgba) {
        var pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(rgba);
        pixmap.fillRectangle(32 + layer * 8, 80, 8, 12);
        pixmap.fillRectangle(60, 60, 8, 8);
        // Each marker overlaps exactly two layers, exposing occlusion hidden by the shared top pixel.
        int pair = 0;
        for (int first = 0; first < 6; first++) {
            for (int second = first + 1; second < 6; second++, pair++) {
                if (layer == first || layer == second) {
                    pixmap.fillRectangle(36 + pair % 5 * 8, 40 + pair / 5 * 6, 3, 3);
                }
            }
        }
        if (layer == 0) {
            pixmap.fillRectangle(8, 60, 8, 8);
            pixmap.fillRectangle(112, 60, 8, 8);
            pixmap.fillRectangle(60, 8, 8, 8);
            pixmap.fillRectangle(60, 112, 8, 8);
            pixmap.setColor(0xff000080);
            pixmap.fillRectangle(34, 88, 6, 6);
        }
        if (layer == 1) { pixmap.setColor(0x0000ff80); pixmap.fillRectangle(36, 90, 2, 2); }
        var texture = new Texture(pixmap);
        textures.add(texture); pixmap.dispose();
        return texture;
    }

    private Texture solid(int color) {
        var pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(color); pixmap.fill();
        var texture = new Texture(pixmap);
        textures.add(texture); pixmap.dispose();
        return texture;
    }

    private Image renderFrame() {
        output.begin();
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        try {
            engine.update(0);
            int width = output.getWidth(), height = output.getHeight();
            return new Image(Pixmap.createFromFrameBuffer(0, 0, width, height));
        } finally { output.end(); }
    }

    private static CombatWeapon.Weapon weapon(String id, String prefix, String primary) {
        return new CombatWeapon.Weapon(id, prefix, attack(id, primary), attack(id, "jab"));
    }
    private static CombatWeapon.Attack attack(String id, String action) {
        var zero = new Offset(0, 0);
        return new CombatWeapon.Attack(id, 1, 0, new Animation2D.Attack(action, 1, 0.1f, 0, ""), "",
            BoundsType.RECT, new CombatWeapon.Bounds(0, 0, 1, 1, zero, zero, zero, zero), CollisionType.MELEE);
    }
    private static int color(int layer, int direction, int frame) {
        return ((40 + layer * 30) << 24) | ((40 + direction * 40) << 16) | ((40 + frame * 20) << 8) | 255;
    }
    private static int alternate(int rgba) { return rgba ^ 0x44000000; }
    private static int green(int rgba) { return rgba & 0x00ff00ff; }
    private static int tint(int rgba) {
        int r = rgba >>> 24, g = rgba >>> 16 & 255, b = rgba >>> 8 & 255;
        return (Math.round(r * 0.5f) << 24) | (Math.round(g * 0.25f) << 16) | (Math.round(b * 0.5f) << 8) | 128;
    }
    private static void pixel(Image image, int x, int y, int expected, String message) {
        int actual = image.pixmap.getPixel(x, y);
        require(actual == expected, message + " at " + x + "," + y + ": expected " + Integer.toHexString(expected) + " got " + Integer.toHexString(actual));
    }
    private static void near(Image image, int x, int y, int expected, String message) {
        int actual = image.pixmap.getPixel(x, y);
        for (int shift : new int[]{0, 8, 16, 24}) {
            require(Math.abs((actual >>> shift & 255) - (expected >>> shift & 255)) <= 2,
                message + ": expected " + Integer.toHexString(expected) + " got " + Integer.toHexString(actual));
        }
    }
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) { throw new AssertionError(message); }
    }
    private static void screenshot(Image image, String path) {
        var flipped = new Pixmap(image.pixmap.getWidth(), image.pixmap.getHeight(), Pixmap.Format.RGBA8888);
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < flipped.getHeight(); y++) {
            for (int x = 0; x < flipped.getWidth(); x++) {
                flipped.drawPixel(x, flipped.getHeight() - 1 - y, image.pixmap.getPixel(x, y));
            }
        }
        PixmapIO.writePNG(Gdx.files.absolute(path), flipped);
        flipped.dispose();
    }

    private static void requireThrows(Runnable action, String message) {
        try { action.run(); }
        catch (IllegalArgumentException | IllegalStateException expected) { require(true, message); return; }
        throw new AssertionError(message);
    }

    @Override public void dispose() {
        if (renderer != null) { renderer.dispose(); }
        if (output != null) { output.dispose(); }
        if (batch != null) { batch.dispose(); }
        textures.forEach(Texture::dispose);
        CompositeGameTest.dispose();
    }
    private record Image(Pixmap pixmap) implements AutoCloseable {
        @Override public void close() { pixmap.dispose(); }
    }
    private static final class TestAssets extends AssetService {
        final Map<String, TextureAtlas> atlases = new HashMap<>();
        TestAssets() { super(path -> Gdx.files.internal(path)); }
        @Override public <T> T get(Class<T> type, String id) { return type.cast(atlases.get(id)); }
    }
}
