package com.alaaeltaweel.thikrallah.library;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.alaaeltaweel.thikrallah.R;

/** أدوات صغيرة لبناء شاشات المكتبة بالكود (نفس ألوان التطبيق). */
final class LibUi {
    static final int GOLD = 0xFFD4AF37;
    static final int WHITE = 0xFFFFFFFF;
    static final int GREY = 0xFF8FA3B1;
    static final int PAGE_BG = 0xFF0B1120;
    static final int PANEL_BG = 0xFF162235;

    private LibUi() {
    }

    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static void rtl(View v) {
        v.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.START);
        return t;
    }

    static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 18, GOLD);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(c, 4), dp(c, 16), dp(c, 4), dp(c, 8));
        return t;
    }

    static Button button(Context c, String s) {
        Button b = new Button(c);
        b.setText(s);
        b.setTextColor(WHITE);
        b.setAllCaps(false);
        b.setBackgroundResource(R.drawable.card_bg);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        return b;
    }
}
