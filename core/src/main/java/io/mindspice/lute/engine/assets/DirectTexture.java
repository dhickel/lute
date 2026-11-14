package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;

public final class DirectTexture extends Texture {
    public DirectTexture(FileHandle file, Format format, boolean useMipMaps) {
        super(file, format, useMipMaps);
    }

    @Override
    public void load(TextureData data) {
        try {
            super.load(data);
        } catch (RuntimeException failure) {
            // Texture's constructor calls load after allocating its GL handle, before
            // the caller can retain the Texture. Clean up at that ownership boundary.
            try { dispose(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
