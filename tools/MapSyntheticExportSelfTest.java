import com.veniilocal.MapExportBlocks;
import com.veniilocal.MapGridDecoder;
import com.veniilocal.MapGridStatistics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Verifies the synthetic TXT export can be decoded entirely without the robot. */
public final class MapSyntheticExportSelfTest {
    private static void check(boolean pass, String what) {
        if (!pass) throw new AssertionError(what);
    }
    public static void main(String[] args) throws Exception {
        String example = Files.readString(
                Path.of("tools/samples/KOOMA-I-LiDAR-exemple-fictif.txt"), StandardCharsets.UTF_8);
        check(example.contains("FICTIVES"), "not a real map");
        List<MapExportBlocks.Block> blocks = MapExportBlocks.extract(example);
        check(blocks.size() == 2, "two sample blocks");
        check(blocks.get(0).id == 1219, "first id");
        String payload = blocks.get(0).payload;
        check(payload.contains("\"width\":16") && payload.contains("\"height\":12"), "dimensions");
        String opening = "\"map\":\"";
        int start = payload.indexOf(opening);
        check(start >= 0, "map field");
        start += opening.length();
        int end = payload.indexOf('"', start);
        check(end > start, "map terminator");
        byte[] cells = MapGridDecoder.decodeBase64Lz4(payload.substring(start, end), 16, 12);
        check(cells.length == 192, "size");
        check((cells[0] & 255) == 0, "first pixel");
        check((cells[1] & 255) == 16, "second pixel");
        check(MapGridStatistics.summarize(cells, 8).contains("192 cellules"), "statistics");
        System.out.println("MapSyntheticExportSelfTest OK");
    }
}
