package com.alaaeltaweel.thikrallah.Fragments;


import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.icu.util.IslamicCalendar;
import android.icu.util.ULocale;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.TextView;
import net.time4j.*;
import net.time4j.calendar.HijriCalendar;
import net.time4j.format.expert.ChronoFormatter;
import net.time4j.format.expert.PatternType;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.Models.Prayer;
import com.alaaeltaweel.thikrallah.Notification.MyAlarmsManager;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.CustomLocation;
import com.alaaeltaweel.thikrallah.Utilities.MainInterface;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;

import java.util.Calendar;

import android.widget.RadioGroup;

public class AthanFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener, View.OnClickListener, DialogInterface.OnDismissListener {

    private Prayer[] prayers;

    private MainInterface mCallback;
    private TextView prayer1_time;
    private TextView HijriDate;
    private TextView prayer2_time;
    private TextView prayer3_time;
    private TextView prayer4_time;
    private TextView prayer5_time;
    private TextView sunrise_time;
    private SwitchCompat fajr_switch;
    private SwitchCompat duhr_switch;
    private SwitchCompat asr_switch;
    private SwitchCompat maghrib_switch;
    private SwitchCompat ishaa_switch;
   // ── التنبيه قبل الأذان ──
    private LinearLayout preAthanRow1, preAthanRow2, preAthanRow3, preAthanRow4, preAthanRow5;
    private EditText preAthanMinutes1, preAthanMinutes2, preAthanMinutes3, preAthanMinutes4, preAthanMinutes5;
    private SwitchCompat preAthanCheck1, preAthanCheck2, preAthanCheck3, preAthanCheck4, preAthanCheck5;
    private SharedPreferences mPrefs;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
    private CheckBox is_Manual_Location;
    private TextView currentLocation;
   private LinearLayout iqamaRow1, iqamaRow2, iqamaRow3, iqamaRow4, iqamaRow5;
    private EditText iqamaMinutes1, iqamaMinutes2, iqamaMinutes3, iqamaMinutes4, iqamaMinutes5;
    private SwitchCompat iqamaCheck1, iqamaCheck2, iqamaCheck3, iqamaCheck4, iqamaCheck5;
    private RadioGroup iqamaSound1, iqamaSound2, iqamaSound3, iqamaSound4, iqamaSound5;
    // ── العداد التنازلي ──
    private TextView countdownTimerView;
    private CountDownTimer countdownTimer;

    // ── الخط الديجيتال ──
    private Typeface digitalFont;

    public AthanFragment() {
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equalsIgnoreCase("latitude") || key.equalsIgnoreCase("longitude")
                || key.equalsIgnoreCase("isCustomLocation") || key.equalsIgnoreCase("c_latitude")
                || key.equalsIgnoreCase("c_longitude") || key.equalsIgnoreCase("city")) {
            if (this.getView() != null) {
                updateprayerTimes();
                boolean isLocationManual = PreferenceManager.getDefaultSharedPreferences(this.getContext()).getBoolean("isCustomLocation", false);
                is_Manual_Location.setChecked(isLocationManual);
                currentLocation.setText(MainActivity.getCityCountryLocation(this.getContext()));
            }
        }
        if (key.equals("hijri_offset")) {
            HijriDate.setText(getHijriDate());
        }
  new MyAlarmsManager(getContext()).UpdateAllApplicableAlarms();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        MainActivity.setLocale(context);
        try {
            prefListener = this;
            mCallback = (MainInterface) context;
            mCallback.requestLocationUpdate();
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement MainInterface");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        MainActivity.setLocale(this.getContext());

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());

