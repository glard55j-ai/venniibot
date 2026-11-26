package com.veniilocal;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Local UI drafts ONLY. These records are never translated into robot commands,
 * maps, coordinates, schedules or remote requests.
 */
public final class KoomaDraftStore {
    public static final int LIMIT = 6;
    public static final String FAVORITES = "favorites";
    public static final String ZONES = "zones";
    private static final String PREFS = "kooma_ui_drafts_v1";
    private final SharedPreferences preferences;

    public static final class Draft {
        public final String name;
        public final String detail;
        public final int color;
        public Draft(String name, String detail, int color) {
            this.name = validateName(name);
            this.detail = detail == null ? "" : detail;
            if (this.detail.length() > 70) throw new IllegalArgumentException("Détail trop long");
            this.color = Math.max(0, Math.min(4, color));
        }
    }

    public KoomaDraftStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String validateName(String text) {
        if (text == null) throw new IllegalArgumentException("Nom absent");
        String name = text.trim();
        if (name.length() < 2 || name.length() > 35)
            throw new IllegalArgumentException("Entre 2 et 35 caractères sont nécessaires");
        for (int i = 0; i < name.length(); i++)
            if (Character.isISOControl(name.charAt(i)))
                throw new IllegalArgumentException("Caractères non autorisés");
        return name;
    }

    private static void validateCategory(String category) {
        if (!FAVORITES.equals(category) && !ZONES.equals(category))
            throw new IllegalArgumentException("Catégorie inconnue");
    }

    public synchronized List<Draft> read(String category) {
        validateCategory(category);
        ArrayList<Draft> results = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(category, "[]"));
            for (int i = 0; i < Math.min(array.length(), LIMIT); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                try {
                    results.add(new Draft(obj.optString("name", ""),
                        obj.optString("detail", ""), obj.optInt("color", 0)));
                } catch (IllegalArgumentException ignored) { /* Invalid stored entry */ }
            }
        } catch (Exception ignored) { /* Corrupt draft data: do not crash app. */ }
        return results;
    }

    public synchronized boolean add(String category, Draft draft) {
        List<Draft> list = read(category);
        if (list.size() >= LIMIT) return false;
        list.add(draft);
        return write(category, list);
    }

    public synchronized boolean replace(String category, int index, Draft draft) {
        List<Draft> list = read(category);
        if (index < 0 || index >= list.size()) return false;
        list.set(index, draft);
        return write(category, list);
    }

    public synchronized boolean delete(String category, int index) {
        List<Draft> list = read(category);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        return write(category, list);
    }

    private boolean write(String category, List<Draft> list) {
        validateCategory(category);
        try {
            JSONArray array = new JSONArray();
            for (Draft draft : list) {
                JSONObject obj = new JSONObject();
                obj.put("name", draft.name);
                obj.put("detail", draft.detail);
                obj.put("color", draft.color);
                array.put(obj);
            }
            return preferences.edit().putString(category, array.toString()).commit();
        } catch (Exception ignored) {
            return false;
        }
    }
}
