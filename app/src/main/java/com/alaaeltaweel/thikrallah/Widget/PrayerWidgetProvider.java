package com.alaaeltaweel.thikrallah.Widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrayerWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "PrayerWidget";
    public static final String ACTION_UPDATE_WIDGET = "com.alaaeltaweel.thikrallah.WIDGET_UPDATE";
    // ✅ نفس المفتاح المؤمّن المستخدم في MainFragment - بييجي من BuildConfig/GitHub Secret مش مكتوب هنا صريح

    private static final String[] PRAYER_NAMES = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    private static final int[] PRAYER_POSITIONS = {0, 2, 3, 4, 5};

    private static final String[] MONTHS_AR = {
        "يناير","فبراير","مارس","أبريل","مايو","يونيو",
        "يوليو","أغسطس","سبتمبر","أكتوبر","نوفمبر","ديسمبر"
    };

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
        scheduleNextUpdate(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_UPDATE_WIDGET.equals(intent.getAction())) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, PrayerWidgetProvider.class);
       int[] ids = manager.getAppWidgetIds(component);
            for (int id : ids) {
                updateWidget(context, manager, id);
            }
            if (ids.length > 0) {
                scheduleNextUpdate(context);
            }
          }
    }   
    


    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        // خطوات سريعة فقط على الـ Main Thread
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_prayer_layout);

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (launchIntent != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.widget_root, pi);
        }

        Calendar now = Calendar.getInstance();
        views.setTextViewText(R.id.widget_date,
            now.get(Calendar.DAY_OF_MONTH) + " " + MONTHS_AR[now.get(Calendar.MONTH)]);
        views.setTextViewText(R.id.widget_clock,
            String.format("%02d:%02d %s",
                (now.get(Calendar.HOUR) == 0 ? 12 : now.get(Calendar.HOUR)),
                now.get(Calendar.MINUTE),
                (now.get(Calendar.AM_PM) == Calendar.AM ? "ص" : "م")));
        // نبعت الشكل الأساسي فوراً عشان الويدجت يظهر بسرعة من غير Timeout
        appWidgetManager.updateAppWidget(widgetId, views);

        // حساب أوقات الصلاة والطقس على خيط تحت
        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());

        EXECUTOR.execute(() -> {
            RemoteViews prayerViews = new RemoteViews(appContext.getPackageName(), R.layout.widget_prayer_layout);
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
                String lat = prefs.getString("latitude", "");
                String lon = prefs.getString("longitude", "");
                if (lat.isEmpty() || lon.isEmpty()) {
                    prayerViews.setTextViewText(R.id.widget_next_prayer_time, "حدد موقعك أولاً");
                } else {
                    PrayTime prayTime = PrayTime.instancePrayTime(appContext);
                    prayTime.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
                    String[] times24 = prayTime.getPrayerTimes(appContext);
                    prayTime.setTimeFormat(PrayTime.TIME_FORMAT_Time12);
                    String[] times12 = prayTime.getPrayerTimes(appContext);
                    if (times24 != null && times24.length >= 6) {
                        setNextPrayer(prayerViews, times12, times24,
                            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Prayer times error", e);
                prayerViews.setTextViewText(R.id.widget_next_prayer_time, "خطأ في الحساب");
            }

            handler.post(() -> {
                try {
                    appWidgetManager.partiallyUpdateAppWidget(widgetId, prayerViews);
                } catch (Exception e) {
                    Log.e(TAG, "partial update (prayer) failed", e);
                }
            });

            fetchWeather(appContext, appWidgetManager, widgetId);
        });
    }

    private void setNextPrayer(RemoteViews views, String[] times12, String[] times24, int hour, int minute) {
        int currentTotal = hour * 60 + minute;
        String nextName = PRAYER_NAMES[0];
        String nextTime = (times12 != null) ? times12[0] : "--";
        long minutesLeft = 0;
        boolean found = false;

        for (int i = 0; i < PRAYER_POSITIONS.length; i++) {
            int pos = PRAYER_POSITIONS[i];
            if (pos >= times24.length) continue;
            String t = times24[pos];
            if (t == null || t.contains("-")) continue;
            try {
                String[] parts = t.split(":");
                int total = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                if (total > currentTotal) {
                    nextName = PRAYER_NAMES[i];
                    nextTime = (times12 != null && pos < times12.length) ? times12[pos] : t;
                    minutesLeft = total - currentTotal;
                    found = true;
                    break;
                }
            } catch (Exception ignored) {}
        }

        if (!found) {
            nextName = PRAYER_NAMES[0];
            nextTime = (times12 != null) ? times12[0] : "--";
            try {
                String[] parts = times24[0].split(":");
                int fajrTotal = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
                minutesLeft = (24 * 60 - currentTotal) + fajrTotal;
            } catch (Exception ignored) {}
        }

        views.setTextViewText(R.id.widget_next_prayer_time, nextName + " " + nextTime);
        views.setTextViewText(R.id.widget_countdown,
            String.format("متبقي %02d:%02d", minutesLeft / 60, minutesLeft % 60));
    }

    private void fetchWeather(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isCustom = prefs.getBoolean("isCustomLocation", false);
        String lat = isCustom ? prefs.getString("c_latitude", prefs.getString("latitude", ""))
                              : prefs.getString("latitude", "");
        String lon = isCustom ? prefs.getString("c_longitude", prefs.getString("longitude", ""))
                              : prefs.getString("longitude", "");
        if (lat.isEmpty() || lon.isEmpty()) return;

        String result0 = null, result1 = null;
        try {
            String urlStr = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat
                + "&lon=" + lon + "&appid=" + com.alaaeltaweel.thikrallah.BuildConfig.WEATHER_API_KEY + "&units=metric&lang=ar";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            StringBuilder sb = new StringBuilder();
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
            } finally {
                // ✅ نتأكد إن الاتصال بيتقفل حتى لو حصل خطأ أثناء القراءة
                conn.disconnect();
            }
            JSONObject json = new JSONObject(sb.toString());
            double temp = json.getJSONObject("main").getDouble("temp");
            String desc = json.getJSONArray("weather").getJSONObject(0).getString("description");
            String icon = json.getJSONArray("weather").getJSONObject(0).getString("icon");
            result0 = String.format("%.0f°م | %s", temp, desc);
            result1 = getEmoji(icon);
        } catch (Exception e) {
            Log.e(TAG, "Weather error", e);
        }

        final String fResult0 = result0, fResult1 = result1;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> {
            RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_prayer_layout);
            if (fResult0 != null) {
                v.setTextViewText(R.id.widget_weather_text, fResult0);
                v.setTextViewText(R.id.widget_weather_icon, fResult1);
            } else {
                v.setTextViewText(R.id.widget_weather_text, "غير متاح");
            }
            try {
                appWidgetManager.partiallyUpdateAppWidget(widgetId, v);
            } catch (Exception e) {
                Log.e(TAG, "partial update (weather) failed", e);
            }
        });
    }

    private String getEmoji(String icon) {
        if (icon == null) return "🌤";
        switch (icon) {
            case "01d": return "☀️";
            case "01n": return "🌙";
            case "02d": case "02n": return "⛅";
            case "03d": case "03n": return "🌥";
            case "04d": case "04n": return "☁️";
            case "09d": case "09n": return "🌧";
            case "10d": return "🌦";
            case "10n": return "🌧";
            case "11d": case "11n": return "⛈";
            case "13d": case "13n": return "❄️";
            case "50d": case "50n": return "🌫";
            default: return "🌤";
        }
    }

    private void scheduleNextUpdate(Context context) {
        Intent intent = new Intent(context, PrayerWidgetProvider.class);
        intent.setAction(ACTION_UPDATE_WIDGET);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            long next = SystemClock.elapsedRealtime() + (15 * 60 * 1000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, next, pi);
            }
        }
    }
}
