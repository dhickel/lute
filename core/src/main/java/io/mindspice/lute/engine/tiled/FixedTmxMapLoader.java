package io.mindspice.lute.engine.tiled;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader.Element;
import io.mindspice.lute.engine.assets.DirectTexture;

public class FixedTmxMapLoader extends TmxMapLoader {

    public FixedTmxMapLoader(FileHandleResolver resolver) {
        super(resolver);
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
        super.addStaticTiles(imageBase, imageResolver, tileSet, element, tileElements,
                name, firstgid, tilewidth, tileheight, spacing, margin, offsetX,
                offsetY, imageSource, imageWidth, imageHeight, image);
    }
}
