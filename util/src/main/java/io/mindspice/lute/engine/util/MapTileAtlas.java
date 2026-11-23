package io.mindspice.lute.engine.util;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.tiled.BaseTmxMapLoader;
import com.badlogic.gdx.tools.texturepacker.TexturePacker;
import com.badlogic.gdx.utils.XmlReader;
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static io.mindspice.lute.engine.util.TexturePackerTool.children;

/** Builds one pack job across all grid tilesets of a map. The object atlas is already staged. */
final class MapTileAtlas {
    static final String REGION_PREFIX = "__lute_tile/";
    private static final String LOCAL_IDS = "luteAtlasLocalIds";
    private static final int GID_MASK = 0x1fffffff; // libGDX's ~BaseTiledMapLoader.MASK_CLEAR
    // Tilesets are visited last to first: a sheet claims a placement by setting its qualified key;
    // a collection removes the placement only if still unclaimed. Retain first-use order for packing.
    private final LinkedHashMap<Integer, String> placements = new LinkedHashMap<>();
    private final LinkedHashMap<String, BufferedImage> images = new LinkedHashMap<>();

    MapTileAtlas(Path raw, Path map, Path stage) throws IOException {
        // Use the runtime's codec, including unsigned CSV GIDs and little-endian raw/gzip/zlib base64.
        var xml = new XmlReader();
        var root = xml.parse(new FileHandle(map.toFile()));
        var firstGids = new HashSet<Integer>();
        var externalSets = new HashMap<Path, Integer>();
        for (var ref : root.getChildrenByName("tileset")) {
            int first = ref.getIntAttribute("firstgid");
            if (first < 1 || !firstGids.add(first)) { throw new IOException("Invalid firstgid " + first + " in " + map); }
            if (ref.hasAttribute("source")) { externalSets.put(map.resolveSibling(ref.getAttribute("source")).normalize(), first); }
        }
        for (var layer : root.getChildrenByNameRecursively("layer")) {
            if (layer.getChildByName("data").getChildByName("chunk") != null) {
                throw new IOException("Chunked tile layers are not supported by the TMX loader: " + map);
            }
            for (int gid : BaseTmxMapLoader.getTileIds(layer, layer.getIntAttribute("width"), layer.getIntAttribute("height"))) {
                placements.put(gid & GID_MASK, null);
            }
        }
        var templates = new HashMap<Path, XmlReader.Element>();
        for (var group : root.getChildrenByNameRecursively("objectgroup")) {
            for (var object : group.getChildrenByName("object")) {
                int gid = (int)Long.parseLong(object.getAttribute("gid", "0")) & GID_MASK;
                if (object.hasAttribute("template")) {
                    Path template = TexturePackerTool.mapDependency(raw, map.getParent(), map.resolveSibling(object.getAttribute("template")));
                    var tx = templates.get(template);
                    if (tx == null) {
                        tx = xml.parse(new FileHandle(template.toFile()));
                        templates.put(template, tx);
                        TexturePackerTool.copy(template, stage.resolve(raw.relativize(template)));
                    }
                    var tile = tx.getChildByName("object");
                    if (gid == 0 && tile.hasAttribute("gid")) {
                        var set = tx.getChildByName("tileset");
                        Integer first = externalSets.get(template.resolveSibling(set.getAttribute("source")).normalize());
                        if (first == null) { throw new IOException("Template tileset is not in " + map + ": " + template); }
                        gid = first + ((int)Long.parseLong(tile.getAttribute("gid")) & GID_MASK) - set.getIntAttribute("firstgid", 1);
                    }
                }
                placements.put(gid, null);
            }
        }
        placements.remove(0);
    }

    void addCollection(Element reference, Element definition) {
        int first = Integer.parseInt(reference.getAttribute("firstgid"));
        for (Element tile : children(definition, "tile")) {
            // TmxMapLoader only creates a collection tile when it has an image. Holes do not claim GIDs.
            if (!children(tile, "image").isEmpty()) {
                placements.remove(first + Integer.parseInt(tile.getAttribute("id")), null);
            }
        }
    }

