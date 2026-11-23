package io.mindspice.lute.engine.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.maps.MapGroupLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapImageLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.maps.tiled.tiles.AnimatedTiledMapTile;
import com.badlogic.gdx.math.Matrix4;
import io.mindspice.lute.engine.assets.Asset;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.tiled.FixedTmxMapLoader;
import io.mindspice.lute.engine.util.TexturePackerTool;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** A real generator -> AssetService -> GL draw, independent of locally provisioned game art. */
public final class MapTileAtlasTest {
    private static final int TILE = 16;
    private static final int[] COLORS = {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffff00};
    private static final String MAP = "maps/mixed/mixed.tmx";

    public static void main(String[] args) throws IOException {
        Path fixture = Files.createTempDirectory("lute-mixed-sheets");
        try {
            Path raw = fixture.resolve("raw_assets");
            Path assets = fixture.resolve("assets");
            writeFixture(raw);
            writeOverlapFixture(raw);
            TexturePackerTool.main(new String[]{raw.toString(), assets.toString()});
            var config = new Lwjgl3ApplicationConfiguration();
            config.setInitialVisible(false);
            config.disableAudio(true);
            new Lwjgl3Application(new ApplicationAdapter() {
                @Override public void create() {
                    try { exercise(assets); }
                    catch (IOException ex) { throw new AssertionError(ex); }
                    finally { Gdx.app.exit(); }
                }
            }, config);
        } finally { new FileHandle(fixture.toFile()).deleteDirectory(); }
    }

    private static void exercise(Path assets) throws IOException {
        var files = new FileHandle(assets.toFile());
        var direct = new FixedTmxMapLoader(files::child).load(MAP);
        byte[] reference;
        try { reference = render(direct, 8); }
        finally { direct.dispose(); }
        // If any sheet dependency is requested, even for the object-only/nested layer, loading will fail.
        try (var paths = Files.walk(assets)) {
            for (Path file : paths.filter(p -> p.toString().contains(".tiles-")).toList()) { Files.delete(file); }
        }
        var service = new AssetService(files::child);
        try {
            var atlas = service.load(new Asset.Atlas("mixed", "atlases/mixed.atlas"));
            Set<String> packed = new TreeSet<>();
            for (var region : atlas.getRegions()) { packed.add(region.name + ":" + region.index); }
            check(atlas.getRegions().size == 9 && packed.equals(Set.of(
                "__lute_tile/1:997", "__lute_tile/1025:997", "__lute_tile/2049:997", "__lute_tile/3073:997",
                "__lute_tile/1025:2", "__lute_tile/1025:6", "__lute_tile/1025:8", "__lute_tile/3073:8", "__lute_tile/2049:17")),
                "Expected exactly the placed terrain, tile object and animation frames, got " + packed);
            check(atlas.getTextures().size == 1, "Four interleaved sheets must share one page when the subset fits");
            var page = atlas.getTextures().first();
            var map = service.load(new Asset.Map("mixed", MAP));
            check(Arrays.equals(reference, render(map, 1)), "All rendered pixels and transforms must match direct TMX");
            var group = (MapGroupLayer)map.getLayers().get("nested");
            var animLayer = (TiledMapTileLayer)group.getLayers().get("animation");
            var animation = (AnimatedTiledMapTile)animLayer.getCell(0, 0).getTile();
            check(animation.getId() == 1027 && Arrays.equals(animation.getAnimationIntervals(), new int[]{80, 120}),
                "Animation ID and durations");
            check(animation.getFrameTiles()[0].getId() == 1031 && animation.getFrameTiles()[1].getId() == 1033
                    && animation.getFrameTiles()[1].getTextureRegion() == atlas.findRegion("__lute_tile/1025", 8),
                "Unplaced frames retain their GIDs and borrow the atlas");
            var props = group.getLayers().get("props").getObjects();
            var templated = (TiledMapTileMapObject)props.get("fromTemplate");
            check(templated.getTile().getId() == 2049 + 17, "Resolve template-local GID against the map's external TSX");
            var object = (TiledMapTileMapObject)props.get("marker");
            var tile = object.getTile();
            check(tile.getId() == 3081 && tile.getTextureRegion().getTexture() == page && object.isFlipHorizontally()
                    && object.getRotation() == 45 && tile.getObjects().getCount() == 1
                    && "wall".equals(tile.getProperties().get("material")), "Tile object properties, transform and collision");
            var background = ((TiledMapImageLayer)group.getLayers().get("sky")).getTextureRegion().getTexture();
            check(background != page && background.getTextureObjectHandle() != 0, "Image layers retain a direct dependency");
            service.unload(TiledMap.class, "mixed");
            check(background.getTextureObjectHandle() == 0, "Map unload releases standalone image layers");
            check(page.getTextureObjectHandle() != 0, "The map borrows, not owns, the atlas texture");
            check(atlas.getRegions().removeValue(atlas.findRegion("__lute_tile/1", 997), true), "Remove the region for GID 998");
            try {
                service.load(new Asset.Map("mixed", MAP));
                throw new AssertionError("Missing selected region must fail, not drop the cell or upload the original");
            } catch (com.badlogic.gdx.utils.GdxRuntimeException expected) {
                check(expected.getCause().getMessage().contains("terrain atlas region for GID 998"), "Identify the missing GID");
            }
            service.unload(TextureAtlas.class, "mixed");
            check(page.getTextureObjectHandle() == 0, "Unloading the atlas releases its texture");
            exerciseOverlap(service);
            System.out.println("MapTileAtlasTest: four 512x512 sheets -> nine used regions, one page; same framebuffer, 8 -> 1 draws");
        } finally { service.dispose(); }
    }

