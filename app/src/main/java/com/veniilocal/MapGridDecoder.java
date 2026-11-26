package com.veniilocal;

import java.util.Base64;

/** Read-only decoder for Base64 + LZ4 map payloads found in the Venii 1.2.9 APK.
 * Byte meanings and map orientation are not yet known. Never drives a robot.
 */
public final class MapGridDecoder {
    private static final int MAX_CELLS = 4 * 1024 * 1024;
    private static final int MAX_COMPRESSED_BYTES = 5 * 1024 * 1024;

    private MapGridDecoder() {}

    public static byte[] decodeBase64Lz4(String encoded, int width, int height) {
        if (encoded == null || encoded.isEmpty()) throw new IllegalArgumentException("Contenu de carte absent");
        if (width <= 0 || height <= 0 || (long) width * height > MAX_CELLS)
            throw new IllegalArgumentException("Dimensions de carte invalides ou trop grandes");
        if (encoded.length() > 8 * 1024 * 1024)
            throw new IllegalArgumentException("Carte encodée trop volumineuse");
        byte[] compressed;
        try { compressed = Base64.getDecoder().decode(encoded.replaceAll("\\s", "")); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Base64 invalide", e); }
        if (compressed.length > MAX_COMPRESSED_BYTES)
            throw new IllegalArgumentException("Carte compressée trop volumineuse");
        return decodeLz4Block(compressed, width * height);
    }

    /** Strict LZ4 raw-block decompressor: bounded output, no network or files. */
    static byte[] decodeLz4Block(byte[] in, int expectedBytes) {
        if (in == null || expectedBytes <= 0 || expectedBytes > MAX_CELLS)
            throw new IllegalArgumentException("Taille de sortie invalide");
        byte[] out = new byte[expectedBytes];
        int src = 0, dst = 0;
        while (src < in.length) {
            int token = in[src++] & 0xFF;
            int literals = token >>> 4;
            if (literals == 15) {
                int more;
                do {
                    if (src == in.length) throw new IllegalArgumentException("Longueur littérale tronquée");
                    more = in[src++] & 0xFF;
                    if (literals > expectedBytes - more) throw new IllegalArgumentException("Taille littérale excessive");
                    literals += more;
                } while (more == 255);
            }
            if (literals > in.length - src || literals > expectedBytes - dst)
                throw new IllegalArgumentException("Bloc LZ4 littéral invalide");
            System.arraycopy(in, src, out, dst, literals);
            src += literals;
            dst += literals;
            if (src == in.length) break;
            if (in.length - src < 2) throw new IllegalArgumentException("Décalage LZ4 tronqué");
            int offset = (in[src++] & 0xFF) | ((in[src++] & 0xFF) << 8);
            if (offset == 0 || offset > dst) throw new IllegalArgumentException("Décalage LZ4 invalide");
            int match = (token & 15) + 4;
            if ((token & 15) == 15) {
                int more;
                do {
                    if (src == in.length) throw new IllegalArgumentException("Longueur de répétition tronquée");
                    more = in[src++] & 0xFF;
                    if (match > expectedBytes - more) throw new IllegalArgumentException("Répétition excessive");
                    match += more;
                } while (more == 255);
            }
            if (match > expectedBytes - dst) throw new IllegalArgumentException("Décompression dépasse les dimensions");
            for (int i = 0; i < match; i++) out[dst + i] = out[dst - offset + i];
            dst += match;
        }
        if (dst != expectedBytes) throw new IllegalArgumentException("Taille décompressée incohérente avec largeur × hauteur");
        return out;
    }
}
