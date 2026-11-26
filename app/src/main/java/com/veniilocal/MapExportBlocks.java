package com.veniilocal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Extracts bounded map candidates from KOOMA-I's voluntary raw LiDAR TXT export. */
public final class MapExportBlocks {
    private MapExportBlocks() {}

    public static final class Block {
        public final int id;
        public final String payload;
        Block(int id, String payload) {
            this.id = id;
            this.payload = payload;
        }
    }

    public static List<Block> extract(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        if (input.length() > 6 * 1024 * 1024)
            throw new IllegalArgumentException("Export TXT trop volumineux");
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        ArrayList<Block> blocks = new ArrayList<>();
        int current = -1;
        StringBuilder value = new StringBuilder();
        for (String line : lines) {
            int id = headerId(line);
            if (id >= 0) {
                if (current >= 0) store(blocks, current, value);
                if (blocks.size() >= 20) break;
                current = id;
                value.setLength(0);
            } else if (current >= 0) {
                if (value.length() != 0) value.append('\n');
                value.append(line);
            }
        }
        if (current >= 0 && blocks.size() < 20) store(blocks, current, value);
        return blocks;
    }

    private static int headerId(String line) {
        if ("Code 1219".equals(line)) return 1219;
        if ("Code 1220".equals(line)) return 1220;
        if ("Code 800064".equals(line)) return 800064;
        return -1;
    }

    private static void store(List<Block> blocks, int id, StringBuilder value) {
        String payload = value.toString().trim();
        if (!payload.isEmpty()) blocks.add(new Block(id, payload));
    }
}
