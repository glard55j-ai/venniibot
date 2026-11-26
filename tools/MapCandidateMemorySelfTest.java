import com.veniilocal.MapCandidateMemory;

public final class MapCandidateMemorySelfTest {
    private static void ok(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        MapCandidateMemory.clear();
        ok(MapCandidateMemory.take() == null, "empty");
        ok(!MapCandidateMemory.offer(null), "null rejected");
        ok(!MapCandidateMemory.offer(""), "empty rejected");
        ok(!MapCandidateMemory.offer("X".repeat(1600001)), "size cap");
        ok(MapCandidateMemory.offer("Code 1219\n{\"map\":\"secret\"}"), "store");
        ok("Code 1219\n{\"map\":\"secret\"}".equals(MapCandidateMemory.take()), "read");
        ok(MapCandidateMemory.take() == null, "one-shot");
        ok(MapCandidateMemory.offer("first"), "first");
        ok(MapCandidateMemory.offer("second"), "overwrite");
        ok("second".equals(MapCandidateMemory.take()), "only newest");
        ok(MapCandidateMemory.offer("private"), "private");
        MapCandidateMemory.clear();
        ok(MapCandidateMemory.take() == null, "clear");
        System.out.println("MapCandidateMemorySelfTest OK");
    }
}
