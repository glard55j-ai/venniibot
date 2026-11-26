package com.veniilocal;

import java.util.Arrays;
import java.util.Locale;

/**
 * Descriptive histogram of map bytes. Values are treated as unsigned bytes only.
 * No assumption that a value represents a wall, floor or room.
 */
public final class MapGridStatistics {
    private MapGridStatistics() {}

    public static String summarize(byte[] cells, int maxValues) {
        if (cells == null || cells.length == 0)
            throw new IllegalArgumentException("Grille absente ou vide");
        if (maxValues < 1 || maxValues > 256)
            throw new IllegalArgumentException("Limite de valeurs invalide");

        int[] histogram = new int[256];
        for (byte cell : cells) histogram[cell & 255]++;
        int unique = 0;
        for (int count : histogram) if (count > 0) unique++;

        Integer[] order = new Integer[256];
        for (int i = 0; i < 256; i++) order[i] = i;
        Arrays.sort(order, (left, right) -> {
            int compare = Integer.compare(histogram[right], histogram[left]);
            return compare != 0 ? compare : Integer.compare(left, right);
        });

        StringBuilder info = new StringBuilder();
        info.append("Analyse descriptive — ").append(cells.length)
            .append(" cellules, ").append(unique)
            .append(" valeur(s) distincte(s) de 0 à 255.");
        info.append("\nOctets les plus fréquents :");
        for (int i = 0; i < Math.min(unique, maxValues); i++) {
            int code = order[i];
            info.append("\n").append(code).append(" : ")
                .append(histogram[code]).append(" cellules (")
                .append(String.format(Locale.FRANCE, "%.1f", 100d * histogram[code] / cells.length))
                .append(" %)");
        }
        if (unique > maxValues) info.append("\n… et ").append(unique - maxValues).append(" autres valeurs.");
        info.append("\nCes nombres ne désignent PAS encore les murs ou les sols.");
        return info.toString();
    }
}
