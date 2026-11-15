package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;

final class DirectAtlasLoader {
    private DirectAtlasLoader() { }

    static TextureAtlas load(FileHandle file) {
        var data = new TextureAtlasData(file, file.parent(), false);
        try {
            for (var page : data.getPages()) {
                page.texture = new DirectTexture(page.textureFile, page.format, page.useMipMaps);
            }
            // Keep page ownership until filtering, wrapping, and region processing finish.
            return new TextureAtlas(data);
        } catch (RuntimeException failure) {
            for (var page : data.getPages()) {
                if (page.texture == null) { continue; }
                try { page.texture.dispose(); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
}
