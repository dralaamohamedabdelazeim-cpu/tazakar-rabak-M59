package com.alaaeltaweel.thikrallah.Notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * إشعار "حديث اليوم": حديث صحيح مختلف كل يوم من موسوعة الأحاديث النبوية (hadeethenc.com).
 *
 * شروط الموقع: نص الحديث يتعرض زي ما هو بدون تعديل، والمصدر يتذكر بوضوح.
 * بنخزّن الأحاديث على الجهاز عشان لو الإنترنت قطع يفضل الإشعار شغال.
 */
public class DailyHadithHelper {
    public static final String DATA_TYPE = "daily_hadith";
    public static final String EXTRA_SLOT = "hadith_slot";
    public static final int SLOTS = 3;                 // عدد الأوقات في اليوم
    public static final int REQUEST_CODE_BASE = 27;    // 27 و28 و29

    public static String enabledKey(int slot) {
        return slot == 1 ? "remindMeDailyHadith" : "remindMeDailyHadith" + slot;
    }

    public static String timeKey(int slot) {
        return slot == 1 ? "dailyHadithTime" : "dailyHadithTime" + slot;
    }

    public static String defaultTime(int slot) {
        return slot == 1 ? "09:00" : (slot == 2 ? "16:00" : "20:00");
    }

    private static final String TAG = "DailyHadith";
    private static final String BASE = "https://hadeethenc.com/api/v1";
    private static final String CACHE_FILE = "daily_hadiths.json";
    private static final String CHANNEL_ID = "daily_hadith_v1";
    private static final int NOTIFICATION_ID = 7430;
    private static final int MIN_CHARS = 20;
    private static final int MAX_CHARS = 350;   // الأحاديث الأطول بنستبعدها عشان تظهر كويس في الإشعار
    private static final int MAX_CHARS_FRIDAY = 450;   // أحاديث الجمعة قليلة فبنوسّع الحد شوية
    private static final int PREFETCH_DAYS = 3;
    private static final long ALARM_BUDGET_MS = 8000L;      // وقت الإشعار نفسه: سريع
    private static final long PREFETCH_BUDGET_MS = 60000L;  // التحميل المسبق في الخلفية

    private static final AtomicBoolean prefetchBusy = new AtomicBoolean(false);
    private static final Object FILE_LOCK = new Object();

