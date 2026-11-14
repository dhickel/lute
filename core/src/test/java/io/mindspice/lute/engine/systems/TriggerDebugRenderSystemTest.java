package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.objects.PolygonMapObject;
import com.badlogic.gdx.maps.objects.PolylineMapObject;
import com.badlogic.gdx.maps.tiled.BaseTiledMapLoader;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.GameCore;
import io.mindspice.lute.engine.singletons.GameConsts;
import io.mindspice.lute.engine.tiled.FixedTmxMapLoader;

import java.lang.reflect.Proxy;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/** Geometry assertions only: GL calls made during ShapeRenderer construction are no-ops. */
public final class TriggerDebugRenderSystemTest {
    private static final float EPSILON = 0.0001f;

    public static void main(String[] args) {
        GdxNativesLoader.load();
        Box2D.init();
        var oldApp = Gdx.app;
        var oldGraphics = Gdx.graphics;
        var oldGl = Gdx.gl;
        var oldGl20 = Gdx.gl20;
        try {
            installConstructorStubs();
            sensors();
            loadedMapGeometry();
            savedMapTriggers();
            mutableObjectTransforms();
            System.out.println("TriggerDebugRenderSystemTest: passed (native Box2D, no graphics context or game launch)");
        } finally {
            Gdx.app = oldApp;
            Gdx.graphics = oldGraphics;
            Gdx.gl = oldGl;
            Gdx.gl20 = oldGl20;
        }
    }

    private static void sensors() {
        var world = new World(new Vector2(), true);
        var renderer = new RecordingRenderer();
        var system = attach(world, renderer, null);
        try {
            var definition = new BodyDef();
            definition.position.set(10f, 20f);
            definition.angle = (float) (Math.PI / 2);
            var body = world.createBody(definition);
            var circle = new CircleShape();
            circle.setPosition(new Vector2(2f, 3f));
            circle.setRadius(0.75f);
            sensor(body, circle);
            var polygon = new PolygonShape();
            polygon.set(new float[]{0, 0, 2, 0, 2, 1, 0, 1});
            sensor(body, polygon);
            var chain = new ChainShape();
            chain.createChain(new float[]{0, 3, 2, 3, 2, 5});
            sensor(body, chain);
            var loop = new ChainShape();
            loop.createLoop(new float[]{4, 0, 6, 0, 5, 2});
            sensor(body, loop);
            var edge = new EdgeShape();
            edge.set(5, 1, 6, 3);
            sensor(body, edge);
            var solid = new PolygonShape();
            solid.setAsBox(10, 10);
            body.createFixture(solid, 0);
            solid.dispose();

            var dynamicDefinition = new BodyDef();
            dynamicDefinition.type = BodyDef.BodyType.DynamicBody;
            dynamicDefinition.position.set(-3, -4);
            var dynamic = world.createBody(dynamicDefinition);
            edge = new EdgeShape();
            edge.set(0, 0, 1, 2);
            sensor(dynamic, edge);
            dynamicDefinition.type = BodyDef.BodyType.KinematicBody;
            dynamicDefinition.active = false;
            var inactive = world.createBody(dynamicDefinition);
            edge = new EdgeShape();
            edge.set(0, 0, 1, 2);
            sensor(inactive, edge);

            system.update(0);
            check(renderer.lines.size() == 12, "Expected polygon, open/closed chains, edge, dynamic and inactive sensors only");
            check(renderer.circles.size() == 1, "Expected one sensor circle");
            var actual = new ArrayList<>(renderer.lines);
            float[][] localLines = {
                {0, 0, 2, 0}, {2, 0, 2, 1}, {2, 1, 0, 1}, {0, 1, 0, 0},
                {0, 3, 2, 3}, {2, 3, 2, 5},
                {4, 0, 6, 0}, {6, 0, 5, 2}, {5, 2, 4, 0}, {5, 1, 6, 3}
            };
            for (var line : localLines) {
                removeLine(actual, 10 - line[1], 20 + line[0], 10 - line[3], 20 + line[2]);
            }
            removeLine(actual, -3, -4, -2, -2);
            removeLine(actual, -3, -4, -2, -2);
            check(actual.isEmpty(), "Unexpected sensor lines");
            var renderedCircle = renderer.circles.getFirst();
            near(renderedCircle[0], 7, "Rotated local circle center x");
            near(renderedCircle[1], 22, "Rotated local circle center y");
            near(renderedCircle[2], 0.75f, "Circle radius must not be pixel-scaled");
            check(renderer.colors.stream().allMatch(Color.CYAN::equals), "Sensors must be cyan");

            renderer.clear();
            world.destroyBody(body);
            world.destroyBody(dynamic);
            world.destroyBody(inactive);
            system.update(0);
            check(renderer.lines.isEmpty() && renderer.circles.isEmpty(), "Destroyed bodies must not leave cached sensor geometry");
            check(renderer.begins == 2 && renderer.ends == 2, "Each frame must begin/end once");
        } finally {
            system.dispose();
            check(renderer.disposed, "System must dispose its renderer");
            world.dispose();
        }
    }

