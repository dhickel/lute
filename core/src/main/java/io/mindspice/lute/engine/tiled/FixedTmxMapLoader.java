package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.MapObjects;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.XmlReader.Element;
import io.mindspice.lute.engine.assets.DirectTexture;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class FixedTmxMapLoader extends TmxMapLoader {
    private static final Pattern INDEXED_IMAGE = Pattern.compile("(.+)_([0-9]+)$");
    private final TextureAtlas objectAtlas;

    public FixedTmxMapLoader(FileHandleResolver resolver) {
        this(resolver, null);
    }

    public FixedTmxMapLoader(FileHandleResolver resolver, TextureAtlas objectAtlas) {
        super(resolver);
        this.objectAtlas = objectAtlas;
    }

    @Override
    protected Array<FileHandle> getTileSetDependencyFileHandle(
            Array<FileHandle> files, FileHandle tmxFile, Element tileset) {
        String source = tileset.getAttribute("source", null);
        Element definition = source == null ? tileset : xml.parse(getRelativeFileHandle(tmxFile, source));
        // Collection images are supplied by the borrowed atlas, never opened as source PNGs.
        if (objectAtlas != null && definition.getChildByName("image") == null) { return files; }
        return super.getTileSetDependencyFileHandle(files, tmxFile, tileset);
    }

    @Override
    public TiledMap load(String fileName, Parameters parameter) {
        FileHandle tmxFile = resolve(fileName);
        root = xml.parse(tmxFile);
        var textures = new ObjectMap<String, Texture>();
        try {
            for (FileHandle file : getDependencyFileHandles(tmxFile)) {
                if (textures.containsKey(file.path())) { continue; }
                Texture texture = new DirectTexture(file, null, parameter.generateMipMaps);
                // Retain ownership before filtering, which can also fail.
                textures.put(file.path(), texture);
                texture.setFilter(parameter.textureMinFilter, parameter.textureMagFilter);
            }
            TiledMap loaded = loadTiledMap(tmxFile, parameter, new ImageResolver.DirectImageResolver(textures));
            loaded.setOwnedResources(textures.values().toArray());
            return loaded;
        } catch (RuntimeException failure) {
            // loadTiledMap can fail before returning its map or taking texture ownership.
            for (Texture texture : textures.values()) {
                try { texture.dispose(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }

    @Override
    protected void loadObject(TiledMap map, MapObjects objects, Element element, float heightInPixels) {
        int count = objects.getCount();
        super.loadObject(map, objects, element, heightInPixels);
        if (objects.getCount() == count || !(objects.get(objects.getCount() - 1) instanceof TiledMapTileMapObject object)) { return; }
        var properties = element.getChildByName("properties");
        if (properties == null) { return; }
        for (var property : properties.getChildrenByName("property")) {
            if (!"animation".equals(property.getAttribute("name", null))
                || !"class".equals(property.getAttribute("type", null))
                || !"Animation".equals(property.getAttribute("propertytype", null))) { continue; }
            var tileAnimation = object.getTile().getProperties().get("animation", MapProperties.class);
            var placementAnimation = object.getProperties().get("animation", MapProperties.class);
            if (tileAnimation == null || placementAnimation == null) { return; }
            var merged = new MapProperties();
            merged.putAll(tileAnimation);
            var fields = property.getChildByName("properties");
            if (fields != null) {
                for (var field : fields.getChildrenByName("property")) {
                    String name = field.getAttribute("name");
                    if (placementAnimation.containsKey(name)) { merged.put(name, placementAnimation.get(name)); }
                }
            }
            object.getProperties().put("animation", merged);
        }
    }

    @Override
    protected void addStaticTiles(
            FileHandle tmxFile, ImageResolver imageResolver, TiledMapTileSet tileSet,
            Element element, Array<Element> tileElements, String name, int firstgid,
            int tilewidth, int tileheight, int spacing, int margin, int offsetX,
            int offsetY, String imageSource, int imageWidth, int imageHeight,
            FileHandle image) {
        String tilesetSource = element.getAttribute("source", null);
        FileHandle imageBase = tmxFile;
        // Image-collection paths are relative to the external tileset, not the map.
        if (element.getChildByName("image") == null && tilesetSource != null) {
            imageBase = getRelativeFileHandle(tmxFile, tilesetSource);
        }
        if (objectAtlas != null && image == null) {
            var regions = new ObjectMap<String, TextureRegion>();
            for (Element tile : tileElements) {
                Element tileImage = tile.getChildByName("image");
                if (tileImage == null) { continue; }
                String source = Path.of(tileImage.getAttribute("source").replace('\\', '/'))
                    .normalize().toString().replace('\\', '/');
                int extension = source.lastIndexOf('.');
                if (extension < 1 || source.startsWith("../") || Path.of(source).isAbsolute()) {
                    throw new GdxRuntimeException("Object image must be relative to the collection TSX: " + source);
                }
                String key = source.substring(0, extension);
                int index = -1;
                var indexed = INDEXED_IMAGE.matcher(key);
                if (indexed.matches()) {
                    key = indexed.group(1);
                    try { index = Integer.parseInt(indexed.group(2)); }
                    catch (NumberFormatException failure) {
                        throw new GdxRuntimeException("Invalid atlas frame index: " + source, failure);
                    }
                }
                TextureRegion region = objectAtlas.findRegion(key, index);
                if (region == null) {
                    throw new GdxRuntimeException("Missing object atlas region for '" + source + "' in tileset '" + name + "'");
                }
                if (region instanceof TextureAtlas.AtlasRegion packed && (packed.rotate
                        || packed.packedWidth != packed.originalWidth || packed.packedHeight != packed.originalHeight)) {
                    throw new GdxRuntimeException("Object atlas regions must not be rotated or trimmed: " + source);
                }
                if (region.getRegionWidth() != tileImage.getIntAttribute("width")
                        || region.getRegionHeight() != tileImage.getIntAttribute("height")) {
                    throw new GdxRuntimeException("Packed object dimensions differ from TSX image: " + source);
                }
                regions.put(getRelativeFileHandle(imageBase, tileImage.getAttribute("source")).path(), region);
            }
            imageResolver = regions::get;
        }
        super.addStaticTiles(imageBase, imageResolver, tileSet, element, tileElements,
                name, firstgid, tilewidth, tileheight, spacing, margin, offsetX,
                offsetY, imageSource, imageWidth, imageHeight, image);
    }
}
