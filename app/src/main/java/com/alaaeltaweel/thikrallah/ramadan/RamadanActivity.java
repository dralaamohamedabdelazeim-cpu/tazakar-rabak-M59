package com.alaaeltaweel.thikrallah.ramadan;

import com.alaaeltaweel.thikrallah.R;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import com.alaaeltaweel.thikrallah.Notification.MyAlarmsManager;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;
import com.alaaeltaweel.thikrallah.Utilities.TimePreference;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * شاشة رمضان: إمساكية الشهر (محسوبة من أوقات الصلاة في التطبيق بدون إنترنت)
 * + إعدادات مدفع الإفطار والمسحراتي.
 * بتستخدم نفس مفاتيح الإعدادات القديمة، فإعدادات المستخدمين الحاليين ما بتضيعش.
 */
public class RamadanActivity extends AppCompatActivity {

    // الألوان بتتحدد حسب الثيم (فاتح/غامق) وقت فتح الشاشة
    private static int TEAL;
    private static int TEXT;
    private static int TEXT2;
    private static int GOLD;
    private static int PAGE_BG;
    private static int STRIPE;
    private static int TODAY;
    private static int CARD;
    private static int STROKE;

    private void initColors() {
        TEAL = androidx.core.content.ContextCompat.getColor(this, R.color.solid_teal);
        TEXT = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary);
        TEXT2 = androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary);
        GOLD = androidx.core.content.ContextCompat.getColor(this, R.color.brand_gold);
        PAGE_BG = androidx.core.content.ContextCompat.getColor(this, R.color.page_background);
        STRIPE = androidx.core.content.ContextCompat.getColor(this, R.color.row_stripe);
        TODAY = androidx.core.content.ContextCompat.getColor(this, R.color.row_highlight);
        CARD = androidx.core.content.ContextCompat.getColor(this, R.color.card_white);
        STROKE = androidx.core.content.ContextCompat.getColor(this, R.color.card_stroke);
    }
    private static final int INITIAL_ROWS = 7;

    private SharedPreferences prefs;
    private LinearLayout root;

    // بيانات الإمساكية
    private int rowCount = 0;
    private String[] dayNames;
    private String[] gregorian;
    private String[] fajr;
    private String[] maghrib;
    private int todayIndex = -1;
    private int daysToStart = -1;
    private int hijriYear;
    private boolean expanded = false;
    private LinearLayout tableBody;
    private Button moreButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initColors();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("رمضان");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(PAGE_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(14);
        root.setPadding(p, p, p, p);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root);
        setContentView(sv);

        buildHeader();
        boolean ok = computeTable();
        buildImsakiyaCard(ok);
        buildCannonCard();
        buildMesaharatyCard();

        TextView note = text("المدفع والمسحراتي بيشتغلوا في شهر رمضان بس، وبيتظبطوا تلقائياً حسب موقعك.", 12, TEXT2);
        note.setPadding(dp(4), dp(14), dp(4), dp(8));
        root.addView(note);
    }

    // ===================== عناصر الشاشة =====================

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView intro = text("يمكنك إعداد المسحراتي ومدفع الإفطار ورؤية إمساكية الشهر الكريم من هنا", 16, TEXT);
        intro.setLineSpacing(0f, 1.4f);
        row.addView(intro, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView deco = text("🏮🌙", 44, TEXT);
        deco.setGravity(Gravity.CENTER);
        row.addView(deco, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        root.addView(row, lp);
    }

    /** يحسب أيام رمضان القادم وأوقات الفجر والمغرب لكل يوم. */
    private boolean computeTable() {
        try {
            net.time4j.calendar.HijriCalendar today = net.time4j.SystemClock.inLocalView().today()
                    .transform(net.time4j.calendar.HijriCalendar.class,
                            net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA);
            int hy = today.getYear();
            int hm = today.getMonth().getValue(); // من 1 لـ 12
            int ty = (hm > 9) ? hy + 1 : hy;
            hijriYear = ty;

            net.time4j.PlainDate d1 = net.time4j.calendar.HijriCalendar
                    .of(net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA, ty, 9, 1)
                    .transform(net.time4j.PlainDate.class);
            net.time4j.PlainDate d2 = net.time4j.calendar.HijriCalendar
                    .of(net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA, ty, 10, 1)
                    .transform(net.time4j.PlainDate.class);

            Calendar start = new GregorianCalendar(d1.getYear(), d1.getMonth() - 1, d1.getDayOfMonth());
            Calendar end = new GregorianCalendar(d2.getYear(), d2.getMonth() - 1, d2.getDayOfMonth());
            int days = (int) Math.round((end.getTimeInMillis() - start.getTimeInMillis()) / 86400000.0);
            if (days < 29 || days > 30) {
                days = 30;
            }

            int offset = 0;
            try {
                offset = Integer.parseInt(prefs.getString("hijri_offset", "0"));
            } catch (NumberFormatException ignored) {
                offset = 0;
            }
            start.add(Calendar.DAY_OF_MONTH, -offset);

            PrayTime pray = PrayTime.instancePrayTime(this);
            pray.setTimeFormat(PrayTime.TIME_FORMAT_Time12NS);

            rowCount = days;
            dayNames = new String[days];
            gregorian = new String[days];
            fajr = new String[days];
            maghrib = new String[days];

            SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", new Locale("ar"));
            SimpleDateFormat monthFmt = new SimpleDateFormat("MMMM", new Locale("ar"));
            Calendar now = Calendar.getInstance();

            for (int i = 0; i < days; i++) {
                Calendar c = (Calendar) start.clone();
                c.add(Calendar.DAY_OF_MONTH, i);
                dayNames[i] = dayFmt.format(c.getTime());
                gregorian[i] = arabicDigits(String.valueOf(c.get(Calendar.DAY_OF_MONTH)))
                        + " " + monthFmt.format(c.getTime());
                String[] t = pray.getPrayerTimesForDate(this, c);
                fajr[i] = arabicDigits(t[0]);
                maghrib[i] = arabicDigits(t[5]);
                if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                        && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
                    todayIndex = i;
                }
            }
            if (todayIndex < 0) {
                Calendar a = (Calendar) now.clone();
                a.set(Calendar.HOUR_OF_DAY, 0);
                a.set(Calendar.MINUTE, 0);
                a.set(Calendar.SECOND, 0);
                if (a.getTimeInMillis() < start.getTimeInMillis()) {
                    daysToStart = (int) Math.round((start.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void buildImsakiyaCard(boolean ok) {
        LinearLayout card = card();

        TextView title = text("إمساكية شهر رمضان الكريم لعام " + arabicDigits(String.valueOf(hijriYear)), 17, TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        if (!ok) {
            title.setText("إمساكية شهر رمضان الكريم");
        }
        card.addView(title);

        View line = new View(this);
        line.setBackgroundColor(TEXT);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        llp.topMargin = dp(10);
        llp.bottomMargin = dp(10);
        card.addView(line, llp);

        if (!ok) {
            card.addView(text("تعذّر حساب الإمساكية. تأكد إن الموقع مظبوط من الإعدادات.", 14, TEXT2));
            root.addView(card, cardParams());
            return;
        }

        if (daysToStart > 0) {
            TextView left = text("باقي " + arabicDigits(String.valueOf(daysToStart)) + " يوم على أول رمضان", 14, GOLD);
            left.setTypeface(Typeface.DEFAULT_BOLD);
            left.setPadding(0, 0, 0, dp(8));
            card.addView(left);
        }

        // رأس الجدول
        card.addView(tableRow("اليوم", "", "ميلادي", "فجر", "مغرب", true, false, 0));

        tableBody = new LinearLayout(this);
        tableBody.setOrientation(LinearLayout.VERTICAL);
        card.addView(tableBody);
        fillRows();

        moreButton = new Button(this);
        moreButton.setAllCaps(false);
        moreButton.setTextColor(Color.WHITE);
        moreButton.setTextSize(17);
        moreButton.setTypeface(Typeface.DEFAULT_BOLD);
        moreButton.setBackgroundColor(TEAL);
        updateMoreButton();
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expanded = !expanded;
                fillRows();
                updateMoreButton();
            }
        });
        card.addView(moreButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView share = text("مشاركة", 16, TEXT);
        share.setTypeface(Typeface.DEFAULT_BOLD);
        share.setPaintFlags(share.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        share.setPadding(dp(4), dp(14), dp(4), dp(4));
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareTable();
            }
        });
        card.addView(share);

        TextView foot = text("الأوقات محسوبة حسب موقعك وطريقة الحساب المختارة في الإعدادات.", 12, TEXT2);
        foot.setPadding(dp(4), dp(8), dp(4), 0);
        card.addView(foot);

        root.addView(card, cardParams());
    }

    private void fillRows() {
        tableBody.removeAllViews();
        int shown = expanded ? rowCount : Math.min(INITIAL_ROWS, rowCount);
        for (int i = 0; i < shown; i++) {
            tableBody.addView(tableRow(arabicDigits(String.valueOf(i + 1)) + "  " + dayNames[i], "",
                    gregorian[i], fajr[i], maghrib[i], false, i % 2 == 1, i == todayIndex ? 2 : 0));
        }
    }

    private void updateMoreButton() {
        if (rowCount <= INITIAL_ROWS) {
            moreButton.setVisibility(View.GONE);
        } else {
            moreButton.setText(expanded ? "عرض أقل" : "عرض باقي الشهر");
        }
    }

    /** صف واحد في الجدول. mode: 2 = صف النهاردة. */
    private View tableRow(String day, String unused, String greg, String f, String m,
                          boolean header, boolean stripe, int mode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int bg;
        if (header) {
            bg = TEAL;
        } else if (mode == 2) {
            bg = TODAY;
        } else {
            bg = stripe ? STRIPE : CARD;
        }
        row.setBackgroundColor(bg);
        int color = header ? Color.WHITE : TEXT;
        float size = header ? 15 : 14;
        int padV = dp(header ? 10 : 9);
        row.setPadding(dp(6), padV, dp(6), padV);

        addCell(row, day, size, color, 1.9f, header || mode == 2);
        addCell(row, greg, size, color, 1.9f, header);
        addCell(row, f, size, color, 1f, header);
        addCell(row, m, size, color, 1f, header);
        return row;
    }

    private void addCell(LinearLayout row, String s, float size, int color, float weight, boolean bold) {
        TextView t = text(s, size, color);
        t.setGravity(Gravity.CENTER);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight));
    }

    private void shareTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("إمساكية شهر رمضان ").append(arabicDigits(String.valueOf(hijriYear))).append("\n");
        sb.append("اليوم | التاريخ | الفجر | المغرب\n");
        for (int i = 0; i < rowCount; i++) {
            sb.append(arabicDigits(String.valueOf(i + 1))).append(" ").append(dayNames[i]).append(" | ")
                    .append(gregorian[i]).append(" | ").append(fajr[i]).append(" | ").append(maghrib[i]).append("\n");
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(share, "مشاركة الإمساكية"));
    }

    private void buildCannonCard() {
        LinearLayout card = card();
        card.addView(switchRow("🔊  مدفع الإفطار", "يشتغل وقت الإفطار في رمضان",
                prefs.getBoolean("ramadan_cannon_enabled", true), "ramadan_cannon_enabled"));
        root.addView(card, cardParams());
    }

    private void buildMesaharatyCard() {
        LinearLayout card = card();
        final boolean enabled = prefs.getBoolean("ramadan_mesaharaty_enabled", true);
        card.addView(switchRow("🥁  المسحراتي", "يشتغل في الوقت الذي تحدده",
                enabled, "ramadan_mesaharaty_enabled"));

        View line = new View(this);
        line.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        llp.topMargin = dp(12);
        llp.bottomMargin = dp(12);
        card.addView(line, llp);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL);
        timeRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView label = text("وقت المسحراتي", 16, TEXT);
        timeRow.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Button timeBtn = new Button(this);
        timeBtn.setAllCaps(false);
        timeBtn.setTextColor(Color.WHITE);
        timeBtn.setBackgroundColor(TEAL);
        timeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        timeBtn.setText(displayTime(prefs.getString("mesaharaty_time", "03:00")));
        timeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickMesaharatyTime(timeBtn);
            }
        });
        timeRow.addView(timeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)));
        card.addView(timeRow);
        root.addView(card, cardParams());
    }

    private void pickMesaharatyTime(final Button timeBtn) {
        String cur = prefs.getString("mesaharaty_time", "03:00");
        int h = 3;
        int m = 0;
        try {
            h = TimePreference.getHour(cur);
            m = TimePreference.getMinute(cur);
        } catch (Exception ignored) {
            h = 3;
            m = 0;
        }
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                String value = String.format(Locale.US, "%02d:%02d", hourOfDay, minute);
                prefs.edit().putString("mesaharaty_time", value).apply();
                timeBtn.setText(displayTime(value));
                rescheduleAlarms();
            }
        }, h, m, false).show();
    }

    /** سطر فيه عنوان ووصف ومفتاح تشغيل مربوط بإعداد. */
    private View switchRow(String title, String subtitle, boolean checked, final String prefKey) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(t);
        info.addView(text(subtitle, 13, TEXT2));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(prefKey, isChecked).apply();
                rescheduleAlarms();
            }
        });
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** نفس اللي بيحصل لما أي إعداد تذكير يتغير. */
    private void rescheduleAlarms() {
        try {
            new MyAlarmsManager(getApplicationContext()).UpdateAllApplicableAlarms();
        } catch (Exception ignored) {
            // لو حصل خطأ هيتظبط في أول جدولة عادية
        }
    }

    // ===================== أدوات صغيرة =====================

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), STROKE);
        c.setBackground(bg);
        c.setElevation(dp(2));
        int p = dp(16);
        c.setPadding(p, p, p, p);
        return c;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        return lp;
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** يحوّل الأرقام الإنجليزية لعربية (٠١٢٣…) بشكل ثابت على كل الأجهزة. */
    static String arabicDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append((char) ('\u0660' + (c - '0')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** "03:00" -> "٠٣:٠٠ ص" */
    private String displayTime(String value) {
        try {
            int h = TimePreference.getHour(value);
            int m = TimePreference.getMinute(value);
            String suffix = h < 12 ? "ص" : "م";
            int h12 = h % 12 == 0 ? 12 : h % 12;
            return arabicDigits(String.format(Locale.US, "%d:%02d", h12, m)) + " " + suffix;
        } catch (Exception e) {
            return value;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
