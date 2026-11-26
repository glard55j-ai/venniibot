package com.veniilocal;

/**
 * One-shot, short-lived in-memory handoff of user-selected LiDAR candidates.
 * No files, no disk, no Android intent extras, no network or automatic sharing.
 */
public final class MapCandidateMemory {
    private static final int MAX_CHARS = 1_600_000;
    private static final long LIFE_NS = 5L * 60L * 1_000_000_000L;
    private static String pending;
    private static long storedAt;

    private MapCandidateMemory() {}

    public static synchronized boolean offer(String text) {
        pending = null;
        if (text == null || text.isEmpty() || text.length() > MAX_CHARS) return false;
        pending = text;
        storedAt = System.nanoTime();
        return true;
    }

    public static synchronized String take() {
        String value = pending;
        pending = null;
        if (value == null || System.nanoTime() - storedAt >= LIFE_NS) return null;
        return value;
    }

    public static synchronized void clear() { pending = null; }
}
