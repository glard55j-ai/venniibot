import com.veniilocal.MapGridStatistics;

public final class MapGridStatisticsSelfTest {
    private static void yes(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        byte[] data = {0, 0, 0, (byte)255, (byte)255, 12};
        String result = MapGridStatistics.summarize(data, 2);
        yes(result.contains("6 cellules"), "count");
        yes(result.contains("3 valeur(s)"), "distinct count");
        yes(result.contains("0 : 3 cellules (50,0 %)"), "most common");
        yes(result.contains("255 : 2 cellules (33,3 %)"), "unsigned byte");
        yes(result.contains("1 autres valeurs"), "truncation");
        yes(result.contains("PAS encore les murs"), "unknown semantics");
        try {
            MapGridStatistics.summarize(new byte[0], 2);
            throw new AssertionError("empty rejected");
        } catch (IllegalArgumentException expected) { }
        try {
            MapGridStatistics.summarize(new byte[]{1}, 0);
            throw new AssertionError("bad limit rejected");
        } catch (IllegalArgumentException expected) { }
        System.out.println("MapGridStatisticsSelfTest OK");
    }
}
