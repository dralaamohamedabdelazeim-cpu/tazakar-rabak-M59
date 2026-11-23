package com.alaaeltaweel.thikrallah.Notification;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * الفوائد اليومية: حديث / اسم من أسماء الله / دعاء وذكر / آية.
 * بتظهر في أوقات متنوعة خلال اليوم (عشوائية أو موزعة بالتساوي) وبعيد عن أوقات الصلاة.
 *
 * المصادر:
 *  - الأحاديث: موسوعة الأحاديث النبوية hadeethenc.com (النص بدون تعديل + ذكر المصدر)
 *  - أسماء الله: قاعدة بيانات rn0x/Names_Of_Allah_Json (رخصة MIT) المبنية على كتاب د. سعيد القحطاني
 *  - الأدعية: حصن المسلم الموجود جوه التطبيق
 *  - الآيات: api.alquran.cloud
 */
public class DailyHadithHelper {
    public static final String DATA_TYPE = "daily_hadith";
    public static final String EXTRA_SLOT = "hadith_slot";
    public static final int MAX_SLOTS = 6;
    public static final int REQUEST_CODE_BASE = 3300;      // 3300..3305
    private static final int LEGACY_CODE_BASE = 27;        // نسخة الأوقات الثابتة القديمة: 27..29
    private static final int LEGACY_SLOTS = 3;

    // مفاتيح الإعدادات
    public static final String K_ENABLED = "dailyContent_enabled";
    public static final String K_COUNT = "dailyContent_count";
    public static final String K_START = "dailyContent_start";
    public static final String K_END = "dailyContent_end";
    public static final String K_RANDOM = "dailyContent_random";
    public static final String T_HADITH = "hadith";
    public static final String T_NAMES = "names";
    public static final String T_DUA = "dua";
    public static final String T_AYAH = "ayah";
    private static final String[] TYPES = {T_HADITH, T_NAMES, T_DUA, T_AYAH};

    private static final String TAG = "DailyContent";
    private static final String HADEETH_BASE = "https://hadeethenc.com/api/v1";
    private static final String QURAN_BASE = "https://api.alquran.cloud/v1";
    private static final String CACHE_FILE = "daily_hadiths.json";
    private static final String CHANNEL_ID = "daily_content_v1";
    private static final int NOTIFICATION_ID = 7430;
    private static final int MIN_CHARS = 20;
    private static final int MAX_CHARS = 600;
    private static final int CACHE_VERSION = 3;
    private static final int PREFETCH_DAYS = 3;
    private static final int PRAYER_BUFFER_MIN = 20;       // مفيش إشعار قبل/بعد الصلاة بـ20 دقيقة
    private static final long ALARM_BUDGET_MS = 8000L;
    private static final long PREFETCH_BUDGET_MS = 60000L;

    private static final String[] USEFUL_KEYWORDS = {
            "الفضائل", "الآداب", "الأخلاق", "الرقائق", "المواعظ", "الزهد", "تزكية",
            "الأذكار", "الدعاء", "الدعوات", "التوبة", "الصبر", "الصدقة", "الوالدين",
            "صلة الرحم", "الجار", "الإحسان", "الرحمة", "الصدق", "الأمانة", "الحياء", "التواضع"
    };

    private static final AtomicBoolean prefetchBusy = new AtomicBoolean(false);
    private static final Object FILE_LOCK = new Object();
    private static JSONArray namesCache = null;

