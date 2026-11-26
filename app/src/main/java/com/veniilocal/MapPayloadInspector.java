package com.veniilocal;

/** Offline read-only inspection of manually exported LiDAR candidates. */
public final class MapPayloadInspector {
    private MapPayloadInspector() {}

    public static String inspect(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "Fichier vide : aucune donnée à analyser.";
        }
        String normalized = input.replace("\r\n", "\n").replace('\r', '\n');
        int[] ids = {1219, 1220, 800064};
        StringBuilder result = new StringBuilder(
            "Analyse locale, sans envoi réseau. Carte réelle non décodée.\n");
        boolean found = false;
        for (int id : ids) {
            String marker = "Code " + id + "\n";
            int markerStart = normalized.indexOf(marker);
            if (markerStart < 0 && normalized.startsWith("Code " + id + "\n")) {
                markerStart = 0;
            }
            if (markerStart < 0) {
                result.append("Code ").append(id).append(" : absent\n");
                continue;
            }
            found = true;
            int start = markerStart + marker.length();
            int end = normalized.length();
            for (int other : ids) {
                int next = normalized.indexOf("\nCode " + other + "\n", start);
                if (next >= 0 && next < end) end = next;
            }
            String payload = normalized.substring(start, end).trim();
            result.append("Code ").append(id).append(" : ")
                .append(payload.length()).append(" caractères");
            String shape = "format inconnu";
            if (payload.startsWith("{") && payload.endsWith("}")) {
                shape = "objet JSON possible";
            } else if (payload.startsWith("[") && payload.endsWith("]")) {
                shape = "tableau JSON possible";
            }
            result.append(" • ").append(shape).append("\n");
        }
        if (!found) result.append("Aucun bloc candidat reconnu dans ce fichier.\n");
        result.append("Aucune image de carte ne peut encore être produite de façon fiable.");
        return result.toString();
    }
}
