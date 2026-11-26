package com.veniilocal;

/** Offline, read-only summary of exported LiDAR candidates. No real map format is assumed. */
public final class MapPayloadInspector {
    private MapPayloadInspector() {}

    public static String inspect(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Fichier vide : aucune donnée à analyser.";
        }
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        int[] ids = {1219, 1220, 800064};
        int[] sizes = new int[ids.length];
        int[] blocks = new int[ids.length];
        String[] formats = new String[ids.length];
        String[] lines = normalized.split("\n", -1);
        int current = -1;
        StringBuilder payload = new StringBuilder();
        for (String line : lines) {
            int matching = -1;
            for (int i = 0; i < ids.length; i++) {
                if (line.equals("Code " + ids[i])) { matching = i; break; }
            }
            if (matching >= 0) {
                if (current >= 0) record(payload, current, sizes, formats);
                current = matching;
                blocks[current]++;
                payload.setLength(0);
            } else if (current >= 0) {
                if (payload.length() > 0) payload.append('\n');
                payload.append(line);
            }
        }
        if (current >= 0) record(payload, current, sizes, jsonLike);

        StringBuilder result = new StringBuilder(
            "Analyse locale uniquement. Aucune carte réelle décodée.\n");
        boolean found = false;
        for (int i = 0; i < ids.length; i++) {
            result.append("Code ").append(ids[i]).append(" : ");
            if (blocks[i] == 0) {
                result.append("absent\n");
            } else {
                found = true;
                result.append(blocks[i]).append(" bloc(s), dernier contenu : ")
                    .append(sizes[i]).append(" caractères • ")
                    .append(formats[i])
                    .append("\n");
            }
        }
        if (!found) result.append("Aucun bloc candidat reconnu.\n");
        result.append("Aucun contenu brut affiché ou transmis. La cartographie reste expérimentale.");
        return result.toString();
    }

    private static void record(StringBuilder payload, int index, int[] sizes, String[] formats) {
        String value = payload.toString().trim();
        sizes[index] = value.length();
        // These are superficial signatures, not validated map decoders.
        if ((value.startsWith("{") && value.endsWith("}"))
                || (value.startsWith("[") && value.endsWith("]"))) {
            formats[index] = "JSON possible, non validé";
        } else if (value.length() >= 8 && value.length() % 2 == 0
                && value.matches("[0-9a-fA-F]+")) {
            formats[index] = "hexadécimal possible, non décodé";
        } else if (value.length() >= 8 && value.length() % 4 == 0
                && value.matches("[A-Za-z0-9+/]+={0,2}")) {
            formats[index] = "Base64 possible, non décodé";
        } else {
            formats[index] = "format non identifié";
        }
    }
}
