package com.alaaeltaweel.thikrallah.cards;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** تخزين "كروتي": الكروت اللي المستخدم عدّلها وحفظها (بتتخزن كنصوص وإعدادات، مش صور). */
public final class MyCardsStore {

    private MyCardsStore() {}

    private static final String PREF_KEY = "my_cards_v1";
    private static final int MAX_CARDS = 200;

    public static final class Saved {
        public final String id;
        public final long created;
        public final CardState state;

        Saved(String id, long created, CardState state) {
            this.id = id;
            this.created = created;
            this.state = state;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    /** كل الكروت المحفوظة، الأحدث أولًا. */
    public static synchronized List<Saved> loadAll(Context c) {
        List<Saved> out = new ArrayList<Saved>();
        try {
            String raw = prefs(c).getString(PREF_KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                CardState s = new CardState();
                s.headline = o.optString("headline", "");
                s.body = o.optString("body", "");
                s.details = o.optString("details", "");
                s.sender = o.optString("sender", "");
                s.styleIndex = o.optInt("style", 0);
                s.paletteIndex = o.optInt("palette", 0);
                s.fontIndex = o.optInt("font", 0);
                s.textColor = o.optInt("color", 0xFFFFFFFF);
                s.sizeScale = (float) o.optDouble("scale", 1.0);
                s.portrait = o.optBoolean("portrait", false);
                out.add(new Saved(o.optString("id", ""), o.optLong("created", 0L), s));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static Saved find(Context c, String id) {
        if (id == null) return null;
        for (Saved s : loadAll(c)) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    /** بتحفظ كارت جديد (id = null) أو بتحدّث كارت موجود. بترجع الـ id. */
    public static synchronized String save(Context c, String id, CardState s) {
        try {
            List<Saved> all = loadAll(c);
            String useId = (id == null || id.length() == 0) ? UUID.randomUUID().toString() : id;
            long created = System.currentTimeMillis();
            List<Saved> next = new ArrayList<Saved>();
            for (Saved old : all) {
                if (old.id.equals(useId)) {
                    created = old.created;
                } else {
                    next.add(old);
                }
            }
            next.add(0, new Saved(useId, created, s.copy()));
            while (next.size() > MAX_CARDS) next.remove(next.size() - 1);
            write(c, next);
            return useId;
        } catch (Exception e) {
            return null;
        }
    }

    public static synchronized void delete(Context c, String id) {
        try {
            List<Saved> next = new ArrayList<Saved>();
            for (Saved old : loadAll(c)) {
                if (!old.id.equals(id)) next.add(old);
            }
            write(c, next);
        } catch (Exception ignored) {
        }
    }

    private static void write(Context c, List<Saved> list) throws Exception {
        JSONArray arr = new JSONArray();
        for (Saved s : list) {
            JSONObject o = new JSONObject();
            o.put("id", s.id);
            o.put("created", s.created);
            o.put("headline", s.state.headline);
            o.put("body", s.state.body);
            o.put("details", s.state.details);
            o.put("sender", s.state.sender);
            o.put("style", s.state.styleIndex);
            o.put("palette", s.state.paletteIndex);
            o.put("font", s.state.fontIndex);
            o.put("color", s.state.textColor);
            o.put("scale", (double) s.state.sizeScale);
            o.put("portrait", s.state.portrait);
            arr.put(o);
        }
        prefs(c).edit().putString(PREF_KEY, arr.toString()).apply();
    }
}