    // ------------------------------------------------------------------
    // الإعدادات
    // ------------------------------------------------------------------
    private static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c);
    }

    public static List<String> enabledTypes(SharedPreferences p) {
        List<String> l = new ArrayList<String>();
        for (String t : TYPES) {
            if (p.getBoolean("dailyContent_" + t, true)) l.add(t);
        }
        return l;
    }

    public static int getCount(SharedPreferences p) {
        int n = 4;
        try { n = Integer.parseInt(p.getString(K_COUNT, "4").trim()); } catch (Exception ignored) { }
        return Math.max(1, Math.min(MAX_SLOTS, n));
    }

    private static int parseMinutes(String hhmm, int fallback) {
        try {
            String[] a = hhmm.split(":", 3);
            return Integer.parseInt(a[0].trim()) * 60 + Integer.parseInt(a[1].trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** نلغي منبهات النسخة القديمة (3 أوقات ثابتة) لو لسه متسجلة */
    public static void cancelLegacyAlarms(Context ctx, AlarmManager am) {
        try {
            for (int i = 0; i < LEGACY_SLOTS; i++) {
                Intent in = new Intent(ctx, ThikrAlarmReceiver.class);
                in.putExtra("com.alaaeltaweel.thikrallah.datatype", DATA_TYPE);
                in.putExtra(EXTRA_SLOT, i + 1);
                PendingIntent pi = PendingIntent.getBroadcast(ctx, LEGACY_CODE_BASE + i, in,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    am.cancel(pi);
                    pi.cancel();
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "cancelLegacyAlarms: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // حساب أوقات اليوم (عشوائية أو موزعة بالتساوي، بعيد عن الصلوات)
    // ------------------------------------------------------------------
    /** dayOffset: 0 = النهارده، 1 = بكرة. بيرجع وقت كل إشعار بالملي ثانية. */
    public static long[] timesForDay(Context ctx, int dayOffset) {
        SharedPreferences p = prefs(ctx);
        int count = getCount(p);
        int startMin = parseMinutes(p.getString(K_START, "08:00"), 8 * 60);
        int endMin = parseMinutes(p.getString(K_END, "22:00"), 22 * 60);
        if (endMin - startMin < 60) {      // فترة غير منطقية: نرجع للافتراضي
            startMin = 8 * 60;
            endMin = 22 * 60;
        }
        boolean random = p.getBoolean(K_RANDOM, true);
        List<Integer> prayers = prayerMinutes(ctx);

        long day = epochDay() + dayOffset;
        Calendar midnight = Calendar.getInstance();
        midnight.set(Calendar.HOUR_OF_DAY, 0);
        midnight.set(Calendar.MINUTE, 0);
        midnight.set(Calendar.SECOND, 0);
        midnight.set(Calendar.MILLISECOND, 0);
        midnight.add(Calendar.DAY_OF_YEAR, dayOffset);

        int span = endMin - startMin;
        int[] mins = new int[count];
        for (int i = 0; i < count; i++) {
            // أول إشعار قريب من بداية الفترة، وآخر إشعار قريب من نهايتها، والباقي موزع بينهم
            int base = count == 1 ? startMin
                    : startMin + (int) Math.round((double) span * i / (count - 1));
            int t = base;
            if (random) {
                Random r = new Random(day * 1009L + i * 7919L + count);
                if (i == 0) {
                    t = base + r.nextInt(11);                       // 0..10 دقايق بعد البداية
                } else if (i == count - 1) {
                    t = base - r.nextInt(11);                       // 0..10 دقايق قبل النهاية
                } else {
                    int jitter = (int) ((span / (count - 1)) * 0.2);   // ±20% من المسافة بين الإشعارات
                    t = base - jitter + r.nextInt(2 * jitter + 1);
                }
            }
            t = avoidPrayers(t, prayers, startMin, endMin);
            if (i > 0 && t - mins[i - 1] < 15) {                    // مانخلّيش إشعارين لزق في بعض
                t = avoidPrayers(mins[i - 1] + 15, prayers, startMin, endMin);
            }
            mins[i] = t;
        }
        long[] out = new long[count];
        for (int i = 0; i < count; i++) {
            out[i] = midnight.getTimeInMillis() + mins[i] * 60000L;
        }
        return out;
    }

    private static int avoidPrayers(int t, List<Integer> prayers, int lo, int hi) {
        for (int iter = 0; iter < 12; iter++) {
            int clash = -1;
            for (int pm : prayers) {
                if (Math.abs(t - pm) < PRAYER_BUFFER_MIN) {
                    clash = pm;
                    break;
                }
            }
            if (clash < 0) break;
            int after = clash + PRAYER_BUFFER_MIN;
            int before = clash - PRAYER_BUFFER_MIN;
            boolean afterOk = after <= hi;
            boolean beforeOk = before >= lo;
            if (afterOk && (!beforeOk || Math.abs(after - t) <= Math.abs(t - before))) t = after;
            else if (beforeOk) t = before;
            else t = after;
        }
        return Math.min(Math.max(t, 0), 24 * 60 - 1);
    }

    private static List<Integer> prayerMinutes(Context ctx) {
        List<Integer> l = new ArrayList<Integer>();
        try {
            PrayTime pr = PrayTime.instancePrayTime(ctx);
            pr.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
            String[] times = pr.getPrayerTimes(ctx);
            int[] idx = {0, 2, 3, 5, 6};   // الفجر، الظهر، العصر، المغرب، العشاء
            if (times != null) {
                for (int i : idx) {
                    if (i >= times.length || times[i] == null) continue;
                    if (times[i].equalsIgnoreCase(pr.getInvalidTime())) continue;
                    l.add(parseMinutes(times[i], -1000));
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "prayerMinutes failed: " + e.getMessage());
        }
        return l;
    }

    // ------------------------------------------------------------------
    // نقطة الدخول من المنبه
    // ------------------------------------------------------------------
    public static void handleAlarm(Context ctx, final BroadcastReceiver.PendingResult pending, final int slot) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        new MyAlarmsManager(app).UpdateAllApplicableAlarms();   // نسجل منبهات بكرة
                    } catch (Exception e) {
                        Log.e(TAG, "reschedule failed: " + e.getMessage());
                    }
                    SharedPreferences p = prefs(app);
                    if (p.getBoolean(K_ENABLED, true)) {
                        JSONObject rec = contentFor(app, epochDay(), slot);
                        if (rec != null) showNotification(app, rec, slot);
                        else Log.d(TAG, "nothing to show now");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "handleAlarm failed: " + e.getMessage());
                } finally {
                    if (pending != null) {
                        try { pending.finish(); } catch (Exception ignored) { }
                    }
                }
                prefetch(app);
            }
        }, "DailyContentAlarm").start();
    }

    // ------------------------------------------------------------------
    // تحميل مسبق (أحاديث وآيات الأيام الجاية)
    // ------------------------------------------------------------------
    public static void prefetchAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        SharedPreferences p = prefs(app);
        long last = p.getLong("daily_hadith_last_prefetch", 0);
        if (System.currentTimeMillis() - last < 20 * 60 * 1000L) return;
        p.edit().putLong("daily_hadith_last_prefetch", System.currentTimeMillis()).apply();
        new Thread(new Runnable() {
            @Override
            public void run() {
                prefetch(app);
            }
        }, "DailyContentPrefetch").start();
    }

    private static void prefetch(Context app) {
        if (!prefetchBusy.compareAndSet(false, true)) return;
        try {
            SharedPreferences p = prefs(app);
            if (!p.getBoolean(K_ENABLED, true)) return;
            List<String> types = enabledTypes(p);
            if (types.isEmpty()) return;
            int count = getCount(p);
            long deadline = System.currentTimeMillis() + PREFETCH_BUDGET_MS;
            long today = epochDay();
            for (long d = today; d < today + PREFETCH_DAYS; d++) {
                for (int slot = 1; slot <= count; slot++) {
                    String type = typeFor(types, d, slot);
                    if (!isNetworkType(type)) continue;
                    if (System.currentTimeMillis() > deadline) return;
                    JSONObject cache = loadCache(app);
                    JSONObject c = cache.optJSONObject("days").optJSONObject(dayKey(d, slot));
                    if (c != null && type.equals(c.optString("type"))) continue;
                    JSONObject rec = pickByType(app, cache, type, d, slot, deadline);
                    if (rec == null) return;    // غالبًا مفيش إنترنت
                    putDay(app, dayKey(d, slot), rec);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "prefetch stopped: " + e.getMessage());
        } finally {
            prefetchBusy.set(false);
        }
    }

    // ------------------------------------------------------------------
    // اختيار المحتوى
    // ------------------------------------------------------------------
    private static String dayKey(long day, int slot) {
        return day + "_" + slot;
    }

    private static boolean isFriday(long day) {
        return (day + 4) % 7 == 5;     // اليوم صفر في التقويم كان خميس
    }

    private static boolean isNetworkType(String type) {
        return T_HADITH.equals(type) || T_AYAH.equals(type);
    }

    private static String typeFor(List<String> types, long day, int slot) {
        return types.get((int) ((day + slot) % types.size()));
    }

    private static JSONObject contentFor(Context app, long day, int slot) {
        SharedPreferences p = prefs(app);
        List<String> types = enabledTypes(p);
        if (types.isEmpty()) return null;
        String type = typeFor(types, day, slot);

        JSONObject cache = loadCache(app);
        JSONObject days = cache.optJSONObject("days");
        JSONObject cached = days.optJSONObject(dayKey(day, slot));
        if (cached != null && type.equals(cached.optString("type"))) return cached;

        try {
            JSONObject rec = pickByType(app, cache, type, day, slot, System.currentTimeMillis() + ALARM_BUDGET_MS);
            if (rec != null) {
                if (isNetworkType(type)) putDay(app, dayKey(day, slot), rec);
                return rec;
            }
        } catch (Exception e) {
            Log.d(TAG, "pick " + type + " failed: " + e.getMessage());
        }

        // مفيش إنترنت: نجرب الأنواع المحلية المفعّلة (أسماء الله / الدعاء)
        for (String t : types) {
            if (t.equals(type) || isNetworkType(t)) continue;
            try {
                JSONObject rec = pickByType(app, cache, t, day, slot, 0);
                if (rec != null) return rec;
            } catch (Exception ignored) { }
        }
        // أو أحاديث/آيات محفوظة قبل كده من نفس النوع
        try {
            boolean fri = isFriday(day);
            JSONArray prefer = new JSONArray();
            JSONArray any = new JSONArray();
            Iterator<String> it = days.keys();
            while (it.hasNext()) {
                JSONObject o = days.getJSONObject(it.next());
                if (!type.equals(o.optString("type"))) continue;
                any.put(o);
                if (o.optBoolean("friday", false) == fri) prefer.put(o);
            }
            JSONArray list = prefer.length() > 0 ? prefer : any;
            if (list.length() > 0) return list.getJSONObject((int) ((day * 3 + slot) % list.length()));
        } catch (Exception e) {
            Log.d(TAG, "cache fallback failed: " + e.getMessage());
        }
        return null;
    }

    private static JSONObject pickByType(Context app, JSONObject cache, String type, long day, int slot,
                                         long deadline) throws Exception {
        if (T_NAMES.equals(type)) return pickName(app, day, slot);
        if (T_DUA.equals(type)) return pickDua(app, day, slot);
        if (T_AYAH.equals(type)) return pickAyah(day, slot, deadline);
        // حديث
        if (isFriday(day)) {
            JSONObject rec = pickFridayHadith(app, cache, day, slot, deadline);
            if (rec != null) return rec;
            Log.d(TAG, "no friday hadith found, falling back to a regular one");
        }
        return pickNormalHadith(app, cache, day, slot, deadline);
    }

    // ---------------- أسماء الله (من ملف داخل التطبيق) ----------------
    private static JSONArray loadNames(Context app) throws Exception {
        if (namesCache != null) return namesCache;
        InputStream in = app.getAssets().open("asma_allah.json");
        ByteArrayBuffer buf = new ByteArrayBuffer();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) buf.write(b, 0, n);
        in.close();
        namesCache = new JSONArray(new String(buf.toByteArray(), "UTF-8"));
        return namesCache;
    }

    private static JSONObject pickName(Context app, long day, int slot) throws Exception {
        JSONArray names = loadNames(app);
        int len = names.length();
        if (len == 0) return null;
        JSONObject n = names.getJSONObject((int) ((day * 7 + (slot - 1) * 13) % len));
        JSONObject rec = new JSONObject();
        rec.put("type", T_NAMES);
        rec.put("id", "n" + n.optString("id"));
        rec.put("name", n.optString("n"));
        rec.put("text", n.optString("m"));
        rec.put("evidence", n.optString("e", ""));
        rec.put("evidence_source", n.optString("s", ""));
        return rec;
    }

    // ---------------- حصن المسلم (قاعدة بيانات داخل التطبيق) ----------------
    private static SQLiteDatabase openHisnDb(Context app) throws Exception {
        File dst = new File(app.getCacheDir(), "hisnul_daily.sqlite3");
        if (!dst.exists() || dst.length() < 1000) {
            InputStream in = app.getAssets().open("hisnul.sqlite3");
            OutputStream out = new FileOutputStream(dst);
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            out.close();
            in.close();
        }
        return SQLiteDatabase.openDatabase(dst.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    private static JSONObject pickDua(Context app, long day, int slot) throws Exception {
        SQLiteDatabase db = openHisnDb(app);
        try {
            String where = " FROM dua d JOIN dua_group g ON g._id = d.group_id"
                    + " WHERE length(d.ar_dua) BETWEEN 15 AND 500 AND d.ar_dua NOT LIKE 'يقرأ%'";
            Cursor c = db.rawQuery("SELECT COUNT(*)" + where, null);
            int count = 0;
            if (c.moveToFirst()) count = c.getInt(0);
            c.close();
            if (count == 0) return null;
            int idx = (int) ((day * 11 + (slot - 1) * 17) % count);
            c = db.rawQuery("SELECT d._id, d.ar_dua, g.ar_title" + where
                    + " ORDER BY d._id LIMIT 1 OFFSET " + idx, null);
            JSONObject rec = null;
            if (c.moveToFirst()) {
                rec = new JSONObject();
                rec.put("type", T_DUA);
                rec.put("id", "d" + c.getString(0));
                rec.put("text", c.getString(1).trim());
                rec.put("source", c.getString(2).replace("أذ كار", "أذكار").trim());
            }
            c.close();
            return rec;
        } finally {
            db.close();
        }
    }

    // ---------------- آية ----------------
    private static JSONObject pickAyah(long day, int slot, long deadline) throws Exception {
        long g = day * MAX_SLOTS + (slot - 1);
        for (int attempt = 0; attempt < 6; attempt++) {
            int n = (int) (((g * 131L + attempt * 977L) % 6236L) + 1);
            JSONObject root = new JSONObject(httpGet(QURAN_BASE + "/ayah/" + n + "/quran-uthmani", deadline));
            JSONObject data = root.optJSONObject("data");
            if (data == null) continue;
            String text = data.optString("text", "").trim();
            int numInSurah = data.optInt("numberInSurah", 0);
            JSONObject surah = data.optJSONObject("surah");
            String surahName = surah != null ? surah.optString("name", "") : "";
            if (numInSurah <= 1) continue;                  // أول آية بتيجي ومعاها البسملة
            if (text.length() < 60 || text.length() > 350) continue;
            JSONObject rec = new JSONObject();
            rec.put("type", T_AYAH);
            rec.put("id", "a" + n);
            rec.put("text", text);
            rec.put("source", "(" + surahName + " - الآية " + numInSurah + ")");
            return rec;
        }
        return null;
    }

    // ---------------- أحاديث (موسوعة الأحاديث النبوية) ----------------
    private static JSONObject pickFridayHadith(Context app, JSONObject cache, long day, int slot,
                                               long deadline) throws Exception {
        JSONArray pool = cache.optJSONArray("fridayIdsV2");
        if (pool == null || pool.length() == 0) {
            pool = fetchFridayIds(deadline);
            cache.put("fridayIdsV2", pool);
            saveMeta(app, null, pool);
        }
        int len = pool.length();
        if (len == 0) return null;

        Set<String> used = usedIds(cache.optJSONObject("days"), day, 0);
        int start = (int) (((day / 7) * MAX_SLOTS + (slot - 1)) % len);
        int tries = Math.min(len, 8);
        for (int k = 0; k < tries; k++) {
            String id = pool.getString((start + k) % len);
            if (used.contains(id)) continue;
            JSONObject d = new JSONObject(httpGet(HADEETH_BASE + "/hadeeths/one/?language=ar&id=" + id, deadline));
            JSONObject rec = toHadithRecord(d);
            if (rec != null) {
                rec.put("friday", true);
                return rec;
            }
        }
        return null;
    }

    private static JSONObject pickNormalHadith(Context app, JSONObject cache, long day, int slot,
                                               long deadline) throws Exception {
        JSONArray cats = cache.optJSONArray("catsV2");
        if (cats == null || cats.length() == 0) {
            cats = fetchUsefulCategoryIds(deadline);
            cache.put("catsV2", cats);
            saveMeta(app, cats, null);
        }
        int n = cats.length();
        if (n == 0) return null;

        Set<String> used = usedIds(cache.optJSONObject("days"), day, 5);
        long g = day * MAX_SLOTS + (slot - 1);
        for (int attempt = 0; attempt < 5; attempt++) {
            String catId = cats.getString((int) ((g + attempt) % n));
            JSONArray ids = fetchHadithIds(catId, deadline);
            int len = ids.length();
            if (len == 0) continue;
            int start = (int) ((g / n) % len);
            int tries = Math.min(len, 6);
            for (int k = 0; k < tries; k++) {
                String id = ids.getString((start + k) % len);
                if (used.contains(id)) continue;
                JSONObject d = new JSONObject(httpGet(HADEETH_BASE + "/hadeeths/one/?language=ar&id=" + id, deadline));
                JSONObject rec = toHadithRecord(d);
                if (rec != null) {
                    rec.put("friday", false);
                    return rec;
                }
            }
        }
        return null;
    }

    private static Set<String> usedIds(JSONObject days, long day, int window) {
        Set<String> used = new HashSet<String>();
        if (days == null) return used;
        Iterator<String> it = days.keys();
        while (it.hasNext()) {
            String key = it.next();
            try {
                long kd = Long.parseLong(key.split("_")[0]);
                if (Math.abs(kd - day) <= window) {
                    JSONObject o = days.optJSONObject(key);
                    if (o != null && T_HADITH.equals(o.optString("type"))) used.add(o.optString("id"));
                }
            } catch (Exception ignored) { }
        }
        return used;
    }

    private static JSONObject toHadithRecord(JSONObject d) throws Exception {
        String text = d.optString("hadeeth", "").trim();
        String grade = d.optString("grade", "");
        if (!grade.contains("صحيح")) return null;
        if (text.length() < MIN_CHARS || text.length() > MAX_CHARS) return null;
        JSONObject rec = new JSONObject();
        rec.put("type", T_HADITH);
        rec.put("id", d.optString("id"));
        rec.put("text", text);                                    // بدون أي تعديل
        rec.put("source", d.optString("attribution", "").trim());
        rec.put("grade", grade);
        return rec;
    }

    private static boolean hasUsefulKeyword(String title) {
        if (title == null) return false;
        for (String k : USEFUL_KEYWORDS) {
            if (title.contains(k)) return true;
        }
        return false;
    }

    private static boolean isUsefulCategory(String id, Map<String, String> parents, Map<String, String> titles) {
        String cur = id;
        for (int depth = 0; depth < 8 && cur != null && !cur.isEmpty() && !"null".equals(cur); depth++) {
            if (hasUsefulKeyword(titles.get(cur))) return true;
            cur = parents.get(cur);
        }
        return false;
    }

    private static JSONArray fetchUsefulCategoryIds(long deadline) throws Exception {
        JSONArray arr = new JSONArray(httpGet(HADEETH_BASE + "/categories/list/?language=ar", deadline));
        Map<String, String> parents = new HashMap<String, String>();
        Map<String, String> titles = new HashMap<String, String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            String id = c.optString("id");
            parents.put(id, c.optString("parent_id", ""));
            titles.put(id, c.optString("title", ""));
        }
        JSONArray useful = new JSONArray();
        JSONArray all = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            int count = 0;
            try { count = Integer.parseInt(c.optString("hadeeths_count", "0")); } catch (Exception ignored) { }
            if (count <= 0) continue;
            String id = c.optString("id");
            all.put(id);
            if (isUsefulCategory(id, parents, titles)) useful.put(id);
        }
        Log.d(TAG, "useful categories: " + useful.length() + " of " + all.length());
        if (useful.length() < 3) return all;
        return useful;
    }

    private static JSONArray fetchFridayIds(long deadline) throws Exception {
        JSONArray arr = new JSONArray(httpGet(HADEETH_BASE + "/categories/list/?language=ar", deadline));
        Set<String> catIds = new LinkedHashSet<String>();
        Set<String> looseIds = new LinkedHashSet<String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            String id = c.optString("id");
            String title = c.optString("title", "").trim();
            String parent = c.optString("parent_id", "");
            if (title.contains("الجمعة") || "472".equals(id) || "472".equals(parent)) looseIds.add(id);
            if (!title.contains("الجمعة")) continue;
            if (title.contains("أحكام") || title.contains("شروط")) continue;
            if (title.equals("صلاة الجمعة")) continue;
            catIds.add(id);
        }
        if (catIds.isEmpty()) catIds = looseIds;
        Log.d(TAG, "friday categories: " + catIds);
        Set<String> seen = new LinkedHashSet<String>();
        for (String cid : catIds) {
            JSONArray ids = fetchHadithIds(cid, deadline);
            for (int i = 0; i < ids.length(); i++) seen.add(ids.getString(i));
        }
        JSONArray pool = new JSONArray();
        for (String id : seen) pool.put(id);
        Log.d(TAG, "friday hadith pool size: " + pool.length());
        return pool;
    }

    private static JSONArray fetchHadithIds(String catId, long deadline) throws Exception {
        String body = httpGet(HADEETH_BASE + "/hadeeths/list/?language=ar&category_id=" + catId
                + "&page=1&per_page=50", deadline);
        JSONArray data;
        if (body.trim().startsWith("[")) data = new JSONArray(body);
        else data = new JSONObject(body).optJSONArray("data");
        JSONArray ids = new JSONArray();
        if (data != null) {
            for (int i = 0; i < data.length(); i++) ids.put(data.getJSONObject(i).optString("id"));
        }
        return ids;
    }

    // ------------------------------------------------------------------
    // شبكة
    // ------------------------------------------------------------------
    private static String httpGet(String url, long deadline) throws IOException {
        long left = deadline - System.currentTimeMillis();
        if (left < 1500) throw new IOException("time budget exhausted");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            int t = (int) Math.min(left, 6000);
            c.setConnectTimeout(t);
            c.setReadTimeout(t);
            c.setRequestProperty("User-Agent", "thikr-daily-content/1.0");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    // ------------------------------------------------------------------
    // التخزين المحلي
    // ------------------------------------------------------------------
    private static JSONObject loadCache(Context app) {
        synchronized (FILE_LOCK) {
            JSONObject o = null;
            try {
                File f = new File(app.getFilesDir(), CACHE_FILE);
                if (f.exists()) {
                    FileInputStream in = new FileInputStream(f);
                    byte[] b = new byte[(int) f.length()];
                    int off = 0;
                    while (off < b.length) {
                        int r = in.read(b, off, b.length - off);
                        if (r < 0) break;
                        off += r;
                    }
                    in.close();
                    o = new JSONObject(new String(b, 0, off, "UTF-8"));
                    if (o.optInt("v", 1) < CACHE_VERSION) o = null;   // نسخة قديمة: نبدأ من جديد
                }
            } catch (Exception e) {
                Log.d(TAG, "cache load failed: " + e.getMessage());
            }
            try {
                if (o == null) o = new JSONObject();
                if (o.optJSONObject("days") == null) o.put("days", new JSONObject());
                if (o.optJSONArray("catsV2") == null) o.put("catsV2", new JSONArray());
            } catch (Exception ignored) { }
            return o;
        }
    }

    private static void writeCache(Context app, JSONObject cache) throws Exception {
        cache.put("credit", "hadeethenc.com / api.alquran.cloud");
        cache.put("v", CACHE_VERSION);
        File f = new File(app.getFilesDir(), CACHE_FILE);
        FileOutputStream out = new FileOutputStream(f);
        out.write(cache.toString().getBytes("UTF-8"));
        out.close();
    }

    private static void putDay(Context app, String key, JSONObject rec) {
        synchronized (FILE_LOCK) {
            try {
                JSONObject cache = loadCache(app);
                JSONObject days = cache.getJSONObject("days");
                days.put(key, rec);
                long limit = epochDay() - 40;
                Iterator<String> it = days.keys();
                Set<String> remove = new HashSet<String>();
                while (it.hasNext()) {
                    String k = it.next();
                    try { if (Long.parseLong(k.split("_")[0]) < limit) remove.add(k); } catch (Exception ignored) { }
                }
                for (String k : remove) days.remove(k);
                writeCache(app, cache);
            } catch (Exception e) {
                Log.d(TAG, "cache save failed: " + e.getMessage());
            }
        }
    }

    private static void saveMeta(Context app, JSONArray cats, JSONArray fridayIds) {
        synchronized (FILE_LOCK) {
            try {
                JSONObject cache = loadCache(app);
                if (cats != null) cache.put("catsV2", cats);
                if (fridayIds != null) cache.put("fridayIdsV2", fridayIds);
                writeCache(app, cache);
            } catch (Exception e) {
                Log.d(TAG, "meta save failed: " + e.getMessage());
            }
        }
    }

    private static long epochDay() {
        long now = System.currentTimeMillis();
        return (now + TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    /** بديل بسيط لـ ByteArrayOutputStream عشان مانستوردش حاجات زيادة */
    private static class ByteArrayBuffer extends java.io.ByteArrayOutputStream { }

    // ------------------------------------------------------------------
    // زر "جرّب الآن" في الإعدادات: بيعرض إشعار من كل نوع فورًا ويقولك لو في نوع فشل وليه
    // ------------------------------------------------------------------
    public static void testNow(Context ctx) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                // كل ضغطة بتختار محتوى مختلف: بنزوّد اليوم بمضاعفات 7 (نفس اليوم من الأسبوع، فالجمعة تفضل جمعة)
                SharedPreferences tp = prefs(app);
                int counter = tp.getInt("dailyContent_test_counter", 0) + 1;
                tp.edit().putInt("dailyContent_test_counter", counter).apply();
                long day = epochDay() + 7L * counter;
                int slot = 1;
                for (String type : enabledTypes(prefs(app))) {
                    String label = T_HADITH.equals(type) ? "حديث" : T_NAMES.equals(type) ? "أسماء الله"
                            : T_DUA.equals(type) ? "دعاء" : "آية";
                    try {
                        JSONObject cache = loadCache(app);
                        JSONObject rec = pickByType(app, cache, type, day, slot,
                                System.currentTimeMillis() + 12000L);
                        if (rec != null) {
                            showNotification(app, rec, slot);
                            sb.append(label).append(": تمام\n");
                        } else {
                            sb.append(label).append(": مفيش محتوى مناسب\n");
                        }
                    } catch (Exception e) {
                        sb.append(label).append(": فشل (").append(e.getMessage()).append(")\n");
                    }
                    slot++;
                }
                if (sb.length() == 0) sb.append("مفيش أي نوع مفعّل في الإعدادات\n");
                try {
                    java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm");
                    StringBuilder tm = new StringBuilder();
                    for (long t : timesForDay(app, 0)) {
                        if (t > System.currentTimeMillis()) tm.append(f.format(new java.util.Date(t))).append("  ");
                    }
                    sb.append("باقي النهارده: ").append(tm.length() == 0 ? "لا شيء" : tm.toString().trim());
                } catch (Exception ignored) { }
                final String msg = sb.toString();
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(app, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "DailyContentTest").start();
    }

    // ------------------------------------------------------------------
    // الإشعار
    // ------------------------------------------------------------------
    private static void showNotification(Context app, JSONObject h, int slot) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "الفوائد اليومية",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);        // إشعار هادي من غير صوت
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
            nm.deleteNotificationChannel("daily_hadith_v1");   // القناة القديمة
        }

        String type = h.optString("type", T_HADITH);
        String text = h.optString("text", "");
        String source = h.optString("source", "");
        String title;
        String big;
        String sub;
        String summary;
        String shortText = text;

        if (T_NAMES.equals(type)) {
            String name = h.optString("name", "");
            String ev = h.optString("evidence", "");
            String evSource = h.optString("evidence_source", "");
            title = "من أسماء الله الحسنى: " + name;
            big = name + "\n" + text;
            if (!ev.isEmpty()) {
                big += "\n\nومن أدلته: " + ev + (evSource.isEmpty() ? "" : " (" + evSource + ")");
            }
            sub = "أسماء الله الحسنى";
            summary = "شرح د. سعيد القحطاني - قاعدة بيانات rn0x (MIT)";
        } else if (T_DUA.equals(type)) {
            title = "دعاء وذكر";
            big = source.isEmpty() ? text : text + "\n\n" + source;
            sub = source.isEmpty() ? "حصن المسلم" : source;
            summary = "المصدر: حصن المسلم";
        } else if (T_AYAH.equals(type)) {
            title = "آية من القرآن الكريم";
            big = source.isEmpty() ? text : text + "\n\n" + source;
            sub = "القرآن الكريم";
            summary = "api.alquran.cloud";
        } else {
            boolean friday = h.optBoolean("friday", false);
            title = friday ? "حديث يوم الجمعة" : "حديث اليوم";
            big = source.isEmpty() ? text : text + "\n\n" + source;
            sub = "موسوعة الأحاديث النبوية";
            summary = "المصدر: hadeethenc.com";
        }

        PendingIntent pi = null;
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            pi = PendingIntent.getActivity(app, 7440 + slot, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(shortText)
                .setSubText(sub)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(big).setSummaryText(summary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true);
        if (pi != null) b.setContentIntent(pi);
        Notification n = b.build();
        nm.notify(NOTIFICATION_ID + slot, n);
    }
}
