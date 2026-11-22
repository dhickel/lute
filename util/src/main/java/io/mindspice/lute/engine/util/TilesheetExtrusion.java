package io.mindspice.lute.engine.util;

import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class TilesheetExtrusion {
    private static final int BORDER = 1;
    private static final int SPACING = 2 * BORDER;

    private final Path raw;
    private final Path stage;
    private final Map<Path, Grid> generated = new HashMap<>();

    TilesheetExtrusion(Path raw, Path stage) {
        this.raw = raw;
        this.stage = stage;
    }

    void apply(Path definition, Element tileset, Element image, Path sheet) throws IOException {
        int width = integer(tileset, "tilewidth", 0, definition);
        int height = integer(tileset, "tileheight", 0, definition);
        int margin = integer(tileset, "margin", 0, definition);
        int spacing = integer(tileset, "spacing", 0, definition);
        if (width <= 0 || height <= 0 || margin < 0 || spacing < 0) {
            throw new IOException("Invalid tilesheet geometry (tile size, margin or spacing): " + definition);
        }

        // Keep the source name and layout in the filename: an image can also be used by an
        // image layer, or by another tileset with different tile geometry.
        Path padded = sheet.resolveSibling(sheet.getFileName() + ".tiles-" + width + "x" + height
            + "-m" + margin + "-s" + spacing + ".png");
        Path output = stage.resolve(raw.relativize(padded));
        Grid grid = generated.get(output);
        if (grid == null) {
            if (Files.exists(padded) || Files.exists(output)) {
                throw new IOException("Generated tilesheet path conflicts with an asset: " + padded);
            }
            if (!Files.isRegularFile(sheet)) { throw new IOException("Missing tilesheet: " + sheet); }
            BufferedImage input = ImageIO.read(sheet.toFile());
            if (input == null) { throw new IOException("Cannot decode tilesheet: " + sheet); }
            int columns = tileCount(input.getWidth(), width, margin, spacing);
            int rows = tileCount(input.getHeight(), height, margin, spacing);
            if (columns == 0 || rows == 0) {
                throw new IOException("No complete tiles fit the authored grid in " + sheet + " (" + definition + ")");
            }
            grid = new Grid(columns, rows, paddedSize(columns, width), paddedSize(rows, height));
            validateColumns(tileset, grid, definition);
            BufferedImage result = extrude(input, grid, width, height, margin, spacing);
            Files.createDirectories(output.getParent());
            if (!ImageIO.write(result, "png", output.toFile())) { throw new IOException("Cannot write tilesheet: " + output); }
            generated.put(output, grid);
        } else {
            validateColumns(tileset, grid, definition);
        }

        tileset.setAttribute("margin", Integer.toString(BORDER));
        tileset.setAttribute("spacing", Integer.toString(SPACING));
        tileset.setAttribute("columns", Integer.toString(grid.columns()));
        tileset.setAttribute("tilecount", Integer.toString(grid.columns() * grid.rows()));
        image.setAttribute("width", Integer.toString(grid.width()));
        image.setAttribute("height", Integer.toString(grid.height()));
        image.setAttribute("source", definition.getParent().relativize(padded).toString().replace('\\', '/'));
    }

    private static int integer(Element element, String attribute, int fallback, Path definition) throws IOException {
        if (!element.hasAttribute(attribute)) { return fallback; }
        try {
            return Integer.parseInt(element.getAttribute(attribute));
        } catch (NumberFormatException failure) {
            throw new IOException("Invalid tilesheet '" + attribute + "' in " + definition, failure);
        }
    }

    private static void validateColumns(Element tileset, Grid grid, Path definition) throws IOException {
        int authored = integer(tileset, "columns", grid.columns(), definition);
        if (authored != grid.columns()) {
            throw new IOException("Tilesheet columns in " + definition + " must match its image grid: expected "
                + grid.columns() + ", got " + authored);
        }
    }

    private static int tileCount(int size, int tileSize, int margin, int spacing) {
        long usable = size - 2L * margin;
        if (usable < tileSize) { return 0; }
        return (int) ((usable + spacing) / ((long) tileSize + spacing));
    }

    private static int paddedSize(int count, int tileSize) throws IOException {
        long size = count * ((long) tileSize + SPACING);
        if (size > Integer.MAX_VALUE) { throw new IOException("Padded tilesheet dimensions overflow"); }
        return (int) size;
    }

    private static BufferedImage extrude(BufferedImage input, Grid grid, int width, int height, int margin, int spacing) {
        BufferedImage output = new BufferedImage(grid.width(), grid.height(), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[(width + SPACING) * (height + SPACING)];
        int stride = width + SPACING;
        for (int row = 0; row < grid.rows(); row++) {
            for (int col = 0; col < grid.columns(); col++) {
                int x = margin + (int) (col * ((long) width + spacing));
                int y = margin + (int) (row * ((long) height + spacing));
                input.getRGB(x, y, width, height, pixels, stride + BORDER, stride);
                for (int line = 1; line <= height; line++) {
                    int start = line * stride;
                    pixels[start] = pixels[start + 1];
                    pixels[start + stride - 1] = pixels[start + stride - 2];
                }
                System.arraycopy(pixels, stride, pixels, 0, stride);
                System.arraycopy(pixels, height * stride, pixels, (height + 1) * stride, stride);
                output.setRGB(col * stride, row * (height + SPACING), stride, height + SPACING, pixels, 0, stride);
            }
        }
        return output;
    }

    private record Grid(int columns, int rows, int width, int height) { }
}
