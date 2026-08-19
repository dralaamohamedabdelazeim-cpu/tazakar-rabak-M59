package com.alaaeltaweel.thikrallah.Fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.PreferenceActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.MainInterface;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;
import com.alaaeltaweel.thikrallah.hisnulmuslim.DuaGroupActivity;
import com.alaaeltaweel.thikrallah.quran.labs.androidquran.QuranDataActivity;
import com.alaaeltaweel.thikrallah.PrayerTrackerActivity;
import com.alaaeltaweel.thikrallah.RadioActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainFragment extends Fragment {
    private MainInterface mCallback;
    private Context mContext;
    SharedPreferences mPrefs;
    String TAG = "MainFragment";

    // ✅ Views للتاريخ والرمضان
    private TextView textGregorianDate;
    private TextView textHijriDate;
    private TextView textRamadanInfo;
    private TextView textSuhoor;
    private TextView textIftar;
    private TextView textCountdown;
    private TextView textCountdownLabel;
    private LinearLayout layoutSuhoorIftar;
    private TextView textWeather;
    private TextView textWeatherIcon;
    
    // ✅ Handler للعداد التنازلي
    private Handler countdownHandler = new Handler();
    private Runnable countdownRunnable;

    public MainFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Log.d("MainFragment", "onattach called");
        MainActivity.setLocale(context);
        mContext = context;
        try {
            mCallback = (MainInterface) mContext;
        } catch (ClassCastException e) {
            throw new ClassCastException(mContext.toString()
                    + " must implement MainInterface");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        MainActivity.setLocale(this.getContext());
        ((AppCompatActivity) this.getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        ((AppCompatActivity) this.getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        Button button_remind_me_settings = (Button) view.findViewById(R.id.button_settings);
        Button button_morning_thikr = (Button) view.findViewById(R.id.button_morning_thikr);
        Button button_night_thikr = (Button) view.findViewById(R.id.button_night_thikr);
        Button button_my_athkar = (Button) view.findViewById(R.id.button_my_athkar);
        Button button_sadaqa = (Button) view.findViewById(R.id.button_sadaqa);
        Button button_quran = (Button) view.findViewById(R.id.button_quran);
        Button button_hisn_almuslim = (Button) view.findViewById(R.id.hisn_almuslim);
        Button button_athan = (Button) view.findViewById(R.id.button_athan);
        Button button_qibla = (Button) view.findViewById(R.id.button_qibla);
        Button button_prayer_tracker = (Button) view.findViewById(R.id.button_prayer_tracker);
        Button button_radio = (Button) view.findViewById(R.id.button_radio);
        
        // ✅ Views التاريخ والرمضان
        textGregorianDate  = view.findViewById(R.id.text_gregorian_date);
        textHijriDate      = view.findViewById(R.id.text_hijri_date);
        textRamadanInfo    = view.findViewById(R.id.text_ramadan_info);
        textSuhoor         = view.findViewById(R.id.text_suhoor);
        textIftar          = view.findViewById(R.id.text_iftar);
        textCountdown      = view.findViewById(R.id.text_countdown);
        textCountdownLabel = view.findViewById(R.id.text_countdown_label);
        layoutSuhoorIftar  = view.findViewById(R.id.layout_suhoor_iftar);
        textWeather     = view.findViewById(R.id.text_weather);
        textWeatherIcon = view.findViewById(R.id.text_weather_icon);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
      if (mPrefs != null) {
    String cachedWeather = mPrefs.getString("cached_weather_text", "");
    String cachedIcon = mPrefs.getString("cached_weather_icon", "🌤️");
    if (!cachedWeather.isEmpty()) {
        textWeather.setText(cachedWeather);
        textWeatherIcon.setText(cachedIcon);
    }
    long lastFetch = mPrefs.getLong("last_weather_fetch", 0);
    if (System.currentTimeMillis() - lastFetch > 60 * 60 * 1000) {
        fetchWeather();
    }
    }
            
    
        // ✅ ابدأ عرض التاريخ والرمضان
        updateDateAndRamadan();
        startCountdown();

        button_athan.setOnClickListener(v -> mCallback.launchFragment(new AthanFragment(), new Bundle(), "AthanFragment"));
        button_qibla.setOnClickListener(v -> mCallback.launchFragment(new QiblaFragment(), new Bundle(), "QiblaFragment"));
        
        button_quran.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(v.getContext(), QuranDataActivity.class);
            startActivityForResult(intent, 0);
        });
        button_sadaqa.setOnClickListener(v -> mCallback.share());
        button_remind_me_settings.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(v.getContext(), PreferenceActivity.class);
            startActivityForResult(intent, 0);
        });
        button_hisn_almuslim.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(v.getContext(), DuaGroupActivity.class);
            startActivityForResult(intent, 0);
        });
        button_morning_thikr.setOnClickListener(v -> {
            Bundle data = new Bundle();
            data.putString("DataType", MainActivity.DATA_TYPE_DAY_THIKR);
            mCallback.launchFragment(new ThikrFragment(), data, "ThikrFragment");
        });
        button_night_thikr.setOnClickListener(v -> {
            Bundle data = new Bundle();
            data.putString("DataType", MainActivity.DATA_TYPE_NIGHT_THIKR);
            mCallback.launchFragment(new ThikrFragment(), data, "ThikrFragment");
        });
        button_my_athkar.setOnClickListener(v -> {
            Bundle data = new Bundle();
            mCallback.launchFragment(new MyAthkarFragment(), data, "MyAthkarFragment");
       });
       button_prayer_tracker.setOnClickListener(v -> {
       Intent intent = new Intent();
       intent.setClass(v.getContext(), PrayerTrackerActivity.class);
       startActivityForResult(intent, 0);
       });
    button_radio.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setClass(v.getContext(), RadioActivity.class);
            startActivityForResult(intent, 0);
        });
        Log.d(TAG, "requestBatteryExclusion");
        requestBatteryExclusion(mContext);
        return view;
    }
 


    // ===================== التاريخ والرمضان =====================
    private void updateDateAndRamadan() {
        try {
            // التاريخ الميلادي
            SimpleDateFormat gregorianFormat = new SimpleDateFormat("EEEE، d MMMM yyyy", new Locale("ar"));
            String gregorianDate = gregorianFormat.format(new Date());
            textGregorianDate.setText(gregorianDate);

            // التاريخ الهجري (مع تطبيق تعديل المستخدم hijri_offset)
            int hijriOffset = Integer.parseInt(mPrefs.getString("hijri_offset", "0"));
            net.time4j.calendar.HijriCalendar hijriToday =
    net.time4j.SystemClock.inLocalView().today().transform(
        net.time4j.calendar.HijriCalendar.class,
        net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA);
            if (hijriOffset != 0) {
                hijriToday = hijriToday.plus(hijriOffset, net.time4j.calendar.HijriCalendar.Unit.DAYS);
            }
            int hijriDay   = hijriToday.getDayOfMonth();
            int hijriMonth = hijriToday.getMonth().getValue() - 1; // نفس فهرسة hijriMonths[] القديمة (0-11)
            int hijriYear  = hijriToday.getYear();

            String[] hijriMonths = {
                    "محرم", "صفر", "ربيع الأول", "ربيع الثاني",
                    "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان",
                    "رمضان", "شوال", "ذو القعدة", "ذو الحجة"
            };

            textHijriDate.setText(hijriDay + " " + hijriMonths[hijriMonth] + " " + hijriYear);
            // أوقات الصلاة من PrayTime
            PrayTime prayersObject = PrayTime.instancePrayTime(mContext);
            String[] times = prayersObject.getPrayerTimes(mContext);
            // times[0]=فجر, times[1]=شروق, times[2]=ظهر, times[3]=عصر, times[4]=مغرب, times[5]=عشاء

            boolean isRamadan = (hijriMonth == 8);

            if (isRamadan) {
                textRamadanInfo.setText("🌙 رمضان كريم - اليوم " + hijriDay);
                layoutSuhoorIftar.setVisibility(View.VISIBLE);
                if (times != null && times.length >= 5) {
                    textSuhoor.setText("الإمساك: " + times[0]);
                    textIftar.setText("الإفطار: " + times[4]);
                }
            } else {
                // كم يوم فاضل على رمضان
                int targetRamadanYear = (hijriMonth >= 8) ? hijriYear + 1 : hijriYear;
net.time4j.calendar.HijriCalendar nextRamadan = net.time4j.calendar.HijriCalendar.of(
        net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA, targetRamadanYear, 9, 1);
long daysToRamadan = net.time4j.calendar.HijriCalendar.Unit.DAYS.between(
        hijriToday, nextRamadan, net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA);
                textRamadanInfo.setText("🌙 " + hijriMonths[hijriMonth]);
                textCountdownLabel.setText("باقي على رمضان");
                textCountdown.setText(daysToRamadan + " يوم");
                layoutSuhoorIftar.setVisibility(View.GONE);
            }

        } catch (Exception e) {
            Log.d(TAG, "Error updating date: " + e.getMessage());
        }
    }

    // ===================== العداد التنازلي =====================
    private void startCountdown() {
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (getActivity() == null || !isAdded()) return;
                try {
                    net.time4j.calendar.HijriCalendar islamicCalendar = 
    net.time4j.SystemClock.inLocalView().today().transform(
        net.time4j.calendar.HijriCalendar.class,
        net.time4j.calendar.HijriCalendar.VARIANT_UMALQURA);
                    int hijriMonth = islamicCalendar.getMonth().getValue() - 1;
                    boolean isRamadan = (hijriMonth == 8);

                    if (isRamadan) {
                        PrayTime prayersObject = PrayTime.instancePrayTime(mContext);
                        String[] times = prayersObject.getPrayerTimes(mContext);
                        if (times != null && times.length >= 5) {
                            long now = System.currentTimeMillis();
                            long iftarMs  = parseTimeToMs(times[4]);
                            long suhoorMs = parseTimeToMs(times[0]);

                            long diff;
                            String label;
                            if (now < suhoorMs) {
                                diff  = suhoorMs - now;
                                label = "باقي على الإمساك";
                            } else if (now < iftarMs) {
                                diff  = iftarMs - now;
                                label = "باقي على الإفطار";
                            } else {
                                diff  = 0;
                                label = "أفطر على بركة الله 🌙";
                            }

                            if (diff > 0) {
                                long hours   = diff / (1000 * 60 * 60);
                                long minutes = (diff % (1000 * 60 * 60)) / (1000 * 60);
                                long seconds = (diff % (1000 * 60)) / 1000;
                                textCountdown.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                            } else {
                                textCountdown.setText("🌙");
                            }
                            textCountdownLabel.setText(label);
                        }
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Countdown error: " + e.getMessage());
                }
                countdownHandler.postDelayed(this, 1000);
            }
        };
        countdownHandler.post(countdownRunnable);
    }

    private long parseTimeToMs(String timeStr) {
        try {
            String format = timeStr.length() == 8 ? "hh:mm a" : "HH:mm";
            SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
            Date parsed = sdf.parse(timeStr);
            Calendar cal = Calendar.getInstance();
            Calendar parsedCal = Calendar.getInstance();
            parsedCal.setTime(parsed);
            cal.set(Calendar.HOUR_OF_DAY, parsedCal.get(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, parsedCal.get(Calendar.MINUTE));
            cal.set(Calendar.SECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateDateAndRamadan();
        logScreen();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        countdownHandler.removeCallbacksAndMessages(null);
    }

    private void logScreen() {
    }

    private void requestBatteryExclusion(Context mContext) {
        if (!mPrefs.getBoolean("isFirstLaunch", true)
                && !mPrefs.getBoolean("permissionsRequested", false)) {
            mPrefs.edit().putBoolean("permissionsRequested", true).apply();
            mCallback.requestOverLayPermission();
            mCallback.requestNotificationPermission();
            mCallback.requestBatteryExclusion();
            mCallback.requestExactAlarmPermission();
            mCallback.requestLocationPermission();
            if (mPrefs.getBoolean("isMediaPermissionNeeded", false)) {
                mCallback.requestMediaOrStoragePermission();
            }
        }
    }
private void fetchWeather() {
        String lat = MainFragment.this.mPrefs.getString("latitude", "0");
        String lon = MainFragment.this.mPrefs.getString("longitude", "0");
        // ✅ لو الموقع لسه ما اتحددش (لسه على القيمة الافتراضية صفر)، منجيبش طقس غلط لنقطة في المحيط
        if ("0".equals(lat) && "0".equals(lon)) {
            Log.d(TAG, "Location not set yet - skipping weather fetch");
            return;
        }
        String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat + "&lon=" + lon + "&appid=" + com.alaaeltaweel.thikrallah.BuildConfig.WEATHER_API_KEY + "&units=metric&lang=ar";
        new Thread(() -> {
            try {
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                double temp = json.getJSONObject("main").getDouble("temp");
                String desc = json.getJSONArray("weather").getJSONObject(0).getString("description");
                String icon = getWeatherIcon(json.getJSONArray("weather").getJSONObject(0).getString("main"));
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        textWeather.setText(Math.round(temp) + "°C  " + desc);
                        textWeatherIcon.setText(icon);
                        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                       .putString("cached_weather_text", Math.round(temp) + "°C  " + desc)
                .putString("cached_weather_icon", icon)
                .putLong("last_weather_fetch", System.currentTimeMillis())
                .apply(); 
                    });
                }
            } catch (Exception e) {
                Log.d(TAG, "Weather error: " + e.getMessage());
            }
        }).start();
    }

    private String getWeatherIcon(String main) {
        switch (main) {
            case "Clear": return "☀️";
            case "Clouds": return "☁️";
            case "Rain": return "🌧️";
            case "Thunderstorm": return "⛈️";
            case "Snow": return "❄️";
            case "Drizzle": return "🌦️";
            case "Mist": case "Fog": case "Haze": return "🌫️";
            default: return "🌤️";
        }
    }
}


