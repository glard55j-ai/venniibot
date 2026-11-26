import com.veniilocal.MapExportBlocks;
import java.util.List;

public final class MapExportBlocksSelfTest {
    private static void check(boolean yes, String label) {
        if (!yes) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(MapExportBlocks.extract("sans carte").isEmpty(), "no map codes");
        String export = "KOOMA-I • données candidates non décodées\r\n"
            + "Code 1219\r\n{\"width\":2,\"height\":2,\"map\":\"QQ==\"}\r\n\r\n"
            + "Code 1220\r\n{\"content\":{\"width\":1}}\r\n"
            + "Code 800064\r\nabc123\r\n\r\n";
        List<MapExportBlocks.Block> found = MapExportBlocks.extract(export);
        check(found.size() == 3, "three candidates");
        check(found.get(0).id == 1219, "first id");
        check(found.get(0).payload.contains("\"width\""), "first content");
        check(found.get(1).id == 1220, "second id");
        check(found.get(2).payload.equals("abc123"), "third content");
        check(MapExportBlocks.extract("Code 1219\n\n").isEmpty(), "empty payload");
        check(MapExportBlocks.extract("Code 1220\nfoo\nCode 1220\nbar").size() == 2,
            "repeated blocks");
        check(MapExportBlocks.extract("Code 12190\nfoo").isEmpty(), "no loose code matches");
        System.out.println("MapExportBlocksSelfTest OK");
    }
}
