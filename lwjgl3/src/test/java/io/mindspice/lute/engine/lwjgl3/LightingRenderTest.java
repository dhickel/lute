package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.*;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.components.Transform;
import io.mindspice.lute.engine.data.json.*;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.singletons.GameConsts.DebugLayers;
import io.mindspice.lute.engine.systems.*;

import org.lwjgl.opengl.AMDDrawBuffersBlend;
import org.lwjgl.opengl.ARBDrawBuffersBlend;
import org.lwjgl.opengl.EXTDrawBuffers2;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL40C;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;

import static com.badlogic.gdx.graphics.GL20.*;
import static com.badlogic.gdx.graphics.GL30.*;
import static com.badlogic.gdx.physics.box2d.BodyDef.BodyType.*;
import static org.lwjgl.opengl.ARBSamplerObjects.glBindSampler;
import static org.lwjgl.opengl.ARBSamplerObjects.glDeleteSamplers;
import static org.lwjgl.opengl.ARBSamplerObjects.glGenSamplers;
import static org.lwjgl.opengl.ARBSamplerObjects.glGetSamplerParameteri;
import static org.lwjgl.opengl.ARBSamplerObjects.glSamplerParameteri;

/** Real desktop GL and native Box2D, with no filesystem game assets or screen bootstrapping. */
public final class LightingRenderTest extends ApplicationAdapter {
    private static final int WHITE = 0xffffffff, BAR = 0xde279ba7;
    private static final GameConsts.NightAmbient AMBIENT = new GameConsts.NightAmbient("#804020", .25f);
    private static final DebugLayers OFF = new DebugLayers(false, false, false, false);
    private static final DebugLayers RAW = new DebugLayers(false, false, true, false);
    private static final DebugLayers OUTLINES = new DebugLayers(false, false, false, true);
    private static final DebugLayers BOTH = new DebugLayers(false, false, true, true);
    private static final int DIM = 0x201008ff; // ambient × white
    private static final int SOLID = 0xff5f1fff, RADIUS = 0x7fff1fff;
    private static final float LX = 36.5f, LY = 44.5f;
    private static int checks;
    private static Throwable failure;
    private final boolean coreProfile;

    private LightingRenderTest(boolean coreProfile) { this.coreProfile = coreProfile; }
    private final Engine engine = new Engine();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport view = new FitViewport(128, 128, camera);
    private final List<Texture> textures = new ArrayList<>();
    private final TextureAtlas atlas = new TextureAtlas();
    private SpriteBatch batch;
    private World world;
    private FrameBuffer target;
    private final Map<DebugLayers, RenderSystem> renderers = new HashMap<>();
    private RenderSystem renderer;
    private TriggerDebugRenderSystem debug;
    private TiledMap map;
    private int ox = 13, oy = 9, side = 128; // physical target coordinates, not window coordinates

    public static void main(String[] args) {
        for (String profile : args.length == 0 ? List.of("GL20", "GL30") : List.of(args)) {
            runContext(switch (profile) {
                case "GL20" -> false;
                case "GL30" -> true;
                default -> throw new IllegalArgumentException("Expected GL20 or GL30, not " + profile);
            });
        }
        System.out.println("LightingRenderTest: " + checks + " checks passed");
    }

