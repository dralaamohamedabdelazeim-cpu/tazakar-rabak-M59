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
                || intent.getAction().equalsIgnoreCase(Intent.ACTION_BOOT_COMPLETED)
                || intent.getAction().equalsIgnoreCase("android.intent.action.QUICKBOOT_POWERON")
                || intent.getAction().equalsIgnoreCase("com.htc.intent.action.QUICKBOOT_POWERON")) {

                MyAlarmsManager manager = new MyAlarmsManager(context.getApplicationContext());
                   manager.UpdateAllApplicableAlarms(); 

                // ✅ تذكيرات الصلاة والأذكار (ReminderScheduler) كانت بتتسجل بس لما المستخدم
                // يفتح الشاشة الرئيسية - وبما إن كل المنبهات بتتمسح تلقائيًا بعد أي إعادة تشغيل
                // للجهاز، كانت بتتوقف بصمت لو المستخدم ما فتحش التطبيق بعد الريستارت
                com.alaaeltaweel.thikrallah.Utilities.ReminderScheduler.scheduleAllReminders(context.getApplicationContext());

                                
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