    private static void exerciseOverlap(AssetService service) {
        var atlas = service.load(new Asset.Atlas("overlap", "atlases/overlap.atlas"));
        var map = service.load(new Asset.Map("overlap", "maps/overlap/overlap.tmx"));
        var grids = (TiledMapTileLayer)map.getLayers().get("grids");
        var first = map.getTileSets().getTileSet("first");
        var animation = (AnimatedTiledMapTile)grids.getCell(0, 0).getTile();
        var frame = animation.getFrameTiles()[0];
        var placed = grids.getCell(1, 0).getTile();
        check(frame.getId() == 5 && placed.getId() == 5 && frame != placed && frame == first.getTile(5)
                && placed == map.getTileSets().getTileSet("later").getTile(5),
            "GID 5 is the animation's sheet-local frame but the placed cell belongs to the next sheet");
        check(frame.getTextureRegion() == atlas.findRegion("__lute_tile/1", 4)
                && placed.getTextureRegion() == atlas.findRegion("__lute_tile/5", 0), "Borrow the two distinct GID 5 regions");
        render(map, 1); // Frame must draw red and placed tile green, even if both qualified region names exist.

        var sparse = (TiledMapTileLayer)map.getLayers().get("sparse");
        check(sparse.getCell(0, 0) != null && sparse.getCell(0, 0).getTile() == first.getTile(3)
                && first.getTile(3).getTextureRegion() == atlas.findRegion("__lute_tile/1", 2),
            "Collection has no local ID 0: GID 3 falls back to the earlier grid");
        var collection = map.getTileSets().getTileSet("collection");
        var member = collection.getTile(4);
        check(member != null && sparse.getCell(1, 0) != null && sparse.getCell(1, 0).getTile() == member
                && member.getTextureRegion() == atlas.findRegion("member"), "Collection local ID 1 wins GID 4");
        check(sparse.getCell(2, 0) != null && sparse.getCell(2, 0).getTile() == collection.getTile(6),
            "For GID 6 the collection declared last wins over the grid with the larger firstgid");
    }

    private static byte[] render(TiledMap map, int draws) {
        var layer = (TiledMapTileLayer)map.getLayers().get(0);
        int tileSize = (int)layer.getTileWidth(), width = layer.getWidth() * tileSize;
        var renderer = new OrthogonalTiledMapRenderer(map);
        var target = new FrameBuffer(Pixmap.Format.RGBA8888, width, tileSize, false);
        try {
            target.begin();
            Gdx.gl.glClearColor(0, 0, 0, 0);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            renderer.setView(new Matrix4().setToOrtho2D(0, 0, width, tileSize), 0, 0, width, tileSize);
            renderer.render(new int[]{0});
            check(((SpriteBatch)renderer.getBatch()).renderCalls == draws, "Expected " + draws + " texture batches");
            var pixels = Pixmap.createFromFrameBuffer(0, 0, width, tileSize);
            try {
                for (int i = 0; i < layer.getWidth(); i++) {
                    check(pixels.getPixel(i * tileSize + tileSize / 2, tileSize / 2) == Integer.rotateLeft(COLORS[i % 4], 8),
                        "Wrong sheet in cell " + i);
                }
                byte[] data = new byte[width * tileSize * 4];
                pixels.getPixels().get(data);
                return data;
            } finally { pixels.dispose(); }
        } finally { target.end(); renderer.dispose(); target.dispose(); }
    }

    private static void writeOverlapFixture(Path raw) throws IOException {
        var dir = Files.createDirectories(raw.resolve("maps/overlap"));
        var objects = Files.createDirectory(dir.resolve("objects"));
        Files.writeString(dir.resolve("overlap.manifest.json"), """
            {"atlasAsset":"overlap.atlas","mapAsset":"overlap/overlap.tmx","musicAssets":{},"soundAssets":{}}
            """);
        writePixelRow(dir.resolve("first.png"), COLORS[2], COLORS[2], COLORS[2], COLORS[2], COLORS[0]);
        writePixelRow(dir.resolve("later.png"), COLORS[1], COLORS[2]);
        writePixelRow(objects.resolve("member.png"), COLORS[3]);
        Files.writeString(objects.resolve("pack.json"), "{combineSubdirectories:true}");
        Files.writeString(objects.resolve("collection.tsx"), """
            <tileset name="collection" tilewidth="1" tileheight="1" columns="0" tilecount="2">
              <tile id="1"><image source="member.png" width="1" height="1"/></tile>
              <tile id="3"><image source="member.png" width="1" height="1"/></tile>
            </tileset>
            """);
        Files.writeString(dir.resolve("overlap.tmx"), """
            <map orientation="orthogonal" width="3" height="1" tilewidth="1" tileheight="1">
              <tileset firstgid="1" name="first" tilewidth="1" tileheight="1" columns="5" tilecount="5">
                <image source="first.png" width="5" height="1"/>
                <tile id="0"><animation><frame tileid="4" duration="80"/></animation></tile>
              </tileset>
              <tileset firstgid="5" name="later" tilewidth="1" tileheight="1" columns="2" tilecount="2">
                <image source="later.png" width="2" height="1"/>
              </tileset>
              <tileset firstgid="3" source="objects/collection.tsx"/>
              <layer name="grids" width="2" height="1"><data encoding="csv">1,5</data></layer>
              <layer name="sparse" width="3" height="1"><data encoding="csv">3,4,6</data></layer>
            </map>
            """);
    }

