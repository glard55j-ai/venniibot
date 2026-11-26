package com.veniilocal;

/**
 * Exploratory display of map bytes: no known wall/floor labels or orientation.
 * Runs fully offline with strict output caps and no robot commands.
 */
public final class MapGridRasterizer {
    private MapGridRasterizer() {}

    public static final int GRAYSCALE = 0;
    public static final int INVERTED = 1;
    public static final int FALSE_COLOR = 2;

    public static final class Sample {
        public final int x, y, value;
        Sample(int x, int y, int value) {
            this.x = x; this.y = y; this.value = value;
        }
    }

    public static final class Preview {
        public final int width, height, rawWidth, rawHeight, quarterTurns, palette;
        public final int[] argb;
        private Preview(int width, int height, int rawWidth, int rawHeight,
                        int quarterTurns, int palette, int[] argb) {
            this.width = width;
            this.height = height;
            this.rawWidth = rawWidth;
            this.rawHeight = rawHeight;
            this.quarterTurns = quarterTurns;
            this.palette = palette;
            this.argb = argb;
        }

        /** Maps a displayed pixel back to the original byte coordinate. */
        public Sample sample(byte[] raw, int x, int y) {
            if (raw == null || (long)rawWidth * rawHeight != raw.length
                    || x < 0 || y < 0 || x >= width || y >= height) return null;
            int rotatedW = quarterTurns % 2 == 0 ? rawWidth : rawHeight;
            int rotatedH = quarterTurns % 2 == 0 ? rawHeight : rawWidth;
            int xx = (int)((long)x * rotatedW / width);
            int yy = (int)((long)y * rotatedH / height);
            int origX, origY;
            switch (quarterTurns) {
                case 1: origX = yy; origY = rawHeight - 1 - xx; break;
                case 2: origX = rawWidth - 1 - xx; origY = rawHeight - 1 - yy; break;
                case 3: origX = rawWidth - 1 - yy; origY = xx; break;
                default: origX = xx; origY = yy; break;
            }
            return new Sample(origX, origY, raw[origY * rawWidth + origX] & 255);
        }
    }

    public static Preview rasterize(byte[] cells, int width, int height, int maxSide) {
        return rasterize(cells, width, height, maxSide, 0, GRAYSCALE);
    }

    public static Preview rasterize(byte[] cells, int width, int height, int maxSide,
                                    int quarterTurns, int palette) {
        if (cells == null || width <= 0 || height <= 0 || (long)width * height != cells.length)
            throw new IllegalArgumentException("Grille de carte incohérente");
        if (maxSide < 32 || maxSide > 1024)
            throw new IllegalArgumentException("Limite d'affichage invalide");
        if (quarterTurns < 0 || quarterTurns > 3 || palette < 0 || palette > 2)
            throw new IllegalArgumentException("Mode d'aperçu invalide");

        int rotatedW = quarterTurns % 2 == 0 ? width : height;
        int rotatedH = quarterTurns % 2 == 0 ? height : width;
        double scale = Math.min(1d, Math.min((double) maxSide / rotatedW,
                                                 (double) maxSide / rotatedH));
        int outW = Math.max(1, (int)Math.round(rotatedW * scale));
        int outH = Math.max(1, (int)Math.round(rotatedH * scale));
        Preview preview = new Preview(outW, outH, width, height, quarterTurns, palette,
                new int[outW * outH]);
        for (int y = 0; y < outH; y++)
            for (int x = 0; x < outW; x++) {
                Sample cell = preview.sample(cells, x, y);
                preview.argb[y * outW + x] = color(cell.value, palette);
            }
        return preview;
    }

    private static int color(int value, int palette) {
        int v = palette == INVERTED ? 255 - value : value;
        if (palette != FALSE_COLOR)
            return 0xFF000000 | v << 16 | v << 8 | v;

        // False color is a visual gradient, NOT a classification of walls.
        int r, g, b;
        if (v < 128) {
            r = 20 + v * 50 / 127;
            g = 30 + v * 180 / 127;
            b = 65 + v * 125 / 127;
        } else {
            r = 70 + (v - 128) * 185 / 127;
            g = 210 + (v - 128) * 40 / 127;
            b = 190 - (v - 128) * 140 / 127;
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
