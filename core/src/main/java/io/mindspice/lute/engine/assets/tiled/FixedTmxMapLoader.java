package io.mindspice.lute.engine.assets.tiled;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.ImageResolver;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader.Element;

public class FixedTmxMapLoader extends TmxMapLoader {

    public FixedTmxMapLoader(FileHandleResolver resolver) {
        super(resolver);
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