    private static void runContext(boolean gl30) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.disableAudio(true);
        config.setInitialVisible(false);
        config.setWindowedMode(256, 256);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0); // no default stencil
        if (gl30) { config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2); }
        new Lwjgl3Application(new LightingRenderTest(gl30), config);
        if (failure != null) { throw new AssertionError("LightingRenderTest (GL" + (gl30 ? "30" : "20") + ")", failure); }
    }

    @Override public void create() {
        float oldUnit = GameConsts.UNIT_SCALE;
        try {
            GameConsts.UNIT_SCALE = 1;
            require((Gdx.gl30 != null) == coreProfile, "requested libGDX GL backend is active");
            if (coreProfile || org.lwjgl.opengl.GL.getCapabilities().OpenGL32) {
                int profile = integer(org.lwjgl.opengl.GL32.GL_CONTEXT_PROFILE_MASK);
                int expected = coreProfile ? org.lwjgl.opengl.GL32.GL_CONTEXT_CORE_PROFILE_BIT
                    : org.lwjgl.opengl.GL32.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT;
                require((profile & expected) != 0, "unexpected GL context profile mask 0x" + Integer.toHexString(profile));
            }
            System.out.println("LightingRenderTest: " + Gdx.gl.glGetString(GL_RENDERER) + ", " + Gdx.gl.glGetString(GL_VERSION)
                + ", Gdx GL30=" + (Gdx.gl30 != null) + ", native VAOs="
                + (GL.getCapabilities().OpenGL30 || GL.getCapabilities().GL_ARB_vertex_array_object)
                + ", samplers=" + hasSamplers() + ", MRT=" + dualOutputs() + ", dual indexed outputs=" + dualIndexedOutputs()
                + ", independent blend funcs/equations=" + independentBlend());
            Box2D.init();
            world = new World(new Vector2(), true);
            batch = new SpriteBatch();
            view.update(128, 128, true);
            view.setScreenBounds(ox, oy, side, side);
            target = new FrameBuffer(Pixmap.Format.RGBA8888, 288, 276, false);
            var assets = new AssetService(path -> { throw new AssertionError("Unexpected game asset: " + path); }) {
                @Override public <T> T get(Class<T> type, String id) { return type.cast(atlas); }
            };
            // Each renderer owns a fixed startup configuration; compare them against the same scene.
            for (var layers : List.of(OFF, RAW, OUTLINES, BOTH)) {
                renderers.put(layers, new RenderSystem(batch, view, camera, assets, layers));
            }
            renderer = renderers.get(OFF);
            engine.addSystem(renderer);
            target.begin();
            clear(BAR);
            integrationAndOrder(assets);
            vertexInputs();
            clear(BAR);
            shadowsAndComposition();
            collisionView();
            largeCollisionRings();
            pulseAndProjection();
            stateAndFailure();
            gameplayOverlay(assets);
            require(Gdx.gl.glGetError() == GL_NO_ERROR, "driver error at teardown");
        } catch (Throwable ex) { failure = ex; }
        finally {
            GameConsts.UNIT_SCALE = oldUnit;
            renderers.values().forEach(RenderSystem::dispose);
            if (target != null) { target.end(); target.dispose(); }
            if (debug != null) { debug.dispose(); }
            if (map != null) { map.dispose(); }
            if (batch != null) { batch.dispose(); }
            textures.forEach(Texture::dispose);
            if (world != null) { world.dispose(); }
            // LWJGL3 frees this callback during shutdown but GLFW retains its address for the
            // next initializeGlfw call. Unregister while it is live before starting our GL30 run.
            org.lwjgl.glfw.GLFW.glfwSetErrorCallback(null);
            Gdx.app.exit();
        }
    }

    private void gameplayOverlay(AssetService assets) throws ReflectiveOperationException {
        // Empty black world isolates the unlit font pixels; keep the existing nonzero viewport offset.
        var overlay = new Engine();
        var render = new RenderSystem(batch, view, camera, assets, OFF);
        var combat = new CombatSystem(world, null);
        var clock = new WorldTimeSystem(24);
        overlay.addSystem(clock);
        overlay.addSystem(combat);
        overlay.addSystem(new TiledSystem(null, world, null));
        overlay.addSystem(render);
        render.gameplayFrame(world, AMBIENT, false, 0);
        try {
            var gl = Gdx.gl;
            int active = integer(GL_ACTIVE_TEXTURE);
            gl.glActiveTexture(GL_TEXTURE1);
            try {
                var before = snapshot();
                withSmallUnpackBuffer("first day HUD font upload", () -> overlay.update(0));
                checkState(before, "HUD draw and upload");
                require(glyphs(0, 110, 60, 18, Color.WHITE).pixels() > 0, "Clock in the top viewport strip");
                render.gameplayFrame(world, AMBIENT, true, 0);
                overlay.update(0);
                checkState(before, "Night HUD draw");
            } finally { gl.glActiveTexture(active); }
            require(glyphs(0, 110, 60, 18, Color.WHITE).pixels() > 0, "Unlit clock in the top viewport strip");

            for (var direction : Facing.Direction.values()) {
                var victim = new Entity().add(new Health(100)).add(transform(62, 48, 4, 4, 0));
                overlay.addEntity(victim);
                DamageFeedbackProbe.hit(victim, 10, direction);
                overlay.update(0);
                var fresh = glyphs(32, 52, 64, 54, Color.GREEN);
                require(fresh.pixels() > 0 && Math.abs(fresh.x() - 64) <= 3 && fresh.y() > 52,
                    direction + ": green number starts centered above the victim after night lighting");
                // The victim can move before the next frame; the number stays anchored to the hit.
                Transform.MAPPER.get(victim).getPosition().setZero();
                combat.update(.325f);
                overlay.update(0);
                var faded = glyphs(32, 52, 64, 54, Color.GREEN);
                float dx = faded.x() - fresh.x(), dy = faded.y() - fresh.y();
                require(faded.pixels() > 0 && faded.brightness() < fresh.brightness() * .6f && dy > 7,
                    direction + ": number rises and fades from the original hit position: " + fresh + " -> " + faded);
                require(switch (direction) {
                    case LEFT -> dx < -15;
                    case RIGHT -> dx > 15;
                    // Fading changes which edge texels pass the glyph mask.
                    case UP -> Math.abs(dx) <= 2.5f && dy >= 18;
                    case DOWN -> Math.abs(dx) <= 2.5f && dy <= 12;
                }, direction + ": drift follows the committed facing (" + dx + ", " + dy + ")");
                combat.update(.33f);
                overlay.update(0);
                require(DamageFeedbackProbe.count(victim) == 0 && glyphs(32, 52, 64, 54, Color.GREEN).pixels() == 0,
                    "Expired number is removed and leaves no framebuffer trail");
                overlay.removeEntity(victim);
            }

            var wounded = new Entity().add(new Health(100)).add(transform(34, 48, 4, 4, 0));
            Health.MAPPER.get(wounded).damage(40);
            var killed = new Entity().add(new Health(5)).add(transform(90, 48, 4, 4, 0));
            overlay.addEntity(wounded);
            overlay.addEntity(killed);
            DamageFeedbackProbe.hit(wounded, 10, Facing.Direction.UP);
            DamageFeedbackProbe.hit(killed, 10, Facing.Direction.UP);
            overlay.update(0);
            require(glyphs(16, 52, 36, 32, Color.YELLOW).pixels() > 0, "Half health is yellow");
            require(glyphs(74, 52, 36, 32, Color.RED).pixels() > 0, "Killing number is red and still visible");
            overlay.removeSystem(combat);
            overlay.update(0);
            require(DamageFeedbackProbe.count(wounded) == 0 && DamageFeedbackProbe.count(killed) == 0,
                "Removing combat retires feedback even if the rendering system survives");
            var font = (BitmapFont) field(field(render, "hud"), "font");
            int handle = font.getRegion().getTexture().getTextureObjectHandle();
            gl.glActiveTexture(GL_TEXTURE0);
            gl.glBindTexture(GL_TEXTURE_2D, handle);
            render.dispose();
            require(!gl.glIsTexture(handle) && integer(GL_TEXTURE_BINDING_2D) == 0,
                "HUD disposal releases its font texture without restoring the deleted binding");
        } finally {
            overlay.removeAllSystems();
            render.dispose();
        }
    }

    private Glyphs glyphs(int x, int y, int width, int height, Color tint) {
        var image = Pixmap.createFromFrameBuffer(ox + x, oy + y, width, height);
        try {
            int pixels = 0, xSum = 0, ySum = 0, brightness = 0;
            for (int iy = 0; iy < height; iy++) {
                for (int ix = 0; ix < width; ix++) {
                    int rgba = image.getPixel(ix, iy);
                    int r = red(rgba), g = rgba >>> 16 & 255, b = blue(rgba);
                    if ((tint.r == 0 ? r < 100 : r > 90) && (tint.g == 0 ? g < 32 : g > 90)
                        && (tint.b == 0 ? b < 32 : b > 90)) {
                        pixels++;
                        xSum += x + ix;
                        ySum += y + iy;
                        brightness = Math.max(brightness, Math.max(r, g));
                    }
                }
            }
            return new Glyphs(pixels, (float) xSum / pixels, (float) ySum / pixels, brightness);
        } finally { image.dispose(); }
    }

    private record Glyphs(int pixels, float x, float y, int brightness) { }

    private void integrationAndOrder(AssetService assets) throws Exception {
        // 8px strips overlap at different depths. Adjacent probes independently expose each pass.
        final int ground = 0x96b4d2ff, arrow = 0xd09010ff, character = 0x3050d0ff;
        final int object = 0x40c058ff, front = 0xc060b0ff;
        map = new TiledMap();
        var bg = new TiledMapTileLayer(1, 1, 128, 128);
        bg.setCell(0, 0, tile(128, 112, ground)); // uncovered top strip
        var split = new MapLayer(); split.setName("Objects");
        var fg = new TiledMapTileLayer(16, 16, 8, 8);
        fg.setCell(6, 5, tile(8, 8, front));
        map.getLayers().add(bg); map.getLayers().add(split); map.getLayers().add(fg);
        renderers.values().forEach(r -> r.setMap(map));
        atlas.addRegion("arrow", texture(40, 8, arrow));
        var bare = new CombatWeapon.Weapon("unarmed", "NONE", null, null);
        var actor = sprite(36, 40, 16, 8, 1, WHITE);
        var base = texture(16, 8, character);
        for (var action : List.of("idle", "walk", "death", "slash", "jab", "shot")) {
            for (var dir : Facing.Direction.values()) {
                for (int frame = 0; frame < 8; frame++) { atlas.addRegion(action + "_" + dir.atlasKey, base).index = frame; }
            }
        }
        var anim = new Animation2D(CharacterAssets.CHARACTER.id(), "", Animation2D.AnimationType.IDLE, Animation.PlayMode.LOOP, 1, .1f);
        anim.setAnimation(new Animation<>(.1f, base), Facing.Direction.DOWN);
        actor.add(new CompositeCharacter(16, 8)).add(anim).add(new Loadout(new Loadout.Equipment(null, null, null, null),
            null, Map.of("unarmed", bare), eq -> {}));
        engine.addEntity(actor);
        engine.addEntity(sprite(42, 40, 18, 8, 2, object)); // covers character's right edge and arrow
        var translucent = sprite(50, 114, 8, 8, 1, 0x80e0a080);
        engine.addEntity(translucent);

        var combat = new CombatSystem(world, new ProjectilesData.Projectile("arrow", 1, 5,
            CollisionData.ellipse(new CharacterBounds(0, 0, 2, 2)), 0, 0, 4, 0));
        engine.addSystem(combat);
        var bow = new CombatWeapon.Weapon("bow", "bow", new CombatWeapon.Attack("bow", 1, 0, 1,
            new Animation2D.Attack("shot", 1, .1f, 0, ""), "", null, null, null), null);
        var aim = new BowAim(8, 8);
        var start = transform(24, 44, 0, 0, 0);
        var owner = new Entity().add(start).add(new Facing(Facing.Direction.RIGHT)).add(new Combat()).add(anim);
        aim.start(bow, start, Facing.Direction.RIGHT);
        // Launch a production flight; do not emulate projectiles with regular Graphic entities.
        var launch = CombatSystem.class.getDeclaredMethod("launch", Entity.class, BowAim.class);
        launch.setAccessible(true); launch.invoke(combat, owner, aim);
        aim.start(bow, transform(102.5f, 84.5f, 0, 0, 0), Facing.Direction.RIGHT);
        engine.addEntity(new Entity().add(aim));
        var lamp = lamp(LX, LY, "#ff8040", 64, .65f, null);
        var marker = (QueryCallback) fixture -> true;
        var query = World.class.getDeclaredField("queryCallback"); query.setAccessible(true);
        world.QueryAABB(marker, 0, 0, 1, 1); // Box2D retains its last QueryAABB callback, including empty queries.
        int[] xs = {16, 27, 38, 44, 51};
        int[] colors = {ground, arrow, character, object, front};
        int legacyAlpha;
        clear(0);
        interruptFirstCharacterDraw();
        // The compositor target now exists, but this frame must still upload the crosshair pixmap.
        withSmallUnpackBuffer("crosshair upload", () -> engine.update(0));
        near(pixel(106, 84), WHITE, 0, "crosshair uploaded with caller PBO bound");
        for (int i = 0; i < xs.length; i++) { near(pixel(xs[i], 44), colors[i], 0, "standalone depth " + i); }
        legacyAlpha = pixel(53, 117);
        require(alpha(legacyAlpha) > 0 && alpha(legacyAlpha) < 255, "fixture really has fractional alpha");
        clear(BAR);
        try (var probe = new TargetProbe()) {
            draw(false, 0);
            require(probe.targets.isEmpty() && probe.quadDraws == 0 && field(renderer, "lighting") == null
                && field(renderer, "outlines") == null && query.get(world) == marker,
                "first day: no lighting allocation, pass or fixture query");
        }
        for (int i = 0; i < xs.length; i++) { near(pixel(xs[i], 44), colors[i], 0, "gameplay daylight vs unlit order " + i); }
        int dayAlpha = pixel(53, 117), empty = pixel(118, 119);
        near(dayAlpha, legacyAlpha, 1, "daylight preserves the unlit fractional-alpha blend");
        require((empty >>> 8) == 0, "gameplay starts with clean black uncovered pixels");
        near(pixel(106, 84), WHITE, 0, "day crosshair arm");
        bars(); save("day");

        try (var probe = new TargetProbe()) {
            for (var layers : List.of(RAW, OUTLINES, BOTH)) {
                draw(false, 0, layers);
                require(probe.quadDraws == 0 && field(renderer, "lighting") == null && query.get(world) == marker,
                    "day skips the night pass: " + layers);
                near(pixel(51, 44), front, 0, "day retains world art: " + layers);
                near(pixel(53, 117), dayAlpha, 0, "day retains fractional alpha: " + layers);
                near(pixel(100, 44), layers.lightingOutlines() ? RADIUS : ground, 1, "daytime emitter radius: " + layers);
                bars();
            }
        }
        view.setScreenBounds(ox, oy, 0, side);
        draw(true, 0);
        require(field(renderer, "lighting") == null && query.get(world) == marker, "zero area skips night allocation and query");
        near(pixel(16, 44), ground, 0, "zero area leaves frame untouched");
        view.setScreenBounds(ox, oy, side, side);
        engine.removeEntity(lamp);
        // Fail the first lazy setup after the FBO and shader pair exist; leave a usable day renderer
        // and no partially constructed light instance. A subsequent night must succeed normally.
        try (var probe = new TargetProbe()) {
            probe.failLink = true;
            try { draw(true, 0); throw new AssertionError("failed light program link silently accepted"); }
            catch (IllegalStateException expected) {
                require(probe.linkChecks > 0 && expected.getMessage() != null && !expected.getMessage().isBlank(), "actionable program link failure");
            }
            require(field(renderer, "lighting") == null, "failed lazy setup not published");
            probe.requireColorTargetAllocation();
            probe.requireReleased();
        }
        withSmallUnpackBuffer("first night lightmap", () -> draw(true, 0));
        require(query.get(world) == marker, "ambient-only: no fixture query");
        for (int i = 0; i < xs.length; i++) { near(pixel(xs[i], 44), modulate(colors[i], DIM), 2, "ambient world pass " + i); }
        near(pixel(53, 117), modulate(dayAlpha, DIM), 2, "translucent art modulation");
        near(pixel(106, 84), WHITE, 0, "crosshair follows modulation");
        near(pixel(110, 84), modulate(ground, DIM), 2, "crosshair hole exposes dark world");
        if (hasSamplers()) { hostileSampler(assets, actor, character, ground); }
        // Real trigger overlay following RenderSystem, as in GameScreen. Don't depend on GL line tie rules.
        var sensor = box(78, 81, 5, 3, StaticBody, true);
        debug = new TriggerDebugRenderSystem(world, camera);
        debug.priority = 1; // Keep the overlay above every renderer fixture.
        engine.addSystem(debug);
        view.apply(); // debug systems draw to the caller's applied viewport
        var edge = new EdgeShape(); edge.set(-38, 0, 20, 0);
        var underAim = body(108, 84.5f, 0, StaticBody, edge, false);
        for (var layers : List.of(OFF, RAW, OUTLINES, BOTH)) {
            draw(true, 0, layers);
            int cyan = 0;
            for (int y = 76; y <= 86; y++) { for (int x = 71; x <= 85; x++) { if (pixel(x, y) == 0x00ffffff) { cyan++; } } }
            require(cyan >= 16, layers + ": actual trigger debug outline stays cyan: " + cyan);
            near(pixel(106, 84), WHITE, 0, layers + ": aim covers world and debug lines");
            near(pixel(16, 44), layers.rawLightMap() ? DIM : modulate(ground, DIM), 2, "night surface: " + layers);
            if (layers.lightingOutlines()) {
                near(pixel(110, 84), SOLID, 1, "open aim center exposes the solid edge underneath");
            }
        }
        engine.removeSystem(debug);
        world.destroyBody(underAim); world.destroyBody(sensor);
        engine.addEntity(lamp);
        draw(true, 0);
        require(query.get(world) != marker, "visible night emitter queries live Box2D world");
        for (int i = 0; i < xs.length; i++) {
            require(red(pixel(xs[i], 44)) > red(modulate(colors[i], DIM)) + 8, "emitter illuminates world layer " + i);
        }
        save("night"); bars();
        var solid = box(64, LY, 4, 8, DynamicBody, false);
        draw(true, 0, RAW);
        near(pixel(36, 44), 0xc66332ff, 3, "raw RGB sum at emitter, not modulated character art");
        near(pixel(64, 44), DIM, 1, "raw map shows shadow without leaking zero mask alpha");
        near(pixel(118, 119), DIM, 1, "raw ambient even without underlying art");
        var second = lamp(LX, LY, "#0040ff", 10, .5f, null);
        draw(true, 0, RAW);
        near(pixel(36, 44), 0xc683b2ff, 3, "raw view includes both emitters plus ambient");
        draw(true, 0, BOTH);
        near(pixel(36, 44), 0xc683b2ff, 3, "raw RGB with outlines enabled");
        near(pixel(100, 44), RADIUS, 1, "emitter outline above the raw map");
        engine.removeEntity(second);
        draw(true, 0, RAW);
        save("debug-raw"); bars();
        world.destroyBody(solid);
        aim.cancel(); engine.removeEntity(translucent);
        draw(true, 0);
        int once = pixel(44, 44);
        draw(true, 0);
        near(pixel(44, 44), once, 0, "no compounded night modulation");
        near(pixel(53, 117), empty, 0, "removed translucent sprite leaves no trail");
        world.QueryAABB(marker, 0, 0, 1, 1);
        try (var probe = new TargetProbe()) {
            draw(false, 0);
            require(probe.quadDraws == 0 && probe.targets.isEmpty() && query.get(world) == marker,
                "return to day skips lighting passes, allocations and fixture queries");
        }
        near(pixel(106, 84), ground, 0, "day has no stale night/overlay");
        near(pixel(53, 117), empty, 0, "return to day clears uncovered pixels");
        bars(); engine.removeAllEntities(); engine.removeSystem(combat);
        renderers.values().forEach(r -> r.setMap(null));
    }

    private static boolean hasSamplers() {
        return GL.getCapabilities().OpenGL33 || GL.getCapabilities().GL_ARB_sampler_objects;
    }

    /** A sampler (not a texture parameter) requiring absent mip levels makes a level-zero-only texture incomplete. */
    private void hostileSampler(AssetService assets, Entity actor, int character, int ground) {
        var gl = Gdx.gl;
        var cells = new CharacterCompositor(assets);
        int active = integer(GL_ACTIVE_TEXTURE);
        gl.glActiveTexture(GL_TEXTURE0); int old0 = integer(GL_SAMPLER_BINDING);
        gl.glActiveTexture(GL_TEXTURE1); int old1 = integer(GL_SAMPLER_BINDING);
        int mip = glGenSamplers(), other = glGenSamplers();
        try {
            glSamplerParameteri(mip, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glBindSampler(0, mip); glBindSampler(1, other); // enter on unit 1; the renderer samples unit 0
            draw(true, 0);
            near(pixel(16, 44), modulate(ground, DIM), 2, "mipmapped caller sampler: world and night modulation");
            near(pixel(38, 44), modulate(character, DIM), 2, "mipmapped caller sampler: composed character at night");
            checkSamplers(mip, other, "night draw");

            // Exercise direct composition too: it must not rely on the outer renderer having unbound the sampler.
            cells.compose(List.of(actor), camera);
            checkSamplers(mip, other, "standalone character draw");
            var cell = cells.region(actor);
            int inspect = gl.glGenFramebuffer();
            try {
                gl.glBindFramebuffer(GL_FRAMEBUFFER, inspect);
                gl.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, cell.getTexture().getTextureObjectHandle(), 0);
                require(gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "read completed character cell");
                int x = (int)((cell.getU() + cell.getU2()) * .5f * cell.getTexture().getWidth());
                int y = (int)((cell.getV() + cell.getV2()) * .5f * cell.getTexture().getHeight());
                near(read(x, y), character, 0, "mipmapped caller sampler: standalone character cell");
            } finally {
                gl.glBindFramebuffer(GL_FRAMEBUFFER, target.getFramebufferHandle()); gl.glDeleteFramebuffer(inspect);
            }
            try (var probe = new TargetProbe()) {
                probe.interrupt = Draw.QUAD; // ambient only: the real draw that sampled the lightmap
                try { draw(true, 0); throw new AssertionError("night modulation wasn't interrupted"); }
                catch (DrawFailure expected) { checkSamplers(mip, other, "interrupted night modulation"); }
                probe.interrupt = Draw.SPRITE;
                try { cells.compose(List.of(actor), camera); throw new AssertionError("character draw wasn't interrupted"); }
                catch (DrawFailure expected) { checkSamplers(mip, other, "interrupted standalone character draw"); }
            }
        } finally {
            glBindSampler(0, old0); glBindSampler(1, old1); gl.glActiveTexture(active);
            glDeleteSamplers(mip); glDeleteSamplers(other); cells.dispose();
        }
    }

    private static void checkSamplers(int mip, int other, String where) {
        require(integer(GL_ACTIVE_TEXTURE) == GL_TEXTURE1 && integer(GL_SAMPLER_BINDING) == other,
            where + ": active unit or unrelated unit-1 sampler changed");
        Gdx.gl.glActiveTexture(GL_TEXTURE0);
        require(integer(GL_SAMPLER_BINDING) == mip && glGetSamplerParameteri(mip, GL_TEXTURE_MIN_FILTER) == GL_LINEAR_MIPMAP_LINEAR,
            where + ": lost or modified caller's mipmapped sampler");
        Gdx.gl.glActiveTexture(GL_TEXTURE1);
        require(Gdx.gl.glGetError() == GL_NO_ERROR, where + ": sampler driver error");
    }

    private void interruptFirstCharacterDraw() {
        // Exercise both the lazy offscreen texture allocation and nested RenderState unwinding,
        // before any world sprite, crosshair or lighting pass could hide a broken restoration.
        try (var caller = new CallerVertexInput(); var probe = new TargetProbe()) {
            withSmallUnpackBuffer("nested character target", () -> {
                probe.interrupt = Draw.SPRITE;
                try { engine.update(0); throw new AssertionError("no character draw"); }
                catch (DrawFailure expected) {
                    require(probe.interruptedTarget != 0 && probe.interruptedTarget != target.getFramebufferHandle(),
                        "interrupted inside the character compositor");
                }
            });
            caller.assertIntact("interrupted character draw");
            require(integer(GL_FRAMEBUFFER_BINDING) == target.getFramebufferHandle(), "returned from character FBO");
        }
    }

    private void vertexInputs() {
        // Empty frame: the night quad is the only draw that could overwrite the caller's layout.
        checkVertexInputs(true);
        // Two different textures force a real indexed SpriteBatch flush *inside* the world pass.
        engine.addEntity(sprite(24, 28, 4, 4, 1, WHITE));
        engine.addEntity(sprite(32, 28, 4, 4, 2, BAR));
        try { checkVertexInputs(false); }
        finally { engine.removeAllEntities(); }
    }

    private void checkVertexInputs(boolean night) {
        var point = night ? Draw.QUAD : Draw.SPRITE;
        for (boolean interrupt : new boolean[]{false, true}) {
            String where = point + (interrupt ? " interrupted" : " completed");
            try (var caller = new CallerVertexInput(); var probe = new TargetProbe()) {
                if (interrupt) { probe.interrupt = point; }
                try {
                    draw(night, 0);
                    require(!interrupt, where + ": draw not interrupted");
                } catch (DrawFailure expected) {
                    require(interrupt && expected.draw == point, where + ": wrong injection site");
                }
                require(night ? probe.quadDraws > 0 && probe.spriteDraws == 0 : probe.spriteDraws > 0 && probe.quadDraws == 0,
                    where + ": expected an isolated real draw");
                caller.assertIntact(where);
            }
        }
    }

    private void shadowsAndComposition() {
        engine.addEntity(sprite(-128, -128, 384, 384, 0, WHITE));
        var lamp = lamp(LX, LY, "#ff8040", 64, .65f, null);
        // Body origin deliberately does not equal sprite center; its solid overlaps the source.
        var self = box(LX + 2, LY, 4, 4, StaticBody, false);
        self.setUserData(lamp); lamp.add(new Physic(self, new Vector2()));
        var blocker = box(LX + 12, LY, 4.5f, 6, DynamicBody, false);
        var filter = blocker.getFixtureList().first().getFilterData();
        filter.maskBits = 0; filter.categoryBits = 0; blocker.getFixtureList().first().setFilterData(filter);
        blocker.setActive(false);
        draw(true, 0);
        near(pixel(36, 44), 0xc66332ff, 3, "RGB sum at light center, own filled solid excluded");
        int inner = red(pixel(52, 44)), mid = red(pixel(68, 44)), outer = red(pixel(88, 44));
        require(198 > inner && inner > mid + 8 && mid > outer + 8 && outer >= red(DIM), "radial falloff on unblocked side");
        near(pixel(100, 44), DIM, 2, "at light radius"); near(pixel(105, 44), DIM, 1, "outside radius");
        near(pixel(36, 76), pixel(68, 44), 2, "world-space circular light");
        int[] lit = centerRow(); // Same-pixel, unoccluded radial light plus ambient, not a constant bright reference.
        finiteShapeShadows(blocker, lit);
        independentLightMasks(lamp, self, lit);
        scaledTailCap();
    }

    private void finiteShapeShadows(Body blocker, int[] lit) {
        double angle = Math.PI / 6;
        blocker.setActive(true); blocker.setTransform(LX + 12, LY, (float)angle);
        draw(true, 0);
        // Rotated 9x12 rectangle: maximum vertex chord 15, not an edge or the world AABB diagonal.
        double exit = LX + 12 + 4.5 / Math.cos(angle), length = .75 * 15;
        near(pixel(40, 44), lit[40], 1, "ahead of rotated polygon");
        near(pixel(52, 44), DIM, 1, "deep in rotated zero-filter solid, no fade from entry face");
        fade(54, lit[54], (54.5 - exit) / length, "contact behind polygon");
        fade(56, lit[56], (56.5 - exit) / length, "early smoothstep, not linear");
        fade(59, lit[59], (59.5 - exit) / length, "polygon chord controls midfade");
        near(pixel(68, 44), lit[68], 1, "full radial baseline regained well before rim");
        near(pixel(36, 74), lit[66], 2, "unoccluded flank"); save("shadow");
        int single = pixel(60, 44);
        blocker.createFixture(blocker.getFixtureList().first().getShape(), 1); // coincident parts of the same body
        draw(true, 0); near(pixel(60, 44), single, 1, "duplicate parts do not compound partial darkness");
        blocker.setTransform(LX + 12, LY + 20, .35f);
        draw(true, 0); near(pixel(60, 44), lit[60], 1, "moving body vacates its shadow immediately");
        world.destroyBody(blocker);

        int offAxis = pixel(60, 50);
        var circle = new CircleShape(); circle.setPosition(new Vector2(0, 6)); circle.setRadius(6);
        var offset = body(LX + 10, LY, -(float)Math.PI / 2, KinematicBody, circle, false);
        // World center LX+16, exit LX+22, tail length 0.75 * diameter = 9.
        draw(true, 0); near(pixel(57, 44), DIM, 1, "transformed offset circle interior");
        fade(61, lit[61], 3.0 / 9, "circle fade measured from curved exit");
        // Intersect the ray (24,6) with the rear arc, rather than the far side of a bounding box.
        double distance = Math.hypot(24, 6), perpendicular = 16 * 6 / distance;
        double arcExit = 16 * 24 / distance + Math.sqrt(36 - perpendicular * perpendicular);
        near(pixel(60, 50), faded(offAxis, (distance - arcExit) / 9), 8, "off-axis circular tail (subpixel arc approximation)");
        near(pixel(69, 44), lit[69], 1, "circle shadow ends inside light disk");
        offset.getFixtureList().first().setSensor(true);
        draw(true, 0); near(pixel(57, 44), lit[57], 1, "sensor-only circle does not shadow");
        world.destroyBody(offset);

        // After rotation the central child is vertical at LX+5, length 8. The other two are 23 long.
        var chain = new ChainShape(); chain.createChain(new float[]{4, 18, 4, -5, -4, -5, -4, 18});
        var open = body(LX, LY, (float)Math.PI / 2, StaticBody, chain, false);
        draw(true, 0); fade(42, lit[42], 1. / 6, "chain contact");
        fade(44, lit[44], .5, "chain fade uses this child's length");
        near(pixel(49, 44), lit[49], 1, "past short chain child shadow");
        near(pixel(17, 44), lit[17], 1, "open chain has no synthetic closing segment");
        world.destroyBody(open);
        var loop = new ChainShape(); loop.createLoop(new float[]{-5,-7, 5,-7, 5,7, -5,7});
        var closed = body(LX, LY, 0, StaticBody, loop, false);
        draw(true, 0); near(pixel(36, 44), lit[36], 1, "closed chain is hollow");
        fade(27, lit[27], 4. / 10.5, "loop closing edge casts to the left");
        world.destroyBody(closed);
    }

    private void independentLightMasks(Entity lamp, Body self, int[] lit) {
        var edge = new EdgeShape(); edge.set(-200, 0, 200, 0);
        var wall = body(LX + .25f, LY, (float)Math.PI / 2, StaticBody, edge, false);
        draw(true, 0);
        fade(37, lit[37], .75 / 16, "near-source long edge contact");
        fade(40, lit[40], 3.75 / 16, "long edge limited by quarter radius");
        fade(44, lit[44], 7.75 / 16, "long edge midfade");
        near(pixel(54, 44), lit[54], 1, "long edge no longer shadows rest of disk");
        near(pixel(6, 44), lit[6], 1, "near edge doesn't suppress source");
        int warmEast = pixel(44, 44), warmInsideSelf = pixel(40, 44);
        // Overlapping, unequal tails: the darkest caster changes along the same ray.
        var shortEdge = new EdgeShape(); shortEdge.set(0, -4, 0, 4);
        var shortWall = body(LX + 4, LY, 0, StaticBody, shortEdge, false);
        wall.setActive(false); draw(true, 0);
        fade(41, lit[41], 1. / 6, "standalone edge length sets its fade");
        fade(44, lit[44], 4. / 6, "short edge midfade");
        int darkerAt41 = pixel(41, 44);
        wall.setActive(true); draw(true, 0);
        near(pixel(41, 44), darkerAt41, 1, "short edge dominates near contact");
        near(pixel(44, 44), warmEast, 1, "long edge dominates farther from contact");
        world.destroyBody(shortWall);

        var blue = lamp(56.5f, LY, "#4080ff", 64, .8f, null);
        draw(true, 0); int both = pixel(44, 44);
        near(pixel(40, 44), warmInsideSelf, 2, "warm self exclusion is not inherited by cool light");
        engine.removeEntity(lamp); draw(true, 0);
        int coolEast = pixel(44, 44);
        require(blue(coolEast) > 170, "cool light is unoccluded east of wall");
        int expectedBoth = addLights(warmEast, coolEast);
        near(both, expectedBoth, 2, "warm midfade modulates only warm contribution, not cool light or ambient");
        engine.addEntity(lamp); draw(true, 0);
        near(pixel(44, 44), expectedBoth, 2, "same sum with cool then warm light");
        world.destroyBody(wall);
        draw(true, 0); near(pixel(44, 44), addLights(lit[44], coolEast), 2, "unblocked lights add at overlap");
        var lid = box(LX, LY, 3, 4, StaticBody, false);
        draw(true, 0); near(pixel(44, 44), coolEast, 2, "filled solid covers one origin, not other emitter");
        engine.removeEntity(blue);
        lid.setTransform(LX + 3, LY, 0);
        draw(true, 0); near(pixel(6, 44), DIM, 1, "another filled boundary touching exact source fully blocks it");
        world.destroyBody(lid); world.destroyBody(self); lamp.remove(Physic.class);
        var extra = lamp(LX, LY, "#4080ff", 64, 1, null);
        draw(true, 0); near(pixel(36, 44), 0xffe3ffff, 2, "clamped ambient plus both unoccluded RGB contributions");
        engine.removeEntity(extra); engine.removeEntity(lamp);
        draw(true, 0); near(pixel(36, 44), DIM, 1, "retired emitters/fixtures leave no ghost");
    }

    private void scaledTailCap() {
        float previousScale = GameConsts.UNIT_SCALE;
        try {
            GameConsts.UNIT_SCALE = .5f;
            var huge = lamp(LX, LY, "#FFFFFF", Float.MAX_VALUE, .5f, null);
            draw(true, 0); int[] clearDisk = centerRow();
            var nearWall = box(LX + 3, LY, 1, 60, StaticBody, false);
            draw(true, 0);
            require(red(pixel(10, 44)) >= red(DIM) + 125, "enormous disk remains lit on the near side");
            near(pixel(39, 44), DIM, 1, "contact on enormous disk");
            fade(46, clearDisk[46], .5, "six world units past exit: halfway to 24 * UNIT_SCALE");
            near(pixel(54, 44), clearDisk[54], 1, "scaled absolute cap ends shadow");
            near(pixel(120, 44), clearDisk[120], 1, "enormous disk recovered through far viewport edge");
            // The visible half-diagonal is now only ~22.6. Clipping the fixture query to it
            // must not substitute .25 * 22.6 for the cap based on the actual disk radius.
            camera.position.set(LX, LY, 0); camera.zoom = .25f;
            draw(true, 0);
            near(pixel(104, 64), faded(clearDisk[46], 6.125 / 12), 4, "zoomed view does not shorten the tail");
            world.destroyBody(nearWall); engine.removeEntity(huge);
        } finally {
            camera.zoom = 1; camera.position.set(64, 64, 0); GameConsts.UNIT_SCALE = previousScale;
        }
    }

    private void collisionView() {
        var light = lamp(60.5f, 54.5f, "#808080", 12, .75f, new EmissiveData.Pulse(4, .5f, 0));
        Transform.MAPPER.get(light).getScaling().x = 2; // actual center now 68.5,54.5
        var poly = box(48, 44, 6, 2, DynamicBody, false);
        poly.setTransform(48, 44, (float)Math.PI / 2);
        var circle = new CircleShape(); circle.setPosition(new Vector2(6, 0)); circle.setRadius(4);
        var disk = body(35, 40, (float)Math.PI / 2, KinematicBody, circle, false);
        var chain = new ChainShape(); chain.createChain(new float[]{0,0, 8,0, 8,8});
        var open = body(42, 59, 0, StaticBody, chain, false);
        var sensor = box(68, 44, 3, 3, StaticBody, true);
        var inactive = box(76, 44, 3, 3, StaticBody, false); inactive.setActive(false);
        camera.position.set(60, 50, 0); camera.zoom = .5f; // 2px/unit, world origin of viewport = 28,18
        draw(false, 1, OUTLINES);
        near(pixel(36, 52), SOLID, 1, "rotated polygon edge at world 46,44");
        near(pixel(40, 52), WHITE, 0, "polygon is outlined, not filled");
        near(pixel(22, 56), SOLID, 1, "rotated offset circle right edge at world 39,46");
        near(pixel(36, 82), SOLID, 1, "open chain bottom segment");
        near(pixel(36, 90), WHITE, 0, "no fabricated closing edge on open chain");
        near(pixel(74, 52), WHITE, 0, "sensor not outlined");
        near(pixel(90, 52), WHITE, 0, "inactive body not outlined");
        near(pixel(117, 73), RADIUS, 1, "ring at peak (scaled sprite center + 18 world units)");
        save("debug-collision");
        draw(true, 3, OUTLINES);
        near(pixel(93, 73), RADIUS, 1, "ring moves to pulsed trough, plus 6 world units");
        near(pixel(117, 73), DIM, 1, "no stale peak outline on lit world");
        near(pixel(36, 52), SOLID, 1, "night does not dim solid outline");
        draw(false, 3);
        near(pixel(93, 73), WHITE, 0, "Off removes the ring");
        near(pixel(36, 52), WHITE, 0, "Off removes solid outlines");
        world.destroyBody(poly); world.destroyBody(disk); world.destroyBody(open);
        world.destroyBody(sensor); world.destroyBody(inactive); engine.removeEntity(light);
        camera.zoom = 1; camera.position.set(64, 64, 0);
    }

    private void largeCollisionRings() {
        float radius = 1_000_000;
        double angle = 31.5 * (2 * Math.PI / 256); // halfway between vertices of the former capped polygon
        camera.position.set((float)(radius * Math.cos(angle)), (float)(radius * Math.sin(angle)), 0);
        var light = lamp(0, 0, "#ffffff", radius, 1, null);
        var circle = new CircleShape(); circle.setRadius(radius);
        var solid = body(20, 0, 0, StaticBody, circle, false);
        batch.maxSpritesInBatch = 0;
        draw(false, 0, OUTLINES);
        for (int x : new int[]{32, 64, 96}) {
            double worldX = camera.position.x - 64 + x + .5;
            int y = (int)Math.floor(Math.sqrt(radius * (double)radius - worldX * worldX) - camera.position.y + 64);
            near(pixel(x, y), RADIUS, 1, "million-unit light's true off-center arc");
            near(pixel(x + 20, y), SOLID, 1, "translated million-unit fixture's true arc");
            near(pixel(x, y - 6), 0, 0, "huge outlines do not fill the interior");
        }
        require(batch.maxSpritesInBatch < 32, "only the local arcs are batched");
        world.destroyBody(solid); engine.removeEntity(light);

        camera.position.set(32, 64, 0);
        var extreme = lamp(-Float.MAX_VALUE, 0, "#ffffff", Float.MAX_VALUE, 1, null);
        draw(false, 0, OUTLINES);
        near(pixel(32, 110), RADIUS, 1, "extreme radius tangent at world x=0, not the camera center");
        near(pixel(64, 110), WHITE, 0, "no displaced extreme tangent");
        engine.removeEntity(extreme);
        camera.position.set(64, 64, 0);
    }

    private void pulseAndProjection() {
        var lamp = lamp(LX, LY, "#80ff40", 40, .8f, new EmissiveData.Pulse(4, .5f, .5f));
        int[] center = new int[4], rim = new int[4];
        for (int t = 0; t < 4; t++) {
            draw(true, t); center[t] = pixel(36, 44); rim[t] = pixel(75, 44);
        }
        int clearTrough = pixel(42, 44);
        draw(true, 1); int clearPeak = pixel(64, 44); // Unsaturated reference even at peak intensity.
        var edge = new EdgeShape(); edge.set(0, -40, 0, 40);
        var wall = body(LX + 24, LY, 0, StaticBody, edge, false);
        draw(true, 1); fade(64, clearPeak, 4. / 15, "peak radius 60 gives a 15-unit shadow");
        wall.setTransform(LX + 2, LY, 0);
        draw(true, 3); fade(42, clearTrough, 4. / 5, "trough radius 20 gives a 5-unit shadow");
        draw(true, 1);
        // At x=46.5 the unclamped green is 1.2 * (1-smoothstep(10/60)) = 10/9.
        // It must be attenuated before the RGB target saturates, not capped to 1 first.
        double t = 8. / 15, expectedGreen = 16 + 255 * (10. / 9) * t * t * (3 - 2 * t);
        require(Math.abs((pixel(46, 44) >>> 16 & 255) - expectedGreen) < 3, "masked pulse preserves above-one intensity");
        world.destroyBody(wall);
        near(center[0], 0x86dc3bff, 3, "phase zero"); near(center[2], center[0], 1, "half period");
        near(center[1], 0xbaff55ff, 3, "peak: unclamped 1.2 intensity, clamp channels after addition");
        near(center[3], 0x537622ff, 3, "trough intensity");
        require(red(rim[1]) > red(rim[0]) + 5, "peak radius grows"); near(rim[3], DIM, 1, "trough radius shrinks");
        camera.position.set(52, 51, 0); camera.zoom = .5f;
        // World x range [20,84], y [19,83]. Physical 192x192 viewport at nonzero offset.
        ox = 27; oy = 15; side = 192; view.setScreenBounds(ox, oy, side, side);
        draw(true, 0);
        near(read(ox + 49, oy + 76), center[0], 3, "camera pan, zoom, resized target and bottom-up UV");
        require(red(read(ox + 49, oy + 166)) < red(center[0]) - 20, "upper sample not flipped onto light origin");
        Transform.MAPPER.get(lamp).getPosition().add(-20, 0); // center (16.5,44.5) outside camera, disk still intersects
        draw(true, 0); require(red(read(ox + 1, oy + 76)) > 100, "offscreen source still illuminates visible disk");
        // Exercise libGDX's real HiDPI viewport conversion with a deterministic 2x logical/physical ratio.
        var real = Gdx.graphics;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class<?>[]{Graphics.class}, (p, m, a) -> switch (m.getName()) {
            case "getWidth", "getHeight" -> 144;
            case "getBackBufferWidth", "getBackBufferHeight" -> 288;
            default -> invoke(real, m, a);
        });
        try {
            view.setScreenBounds(11, 7, 96, 96); clear(BAR); draw(true, 0);
            require(red(read(23, 90)) > 100, "physical 2x viewport tracks offscreen light");
            near(read(213, 90), DIM, 1, "rightmost physical viewport column"); near(read(214, 90), BAR, 0, "physical right bar");
            near(read(21, 90), BAR, 0, "physical left bar");
        } finally { Gdx.graphics = real; }
        ox = 13; oy = 9; side = 128; view.setScreenBounds(ox, oy, side, side);
        camera.zoom = 1; camera.position.set(64, 64, 0);
    }

    private void stateAndFailure() {
        var gl = Gdx.gl;
        box(28, LY, 1, 5, StaticBody, false); // Exercise an actual alpha-mask draw under hostile caller state.
        var nested = new FrameBuffer(Pixmap.Format.RGBA8888, 180, 180, false, true);
        var shader = SpriteBatch.createDefaultShader();
        var texture0 = texture(1, 1, WHITE).getTexture();
        var texture1 = texture(1, 1, BAR).getTexture();
        int array = gl.glGenBuffer(), elements = gl.glGenBuffer(), rb = gl.glGenRenderbuffer();
        int[] vao = {0};
        var projection = new Matrix4().setToOrtho2D(-1, -4, 19, 33);
        var transform = new Matrix4().translate(6, 9, 0).rotate(0, 0, 1, 15);
        try {
            nested.begin();
            boolean mrt = dualOutputs();
            if (mrt) {
                clear(BAR); // Attachment 0 is the sentinel; output zero deliberately points elsewhere.
                var worldColor = texture(nested.getWidth(), nested.getHeight(), 0).getTexture();
                gl.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, worldColor.getTextureObjectHandle(), 0);
                GL20C.glDrawBuffers(new int[]{GL_COLOR_ATTACHMENT1, GL_COLOR_ATTACHMENT0});
                require(gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE, "two active caller draw attachments");
            }
            if (splitFbos()) { gl.glBindFramebuffer(GL_READ_FRAMEBUFFER, target.getFramebufferHandle()); }
            if (Gdx.gl30 != null) {
                Gdx.gl30.glGenVertexArrays(1, vao, 0); Gdx.gl30.glBindVertexArray(vao[0]);
            }
            gl.glBindBuffer(GL_ARRAY_BUFFER, array); gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elements);
            gl.glBindRenderbuffer(GL_RENDERBUFFER, rb);
            batch.setShader(shader); batch.setProjectionMatrix(projection); batch.setTransformMatrix(transform);
            batch.setBlendFunctionSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_ZERO, GL_ONE);
            batch.disableBlending(); batch.setColor(.3f, .4f, .5f, .6f);
            shader.bind(); shader.setUniformMatrix("u_projTrans", projection); shader.setUniformi("u_texture", 3);
            texture0.bind(0); texture1.bind(1);
            gl.glViewport(3, 5, 73, 61); gl.glScissor(9, 12, 3, 4);
            gl.glClearColor(.25f, .125f, .5f, .75f); gl.glClearStencil(37);
            gl.glPixelStorei(GL_UNPACK_ALIGNMENT, 8);
            gl.glColorMask(false, true, false, true); gl.glDepthMask(false);
            gl.glStencilFuncSeparate(GL_FRONT, GL_LESS, 19, 0x3f); gl.glStencilFuncSeparate(GL_BACK, GL_GREATER, 31, 0xfc);
            gl.glStencilMaskSeparate(GL_FRONT, 0x13); gl.glStencilMaskSeparate(GL_BACK, 0x46);
            gl.glStencilOpSeparate(GL_FRONT, GL_INVERT, GL_DECR_WRAP, GL_INCR);
            gl.glStencilOpSeparate(GL_BACK, GL_ZERO, GL_REPLACE, GL_INCR_WRAP);
            gl.glBlendFuncSeparate(GL_ZERO, GL_ONE, GL_DST_ALPHA, GL_SRC_ALPHA);
            gl.glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
            for (int cap : ENABLES) { gl.glEnable(cap); }
            if (dualIndexedOutputs()) { setSecondOutput(); }
            require(gl.glGetError() == GL_NO_ERROR, "valid custom caller setup");
            var before = snapshot();
            if (mrt) {
                try (var probe = new TargetProbe()) {
                    probe.mrtWorldTarget = nested.getFramebufferHandle();
                    draw(false, 0);
                }
                near(callerPixel(nested, GL_COLOR_ATTACHMENT0, 16), BAR, 0, "day preserved secondary output sentinel");
                near(callerPixel(nested, GL_COLOR_ATTACHMENT1, 16), WHITE, 0, "day rendered to caller's output zero");
                checkState(before, "MRT day");
            }
            for (var layers : List.of(RAW, OUTLINES, BOTH)) {
                try (var probe = new TargetProbe()) {
                    if (mrt) { probe.mrtWorldTarget = nested.getFramebufferHandle(); }
                    draw(true, 0, layers);
                }
                checkState(before, "debug " + layers);
                int output = mrt ? GL_COLOR_ATTACHMENT1 : GL_COLOR_ATTACHMENT0;
                near(callerPixel(nested, output, 28), layers.lightingOutlines() ? SOLID : DIM, 1,
                    layers + " draws to isolated output zero of caller");
                if (mrt) { near(callerPixel(nested, GL_COLOR_ATTACHMENT0, 28), BAR, 0, layers + " preserved secondary sentinel"); }
            }
            int oldMap, oldColor;
            try (var probe = new TargetProbe()) {
                draw(true, 0);
                oldMap = probe.maskTarget; oldColor = probe.maskColor;
                require(gl.glIsFramebuffer(oldMap) && gl.glIsTexture(oldColor), "live color target before resize");
            }
            checkState(before, "masked night before resize");
            // Force a resize of the existing lighting map. Fail *after* real driver allocation,
            // not by throwing before the pass; retain native handle evidence before any reuse.
            view.setScreenBounds(ox, oy, 123, 123);
            try (var probe = new TargetProbe()) {
                probe.incomplete = true;
                withSmallUnpackBuffer("exceptional lightmap resize", () -> {
                    try { draw(true, 0); throw new AssertionError("incomplete lighting framebuffer accepted"); }
                    catch (IllegalStateException error) {
                        require(probe.statusChecks > 0 && error.getMessage() != null && !error.getMessage().isBlank(),
                            "incomplete lighting framebuffer fails explicitly");
                        System.out.println("Expected lighting allocation failure: " + error.getMessage());
                    }
                });
                probe.requireColorTargetAllocation();
                probe.requireReleased();
                require(gl.glIsFramebuffer(oldMap) && gl.glIsTexture(oldColor), "failed resize retained previous lightmap");
            }
            checkState(before, "exceptional resize");
            view.setScreenBounds(ox, oy, side, side);
            try (var probe = new TargetProbe()) {
                if (mrt) { probe.mrtWorldTarget = nested.getFramebufferHandle(); }
                // Resize successfully. Repeating the frame must reuse the lighting target.
                view.setScreenBounds(ox, oy, 127, 127);
                withSmallUnpackBuffer("successful lightmap resize", () -> draw(true, 0));
                checkState(before, "successful resize");
                probe.requireColorTargetAllocation();
                require(probe.statusChecks > 0 && probe.maskColor == probe.handle("glGenTexture"), "validated replacement texture used by mask");
                require(!gl.glIsFramebuffer(oldMap) && !gl.glIsTexture(oldColor), "successful resize retired previous lightmap");
                int allocations = probe.targets.size();
                // Interrupt both the GL_MIN union and the subsequent visibility-weighted light addition.
                for (Draw pass : List.of(Draw.MASK, Draw.QUAD)) {
                    probe.interrupt = pass;
                    try { draw(true, 0); throw new AssertionError("missing offscreen draw: " + pass); }
                    catch (DrawFailure expected) {
                        require(expected.draw == pass && probe.interruptedTarget == probe.handle("glGenFramebuffer"),
                            "interrupted inside offscreen " + pass);
                        checkState(before, pass + " draw failure");
                        if (mrt) { near(callerPixel(nested, GL_COLOR_ATTACHMENT0, 16), BAR, 0, "interrupted frame preserved secondary sentinel"); }
                    }
                }
                draw(true, 0);
                require(allocations == probe.targets.size(), "same-size frames reuse lighting storage");
                checkState(before, "repeated night");
                if (mrt) { near(callerPixel(nested, GL_COLOR_ATTACHMENT0, 16), BAR, 0, "night preserved secondary output sentinel"); }
                int outputZero = mrt ? GL_COLOR_ATTACHMENT1 : GL_COLOR_ATTACHMENT0;
                near(callerPixel(nested, outputZero, 16), 0x86dc3bff, 3, "composite returned to caller's output zero, not window or read FBO");
                near(callerPixel(nested, outputZero, 28), DIM, 1, "masked night recovered after interrupted GL_MIN draw");
                // A caller may still have a renderer-owned handle bound while it is retired.
                // Never restore the deleted name (which can silently recreate a texture on GL20).
                if (splitFbos()) { gl.glBindFramebuffer(GL_READ_FRAMEBUFFER, probe.handle("glGenFramebuffer")); }
                gl.glActiveTexture(GL_TEXTURE0); gl.glBindTexture(GL_TEXTURE_2D, probe.handle("glGenTexture"));
                gl.glActiveTexture(GL_TEXTURE1);
                renderer.dispose(); renderer.dispose();
                if (splitFbos()) {
                    require(integer(GL_READ_FRAMEBUFFER_BINDING) == 0, "no deleted read FBO restored");
                    gl.glBindFramebuffer(GL_READ_FRAMEBUFFER, target.getFramebufferHandle());
                }
                require(integer(GL_RENDERBUFFER_BINDING) == rb && gl.glIsRenderbuffer(rb), "disposal retained caller-owned renderbuffer");
                require(!gl.glIsProgram(probe.maskProgram) && !gl.glIsBuffer(probe.maskBuffer), "released mask program and mesh storage");
                require(integer(GL_ACTIVE_TEXTURE) == GL_TEXTURE1, "disposal preserved active unit");
                gl.glActiveTexture(GL_TEXTURE0); require(integer(GL_TEXTURE_BINDING_2D) == 0, "no deleted lightmap texture restored");
                texture0.bind(0); gl.glActiveTexture(GL_TEXTURE1);
                checkState(before, "disposal (surviving state)"); probe.requireReleased();
            }
        } finally {
            for (int cap : ENABLES) { gl.glDisable(cap); }
            gl.glColorMask(true, true, true, true); gl.glDepthMask(true); gl.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            gl.glBlendEquation(GL_FUNC_ADD); gl.glStencilMask(-1);
            batch.setShader(null); gl.glUseProgram(0); shader.dispose();
            gl.glBindBuffer(GL_ARRAY_BUFFER, 0); gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            gl.glDeleteBuffer(array); gl.glDeleteBuffer(elements);
            if (Gdx.gl30 != null) { Gdx.gl30.glBindVertexArray(0); Gdx.gl30.glDeleteVertexArrays(1, vao, 0); }
            gl.glBindRenderbuffer(GL_RENDERBUFFER, 0); gl.glDeleteRenderbuffer(rb);
            gl.glActiveTexture(GL_TEXTURE0); nested.end(); nested.dispose(); target.begin();
        }
    }

    private static final int[] ENABLES = {GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_SCISSOR_TEST, GL_STENCIL_TEST};

    private static boolean splitFbos() {
        var caps = org.lwjgl.opengl.GL.getCapabilities();
        return caps.OpenGL30 || caps.GL_ARB_framebuffer_object || caps.GL_EXT_framebuffer_blit;
    }

    private int callerPixel(FrameBuffer caller, int attachment, int x) {
        int target = splitFbos() ? GL_READ_FRAMEBUFFER : GL_FRAMEBUFFER;
        int previous = integer(splitFbos() ? GL_READ_FRAMEBUFFER_BINDING : GL_FRAMEBUFFER_BINDING);
        Gdx.gl.glBindFramebuffer(target, caller.getFramebufferHandle());
        int readBuffer = integer(GL_READ_BUFFER);
        try {
            GL11C.glReadBuffer(attachment);
            return pixel(x, 44);
        } finally {
            GL11C.glReadBuffer(readBuffer);
            Gdx.gl.glBindFramebuffer(target, previous);
        }
    }

    private static boolean dualOutputs() {
        return integer(GL_MAX_DRAW_BUFFERS) >= 2 && integer(GL_MAX_COLOR_ATTACHMENTS) >= 2;
    }

    private static boolean dualIndexedOutputs() {
        var caps = GL.getCapabilities();
        return (caps.OpenGL30 || caps.GL_EXT_draw_buffers2) && dualOutputs();
    }

    private static boolean independentBlend() {
        var caps = GL.getCapabilities();
        return caps.OpenGL40 || caps.GL_ARB_draw_buffers_blend || caps.GL_AMD_draw_buffers_blend;
    }

    /** Set slot 1 *after* the broadcast calls in stateAndFailure; leave slot 0 deliberately different. */
    private static void setSecondOutput() {
        if (GL.getCapabilities().OpenGL30) {
            GL30C.glColorMaski(1, true, false, true, false);
            GL30C.glDisablei(GL_BLEND, 1);
        } else {
            EXTDrawBuffers2.glColorMaskIndexedEXT(1, true, false, true, false);
            EXTDrawBuffers2.glDisableIndexedEXT(GL_BLEND, 1);
        }
        if (GL.getCapabilities().OpenGL40) {
            GL40C.glBlendEquationSeparatei(1, GL_FUNC_ADD, GL_FUNC_REVERSE_SUBTRACT);
            GL40C.glBlendFuncSeparatei(1, GL_ONE_MINUS_SRC_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_DST_ALPHA);
        } else if (GL.getCapabilities().GL_ARB_draw_buffers_blend) {
            ARBDrawBuffersBlend.glBlendEquationSeparateiARB(1, GL_FUNC_ADD, GL_FUNC_REVERSE_SUBTRACT);
            ARBDrawBuffersBlend.glBlendFuncSeparateiARB(1, GL_ONE_MINUS_SRC_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_DST_ALPHA);
        } else if (GL.getCapabilities().GL_AMD_draw_buffers_blend) {
            AMDDrawBuffersBlend.glBlendEquationSeparateIndexedAMD(1, GL_FUNC_ADD, GL_FUNC_REVERSE_SUBTRACT);
            AMDDrawBuffersBlend.glBlendFuncSeparateIndexedAMD(1, GL_ONE_MINUS_SRC_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_DST_ALPHA);
        }
    }

    private static void snapshotOutputs(Map<String, Object> result) {
        if (!dualOutputs()) { return; }
        boolean core = GL.getCapabilities().OpenGL30;
        for (int i = 0; i < 2; i++) {
            String slot = "draw buffer " + i;
            result.put(slot + " routing", integer(GL_DRAW_BUFFER0 + i));
            if (!dualIndexedOutputs()) { continue; }
            var mask = BufferUtils.newByteBuffer(4);
            if (core) { GL30C.glGetBooleani_v(GL_COLOR_WRITEMASK, i, mask); }
            else { EXTDrawBuffers2.glGetBooleanIndexedvEXT(GL_COLOR_WRITEMASK, i, mask); }
            result.put(slot + " mask", List.of(mask.get(0), mask.get(1), mask.get(2), mask.get(3)));
            result.put(slot + " blend", core ? GL30C.glIsEnabledi(GL_BLEND, i) : EXTDrawBuffers2.glIsEnabledIndexedEXT(GL_BLEND, i));
            if (independentBlend()) {
                for (int key : new int[]{GL_BLEND_EQUATION_RGB, GL_BLEND_EQUATION_ALPHA, GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB,
                    GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA}) {
                    result.put(slot + " blend 0x" + Integer.toHexString(key), core ? GL30C.glGetIntegeri(key, i)
                        : EXTDrawBuffers2.glGetIntegerIndexedEXT(key, i));
                }
            }
        }
        require(Gdx.gl.glGetError() == GL_NO_ERROR, "valid indexed state readback");
    }

    private Map<String, Object> snapshot() {
        var gl = Gdx.gl;
        var result = new LinkedHashMap<String, Object>();
        snapshotOutputs(result);
        for (int key : new int[]{GL_FRAMEBUFFER_BINDING, GL_RENDERBUFFER_BINDING, GL_ARRAY_BUFFER_BINDING,
            GL_ELEMENT_ARRAY_BUFFER_BINDING, GL_CURRENT_PROGRAM, GL_ACTIVE_TEXTURE, GL_DEPTH_WRITEMASK, GL_UNPACK_ALIGNMENT,
            GL_BLEND_SRC_RGB, GL_BLEND_DST_RGB, GL_BLEND_SRC_ALPHA, GL_BLEND_DST_ALPHA, GL_BLEND_EQUATION_RGB,
            GL_BLEND_EQUATION_ALPHA, GL_STENCIL_CLEAR_VALUE, GL_STENCIL_FUNC, GL_STENCIL_REF, GL_STENCIL_VALUE_MASK,
            GL_STENCIL_WRITEMASK, GL_STENCIL_FAIL, GL_STENCIL_PASS_DEPTH_FAIL, GL_STENCIL_PASS_DEPTH_PASS,
            GL_STENCIL_BACK_FUNC, GL_STENCIL_BACK_REF, GL_STENCIL_BACK_VALUE_MASK, GL_STENCIL_BACK_WRITEMASK,
            GL_STENCIL_BACK_FAIL, GL_STENCIL_BACK_PASS_DEPTH_FAIL, GL_STENCIL_BACK_PASS_DEPTH_PASS}) {
            result.put("GL 0x" + Integer.toHexString(key), integer(key));
        }
        if (splitFbos()) { result.put("read FBO", integer(GL_READ_FRAMEBUFFER_BINDING)); }
        if (Gdx.gl30 != null) { result.put("VAO", integer(GL_VERTEX_ARRAY_BINDING)); }
        for (int cap : ENABLES) { result.put("enabled " + cap, gl.glIsEnabled(cap)); }
        for (int key : new int[]{GL_VIEWPORT, GL_SCISSOR_BOX, GL_COLOR_WRITEMASK}) { result.put("vec4 " + key, ints(key, 4)); }
        var floats = BufferUtils.newFloatBuffer(16);
        var clear = new float[4]; gl.glGetFloatv(GL_COLOR_CLEAR_VALUE, floats); floats.get(clear);
        result.put("clear color", Arrays.toString(clear));
        for (int unit = 0; unit <= 1; unit++) {
            gl.glActiveTexture(GL_TEXTURE0 + unit); result.put("texture " + unit, integer(GL_TEXTURE_BINDING_2D));
        } // restore unit 1, as installed by the test (original active unit was sampled above).
        var shader = batch.getShader();
        var matrix = new float[16]; floats.clear();
        gl.glGetUniformfv(shader.getHandle(), shader.getUniformLocation("u_projTrans"), floats); floats.get(matrix);
        result.put("caller matrix uniform", Arrays.toString(matrix));
        var sampler = BufferUtils.newIntBuffer(1);
        gl.glGetUniformiv(shader.getHandle(), shader.getUniformLocation("u_texture"), sampler);
        result.put("caller sampler", sampler.get(0)); result.put("batch shader", shader);
        result.put("batch matrices", List.of(Arrays.toString(batch.getProjectionMatrix().val), Arrays.toString(batch.getTransformMatrix().val)));
        result.put("batch color", List.of(batch.getColor().r, batch.getColor().g, batch.getColor().b, batch.getColor().a, batch.getPackedColor()));
        result.put("batch blend", List.of(batch.getBlendSrcFunc(), batch.getBlendDstFunc(), batch.getBlendSrcFuncAlpha(), batch.getBlendDstFuncAlpha()));
        result.put("batch flags", List.of(batch.isBlendingEnabled(), batch.isDrawing()));
        return result;
    }

    private void checkState(Map<String, Object> expected, String where) {
        var actual = snapshot();
        expected.forEach((k, v) -> require(v.equals(actual.get(k)), where + " changed " + k + ": " + v + " -> " + actual.get(k)));
    }

    /** A real data store, too small for any of the renderer's uploads (null means offset zero with a PBO). */
    private static void withSmallUnpackBuffer(String where, Runnable action) {
        var gl = Gdx.gl;
        int old = integer(GL_PIXEL_UNPACK_BUFFER_BINDING), buffer = gl.glGenBuffer();
        int rowLength = integer(GL_UNPACK_ROW_LENGTH), skipRows = integer(GL_UNPACK_SKIP_ROWS), skipPixels = integer(GL_UNPACK_SKIP_PIXELS);
        try {
            gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
            gl.glBufferData(GL_PIXEL_UNPACK_BUFFER, 4, null, GL_STATIC_DRAW);
            require(gl.glGetError() == GL_NO_ERROR && integer(GL_PIXEL_UNPACK_BUFFER_BINDING) == buffer,
                where + ": valid small caller PBO");
            // Wrong for the 15x15 crosshair, but stays within its pixmap if only the PBO is neutralized.
            gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, 4);
            gl.glPixelStorei(GL_UNPACK_SKIP_ROWS, 1);
            gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 1);
            action.run();
            int error = gl.glGetError();
            require(error == GL_NO_ERROR, where + ": driver error 0x" + Integer.toHexString(error));
            require(integer(GL_UNPACK_ROW_LENGTH) == 4 && integer(GL_UNPACK_SKIP_ROWS) == 1 && integer(GL_UNPACK_SKIP_PIXELS) == 1,
                where + ": changed unpack layout");
            require(integer(GL_PIXEL_UNPACK_BUFFER_BINDING) == buffer, where + ": caller PBO binding lost");
        } finally {
            gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, rowLength);
            gl.glPixelStorei(GL_UNPACK_SKIP_ROWS, skipRows);
            gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, skipPixels);
            gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, old);
            gl.glDeleteBuffer(buffer);
        }
    }

    private static int integer(int key) {
        var buffer = BufferUtils.newIntBuffer(1); Gdx.gl.glGetIntegerv(key, buffer); return buffer.get(0);
    }
    private static String ints(int key, int size) {
        var buffer = BufferUtils.newIntBuffer(size); Gdx.gl.glGetIntegerv(key, buffer);
        var result = new int[size]; buffer.get(result); return Arrays.toString(result);
    }

    private enum Draw { SPRITE, MASK, QUAD }
    private static final class DrawFailure extends RuntimeException {
        final Draw draw;
        DrawFailure(Draw draw) { this.draw = draw; }
    }

    /** Delegate to the driver first; inject only the requested link/status failure or draw interruption. */
    private static final class TargetProbe implements AutoCloseable {
        final GL20 real = Gdx.gl, real20 = Gdx.gl20;
        final GL30 real30 = Gdx.gl30;
        final List<Map.Entry<String, Integer>> targets = new ArrayList<>();
        boolean incomplete, failLink;
        Draw interrupt;
        int statusChecks, linkChecks, quadDraws, spriteDraws, interruptedTarget, mrtWorldTarget;
        int maskTarget, maskColor, maskProgram, maskBuffer;

        TargetProbe() {
            Class<?> api = real30 == null ? GL20.class : GL30.class;
            var probe = (GL20) Proxy.newProxyInstance(api.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
                Object value = invoke(real30 == null ? real : real30, method, args);
                switch (method.getName()) {
                    case "glDrawArrays" -> {
                        if (((Integer)args[0]) == GL_TRIANGLE_STRIP) { quadDraws++; after(Draw.QUAD); }
                        if (((Integer)args[0]) == GL_TRIANGLES) { after(Draw.MASK); }
                    }
                    case "glDrawElements" -> { spriteDraws++; after(Draw.SPRITE); }
                    case "glGetProgramiv" -> {
                        if (failLink && ((Integer)args[1]) == GL_LINK_STATUS) { linkChecks++; ((java.nio.IntBuffer)args[2]).put(0, 0); }
                    }
                    case "glCheckFramebufferStatus" -> { statusChecks++; if (incomplete) { return GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT; } }
                    case "glCreateShader", "glCreateProgram", "glGenFramebuffer", "glGenTexture", "glGenRenderbuffer" -> targets.add(Map.entry(method.getName(), (Integer)value));
                }
                return value;
            });
            Gdx.gl = probe; Gdx.gl20 = probe; if (real30 != null) { Gdx.gl30 = (GL30)probe; }
        }

        private int attachment(int point, int key) {
            var result = BufferUtils.newIntBuffer(1);
            real.glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, point, key, result);
            return result.get(0);
        }

        private void after(Draw draw) {
            if (draw == Draw.MASK) {
                maskTarget = integer(GL_FRAMEBUFFER_BINDING);
                require(maskTarget != 0 && attachment(GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_TEXTURE,
                    "mask triangles render to the lightmap texture");
                maskColor = attachment(GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                maskProgram = integer(GL_CURRENT_PROGRAM); maskBuffer = integer(GL_ARRAY_BUFFER_BINDING);
                require(real.glIsProgram(maskProgram) && real.glIsBuffer(maskBuffer), "real mask shader and vertex storage");
                require(attachment(GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_NONE
                    && attachment(GL_STENCIL_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL_NONE,
                    "lightmap has no depth or stencil attachment");
                require(real.glIsEnabled(GL_BLEND) && integer(GL_BLEND_EQUATION_ALPHA) == GL_MIN && !real.glIsEnabled(GL_STENCIL_TEST),
                    "mask draw unions visibility in alpha with GL_MIN and no stencil");
            }
            if (mrtWorldTarget != 0 && integer(GL_FRAMEBUFFER_BINDING) == mrtWorldTarget) {
                require(integer(GL_DRAW_BUFFER0) == GL_COLOR_ATTACHMENT1 && integer(GL_DRAW_BUFFER1) == GL_NONE,
                    draw + " must draw only to caller's output zero");
            }
            if (draw == interrupt) {
                interruptedTarget = integer(GL_FRAMEBUFFER_BINDING);
                interrupt = null; // One-shot: SpriteBatch may retry the flush while unwinding.
                throw new DrawFailure(draw);
            }
        }

        int handle(String allocation) {
            return targets.stream().filter(t -> t.getKey().equals(allocation)).findFirst().orElseThrow().getValue();
        }

        void requireColorTargetAllocation() {
            var names = targets.stream().map(Map.Entry::getKey).toList();
            require(Collections.frequency(names, "glGenFramebuffer") == 1 && Collections.frequency(names, "glGenTexture") == 1
                && !names.contains("glGenRenderbuffer"), "one color lightmap, no renderbuffer: " + names);
        }

        void requireReleased() {
            require(!targets.isEmpty(), "real lighting target allocations observed");
            for (var entry : targets) {
                int id = entry.getValue();
                boolean live = switch (entry.getKey()) {
                    case "glCreateShader" -> real.glIsShader(id);
                    case "glCreateProgram" -> real.glIsProgram(id);
                    case "glGenTexture" -> real.glIsTexture(id);
                    case "glGenFramebuffer" -> real.glIsFramebuffer(id);
                    case "glGenRenderbuffer" -> real.glIsRenderbuffer(id);
                    default -> throw new AssertionError(entry);
                };
                require(!live, "released " + entry.getKey() + " " + id);
            }
        }
        @Override public void close() { Gdx.gl = real; Gdx.gl20 = real20; Gdx.gl30 = real30; }
    }

    private static Object invoke(Object delegate, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try { return method.invoke(delegate, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }

    private TiledMapTileLayer.Cell tile(int width, int height, int color) {
        return new TiledMapTileLayer.Cell().setTile(new StaticTiledMapTile(texture(width, height, color)));
    }

    private TextureRegion texture(int w, int h, int color) {
        var pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None); pixmap.setColor(color); pixmap.fill();
            var tex = new Texture(pixmap); textures.add(tex); return new TextureRegion(tex);
        } finally { pixmap.dispose(); }
    }

    private static Transform transform(float x, float y, int w, int h, int z) {
        return new Transform(new Vector2(x, y), z, new Vector2(w, h), new Vector2(1, 1), 0, 0);
    }
    private Entity sprite(float x, float y, int w, int h, int z, int color) {
        return new Entity().add(transform(x, y, w, h, z)).add(new Graphic(texture(2, 2, color), Color.WHITE.cpy()));
    }
    private Entity lamp(float x, float y, String color, float radius, float intensity, EmissiveData.Pulse pulse) {
        var entity = new Entity().add(transform(x - 8, y - 4, 16, 8, 1)).add(new Emissive(
            new EmissiveData(color, radius, intensity, Optional.ofNullable(pulse)), GameConsts.UNIT_SCALE));
        engine.addEntity(entity); return entity;
    }
    private Body box(float x, float y, float hw, float hh, BodyDef.BodyType type, boolean sensor) {
        var shape = new PolygonShape(); shape.setAsBox(hw, hh); return body(x, y, 0, type, shape, sensor);
    }
    private Body body(float x, float y, float angle, BodyDef.BodyType type, Shape shape, boolean sensor) {
        try {
            var def = new BodyDef(); def.type = type; def.angle = angle; def.position.set(x, y);
            var body = world.createBody(def); body.createFixture(shape, 1).setSensor(sensor); return body;
        } finally { shape.dispose(); }
    }

    private void draw(boolean night, double seconds) {
        draw(night, seconds, OFF);
    }
    private void draw(boolean night, double seconds, DebugLayers layers) {
        var fixture = renderers.get(layers);
        if (renderer != fixture) {
            renderer = fixture;
            engine.addSystem(renderer); // Ashley replaces the previously installed RenderSystem.
        }
        renderer.gameplayFrame(world, AMBIENT, night, seconds);
        engine.update(0);
    }
    private static void clear(int color) {
        var c = new Color(color); Gdx.gl.glClearColor(c.r, c.g, c.b, c.a); Gdx.gl.glClear(GL_COLOR_BUFFER_BIT);
    }
    private static int read(int x, int y) {
        var buf = BufferUtils.newByteBuffer(4); Gdx.gl.glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        return (buf.get(0) & 255) << 24 | (buf.get(1) & 255) << 16 | (buf.get(2) & 255) << 8 | (buf.get(3) & 255);
    }
    private int pixel(int x, int y) { return read(x + ox, y + oy); }

    private int[] centerRow() {
        var rgba = BufferUtils.newByteBuffer(4 * side);
        Gdx.gl.glReadPixels(ox, oy + 44, side, 1, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        var row = new int[side];
        rgba.order(java.nio.ByteOrder.BIG_ENDIAN).asIntBuffer().get(row);
        return row;
    }

    private void fade(int x, int unoccluded, double fraction, String why) {
        near(pixel(x, 44), faded(unoccluded, fraction), 4, why);
    }

    /** Interpolate only the emitter's contribution, using its actual unoccluded same-pixel value. */
    private static int faded(int unoccluded, double fraction) {
        double visibility = fraction * fraction * (3 - 2 * fraction);
        int expected = alpha(unoccluded);
        for (int shift : new int[]{8, 16, 24}) {
            int ambient = DIM >>> shift & 255, full = unoccluded >>> shift & 255;
            expected |= (int)Math.round(ambient + visibility * (full - ambient)) << shift;
        }
        return expected;
    }

    private static int addLights(int a, int b) {
        int sum = 255;
        for (int shift : new int[]{8, 16, 24}) {
            sum |= Math.min(255, (a >>> shift & 255) + (b >>> shift & 255) - (DIM >>> shift & 255)) << shift;
        }
        return sum;
    }
    private void bars() {
        for (int d = 0; d < side; d++) {
            near(pixel(-1, d), BAR, 0, "left bar"); near(pixel(side, d), BAR, 0, "right bar");
            near(pixel(d, -1), BAR, 0, "bottom bar"); near(pixel(d, side), BAR, 0, "top bar");
        }
    }
    private void save(String label) {
        if (Gdx.gl30 != null) { return; }
        var image = Pixmap.createFromFrameBuffer(ox - 3, oy - 3, side + 6, side + 6);
        try { PixmapIO.writePNG(Gdx.files.absolute("/tmp/lute-lighting-" + label + ".png"), image, -1, true); }
        finally { image.dispose(); }
    }
    private static int red(int rgba) { return rgba >>> 24; }
    private static int blue(int rgba) { return rgba >>> 8 & 255; }
    private static int alpha(int rgba) { return rgba & 255; }
    private static int modulate(int rgba, int light) {
        int result = alpha(rgba);
        for (int shift : new int[]{8, 16, 24}) {
            result |= Math.round(((rgba >>> shift) & 255) * ((light >>> shift) & 255) / 255f) << shift;
        }
        return result;
    }
    private static void near(int actual, int expected, int tolerance, String why) {
        for (int shift : new int[]{0, 8, 16, 24}) {
            if (Math.abs((actual >>> shift & 255) - (expected >>> shift & 255)) > tolerance) {
                throw new AssertionError("%s: expected RGBA %08x (+/-%d), got %08x".formatted(why, expected, tolerance, actual));
            }
        }
        checks++;
    }
    private static void require(boolean ok, String why) { if (!ok) { throw new AssertionError(why); } checks++; }
}
