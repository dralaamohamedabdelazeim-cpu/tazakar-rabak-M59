package com.alaaeltaweel.thikrallah.library;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * تخزين وتحميل كتب التفسير. كل تفسير يتحمّل مرة واحدة من الإنترنت (114 ملف، ملف لكل سورة)
 * ويتحفظ في ذاكرة التطبيق، وبعدها يشتغل بدون إنترنت.
 */
public class TafsirStore {

    /** {الاسم في الرابط، الاسم المعروض} */
    public static final String[][] EDITIONS = {
            {"ar-tafsir-ibn-kathir", "تفسير ابن كثير"},
            {"ar-tafsir-al-tabari", "تفسير الطبري"},
            {"ar-tafseer-al-qurtubi", "تفسير القرطبي"},
            {"ar-tafseer-al-saddi", "تفسير السعدي"},
            {"ar-tafsir-muyassar", "التفسير الميسر"},
            {"ar-tafsir-al-baghawi", "تفسير البغوي (معالم التنزيل)"},
    };

    public static final int TOTAL_SURAS = 114;

    /** عدد آيات كل سورة (للتنقل بين الآيات). */
    public static final int[] AYAH_COUNTS = {
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
            112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
            54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
            14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
            29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
            11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6};

    private static final String CDN_URL =
            "https://cdn.jsdelivr.net/gh/spa5k/tafsir_api@main/tafsir/";
    private static final String RAW_URL =
            "https://raw.githubusercontent.com/spa5k/tafsir_api/main/tafsir/";

    public interface Listener {
        void onProgress(int done, int total);

        void onFinished(boolean ok, String message);
    }

    private static String cacheKey;
    private static TreeMap<Integer, String> cacheValue;

    private TafsirStore() {
    }

    public static String editionName(String slug) {
        for (String[] e : EDITIONS) {
            if (e[0].equals(slug)) {
                return e[1];
            }
        }
        return slug;
    }

    public static File dir(Context ctx, String slug) {
        return new File(new File(ctx.getFilesDir(), "tafsir"), slug);
    }

    public static boolean isInstalled(Context ctx, String slug) {
        return new File(dir(ctx, slug), ".done").exists();
    }

    public static ArrayList<String> installedSlugs(Context ctx) {
        ArrayList<String> out = new ArrayList<>();
        for (String[] e : EDITIONS) {
            if (isInstalled(ctx, e[0])) {
                out.add(e[0]);
            }
        }
        return out;
    }

    public static long sizeOnDisk(Context ctx, String slug) {
        long total = 0;
        File[] files = dir(ctx, slug).listFiles();
        if (files != null) {
            for (File f : files) {
                total += f.length();
            }
        }
        return total;
    }

    public static synchronized void delete(Context ctx, String slug) {
        File[] files = dir(ctx, slug).listFiles();
        if (files != null) {
            for (File f : files) {
                f.delete();
            }
        }
        dir(ctx, slug).delete();
        cacheKey = null;
        cacheValue = null;
    }

