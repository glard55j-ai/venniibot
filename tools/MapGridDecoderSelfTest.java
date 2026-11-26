import com.veniilocal.MapGridDecoder;
import java.util.Arrays;
import java.util.Base64;

public final class MapGridDecoderSelfTest {
    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
    }
    private static void rejects(String input, int w, int h, String label) {
        try { MapGridDecoder.decodeBase64Lz4(input, w, h); throw new AssertionError(label); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
    public static void main(String[] args) {
        String plain = Base64.getEncoder().encodeToString(new byte[] {0x40,1,2,3,4});
        check(Arrays.equals(MapGridDecoder.decodeBase64Lz4(plain, 2, 2),new byte[]{1,2,3,4}), "literal-only block");
        String repeated = Base64.getEncoder().encodeToString(new byte[]{0x10, 65, 0x01,0});
        check(Arrays.equals(MapGridDecoder.decodeBase64Lz4(repeated,5,1),new byte[]{65,65,65,65,65}), "overlapping match");
        rejects("###",2,2,"bad Base64");
        rejects(plain, 0, 2, "bad dimensions");
        rejects(plain, 5000, 5000, "dimension cap");
        rejects(plain,2,3,"wrong uncompressed length");
        rejects(Base64.getEncoder().encodeToString(new byte[]{0x10,65,0,0}),5,1,"zero offset");
        rejects(Base64.getEncoder().encodeToString(new byte[]{(byte)0xF0}),1,1,"truncated extension");
        System.out.println("MapGridDecoderSelfTest OK");
    }
}
