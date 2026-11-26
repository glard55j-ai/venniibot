package com.veniilocal;

/**
 * Lossless byte-value visualisation only: 0..255 -> grey level, not occupancy semantics.
 * Downsamples to avoid large Android bitmaps. No robot communication.
 */
public final class MapGridRasterizer {
    private MapGridRasterizer() {}
    public static final class Preview {
        public final int width, height;
        public final int[] argb;
        private Preview(int width, int height, int[] argb) {
            this.width = width;
            this.height = height;
            this.argb = argb;
        }
    }
    public static Preview rasterize(byte[] cells, int width, int height, int maxSide) {
        if (cells == null || width <= 0 || height <= 0 || (long) width * height != cells.length)
            throw new IllegalArgumentException("Grille de carte incohérente");
        if (maxSide < 32 || maxSide > 1024)
            throw new IllegalArgumentException("Limite d'affichage invalide");
        double scale = Math.min(1d, Math.min((double) maxSide / width, (double) maxSide / height));
        int outW = Math.max(1, (int)Math.round(width * scale));
        int outH = Math.max(1, (int)Math.round(height * scale));
        int[] argb = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            int originalY = Math.min(height - 1, (int)((long)y * height / outH));
            for (int x = 0; x < outW; x++) {
                int originalX = Math.min(width - 1, (int)((long)x * width / outW));
                int brightness = cells[originalY * width + originalX] & 255;
                argb[y * outW + x] = 0xFF000000 | brightness << 16 | brightness << 8 | brightness;
            }
        }
        return new Preview(outW, outH, argb);
    }
}
