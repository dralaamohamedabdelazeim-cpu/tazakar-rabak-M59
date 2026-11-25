package com.alaaeltaweel.thikrallah.library;

import android.content.Context;
import android.util.JsonReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

/**
 * كتب الأحاديث (البخاري ومسلم) المخزّنة داخل التطبيق في مجلد assets/hadith.
 * يتم تحميل كتاب واحد في الذاكرة في كل مرة لتوفير الذاكرة.
 */
public class HadithData {

    public static final String[] BOOK_IDS = {"bukhari", "muslim", "riyad_assalihin", "nawawi40", "qudsi40", "bulugh_almaram", "aladab_almufrad", "shamail_muhammadiyah", "abudawud", "tirmidhi", "nasai", "ibnmajah", "malik", "ahmed", "darimi", "mishkat_almasabih"};
    public static final String[] BOOK_NAMES = {"صحيح البخاري", "صحيح مسلم", "رياض الصالحين", "الأربعون النووية", "الأربعون القدسية", "بلوغ المرام", "الأدب المفرد", "الشمائل المحمدية", "سنن أبي داود", "جامع الترمذي", "سنن النسائي", "سنن ابن ماجه", "موطأ مالك", "مسند الإمام أحمد", "سنن الدارمي", "مشكاة المصابيح"};

    public static final int MAX_RESULTS = 300;

    public final String bookId;
    public int[] chapterIds;
    public String[] chapterNames;
    public int[] numbers;     // رقم الحديث داخل الكتاب
    public int[] chapterOf;   // رقم الكتاب (الباب الكبير) الذي ينتمي له الحديث
    public String[] texts;
    private String[] normalized;

    private static HadithData current;

    private HadithData(String bookId) {
        this.bookId = bookId;
    }

    public static String bookName(String bookId) {
        for (int i = 0; i < BOOK_IDS.length; i++) {
            if (BOOK_IDS[i].equals(bookId)) {
                return BOOK_NAMES[i];
            }
        }
        return bookId;
    }

    public static synchronized HadithData load(Context ctx, String bookId) throws IOException {
        if (current != null && current.bookId.equals(bookId)) {
            return current;
        }
        current = null; // نحرر الكتاب القديم من الذاكرة قبل تحميل الجديد
        HadithData d = parse(ctx.getApplicationContext(), bookId);
        current = d;
        return d;
    }

    private static HadithData parse(Context ctx, String bookId) throws IOException {
        HadithData d = new HadithData(bookId);
        ArrayList<Integer> cIds = new ArrayList<>();
        ArrayList<String> cNames = new ArrayList<>();
        ArrayList<Integer> nums = new ArrayList<>();
        ArrayList<Integer> chs = new ArrayList<>();
        ArrayList<String> txt = new ArrayList<>();

        InputStream in = ctx.getAssets().open("hadith/" + bookId + ".json");
        JsonReader r = new JsonReader(new BufferedReader(new InputStreamReader(in, "UTF-8"), 65536));
        try {
            r.beginObject();
            while (r.hasNext()) {
                String name = r.nextName();
                if ("chapters".equals(name)) {
                    r.beginArray();
                    while (r.hasNext()) {
                        r.beginObject();
                        int id = 0;
                        String n = "";
                        while (r.hasNext()) {
                            String k = r.nextName();
                            if ("id".equals(k)) {
                                id = r.nextInt();
                            } else if ("n".equals(k)) {
                                n = r.nextString();
                            } else {
                                r.skipValue();
                            }
                        }
                        r.endObject();
                        cIds.add(id);
                        cNames.add(n);
                    }
                    r.endArray();
                } else if ("h".equals(name)) {
                    r.beginArray();
                    while (r.hasNext()) {
                        r.beginArray();
                        int num = r.nextInt();
                        int ch = r.nextInt();
                        String t = r.nextString();
                        r.endArray();
                        nums.add(num);
                        chs.add(ch);
                        txt.add(t);
                    }
                    r.endArray();
                } else {
                    r.skipValue();
                }
            }
            r.endObject();
        } finally {
            r.close();
        }

        d.chapterIds = new int[cIds.size()];
        d.chapterNames = new String[cIds.size()];
        for (int i = 0; i < cIds.size(); i++) {
            d.chapterIds[i] = cIds.get(i);
            d.chapterNames[i] = cNames.get(i);
        }
        int n = txt.size();
        d.numbers = new int[n];
        d.chapterOf = new int[n];
        d.texts = new String[n];
        for (int i = 0; i < n; i++) {
            d.numbers[i] = nums.get(i);
            d.chapterOf[i] = chs.get(i);
            d.texts[i] = txt.get(i);
        }
        return d;
    }

    /** عدد الأحاديث في كتاب (باب كبير) معيّن. */
    public int countInChapter(int chapterId) {
        int c = 0;
        for (int i = 0; i < chapterOf.length; i++) {
            if (chapterOf[i] == chapterId) {
                c++;
            }
        }
        return c;
    }

    /** أرقام (مواضع) الأحاديث الموجودة في كتاب معيّن بالترتيب. */
    public ArrayList<Integer> indicesInChapter(int chapterId) {
        ArrayList<Integer> out = new ArrayList<>();
        for (int i = 0; i < chapterOf.length; i++) {
            if (chapterOf[i] == chapterId) {
                out.add(i);
            }
        }
        return out;
    }

    public String chapterName(int chapterId) {
        for (int i = 0; i < chapterIds.length; i++) {
            if (chapterIds[i] == chapterId) {
                return chapterNames[i];
            }
        }
        return "";
    }

    /** بحث بدون تشكيل: كل الكلمات لازم تكون موجودة في نص الحديث. */
    public synchronized ArrayList<Integer> search(String query) {
        ArrayList<Integer> out = new ArrayList<>();
        String q = normalize(query).trim();
        if (q.length() == 0) {
            return out;
        }
        String[] terms = q.split("\\s+");
        if (normalized == null) {
            String[] nz = new String[texts.length];
            for (int i = 0; i < texts.length; i++) {
                nz[i] = normalize(texts[i]);
            }
            normalized = nz;
        }
        for (int i = 0; i < normalized.length; i++) {
            boolean all = true;
            for (String t : terms) {
                if (!normalized[i].contains(t)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                out.add(i);
                if (out.size() >= MAX_RESULTS) {
                    break;
                }
            }
        }
        return out;
    }

    /** يشيل التشكيل والتطويل ويوحّد شكل الألف والياء والتاء المربوطة. */
    public static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '\u064B' && c <= '\u065F') || c == '\u0670' || c == '\u0640'
                    || (c >= '\u06D6' && c <= '\u06ED')) {
                continue;
            }
            switch (c) {
                case '\u0623':
                case '\u0625':
                case '\u0622':
                case '\u0671':
                    sb.append('\u0627');
                    break;
                case '\u0649':
                    sb.append('\u064A');
                    break;
                case '\u0629':
                    sb.append('\u0647');
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}
