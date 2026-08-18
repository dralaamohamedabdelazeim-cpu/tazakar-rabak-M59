package com.alaaeltaweel.thikrallah.Notification;

import android.app.NotificationChannel;

import android.app.NotificationManager;

import android.app.PendingIntent;

import android.content.BroadcastReceiver;

import android.content.Context;

import android.content.Intent;

import android.content.SharedPreferences;

import android.preference.PreferenceManager;

import java.util.Calendar;

import android.os.Build;

import android.os.Bundle;

import android.telephony.TelephonyManager;

import android.util.Log;


import androidx.core.app.NotificationCompat;


import com.alaaeltaweel.thikrallah.MainActivity;

import com.alaaeltaweel.thikrallah.R;


public class ThikrAlarmReceiver extends BroadcastReceiver {
    String TAG = "ThikrAlarmReceiver";


    @Override
    public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onrecieve called");

        Bundle data = intent.getExtras();

        if (data == null) return;

        String dataType = data.getString("com.alaaeltaweel.thikrallah.datatype");

        // ✅ تنبيه قبل الصلاة بـ 15 دقيقة
        if (MyAlarmsManager.DATA_TYPE_PRE_ATHAN.equals(dataType)) {

            String prayerName = data.getString("prayer_name", "الصلاة");

            showPreAthanNotification(context, prayerName);

            return;

        }

        // لو الأذان افتح شاشة الأذان
        if (isAthanType(dataType)) {

            // ✅ منع تكرار الأذان في نفس اليوم
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            long lastAthanTime = prefs.getLong("last_athan_time_" + dataType, 0);
            long nowMs = System.currentTimeMillis();
            Calendar lastCal = Calendar.getInstance();
            lastCal.setTimeInMillis(lastAthanTime);
            Calendar nowCal = Calendar.getInstance();
            if (lastAthanTime > 0 &&
                lastCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
                lastCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                Log.d(TAG, "Athan already played today, skipping: " + dataType);
                return;
            }
            prefs.edit().putLong("last_athan_time_" + dataType, nowMs).apply();

            // ✅ تحقق من وجود مكالمة وابعت الحالة للشاشة
            boolean isInCall = false;
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    isInCall = true;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Cannot check call state");
            }

            Intent athanIntent = new Intent(context, AthanScreenActivity.class);
            athanIntent.putExtras(data);
            athanIntent.putExtra("isCallInProgress", isInCall);
            athanIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            context.startActivity(athanIntent);

        } else {

            // ✅ الأذكار العادية — لا تشتغل أثناء المكالمات
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "Call in progress, scheduling thikr after 15 min");
android.app.AlarmManager alarmManager = 
    (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
android.app.PendingIntent pendingIntent = android.app.PendingIntent.getBroadcast(
    context,
    dataType.hashCode() + 9999,
    new Intent(context, ThikrAlarmReceiver.class).putExtras(data),
    android.app.PendingIntent.FLAG_UPDATE_CURRENT | 
    android.app.PendingIntent.FLAG_IMMUTABLE);
alarmManager.setExactAndAllowWhileIdle(
    android.app.AlarmManager.RTC_WAKEUP,
    System.currentTimeMillis() + (15 * 60 * 1000),
    pendingIntent);
return;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Cannot check call state, proceeding");
            }

            // باقي التنبيهات تشتغل عادي
            data.putBoolean("isUserAction", false);
            Intent intent2 = new Intent(context, ThikrService.class).putExtras(data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Log.d(TAG, "starting foreground service ThikrService");
                context.startForegroundService(intent2);
            } else {
                Log.d(TAG, "starting background service ThikrService");
                context.startService(intent2);
            }
        }
    }

    // ✅ notification قبل الصلاة بـ 15 دقيقة
    private void showPreAthanNotification(Context context, String prayerName) {
        String channelId = "pre_athan_reminder";
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "تنبيه اقتراب الصلاة", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent launchIntent = new Intent(context, MainActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0,
                launchIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("اقترب وقت صلاة " + prayerName)
                .setContentText("تبقى 15 دقيقة على صلاة " + prayerName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(prayerName.hashCode(), builder.build());
    }

    private boolean isAthanType(String dataType) {
        if (dataType == null) return false;
        return dataType.equals(MainActivity.DATA_TYPE_ATHAN1) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN2) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN3) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN4) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN5);
    }
}
