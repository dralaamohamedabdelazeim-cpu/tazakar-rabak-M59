package com.alaaeltaweel.thikrallah.Utilities;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/** مظهر التطبيق: داكن (الافتراضي) / فاتح / حسب الموبايل. */
public final class ThemeHelper {
    public static final String KEY = "app_theme_mode";

    private ThemeHelper() {
    }

    public static void apply(Context ctx) {
        String v = PreferenceManager.getDefaultSharedPreferences(ctx).getString(KEY, "dark");
        int mode = AppCompatDelegate.MODE_NIGHT_YES;
        if ("light".equals(v)) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if ("system".equals(v)) {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
