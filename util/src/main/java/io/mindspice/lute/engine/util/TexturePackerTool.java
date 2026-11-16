package io.mindspice.lute.engine.util;

import com.badlogic.gdx.tools.texturepacker.TexturePacker;

public class TexturePackerTool {

    static void main(String[] args) {
        String inputDir = "assets/objects";
        String outputDir ="assets/objects/packed";
        String packFileName = "objects";


        TexturePacker.process(inputDir, outputDir, packFileName);

    }
}