    private static String packedKey(int first, int localId) {
        // TexturePacker strips the final _<index> to produce (name, index).
        return REGION_PREFIX + first + "_" + localId;
    }

    void addSheet(Element reference, Element definition, Path padded) throws IOException {
        int first = Integer.parseInt(reference.getAttribute("firstgid"));
        int count = Integer.parseInt(definition.getAttribute("tilecount"));
        var ids = new TreeSet<Integer>();
        for (var placement : placements.entrySet()) {
            int localId = placement.getKey() - first;
            if (placement.getValue() == null && localId >= 0 && localId < count) {
                ids.add(localId);
                placement.setValue(packedKey(first, localId));
            }
        }

        var definitions = new HashMap<Integer, Element>();
        for (Element tile : children(definition, "tile")) { definitions.put(Integer.parseInt(tile.getAttribute("id")), tile); }
        var pending = new ArrayDeque<>(ids);
        while (!pending.isEmpty()) {
            int id = pending.removeFirst();
            // Native animations reference this sheet's static tiles, not the map-wide placement lookup.
            if (id < 0 || id >= count) {
                throw new IOException("Animation frame " + id + " outside tileset starting at " + first + ": " + padded);
            }
            var tile = definitions.get(id);
            if (tile == null) { continue; }
            for (Element animation : children(tile, "animation")) {
                for (Element frame : children(animation, "frame")) {
                    int frameId = Integer.parseInt(frame.getAttribute("tileid"));
                    if (ids.add(frameId)) { pending.addLast(frameId); }
                }
            }
        }
        // On the map reference, never the shared TSX: two maps can use different subsets and firstgids.
        reference.setAttribute(LOCAL_IDS, ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        if (ids.isEmpty()) { return; }
        int width = Integer.parseInt(definition.getAttribute("tilewidth"));
        int height = Integer.parseInt(definition.getAttribute("tileheight"));
        int columns = Integer.parseInt(definition.getAttribute("columns"));
        var sheet = ImageIO.read(padded.toFile());
        int[] pixels = new int[width * height];
        for (int id : ids) {
            int x = TilesheetExtrusion.BORDER + (id % columns) * (width + TilesheetExtrusion.SPACING);
            int y = TilesheetExtrusion.BORDER + (id / columns) * (height + TilesheetExtrusion.SPACING);
            // Don't retain entire source sheets via subimages or composite away transparent RGB.
            var image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
            sheet.getRGB(x, y, width, height, pixels, 0, width);
            image.setRGB(0, 0, width, height, pixels, 0, width);
            images.put(packedKey(first, id), image);
        }
    }

    void pack(Path atlasDirectory, String name) throws IOException {
        for (var placement : placements.entrySet()) {
            if (placement.getValue() == null) {
                throw new IOException("No tileset defines placed GID " + placement.getKey() + " in map " + name);
            }
        }
        if (images.isEmpty()) { return; }
        var settings = new TexturePacker.Settings();
        settings.maxWidth = settings.maxHeight = 2048;
        settings.fast = true;
        settings.alias = false;
        settings.ignoreBlankImages = false;
        settings.paddingX = settings.paddingY = TilesheetExtrusion.SPACING;
        settings.duplicatePadding = true; // two pixels between regions, one repeated RGBA edge on each side
        settings.bleed = false; // keep even the RGB of fully transparent pixels
        var packer = new TexturePacker(settings);
        for (String key : placements.values()) { packer.addImage(images.remove(key), key); }
        images.forEach((key, frame) -> packer.addImage(frame, key));
        // Append pages to the manifest's existing atlas; TexturePacker never overwrites a page PNG.
        packer.pack(atlasDirectory.toFile(), name);
    }
}
