package com.veniilocal;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Finds a complete MapEntity-like object in a locally selected JSON document.
 * It does not claim to know the precise network envelope of a Veniibot N1 Max.
 */
public final class MapDocumentParser {
    private MapDocumentParser() {}

    public static JSONObject find(String raw) throws JSONException {
        if (raw == null || raw.trim().isEmpty()) return null;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;
        return search(new JSONTokener(trimmed).nextValue(), 0, new int[]{0});
    }

    private static JSONObject search(Object item, int depth, int[] visited) throws JSONException {
        if (depth > 6 || visited[0]++ >= 100) return null;
        if (item instanceof JSONObject) {
            JSONObject obj = (JSONObject)item;
            Object width = obj.opt("width");
            Object height = obj.opt("height");
            Object map = obj.opt("map");
            if (width instanceof Number && height instanceof Number
                    && map instanceof String && !((String)map).isEmpty())
                return obj;

            for (String key : new String[]{"content", "data", "mapData", "mapEntity", "result",
                    "body", "payload", "map", "value"}) {
                Object child = obj.opt(key);
                JSONObject result = searchChild(child, depth + 1, visited);
                if (result != null) return result;
            }
        } else if (item instanceof JSONArray) {
            JSONArray array = (JSONArray)item;
            for (int i = 0; i < Math.min(array.length(), 24); i++) {
                JSONObject result = searchChild(array.opt(i), depth + 1, visited);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static JSONObject searchChild(Object child, int depth, int[] visited) throws JSONException {
        if (child instanceof JSONObject || child instanceof JSONArray)
            return search(child, depth, visited);
        if (!(child instanceof String) || depth > 6) return null;
        String text = ((String)child).trim();
        if (text.length() < 2 || (!text.startsWith("{") && !text.startsWith("["))) return null;
        try { return search(new JSONTokener(text).nextValue(), depth, visited); }
        catch (JSONException malformed) { return null; }
    }
}
