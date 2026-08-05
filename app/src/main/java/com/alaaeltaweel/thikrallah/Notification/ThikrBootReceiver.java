package com.alaaeltaweel.thikrallah.Notification;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.app.ActivityManager;
import java.util.List;

public class ThikrBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (null != intent.getAction()) {
            Log.d("ThikrBootReceiver", "intent called with action" + intent.getAction());
if (intent.getBooleanExtra("isWatchdog", false)) {
            if (!isServiceRunning(context, AthanTimerService.class)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(new Intent(context, AthanTimerService.class));
                } else {
                    context.startService(new Intent(context, AthanTimerService.class));
                }
            }
            return;
  }
            // إعادة جدولة الـ alarms فقط بدون تشغيل الأذان
            if (intent.getAction().equalsIgnoreCase(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
                || intent.getAction().equalsIgnoreCase(Intent.ACTION_TIME_CHANGED)
                || intent.getAction().equalsIgnoreCase(Intent.ACTION_TIMEZONE_CHANGED)) {
                new MyAlarmsManager(context.getApplicationContext()).UpdateAllApplicableAlarms();
                return;
            }

            // تشغيل الـ alarms عند البوت
            if (intent.getAction().equalsIgnoreCase("com.alaaeltaweel.thikrallah.Notification.ThikrBootReceiver.android.action.broadcast")
                || intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {

                MyAlarmsManager manager = new MyAlarmsManager(context.getApplicationContext());
                   

                Intent launch = new Intent(context, com.alaaeltaweel.thikrallah.MainActivity.class);
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                 context.startActivity(launch);
                
                // تأخير 5 ثواني عشان الجهاز يكمل الإقلاع
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    manager.UpdateAllApplicableAlarms();
                }, 5000);

                SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                boolean isTimer = mPrefs.getBoolean("foreground_athan_timer", true);

                if (isTimer) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)) {
                                context.startForegroundService(new Intent(context, AthanTimerService.class));
                            }
                        } else {
                            context.startForegroundService(new Intent(context, AthanTimerService.class));
                        }
                    } else {
                        context.startService(new Intent(context, AthanTimerService.class));
                    }
                }
            }
        }
    }

    private boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services == null) return false;
        for (ActivityManager.RunningServiceInfo service : services) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