    private static void writePixelRow(Path path, int... colors) throws IOException {
        var image = new BufferedImage(colors.length, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, colors.length, 1, colors, 0, colors.length);
        ImageIO.write(image, "png", path.toFile());
    }

    private static void writeFixture(Path raw) throws IOException {
        var maps = Files.createDirectories(raw.resolve("maps/mixed"));
        var sets = Files.createDirectories(raw.resolve("tilesets"));
        Files.createDirectories(raw.resolve("data"));
        Files.createDirectories(raw.resolve("audio"));
        Files.writeString(raw.resolve("game.settings.json"), """
            {"mapsPath":"maps/","atlasPath":"atlases/","dataPath":"data/","soundPath":"audio/",
             "musicPath":"audio/","projectPath":"game.tiled-project"}
            """);
        Files.writeString(raw.resolve("game.tiled-project"), "{}");
        Files.writeString(maps.resolve("mixed.manifest.json"), """
            {"atlasAsset":"mixed.atlas","mapAsset":"mixed/mixed.tmx","musicAssets":{},"soundAssets":{}}
            """);
        var sprite = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(sprite, "png", maps.resolve("sky.png").toFile());
        for (String input : new String[]{"character/player", "character/basic_npc", "enemies/brainless_undead",
                "equipment/basic/body", "equipment/basic/head", "equipment/basic/feet", "equipment/basic/hands",
                "weapons/melee/basic_sword", "weapons/ranged/basic_bow", "objects"}) {
            var dir = Files.createDirectories(raw.resolve("sprites").resolve(input));
            ImageIO.write(sprite, "png", dir.resolve("frame_00.png").toFile());
        }
        for (int i = 0; i < 4; i++) {
            var sheet = new BufferedImage(512, 512, BufferedImage.TYPE_INT_ARGB);
            int x = (997 % 32) * TILE, y = (997 / 32) * TILE;
            for (int h = 0; h < TILE; h++) {
                for (int w = 0; w < TILE; w++) { sheet.setRGB(x + w, y + h, w + h < 6 ? 0xffffffff : COLORS[i]); }
            }
            ImageIO.write(sheet, "png", sets.resolve(i + ".png").toFile());
            Files.writeString(sets.resolve(i + ".tsx"), """
                <tileset name="sheet%s" tilewidth="16" tileheight="16" columns="32" tilecount="1024">
                  <image source="%s.png" width="512" height="512"/>
                  <tile id="2"><animation><frame tileid="6" duration="80"/><frame tileid="8" duration="120"/></animation></tile>
                  <tile id="8"><properties><property name="material" value="wall"/></properties>
                    <objectgroup><object id="91" x="0" y="0" width="8" height="7"/></objectgroup></tile>
                </tileset>
                """.formatted(i, i));
        }
        Files.writeString(maps.resolve("object.tx"), """
            <template><tileset firstgid="1" source="../../tilesets/2.tsx"/><object gid="18"/></template>
            """);
        Files.writeString(raw.resolve(MAP), """
            <map orientation="orthogonal" width="8" height="1" tilewidth="16" tileheight="16">
              <tileset firstgid="1" source="../../tilesets/0.tsx"/>
              <tileset firstgid="1025" source="../../tilesets/1.tsx"/>
              <tileset firstgid="2049" source="../../tilesets/2.tsx"/>
              <tileset firstgid="3073" source="../../tilesets/3.tsx"/>
              <layer name="stream" width="8" height="1"><data encoding="csv">998,2022,3046,4070,%d,%d,%d,4070</data></layer>
              <group name="nested"><layer name="animation" width="1" height="1"><data encoding="base64">AwQAAA==</data></layer>
                <objectgroup name="props"><object id="1" name="marker" gid="%d" x="32" y="16" rotation="45"/>
                  <object id="2" name="fromTemplate" template="object.tx" x="16" y="16"/></objectgroup>
                <imagelayer name="sky"><image source="sky.png" width="2" height="2"/></imagelayer>
              </group>
            </map>
            """.formatted(0x80000000L | 998, 0x40000000L | 2022, 0x20000000L | 3046, 0x80000000L | 3081));
    }

    private static void check(boolean ok, String message) { if (!ok) { throw new AssertionError(message); } }
}
