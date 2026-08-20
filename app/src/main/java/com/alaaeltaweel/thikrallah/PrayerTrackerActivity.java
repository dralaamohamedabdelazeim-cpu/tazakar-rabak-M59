package com.alaaeltaweel.thikrallah;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrayerTrackerActivity extends AppCompatActivity {

    private CheckBox checkFajr, checkDhuhr, checkAsr, checkMaghrib, checkIsha;
    private TextView textProgress, textDate, textMotivation;
    private ProgressBar progressBar;
    private SharedPreferences prefs;

    private String getTodayKey() {
        // ✅ لازم لغة ثابتة هنا لأن ده مفتاح تخزين داخلي مش نص معروض - لو استخدمنا لغة الجهاز
        // ممكن الأرقام تتغير شكلها لو المستخدم غيّر لغة الموبايل، فيضيع تتبع الصلاة بتاعته
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prayer_tracker);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("متتبع الصلاة");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = getSharedPreferences("prayer_tracker", MODE_PRIVATE);

        checkFajr    = findViewById(R.id.check_fajr);
        checkDhuhr   = findViewById(R.id.check_dhuhr);
        checkAsr     = findViewById(R.id.check_asr);
        checkMaghrib = findViewById(R.id.check_maghrib);
        checkIsha    = findViewById(R.id.check_isha);
        textProgress = findViewById(R.id.text_progress);
        textDate     = findViewById(R.id.text_date);
        textMotivation = findViewById(R.id.text_motivation);
        progressBar  = findViewById(R.id.progress_prayers);
        Button btnReset = findViewById(R.id.btn_reset);

        // تحميل بيانات اليوم
        String key = getTodayKey();
        checkFajr.setChecked(prefs.getBoolean(key + "_fajr", false));
        checkDhuhr.setChecked(prefs.getBoolean(key + "_dhuhr", false));
        checkAsr.setChecked(prefs.getBoolean(key + "_asr", false));
        checkMaghrib.setChecked(prefs.getBoolean(key + "_maghrib", false));
        checkIsha.setChecked(prefs.getBoolean(key + "_isha", false));

        // التاريخ
        textDate.setText(new SimpleDateFormat("EEEE، d MMMM yyyy",
                new Locale("ar")).format(new Date()));

        updateProgress();

        // Listeners
        checkFajr.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(getTodayKey() + "_fajr", checked).apply();
            updateProgress();
        });
        checkDhuhr.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(getTodayKey() + "_dhuhr", checked).apply();
            updateProgress();
        });
        checkAsr.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(getTodayKey() + "_asr", checked).apply();
            updateProgress();
        });
        checkMaghrib.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(getTodayKey() + "_maghrib", checked).apply();
            updateProgress();
        });
        checkIsha.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(getTodayKey() + "_isha", checked).apply();
            updateProgress();
        });

        btnReset.setOnClickListener(v -> {
            String k = getTodayKey();
            prefs.edit()
                .putBoolean(k + "_fajr", false)
                .putBoolean(k + "_dhuhr", false)
                .putBoolean(k + "_asr", false)
                .putBoolean(k + "_maghrib", false)
                .putBoolean(k + "_isha", false)
                .apply();
            checkFajr.setChecked(false);
            checkDhuhr.setChecked(false);
            checkAsr.setChecked(false);
            checkMaghrib.setChecked(false);
            checkIsha.setChecked(false);
            updateProgress();
        });
    }

    private void updateProgress() {
        int count = 0;
        if (checkFajr.isChecked())    count++;
        if (checkDhuhr.isChecked())   count++;
        if (checkAsr.isChecked())     count++;
        if (checkMaghrib.isChecked()) count++;
        if (checkIsha.isChecked())    count++;

        textProgress.setText(count + " / 5 صلوات");
        progressBar.setProgress(count);

        String[] motivations = {
            "اللهم أعنّا على ذكرك وشكرك وحسن عبادتك",
            "الصلاة نور، حافظ عليها",
            "إن الصلاة كانت على المؤمنين كتاباً موقوتاً",
            "أكملت يومك بخير، بارك الله فيك 🌟",
            "ما شاء الله! أكملت صلوات اليوم كلها 🏆"
        };
        textMotivation.setText(motivations[Math.min(count, motivations.length - 1)]);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
