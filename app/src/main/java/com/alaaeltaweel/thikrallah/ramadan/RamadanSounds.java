package com.alaaeltaweel.thikrallah.ramadan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Random;

/**
 * أصوات المدفع والمسحراتي. المستخدم بيختار من شاشة رمضان، وفي اختيار "عشوائي"
 * بيشغّل صوت مختلف كل مرة. الاختيار الأول (1) هو الصوت الأصلي الموجود في التطبيق من قبل،
 * وهو الافتراضي، فمن مفيش عنده اختيار محفوظ بيشتغل عنده زي الأول.
 */
public final class RamadanSounds {

    public static final String TYPE_CANNON = "cannon";
    public static final String TYPE_MESAHARATY = "mesaharaty";
    public static final String RANDOM_ID = "random";
    public static final String RANDOM_LABEL = "عشوائي";

    public static final class Option {
        public final String id;
        public final String label;
        public final String assetPath;
        public final int seconds;

        Option(String id, String label, String assetPath, int seconds) {
            this.id = id;
            this.label = label;
            this.assetPath = assetPath;
            this.seconds = seconds;
        }
    }

    private static final Option[] CANNON = {
            new Option("1", "المدفع الأصلي", "cannon.mp3", 6),
            new Option("2", "2 مدفع الإفطار ", "ramadan_sounds/cannon_2.mp3", 23),
            new Option("3", "مدفع الإفطار 3", "ramadan_sounds/cannon_3.mp3", 12),
    };

    // مقاطع سيد مكاوي مقطوعة من التسجيل الكامل، وأسماؤها حسب ترتيب الأغاني في صورة المرجع (راجعها بالسماع)
    private static final Option[] MESAHARATY = {
            new Option("1", "المسحراتي الأصلي", "mesaharaty.mp3", 42),
            new Option("2", "اصحى يا نايم - سيد مكاوي", "ramadan_sounds/mesaharaty_2.mp3", 31),
            new Option("3", "مسحراتى  - سيد مكاوي", "ramadan_sounds/mesaharaty_3.mp3", 35),
            new Option("4", "اهدينى رحمة - سيد مكاوي", "ramadan_sounds/mesaharaty_4.mp3", 35),
            new Option("5", "الرجل تدب - سيد مكاوي", "ramadan_sounds/mesaharaty_5.mp3", 43),
            new Option("6", "يارب ياهادى - سيد مكاوي", "ramadan_sounds/mesaharaty_6.mp3", 32),
            new Option("7", "ليلة القدر - حمزة نمرة", "ramadan_sounds/mesaharaty_7.mp3", 117),
            new Option("8", "منيش درويش - حمزة نمرة", "ramadan_sounds/mesaharaty_8.mp3", 111),
            new Option("9", "هلال - حمزة نمرة", "ramadan_sounds/mesaharaty_9.mp3", 97),
            new Option("10", "آخر ليلة - حمزة نمرة", "ramadan_sounds/mesaharaty_10.mp3", 156),
            new Option("11", "آخر عشر أيام - حمزة نمرة", "ramadan_sounds/mesaharaty_11.mp3", 134),
    };

    private RamadanSounds() {
    }

    /** الأصوات الفعلية (من غير "عشوائي"). */
    public static Option[] options(String type) {
        return TYPE_CANNON.equals(type) ? CANNON : MESAHARATY;
    }

    /** أسماء القايمة اللي بتظهر للمستخدم: "عشوائي" أولاً وبعدها الأصوات. */
    public static String[] labels(String type) {
        Option[] all = options(type);
        String[] out = new String[all.length + 1];
        out[0] = RANDOM_LABEL;
        for (int i = 0; i < all.length; i++) {
            out[i + 1] = all[i].label;
        }
        return out;
    }

    public static String prefKey(String type) {
        return TYPE_CANNON.equals(type) ? "ramadan_cannon_sound" : "ramadan_mesaharaty_sound";
    }

    public static String selectedId(Context ctx, String type) {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(ctx);
        return p.getString(prefKey(type), "1");
    }

    /** الاسم اللي يظهر في خانة الاختيار. */
    public static String selectedLabel(Context ctx, String type) {
        String id = selectedId(ctx, type);
        if (RANDOM_ID.equals(id)) {
            return RANDOM_LABEL;
        }
        for (Option o : options(type)) {
            if (o.id.equals(id)) {
                return o.label;
            }
        }
        return options(type)[0].label;
    }

    /** الصوت اللي هيشتغل فعلاً: لو الاختيار "عشوائي" بيطلع صوت مختلف كل مرة. */
    public static Option selected(Context ctx, String type) {
        Option[] all = options(type);
        String id = selectedId(ctx, type);
        if (RANDOM_ID.equals(id)) {
            return all[new Random().nextInt(all.length)];
        }
        for (Option o : all) {
            if (o.id.equals(id)) {
                return o;
            }
        }
        return all[0];
    }

    public static void select(Context ctx, String type, String id) {
        PreferenceManager.getDefaultSharedPreferences(ctx).edit().putString(prefKey(type), id).apply();
    }

    /** الصوت الأصلي (للرجوع له لو الملف المختار مش اتفتح). */
    public static Option defaultOption(String type) {
        return options(type)[0];
    }
}