    private static void loadedMapGeometry() {
        var map = new GeometryLoader().load();
        var world = new World(new Vector2(), true);
        var renderer = new RecordingRenderer();
        var system = attach(world, renderer, map);
        try {
            system.update(0);
            check(renderer.lines.size() == 67, "Expected 67 lines for all trigger shapes and nested layers, got " + renderer.lines.size());
            check(renderer.colors.stream().allMatch(Color.MAGENTA::equals), "Map triggers must be magenta");
            check(renderer.circles.isEmpty(), "Map ellipse must use its rotated outline");
            mapLine(renderer, 0, 26, 274, 34, 274);
            mapLine(renderer, 1, 30, 270, 30, 278);
            mapLine(renderer, 2, 40, 254, 40, 234);
            mapLine(renderer, 3, 40, 234, 50, 234);
            mapLine(renderer, 4, 50, 234, 50, 254);
            mapLine(renderer, 5, 50, 254, 40, 254);
            // The rotated ellipse pivots at Tiled's top-left, not its center.
            var ellipseStart = renderer.lines.get(6);
            near(ellipseStart[0], 80 * GameConsts.UNIT_SCALE, "Ellipse first x");
            near(ellipseStart[1], 194 * GameConsts.UNIT_SCALE, "Ellipse first y");
            var ellipseQuarter = renderer.lines.get(13);
            near(ellipseQuarter[2], 90 * GameConsts.UNIT_SCALE, "Ellipse quarter x");
            near(ellipseQuarter[3], 214 * GameConsts.UNIT_SCALE, "Ellipse quarter y");
            var ellipseEnd = renderer.lines.get(37);
            near(ellipseEnd[2], ellipseStart[0], "Ellipse closes x");
            near(ellipseEnd[3], ellipseStart[1], "Ellipse closes y");
            mapLine(renderer, 38, 150, 214, 150, 194);
            mapLine(renderer, 39, 150, 194, 140, 204);
            mapLine(renderer, 40, 140, 204, 150, 214);
            mapLine(renderer, 41, 190, 194, 190, 204);
            mapLine(renderer, 42, 190, 204, 210, 204);
            // H/V GID flips change image UVs but never this scaled rectangular footprint.
            for (int start : new int[]{43, 47, 51, 55}) {
                mapLine(renderer, start, 230, 144, 230, 80);
                mapLine(renderer, start + 1, 230, 80, 278, 80);
                mapLine(renderer, start + 2, 278, 80, 278, 144);
                mapLine(renderer, start + 3, 278, 144, 230, 144);
            }
            mapLine(renderer, 59, 300, 284, 300, 264);
            mapLine(renderer, 60, 300, 264, 310, 264);
            mapLine(renderer, 61, 310, 264, 310, 284);
            mapLine(renderer, 62, 310, 284, 300, 284);
            mapLine(renderer, 63, 4, 300, 12, 300);
            mapLine(renderer, 64, 8, 296, 8, 304);
            mapLine(renderer, 65, -3, 318, 5, 318);
            mapLine(renderer, 66, 1, 314, 1, 322);
            check(java.util.Arrays.equals(renderer.getProjectionMatrix().val, renderer.camera.combined.val),
                "Projection must use the active camera");
            var polygon = (PolygonMapObject) map.getLayers().get(0).getObjects().get(0);
            check(polygon.getPolygon().getRotation() == 0, "Rendering must not mutate unrelated map geometry");
        } finally {
            system.dispose();
            world.dispose();
            map.dispose();
        }
    }

