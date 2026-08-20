package com.alaaeltaweel.thikrallah.Utilities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import com.alaaeltaweel.thikrallah.Notification.AthkarReminderReceiver;
import com.alaaeltaweel.thikrallah.Notification.PrayerReminderReceiver;
import java.util.Calendar;

public class ReminderScheduler {

    public static void scheduleAllReminders(Context context) {
        schedulePrayerReminders(context);
        scheduleAthkarReminders(context);
    }

    private static void schedulePrayerReminders(Context context) {
        PrayTime prayers = PrayTime.instancePrayTime(context);
        prayers.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
        String[] times = prayers.getPrayerTimes(context);

        scheduleAthanAfterPrayer(context, times[0], "fajr", "الفجر");
        scheduleAthanAfterPrayer(context, times[2], "dhuhr", "الظهر");
        scheduleAthanAfterPrayer(context, times[3], "asr", "العصر");
        scheduleAthanAfterPrayer(context, times[5], "maghrib", "المغرب");
        scheduleAthanAfterPrayer(context, times[6], "isha", "العشاء");
    }

    private static void scheduleAthanAfterPrayer(Context context, String prayerTime, String key, String name) {
        try {
            String[] parts = prayerTime.split(":");
            int hour = Integer.parseInt(parts[0].trim());
            int minute = Integer.parseInt(parts[1].trim()) + 30;
            if (minute >= 60) { minute -= 60; hour += 1; }
            if (hour >= 24) hour -= 24;
            schedulePrayer(context, key, name, hour, minute);
        } catch (Exception e) {
            // لو الوقت مش صح
        }
    }

    private static void schedulePrayer(Context context,
            String key, String name, int hour, int minute) {

        AlarmManager alarmManager =
            (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, PrayerReminderReceiver.class);
        intent.putExtra(PrayerReminderReceiver.EXTRA_PRAYER_KEY, key);
        intent.putExtra(PrayerReminderReceiver.EXTRA_PRAYER_NAME, name);
        // ✅ بنبعت الساعة/الدقيقة عشان الـ receiver يقدر يجدد التذكير لبكرة بنفس الميعاد
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // ✅ setExactAndAllowWhileIdle بدل setRepeating - دقة حقيقية بدل تقريب أندرويد للبطارية
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.getTimeInMillis(),
            pendingIntent);
    }

    private static void scheduleAthkarReminders(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // أذكار الصباح - وقت المستخدم + 30 دقيقة
        String morningTime = prefs.getString("daytReminderTime", "08:00");
        String[] morningParts = morningTime.split(":");
        int morningHour = Integer.parseInt(morningParts[0]);
        int morningMinute = Integer.parseInt(morningParts[1]) + 30;
        if (morningMinute >= 60) { morningMinute -= 60; morningHour += 1; }
        if (morningHour >= 24) morningHour -= 24;
        scheduleAthkar(context, "morning", morningHour, morningMinute);

        // أذكار المساء - وقت المستخدم + 30 دقيقة
        String eveningTime = prefs.getString("nightReminderTime", "20:00");
        String[] eveningParts = eveningTime.split(":");
        int eveningHour = Integer.parseInt(eveningParts[0]);
        int eveningMinute = Integer.parseInt(eveningParts[1]) + 30;
        if (eveningMinute >= 60) { eveningMinute -= 60; eveningHour += 1; }
        if (eveningHour >= 24) eveningHour -= 24;
        scheduleAthkar(context, "evening", eveningHour, eveningMinute);
    }

    private static void scheduleAthkar(Context context,
            String type, int hour, int minute) {

        AlarmManager alarmManager =
            (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, AthkarReminderReceiver.class);
        intent.putExtra(AthkarReminderReceiver.EXTRA_ATHKAR_TYPE, type);
        // ✅ بنبعت الساعة/الدقيقة عشان الـ receiver يقدر يجدد التذكير لبكرة بنفس الميعاد
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // ✅ setExactAndAllowWhileIdle بدل setRepeating - دقة حقيقية بدل تقريب أندرويد للبطارية
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.getTimeInMillis(),
            pendingIntent);
    }
}
