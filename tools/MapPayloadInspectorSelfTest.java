import com.veniilocal.MapPayloadInspector;

public final class MapPayloadInspectorSelfTest {
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String empty = MapPayloadInspector.inspect("");
        check(empty.contains("Fichier vide"), "empty input");
        String none = MapPayloadInspector.inspect("État : charge terminée\n02 h 35");
        check(none.contains("Aucun bloc candidat"), "unrelated history");
        String candidate = MapPayloadInspector.inspect(
            "KOOMA-I • données candidates non décodées\nCode 1219\n{\"points\":[]}\n\nCode 1220\n??\n\nCode 800064\n[1,2]\n\n");
        check(candidate.contains("Code 1219 : 1 bloc(s)"), "1219 candidate");
        check(candidate.contains("Code 1220 : 1 bloc(s)"), "1220 candidate");
        check(candidate.contains("Code 800064 : 1 bloc(s)"), "800064 candidate");
        check(!candidate.contains("points"), "no raw output");
        String repeat = MapPayloadInspector.inspect("Code 1219\nx\nCode 1219\ny");
        check(repeat.contains("2 bloc(s)"), "repeated candidates");
        String hex = MapPayloadInspector.inspect("Code 1219\\n0A1B2C3D4E5F");
        check(hex.contains("hexadécimal possible"), "hex candidate");
        String b64 = MapPayloadInspector.inspect("Code 1220\\nSGVsbG8gV29ybGQ=");
        check(b64.contains("Base64 possible"), "base64 candidate");
        check(!b64.contains("SGVsbG8"), "no raw base64 output");
        String unknown = MapPayloadInspector.inspect("Code 800064\\n?malformed!");
        check(unknown.contains("format non identifié"), "unknown format");
        System.out.println("MapPayloadInspectorSelfTest OK");
    }
}