        ((AppCompatActivity) this.getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ((AppCompatActivity) this.getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);
        this.setHasOptionsMenu(true);

        View view = inflater.inflate(R.layout.athan_fragment, container, false);

        // ── تحميل الخط الديجيتال ──
        try {
            digitalFont = Typeface.createFromAsset(
                    getActivity().getAssets(),
                    "fonts_2/Orbitron-Bold.ttf"
            );
        } catch (Exception e) {
            Log.e("AthanFragment", "Failed to load digital font: " + e.getMessage());
            digitalFont = null;
        }

        HijriDate = view.findViewById(R.id.Hijri_date);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IslamicCalendar islamic_cal = (IslamicCalendar) IslamicCalendar
                    .getInstance(new ULocale("ar_SA@calendar=islamic"));
            islamic_cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC);
            HijriDate.setText(getHijriDate());
        }

        countdownTimerView = view.findViewById(R.id.countdown_timer);

        prayer1_time = view.findViewById(R.id.athan_timing1);
        prayer2_time = view.findViewById(R.id.athan_timing2);
        prayer3_time = view.findViewById(R.id.athan_timing3);
        prayer4_time = view.findViewById(R.id.athan_timing4);
        prayer5_time = view.findViewById(R.id.athan_timing5);
        sunrise_time = view.findViewById(R.id.sunrise_timing1);

        // ── تطبيق الخط + اللون الأحمر المضيء ──
        applyDigitalStyle(prayer1_time);
        applyDigitalStyle(prayer2_time);
        applyDigitalStyle(prayer3_time);
        applyDigitalStyle(prayer4_time);
        applyDigitalStyle(prayer5_time);
        applyDigitalStyle(sunrise_time);
        applyDigitalStyle(countdownTimerView);

        is_Manual_Location = view.findViewById(R.id.is_manual_location);
        currentLocation    = view.findViewById(R.id.current_location);

        boolean isLocationManual = PreferenceManager.getDefaultSharedPreferences(this.getContext()).getBoolean("isCustomLocation", false);
        is_Manual_Location.setChecked(isLocationManual);
        currentLocation.setText(MainActivity.getCityCountryLocation(this.getContext()));

        currentLocation.setOnClickListener(this);
        is_Manual_Location.setOnClickListener(this);

        fajr_switch    = view.findViewById(R.id.switch1);
        duhr_switch    = view.findViewById(R.id.switch2);
        asr_switch     = view.findViewById(R.id.switch3);
        maghrib_switch = view.findViewById(R.id.switch4);
        ishaa_switch   = view.findViewById(R.id.switch5);
        fajr_switch.setChecked(mPrefs.getBoolean("isFajrReminder", true));
        duhr_switch.setChecked(mPrefs.getBoolean("isDuhrReminder", true));
        asr_switch.setChecked(mPrefs.getBoolean("isAsrReminder", true));
        maghrib_switch.setChecked(mPrefs.getBoolean("isMaghribReminder", true));
        ishaa_switch.setChecked(mPrefs.getBoolean("isIshaaReminder", true));
        