    private static void savedMapTriggers() {
        var map = new GeometryLoader().loadSavedMap();
        var world = new World(new Vector2(), true);
        var renderer = new RecordingRenderer();
        var system = attach(world, renderer, map);
        try {
            system.update(0);
            check(renderer.lines.size() == 6, "Saved map must render Teleport's four sides and PlayerSpawn's cross");
            float bottom = 640f - 275.333f - 35.3333f;
            float right = 432.667f + 34.6667f;
            float top = 640f - 275.333f;
            mapLine(renderer, 0, 432.667f, bottom, right, bottom);
            mapLine(renderer, 1, right, bottom, right, top);
            mapLine(renderer, 2, right, top, 432.667f, top);
            mapLine(renderer, 3, 432.667f, top, 432.667f, bottom);
            float spawnY = 640f - 561.672f;
            mapLine(renderer, 4, 302.248f - 4, spawnY, 302.248f + 4, spawnY);
            mapLine(renderer, 5, 302.248f, spawnY - 4, 302.248f, spawnY + 4);
        } finally {
            system.dispose();
            world.dispose();
            map.dispose();
        }
    }

    private static void mutableObjectTransforms() {
        var map = new GeometryLoader().load();
        var world = new World(new Vector2(), true);
        var renderer = new RecordingRenderer();
        var system = attach(world, renderer, map);
        try {
            while (map.getLayers().getCount() > 0) { map.getLayers().remove(0); }
            var layer = new MapLayer();
            layer.setName("Triggers");
            map.getLayers().add(layer);
            var polygon = new PolygonMapObject(new float[]{0, 0, 4, 0, 0, 2});
            polygon.getPolygon().setPosition(10, 20);
            polygon.getPolygon().setOrigin(1, 1);
            polygon.getPolygon().setScale(2, 3);
            polygon.getPolygon().setRotation(90);
            layer.getObjects().add(polygon);
            var polyline = new PolylineMapObject(new float[]{0, 0, 4, 0, 0, 2});
            polyline.getPolyline().setPosition(10, 20);
            polyline.getPolyline().setOrigin(1, 1);
            polyline.getPolyline().setScale(2, 3);
            polyline.getPolyline().setRotation(90);
            layer.getObjects().add(polyline);
            var tile = new TiledMapTileMapObject(map.getTileSets().getTile(1), true, true);
            tile.setX(100);
            tile.setY(200);
            tile.setOriginX(4);
            tile.setOriginY(2);
            tile.setScaleX(-2);
            tile.setScaleY(3);
            tile.setRotation(90);
            layer.getObjects().add(tile);
            system.update(0);
            check(renderer.lines.size() == 9, "Expected closed polygon, open polyline, tile bounds");
            mapLine(renderer, 0, 14, 19, 14, 27);
            mapLine(renderer, 1, 14, 27, 8, 19);
            mapLine(renderer, 2, 8, 19, 14, 19);
            mapLine(renderer, 3, 14, 19, 14, 27);
            mapLine(renderer, 4, 14, 27, 8, 19);
            mapLine(renderer, 5, 98, 194, 98, 258);
            mapLine(renderer, 6, 98, 258, 146, 258);
            mapLine(renderer, 7, 146, 258, 146, 194);
            mapLine(renderer, 8, 146, 194, 98, 194);
            check(polygon.getPolygon().getRotation() == 90, "Polygon transform must remain unchanged");
            check(polyline.getPolyline().getRotation() == 90, "Polyline transform must remain unchanged");
        } finally {
            system.dispose();
            world.dispose();
            map.dispose();
        }
    }