    /** يقرأ تفسير سورة كاملة: رقم الآية -> نص التفسير. */
    public static synchronized TreeMap<Integer, String> loadSura(Context ctx, String slug, int sura)
            throws Exception {
        String key = slug + "#" + sura;
        if (key.equals(cacheKey) && cacheValue != null) {
            return cacheValue;
        }
        File f = new File(dir(ctx, slug), sura + ".json");
        byte[] bytes = readAll(new FileInputStream(f));
        JSONArray arr = new JSONArray(new String(bytes, "UTF-8"));
        TreeMap<Integer, String> map = new TreeMap<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray pair = arr.getJSONArray(i);
            map.put(pair.getInt(0), pair.getString(1));
        }
        cacheKey = key;
        cacheValue = map;
        return map;
    }

    /** يحمّل كل سور التفسير. لازم يتنادى من خيط خلفي. لو اتقطع يكمّل من حيث وقف. */
    public static void download(Context ctx, final String slug, AtomicBoolean cancel,
                                final Listener listener) {
        final Handler ui = new Handler(Looper.getMainLooper());
        File dir = dir(ctx, slug);
        dir.mkdirs();
        File done = new File(dir, ".done");
        if (done.exists()) {
            done.delete();
        }
        for (int n = 1; n <= TOTAL_SURAS; n++) {
            if (cancel.get()) {
                postFinish(ui, listener, false,
                        "تم إيقاف التحميل. لو دوست تحميل تاني هيكمّل من حيث وقف.");
                return;
            }
            File f = new File(dir, n + ".json");
            if (!(f.exists() && f.length() > 2)) {
                boolean got = false;
                for (int attempt = 0; attempt < 3 && !got; attempt++) {
                    if (cancel.get()) {
                        break;
                    }
                    try {
                        String raw;
                        try {
                            raw = fetch(CDN_URL + slug + "/" + n + ".json");
                        } catch (IOException first) {
                            raw = fetch(RAW_URL + slug + "/" + n + ".json");
                        }
                        saveCleaned(raw, f);
                        got = true;
                    } catch (Exception | OutOfMemoryError e) {
                        try {
                            Thread.sleep(800);
                        } catch (InterruptedException ie) {
                            // نكمل المحاولة
                        }
                    }
                }
                if (!got) {
                    if (cancel.get()) {
                        postFinish(ui, listener, false,
                                "تم إيقاف التحميل. لو دوست تحميل تاني هيكمّل من حيث وقف.");
                    } else {
                        postFinish(ui, listener, false,
                                "تعذّر تحميل سورة رقم " + n
                                        + ". تأكد من الإنترنت وجرّب تاني، والتحميل هيكمّل من حيث وقف.");
                    }
                    return;
                }
            }
            final int finished = n;
            ui.post(new Runnable() {
                @Override
                public void run() {
                    listener.onProgress(finished, TOTAL_SURAS);
                }
            });
        }
        try {
            FileOutputStream out = new FileOutputStream(done);
            out.write('1');
            out.close();
        } catch (IOException e) {
            postFinish(ui, listener, false, "تعذّر حفظ التفسير على الجهاز. تأكد من وجود مساحة كافية.");
            return;
        }
        postFinish(ui, listener, true, "تم تحميل التفسير، وبقى يشتغل بدون إنترنت.");
    }

    private static void postFinish(Handler ui, final Listener l, final boolean ok, final String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                l.onFinished(ok, msg);
            }
        });
    }

    private static String fetch(String address) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }
            return new String(readAll(conn.getInputStream()), "UTF-8");
        } finally {
            conn.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** يحوّل ملف السورة الأصلي إلى شكل مختصر ونظيف ويحفظه. */
    private static void saveCleaned(String raw, File target) throws Exception {
        Object root = new JSONTokener(raw).nextValue();
        JSONArray arr;
        if (root instanceof JSONArray) {
            arr = (JSONArray) root;
        } else if (root instanceof JSONObject) {
            arr = ((JSONObject) root).optJSONArray("ayahs");
            if (arr == null) {
                arr = ((JSONObject) root).optJSONArray("data");
            }
            if (arr == null) {
                throw new IOException("شكل الملف غير معروف");
            }
        } else {
            throw new IOException("شكل الملف غير معروف");
        }
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) {
                continue;
            }
            int ayah = e.optInt("ayah", e.optInt("ayah_number", e.optInt("number", 0)));
            if (ayah <= 0) {
                continue;
            }
            JSONArray pair = new JSONArray();
            pair.put(ayah);
            pair.put(clean(e.optString("text", "")));
            out.put(pair);
        }
        if (out.length() == 0) {
            throw new IOException("الملف فاضي");
        }
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
        try {
            w.write(out.toString());
        } finally {
            w.close();
        }
        if (target.exists()) {
            target.delete();
        }
        if (!tmp.renameTo(target)) {
            throw new IOException("تعذّر حفظ الملف");
        }
    }

    /** يشيل ملاحظات المحققين اللي بين [[ ]] وينظّف المسافات الزايدة. */
    static String clean(String t) {
        t = t.replaceAll("(?s)\\[\\[.*?\\]\\]", "");
        t = t.replaceAll("[ \\t]{2,}", " ");
        t = t.replaceAll("\\n{3,}", "\n\n");
        return t.trim();
    }
}