// ── ربط views التنبيه قبل الأذان ──
        preAthanRow1 = view.findViewById(R.id.pre_athan_row1);
        preAthanRow2 = view.findViewById(R.id.pre_athan_row2);
        preAthanRow3 = view.findViewById(R.id.pre_athan_row3);
        preAthanRow4 = view.findViewById(R.id.pre_athan_row4);
        preAthanRow5 = view.findViewById(R.id.pre_athan_row5);

        preAthanMinutes1 = view.findViewById(R.id.pre_athan_minutes1);
        preAthanMinutes2 = view.findViewById(R.id.pre_athan_minutes2);
        preAthanMinutes3 = view.findViewById(R.id.pre_athan_minutes3);
        preAthanMinutes4 = view.findViewById(R.id.pre_athan_minutes4);
        preAthanMinutes5 = view.findViewById(R.id.pre_athan_minutes5);

        preAthanCheck1 = view.findViewById(R.id.pre_athan_check1);
        preAthanCheck2 = view.findViewById(R.id.pre_athan_check2);
        preAthanCheck3 = view.findViewById(R.id.pre_athan_check3);
        preAthanCheck4 = view.findViewById(R.id.pre_athan_check4);
        preAthanCheck5 = view.findViewById(R.id.pre_athan_check5);

        // ── تحميل القيم المحفوظة ──
        setupPreAthan(fajr_switch,    preAthanRow1, preAthanCheck1, preAthanMinutes1, "fajr");
        setupPreAthan(duhr_switch,    preAthanRow2, preAthanCheck2, preAthanMinutes2, "dhuhr");
        setupPreAthan(asr_switch,     preAthanRow3, preAthanCheck3, preAthanMinutes3, "asr");
        setupPreAthan(maghrib_switch, preAthanRow4, preAthanCheck4, preAthanMinutes4, "maghrib");
        setupPreAthan(ishaa_switch,   preAthanRow5, preAthanCheck5, preAthanMinutes5, "isha");
     
        iqamaRow1 = view.findViewById(R.id.iqama_row1);
        iqamaRow2 = view.findViewById(R.id.iqama_row2);
        iqamaRow3 = view.findViewById(R.id.iqama_row3);
        iqamaRow4 = view.findViewById(R.id.iqama_row4);
        iqamaRow5 = view.findViewById(R.id.iqama_row5);
        iqamaMinutes1 = view.findViewById(R.id.iqama_minutes1);
        iqamaMinutes2 = view.findViewById(R.id.iqama_minutes2);
        iqamaMinutes3 = view.findViewById(R.id.iqama_minutes3);
        iqamaMinutes4 = view.findViewById(R.id.iqama_minutes4);
        iqamaMinutes5 = view.findViewById(R.id.iqama_minutes5);
        iqamaCheck1 = view.findViewById(R.id.iqama_check1);
        iqamaCheck2 = view.findViewById(R.id.iqama_check2);
        iqamaCheck3 = view.findViewById(R.id.iqama_check3);
        iqamaCheck4 = view.findViewById(R.id.iqama_check4);
        iqamaCheck5 = view.findViewById(R.id.iqama_check5);
        iqamaSound1 = view.findViewById(R.id.iqama_sound1);
        iqamaSound2 = view.findViewById(R.id.iqama_sound2);
        iqamaSound3 = view.findViewById(R.id.iqama_sound3);
        iqamaSound4 = view.findViewById(R.id.iqama_sound4);
        iqamaSound5 = view.findViewById(R.id.iqama_sound5);
        setupIqama(fajr_switch,    iqamaRow1, iqamaCheck1, iqamaMinutes1, iqamaSound1, "fajr");
        setupIqama(duhr_switch,    iqamaRow2, iqamaCheck2, iqamaMinutes2, iqamaSound2, "dhuhr");
        setupIqama(asr_switch,     iqamaRow3, iqamaCheck3, iqamaMinutes3, iqamaSound3, "asr");
        setupIqama(maghrib_switch, iqamaRow4, iqamaCheck4, iqamaMinutes4, iqamaSound4, "maghrib");
        setupIqama(ishaa_switch,   iqamaRow5, iqamaCheck5, iqamaMinutes5, iqamaSound5, "isha");
       

        fajr_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isFajrReminder", isChecked).apply();
            updateAthanAlarms();
        });
        duhr_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isDuhrReminder", isChecked).apply();
            updateAthanAlarms();
        });
        asr_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isAsrReminder", isChecked).apply();
            updateAthanAlarms();
        });
        maghrib_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isMaghribReminder", isChecked).apply();
            updateAthanAlarms();
        });
        ishaa_switch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isIshaaReminder", isChecked).apply();
            updateAthanAlarms();
        });

        PreferenceManager.getDefaultSharedPreferences(this.getContext()).registerOnSharedPreferenceChangeListener(prefListener);
        this.updateprayerTimes();

        // ✅ منطق التابات - إظهار كارد الصلاة المختارة وإخفاء الباقي
        View tabFajr = view.findViewById(R.id.tab_content_fajr);
        View tabDhuhr = view.findViewById(R.id.tab_content_dhuhr);
        View tabAsr = view.findViewById(R.id.tab_content_asr);
        View tabMaghrib = view.findViewById(R.id.tab_content_maghrib);
        View tabIshaa = view.findViewById(R.id.tab_content_ishaa);

        TextView tabBtnFajr = view.findViewById(R.id.tab_btn_fajr);
        TextView tabBtnDhuhr = view.findViewById(R.id.tab_btn_dhuhr);
        TextView tabBtnAsr = view.findViewById(R.id.tab_btn_asr);
        TextView tabBtnMaghrib = view.findViewById(R.id.tab_btn_maghrib);
        TextView tabBtnIshaa = view.findViewById(R.id.tab_btn_ishaa);

        View[] allTabContents = {tabFajr, tabDhuhr, tabAsr, tabMaghrib, tabIshaa};
        TextView[] allTabButtons = {tabBtnFajr, tabBtnDhuhr, tabBtnAsr, tabBtnMaghrib, tabBtnIshaa};

        View.OnClickListener tabClickListener = clickedView -> {
            for (int i = 0; i < allTabButtons.length; i++) {
                boolean isSelected = (clickedView == allTabButtons[i]);
                allTabContents[i].setVisibility(isSelected ? View.VISIBLE : View.GONE);
                allTabButtons[i].setBackgroundResource(isSelected ? R.drawable.tab_bg_selected : R.drawable.tab_bg_unselected);
                allTabButtons[i].setTextColor(Color.parseColor(isSelected ? "#1B2234" : "#FFFFFF"));
            }
        };

        for (TextView btn : allTabButtons) {
            btn.setOnClickListener(tabClickListener);
        }

        return view;
    }

    // ── تطبيق الخط + اللون الأحمر + الـ Glow ──
    private void applyDigitalStyle(TextView tv) {
        if (tv == null) return;
        if (digitalFont != null) tv.setTypeface(digitalFont);
        tv.setTextColor(Color.RED);
        tv.setShadowLayer(18f, 0f, 0f, Color.RED);
        tv.setBackgroundColor(Color.BLACK);
        tv.setPadding(8, 4, 8, 4);
    }

    private void updateprayerTimes() {
        double latitude  = Double.parseDouble(MainActivity.getLatitude(this.getContext()));
        double longitude = Double.parseDouble(MainActivity.getLongitude(this.getContext()));
        if (latitude == 0.0 && longitude == 0.0) {
            prayer1_time.setText("--:--");
            sunrise_time.setText("--:--");
            prayer2_time.setText("--:--");
            prayer3_time.setText("--:--");
            prayer4_time.setText("--:--");
            prayer5_time.setText("--:--");
            return;
        }
        prayers = getPrayersArray();
        try {
            prayer1_time.setText(prayers[0].getTime());
            sunrise_time.setText(prayers[1].getTime());
            prayer2_time.setText(prayers[2].getTime());
            prayer3_time.setText(prayers[3].getTime());
            prayer4_time.setText(prayers[5].getTime());
            prayer5_time.setText(prayers[6].getTime());
        } catch (NullPointerException e) {
            // ignore
        }

        updateAthanAlarms();
        startCountdown();
        HijriDate.setText(getHijriDate());
    }

    private void updateAthanAlarms() {
        new MyAlarmsManager(this.getActivity().getApplicationContext()).UpdateAllApplicableAlarms();
    }

    // ══════════════════════════════════════════
    // العداد التنازلي
    // ══════════════════════════════════════════

    private void startCountdown() {
        if (prayers == null) return;
        if (countdownTimer != null) countdownTimer.cancel();

        int[] indices = {0, 2, 3, 5, 6};
        long now = System.currentTimeMillis();
        long millisLeft = -1;
        String nextName = "";

        for (int i : indices) {
            long t = parseTimeToMillis(prayers[i].getTime());
            if (t > now) {
                millisLeft = t - now;
                nextName   = prayers[i].getName();
                break;
            }
        }

        if (millisLeft < 0) {
            millisLeft = parseTimeToMillis(prayers[0].getTime()) + 86400000L - now;
            nextName   = prayers[0].getName();
        }

        final String finalName = nextName;

        countdownTimer = new CountDownTimer(millisLeft, 1000) {
            @Override
            public void onTick(long ms) {
                long h = ms / 3600000;
                long m = (ms % 3600000) / 60000;
                long s = (ms % 60000) / 1000;
                if (countdownTimerView != null) {
                    countdownTimerView.setText(
                            finalName + "  |  " + String.format("%02d:%02d:%02d", h, m, s));
                }
            }
            @Override
            public void onFinish() {
                if (getContext() != null) startCountdown();
            }
        }.start();
    }

    private long parseTimeToMillis(String t) {
        try {
            t = t.trim().toLowerCase();
            boolean pm = t.contains("pm");
            t = t.replace("am", "").replace("pm", "").trim();
            String[] parts = t.split(":");
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            if (pm && h != 12) h += 12;
            if (!pm && h == 12) h = 0;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, h);
            cal.set(Calendar.MINUTE, m);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return -1;
        }
    }

    // ══════════════════════════════════════════

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.getActivity().onBackPressed();
            return true;
        }
        return false;
    }

    private Prayer[] getPrayersArray() {
        PrayTime prayersObject = PrayTime.instancePrayTime(this.getActivity().getApplicationContext());
        String[] times  = prayersObject.getPrayerTimes(this.getActivity().getApplicationContext());
        String[] names  = prayersObject.getTimeNames();
        Prayer[] prayers = new Prayer[7];
        for (int i = 0; i < 7; i++) {
            prayers[i] = new Prayer(names[i], times[i]);
        }
        return prayers;
    }

    @Override
    public void onPause() {
        if (countdownTimer != null) countdownTimer.cancel();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this.getContext()).registerOnSharedPreferenceChangeListener(prefListener);
        this.updateprayerTimes();
        HijriDate.setText(getHijriDate());
    }

    @Override
    public void onDestroy() {
        if (countdownTimer != null) countdownTimer.cancel();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.is_manual_location:
                if (is_Manual_Location.isChecked()) {
                    CustomLocation Customlocation = new CustomLocation(this.getActivity());
                    Customlocation.setCanceledOnTouchOutside(true);
                    Customlocation.setOnDismissListener(this);
                    Customlocation.show();
                } else {
                    PreferenceManager.getDefaultSharedPreferences(this.getContext()).edit().putBoolean("isCustomLocation", false).apply();
                    mCallback.requestLocationUpdate();
                }
                updateprayerTimes();
                updateAthanAlarms();
                break;
            case R.id.current_location:
                boolean isLocationManual = PreferenceManager.getDefaultSharedPreferences(this.getContext()).getBoolean("isCustomLocation", false);
                if (isLocationManual) {
                    CustomLocation Customlocation = new CustomLocation(this.getActivity());
                    Customlocation.setCanceledOnTouchOutside(true);
                    Customlocation.setOnDismissListener(this);
                    Customlocation.show();
                }
                break;
        }
    }

    private String getHijriDate() {
        ChronoFormatter<HijriCalendar> hijriFormat =
                ChronoFormatter.setUp(HijriCalendar.family(), this.getResources().getConfiguration().locale)
                        .addPattern(" dd MMMM yyyy", PatternType.CLDR)
                        .build()
                        .withCalendarVariant(HijriCalendar.VARIANT_UMALQURA);

       int hijriOffset = Integer.parseInt(mPrefs.getString("hijri_offset", "0"));
HijriCalendar today =
        SystemClock.inLocalView().today().transform(
                HijriCalendar.class,
                HijriCalendar.VARIANT_UMALQURA
        );
if (hijriOffset != 0) {
    today = today.plus(hijriOffset, HijriCalendar.Unit.DAYS);
}
        return hijriFormat.format(today);
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        Log.d("AthanFragment", "onDismiss called.");
        if (this.getContext() != null) {
            boolean isLocationManual = PreferenceManager.getDefaultSharedPreferences(this.getContext()).getBoolean("isCustomLocation", false);
            is_Manual_Location.setChecked(isLocationManual);
        }
    }
    private void setupPreAthan(SwitchCompat prayerSwitch, LinearLayout row,
                                SwitchCompat check, EditText minutes, String key) {
        // تحميل القيم المحفوظة
        boolean reminderOn = mPrefs.getBoolean("isPreAthanReminder_" + key, true);
        String mins = mPrefs.getString("preAthanMinutes_" + key, "15");
        check.setChecked(reminderOn);
        minutes.setText(mins);

        // إظهار/إخفاء الصف حسب حالة السويتش
        row.setVisibility(prayerSwitch.isChecked() ? View.VISIBLE : View.GONE);

        // لما السويتش يتغير
        prayerSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
            row.setVisibility(isChecked ? View.VISIBLE : View.GONE));

        // لما الـ checkbox يتغير
        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isPreAthanReminder_" + key, isChecked).apply();
            updateAthanAlarms();
        });

        // لما المستخدم يغير الدقايق
        minutes.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String val = s.toString().trim();
                if (!val.isEmpty() && Integer.parseInt(val) >= 1) {
                    mPrefs.edit().putString("preAthanMinutes_" + key, val).apply();
                    new android.os.Handler().postDelayed(() -> updateAthanAlarms(), 300);
                }
            }
        });
    }
    private android.media.MediaPlayer iqamaPreviewPlayer;
    private void setupIqama(SwitchCompat prayerSwitch, LinearLayout row,
                             SwitchCompat check, EditText minutes, RadioGroup sound, String key) {
        boolean iqamaOn = mPrefs.getBoolean("isIqamaReminder_" + key, false);
        String mins = mPrefs.getString("iqamaMinutes_" + key, "10");
        int soundChoice = mPrefs.getInt("iqamaSoundChoice_" + key, 1);
        check.setChecked(iqamaOn);
        minutes.setText(mins);
        row.setVisibility(prayerSwitch.isChecked() ? View.VISIBLE : View.GONE);

        // تحديد الـ RadioButton المحفوظ
        int checkedId = sound.getChildAt(soundChoice - 1) != null ?
            sound.getChildAt(soundChoice - 1).getId() : sound.getChildAt(0).getId();
        sound.check(checkedId);

        check.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPrefs.edit().putBoolean("isIqamaReminder_" + key, isChecked).apply();
            updateAthanAlarms();
        });
        minutes.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                String val = s.toString().trim();
                if (!val.isEmpty() && Integer.parseInt(val) >= 1) {
                    mPrefs.edit().putString("iqamaMinutes_" + key, val).apply();
                    new android.os.Handler().postDelayed(() -> updateAthanAlarms(), 300);
                }
            }
        });
        sound.setOnCheckedChangeListener((group, checkedIdNew) -> {
            int position = 1;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i).getId() == checkedIdNew) {
                    position = i + 1;
                    break;
                }
            }
            mPrefs.edit().putInt("iqamaSoundChoice_" + key, position).apply();
            updateAthanAlarms();
            if (iqamaPreviewPlayer != null) {
                iqamaPreviewPlayer.stop();
                iqamaPreviewPlayer.release();
                iqamaPreviewPlayer = null;
            }
            int soundRes;
            switch (position) {
                case 2: soundRes = R.raw.iqama_2; break;
                case 3: soundRes = R.raw.iqama_3; break;
                default: soundRes = R.raw.iqama_1; break;
            }
            iqamaPreviewPlayer = android.media.MediaPlayer.create(getActivity(), soundRes);
            iqamaPreviewPlayer.setOnCompletionListener(mp -> {
                mp.release();
                iqamaPreviewPlayer = null;
            });
            iqamaPreviewPlayer.start();
        });
    }
}