    // ------------------------------------------------------------------
    // نقطة الدخول من المنبه
    // ------------------------------------------------------------------
    public static void handleAlarm(Context ctx, final BroadcastReceiver.PendingResult pending, final int slot) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // نسجل منبه بكرة الأول
                    try {
                        new MyAlarmsManager(app).UpdateAllApplicableAlarms();
                    } catch (Exception e) {
                        Log.e(TAG, "reschedule failed: " + e.getMessage());
                    }
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
                    if (prefs.getBoolean(enabledKey(slot), true)) {
                        JSONObject h = hadithFor(app, epochDay(), slot);
                        if (h != null) showNotification(app, h, slot);
                        else Log.d(TAG, "no hadith available now (offline and empty cache)");
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
        }, "DailyHadithAlarm").start();
    }

    // ------------------------------------------------------------------
    // تحميل مسبق لأحاديث الأيام الجاية (عشان تشتغل من غير إنترنت)
    // ------------------------------------------------------------------
    public static void prefetchAsync(Context ctx) {
        final Context app = ctx.getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
        long last = prefs.getLong("daily_hadith_last_prefetch", 0);
        if (System.currentTimeMillis() - last < 20 * 60 * 1000L) return;   // مش أكتر من مرة كل 20 دقيقة
        prefs.edit().putLong("daily_hadith_last_prefetch", System.currentTimeMillis()).apply();
        new Thread(new Runnable() {
            @Override
            public void run() {
                prefetch(app);
            }
        }, "DailyHadithPrefetch").start();
    }

    private static void prefetch(Context app) {
        if (!prefetchBusy.compareAndSet(false, true)) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(app);
            long deadline = System.currentTimeMillis() + PREFETCH_BUDGET_MS;
            long today = epochDay();
            for (long d = today; d < today + PREFETCH_DAYS; d++) {
                for (int slot = 1; slot <= SLOTS; slot++) {
                    if (!prefs.getBoolean(enabledKey(slot), true)) continue;
                    if (System.currentTimeMillis() > deadline) return;
                    JSONObject cache = loadCache(app);
                    if (cache.optJSONObject("days").has(dayKey(d, slot))) continue;
                    JSONObject rec = pick(app, cache, d, slot, deadline);
                    if (rec == null) return;   // غالبًا مفيش إنترنت
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
    // اختيار الحديث
    // ------------------------------------------------------------------
    private static String dayKey(long day, int slot) {
        return day + "_" + slot;
    }

    /** يوم الجمعة؟ (اليوم صفر في التقويم كان خميس) */
    private static boolean isFriday(long day) {
        return (day + 4) % 7 == 5;
    }

    private static JSONObject hadithFor(Context app, long day, int slot) {
        JSONObject cache = loadCache(app);
        JSONObject days = cache.optJSONObject("days");
        JSONObject h = days.optJSONObject(dayKey(day, slot));
        if (h != null) return h;

        try {
            h = pick(app, cache, day, slot, System.currentTimeMillis() + ALARM_BUDGET_MS);
            if (h != null) {
                putDay(app, dayKey(day, slot), h);
                return h;
            }
        } catch (Exception e) {
            Log.d(TAG, "online pick failed: " + e.getMessage());
        }

        // مفيش إنترنت: نختار من الأحاديث المحفوظة قبل كده (نفضّل نفس نوع اليوم: جمعة أو عادي)
        try {
            boolean fri = isFriday(day);
            JSONArray prefer = new JSONArray();
            JSONArray any = new JSONArray();
            Iterator<String> it = days.keys();
            while (it.hasNext()) {
                JSONObject o = days.getJSONObject(it.next());
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

    private static JSONObject pick(Context app, JSONObject cache, long day, int slot, long deadline) throws Exception {
        if (isFriday(day)) {
            JSONObject rec = pickFriday(app, cache, day, slot, deadline);
            if (rec != null) return rec;
            Log.d(TAG, "no friday hadith found, falling back to a regular one");
        }
        return pickNormal(app, cache, day, slot, deadline);
    }

    /** أحاديث من أقسام الجمعة بس. كل جمعة بتاخد الأحاديث اللي بعد اللي فاتت. */
    private static JSONObject pickFriday(Context app, JSONObject cache, long day, int slot, long deadline) throws Exception {
        JSONArray pool = cache.optJSONArray("fridayIds");
        if (pool == null || pool.length() == 0) {
            pool = fetchFridayIds(deadline);
            cache.put("fridayIds", pool);
            saveMeta(app, null, pool);
        }
        int len = pool.length();
        if (len == 0) return null;

        Set<String> used = usedIds(cache.optJSONObject("days"), day, 0);   // نفس اليوم بس
        int start = (int) (((day / 7) * SLOTS + (slot - 1)) % len);
        int tries = Math.min(len, 8);
        for (int k = 0; k < tries; k++) {
            String id = pool.getString((start + k) % len);
            if (used.contains(id)) continue;
            JSONObject d = new JSONObject(httpGet(BASE + "/hadeeths/one/?language=ar&id=" + id, deadline));
            JSONObject rec = toRecordIfAcceptable(d, MAX_CHARS_FRIDAY);
            if (rec != null) {
                rec.put("friday", true);
                return rec;
            }
        }
        return null;
    }

    /** اختيار ثابت ومتنوع: كل مرة تصنيف مختلف، وجواه حديث صحيح قصير. */
    private static JSONObject pickNormal(Context app, JSONObject cache, long day, int slot, long deadline) throws Exception {
        JSONArray cats = cache.optJSONArray("cats");
        if (cats == null || cats.length() == 0) {
            cats = fetchCategoryIds(deadline);
            cache.put("cats", cats);
            saveMeta(app, cats, null);
        }
        int n = cats.length();
        if (n == 0) return null;

        Set<String> used = usedIds(cache.optJSONObject("days"), day, 5);
        long g = day * SLOTS + (slot - 1);

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
                JSONObject d = new JSONObject(httpGet(
                        BASE + "/hadeeths/one/?language=ar&id=" + id, deadline));
                JSONObject rec = toRecordIfAcceptable(d, MAX_CHARS);
                if (rec != null) {
                    rec.put("friday", false);
                    return rec;
                }
            }
        }
        return null;
    }

    /** الأحاديث المستخدمة في أيام قريبة (window = عدد الأيام قبل وبعد) عشان مانكررش */
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
                    if (o != null) used.add(o.optString("id"));
                }
            } catch (Exception ignored) { }
        }
        return used;
    }

    private static JSONObject toRecordIfAcceptable(JSONObject d, int maxChars) throws Exception {
        String text = d.optString("hadeeth", "").trim();
        String grade = d.optString("grade", "");
        if (!grade.contains("صحيح")) return null;
        if (text.length() < MIN_CHARS || text.length() > maxChars) return null;
        JSONObject rec = new JSONObject();
        rec.put("id", d.optString("id"));
        rec.put("text", text);                                   // بدون أي تعديل
        rec.put("source", d.optString("attribution", "").trim());
        rec.put("grade", grade);
        return rec;
    }

    private static JSONArray fetchCategoryIds(long deadline) throws Exception {
        JSONArray arr = new JSONArray(httpGet(BASE + "/categories/list/?language=ar", deadline));
        JSONArray ids = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            int count = 0;
            try { count = Integer.parseInt(c.optString("hadeeths_count", "0")); } catch (Exception ignored) { }
            if (count > 0) ids.put(c.optString("id"));
        }
        return ids;
    }

    /** كل الأحاديث اللي في أقسام عنوانها فيه "الجمعة" (وقسم صلاة الجمعة وفروعه) */
    private static JSONArray fetchFridayIds(long deadline) throws Exception {
        JSONArray arr = new JSONArray(httpGet(BASE + "/categories/list/?language=ar", deadline));
        Set<String> catIds = new LinkedHashSet<String>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.getJSONObject(i);
            String id = c.optString("id");
            String title = c.optString("title", "");
            String parent = c.optString("parent_id", "");
            if (title.contains("الجمعة") || "472".equals(id) || "472".equals(parent)) {
                catIds.add(id);
            }
        }
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
        String body = httpGet(BASE + "/hadeeths/list/?language=ar&category_id=" + catId
                + "&page=1&per_page=50", deadline);
        JSONArray data;
        if (body.trim().startsWith("[")) {
            data = new JSONArray(body);
        } else {
            data = new JSONObject(body).optJSONArray("data");
        }
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
            c.setRequestProperty("User-Agent", "thikr-daily-hadith/1.0");
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
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
                    java.io.FileInputStream in = new java.io.FileInputStream(f);
                    byte[] b = new byte[(int) f.length()];
                    int off = 0;
                    while (off < b.length) {
                        int r = in.read(b, off, b.length - off);
                        if (r < 0) break;
                        off += r;
                    }
                    in.close();
                    o = new JSONObject(new String(b, 0, off, "UTF-8"));
                }
            } catch (Exception e) {
                Log.d(TAG, "cache load failed: " + e.getMessage());
            }
            try {
                if (o == null) o = new JSONObject();
                if (o.optJSONObject("days") == null) o.put("days", new JSONObject());
                if (o.optJSONArray("cats") == null) o.put("cats", new JSONArray());
            } catch (Exception ignored) { }
            return o;
        }
    }

    private static void writeCache(Context app, JSONObject cache) throws Exception {
        cache.put("credit", "موسوعة الأحاديث النبوية - hadeethenc.com");
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

                // نمسح الأيام القديمة (نسيب آخر 40 يوم عشان نستخدمهم لو الإنترنت قطع)
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
                if (cats != null) cache.put("cats", cats);
                if (fridayIds != null) cache.put("fridayIds", fridayIds);
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

    // ------------------------------------------------------------------
    // الإشعار
    // ------------------------------------------------------------------
    private static void showNotification(Context app, JSONObject h, int slot) {
        NotificationManager nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "حديث اليوم",
                    NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);        // إشعار هادي من غير صوت
            ch.enableVibration(false);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }

        boolean friday = h.optBoolean("friday", false);
        String text = h.optString("text", "");
        String source = h.optString("source", "");
        String body = source.isEmpty() ? text : text + "\n\n" + source;

        PendingIntent pi = null;
        Intent launch = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (launch != null) {
            pi = PendingIntent.getActivity(app, 7440 + slot, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(app, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(friday ? "حديث يوم الجمعة" : "حديث اليوم")
                .setContentText(text)
                .setSubText("موسوعة الأحاديث النبوية")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(body)
                        .setSummaryText("المصدر: hadeethenc.com"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setAutoCancel(true);
        if (pi != null) b.setContentIntent(pi);
        Notification n = b.build();
        nm.notify(NOTIFICATION_ID + slot, n);
    }
}
