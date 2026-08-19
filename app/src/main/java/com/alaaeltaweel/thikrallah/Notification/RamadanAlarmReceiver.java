package com.alaaeltaweel.thikrallah.Notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class RamadanAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "RamadanAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        // ✅ IslamicCalendar محتاج أندرويد 7+ (API 24) - من غير الشرط ده كان بيعمل Crash فوري
        // على أي جهاز أقدم (زي ما احنا محتاطين له فعلاً في AthanFragment)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.d(TAG, "Device below API 24 - cannot verify Ramadan, skipping");
            return;
        }

        // تحقق إن دلوقتي رمضان
        android.icu.util.IslamicCalendar cal = new android.icu.util.IslamicCalendar();
        int hijriMonth = cal.get(android.icu.util.Calendar.MONTH);
        boolean isRamadan = (hijriMonth == 8);

        if (!isRamadan) {
            Log.d(TAG, "Not Ramadan, skipping");
            return;
        }

        Intent serviceIntent = new Intent(context, RamadanSoundService.class);
        serviceIntent.setAction(action);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}