    private static TriggerDebugRenderSystem attach(World world, RecordingRenderer renderer, TiledMap map) {
        var engine = new Engine();
        if (map != null) {
            // TiledSystem subscribes through GameCore even when only its map accessor is needed.
            try {
                var field = GameCore.class.getDeclaredField("engine");
                field.setAccessible(true);
                var previous = field.get(GameCore.INSTANCE);
                engine.addSystem(new EventSystem());
                try {
                    field.set(GameCore.INSTANCE, engine);
                    var tiled = new TiledSystem(null, world);
                    var mapField = TiledSystem.class.getDeclaredField("currMap");
                    mapField.setAccessible(true);
                    mapField.set(tiled, map);
                    engine.addSystem(tiled);
                } finally {
                    field.set(GameCore.INSTANCE, previous);
                }
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot initialize the isolated map accessor", failure);
            }
        }
        renderer.camera = new OrthographicCamera(20, 20);
        var system = new TriggerDebugRenderSystem(world, renderer.camera, renderer);
        engine.addSystem(system);
        return system;
    }

    private static void sensor(Body body, Shape shape) {
        body.createFixture(shape, 0).setSensor(true);
        shape.dispose();
    }

    private static void mapLine(RecordingRenderer renderer, int index, float x1, float y1, float x2, float y2) {
        var line = renderer.lines.get(index);
        float scale = GameConsts.UNIT_SCALE;
        near(line[0], x1 * scale, "Line " + index + " x1");
        near(line[1], y1 * scale, "Line " + index + " y1");
        near(line[2], x2 * scale, "Line " + index + " x2");
        near(line[3], y2 * scale, "Line " + index + " y2");
    }

    private static void removeLine(List<float[]> lines, float x1, float y1, float x2, float y2) {
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (Math.abs(line[0] - x1) < EPSILON && Math.abs(line[1] - y1) < EPSILON
                && Math.abs(line[2] - x2) < EPSILON && Math.abs(line[3] - y2) < EPSILON) {
                lines.remove(i);
                return;
            }
        }
        throw new AssertionError("Missing sensor line: " + x1 + "," + y1 + " -> " + x2 + "," + y2);
    }

    private static void near(float actual, float expected, String message) {
        check(Math.abs(actual - expected) < EPSILON, message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private static final class RecordingRenderer extends ShapeRenderer {
        final List<float[]> lines = new ArrayList<>();
        final List<float[]> circles = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        OrthographicCamera camera;
        int begins;
        int ends;
        boolean disposed;

        @Override public void begin(ShapeType type) {
            TriggerDebugRenderSystemTest.check(type == ShapeType.Line, "Only outline rendering is expected");
            begins++;
        }
        @Override public void end() { ends++; }
        @Override public void line(float x1, float y1, float z1, float x2, float y2, float z2, Color c1, Color c2) {
            lines.add(new float[]{x1, y1, x2, y2});
            colors.add(new Color(getColor()));
        }
        @Override public void circle(float x, float y, float radius, int segments) {
            TriggerDebugRenderSystemTest.check(segments == 32, "Sensor circle tessellation");
            circles.add(new float[]{x, y, radius});
            colors.add(new Color(getColor()));
        }
        @Override public void dispose() {
            super.dispose();
            disposed = true;
        }
        void clear() {
            lines.clear();
            circles.clear();
            colors.clear();
        }
    }

    private static void installConstructorStubs() {
        Gdx.app = (Application) Proxy.newProxyInstance(Application.class.getClassLoader(), new Class[]{Application.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            });
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class[]{Graphics.class},
            (proxy, method, args) -> 320);
        Gdx.gl = Gdx.gl20 = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(), new Class[]{GL20.class},
            (proxy, method, args) -> {
                if (method.getName().equals("glGetShaderiv") || method.getName().equals("glGetProgramiv")) {
                    int parameter = (Integer) args[1];
                    ((IntBuffer) args[2]).put(0, parameter == GL20.GL_COMPILE_STATUS || parameter == GL20.GL_LINK_STATUS ? 1 : 0);
                }
                if (method.getReturnType() == int.class) { return 1; }
                if (method.getReturnType() == boolean.class) { return false; }
                if (method.getReturnType() == String.class) { return ""; }
                return null;
            });
    }

    private static final class GeometryLoader extends FixedTmxMapLoader {
        GeometryLoader() { super(path -> new FileHandle("assets/" + path)); }

        TiledMap loadSavedMap() {
            var asset = io.mindspice.lute.engine.assets.Asset.Map.of("test_tiled.tmx");
            var file = resolve(asset.descriptor().fileName);
            root = xml.parse(file);
            return loadTiledMap(file, (BaseTiledMapLoader.Parameters) asset.descriptor().params, imagePath -> {
                try {
                    var image = javax.imageio.ImageIO.read(new java.io.File(imagePath));
                    return new TextureRegion(new DimensionTexture(image.getWidth(), image.getHeight()));
                } catch (java.io.IOException failure) {
                    throw new AssertionError("Cannot read image dimensions: " + imagePath, failure);
                }
            });
        }

        TiledMap load() {
            root = xml.parse("""
                <map orientation="orthogonal" width="10" height="10" tilewidth="32" tileheight="32">
                  <tileset firstgid="1" name="fixture" tilewidth="32" tileheight="16" tilecount="1" columns="1">
                    <image source="fixture.png" width="32" height="16"/>
                  </tileset>
                  <objectgroup name="NotTriggers"><object x="0" y="0"><polygon points="0,0 1,0 0,1"/></object></objectgroup>
                  <group name="Parent" offsetx="7" offsety="11">
                    <objectgroup name="Triggers" offsetx="3" offsety="5">
                      <object x="20" y="30" visible="0"><point/></object>
                      <object x="40" y="50" width="20" height="10" rotation="90"/>
                      <object x="80" y="70" width="40" height="20" rotation="90"><ellipse/></object>
                      <object x="140" y="90" rotation="90"><polygon points="0,0 20,0 10,10"/></object>
                      <object x="180" y="110" rotation="-90"><polyline points="0,0 10,0 10,20"/></object>
                      <object x="220" y="160" width="64" height="48" rotation="90" gid="1"/>
                      <object x="220" y="160" width="64" height="48" rotation="90" gid="2147483649"/>
                      <object x="220" y="160" width="64" height="48" rotation="90" gid="1073741825"/>
                      <object x="220" y="160" width="64" height="48" rotation="90" gid="3221225473"/>
                      <object x="300" y="20" width="20" height="10" rotation="90"><text>label</text></object>
                    </objectgroup>
                    <group name="Triggers" offsetx="3" offsety="5">
                      <objectgroup name="Nested" offsetx="-2" offsety="4"><object x="0" y="0"><point/></object></objectgroup>
                    </group>
                  </group>
                  <objectgroup name="Triggers"><object x="1" y="2"><point/></object></objectgroup>
                </map>
                """);
            return loadTiledMap(new FileHandle("fixture.tmx"), new BaseTiledMapLoader.Parameters(),
                ignored -> new TextureRegion(new DimensionTexture(32, 16)));
        }
    }

    private static final class DimensionTexture extends Texture {
        private final int width;
        private final int height;
        DimensionTexture(int width, int height) {
            super();
            this.width = width;
            this.height = height;
        }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
    }
}
