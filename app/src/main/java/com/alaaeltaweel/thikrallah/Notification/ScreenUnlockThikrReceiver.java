package com.alaaeltaweel.thikrallah.Notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.Calendar;

/**
 * ✅ محفّز جديد: بيسمع لحدث "فتح قفل الشاشة" (ACTION_USER_PRESENT) ويشغّل ذكر تذكيري.
 * ملحوظة مهمة: الحدث ده "محمي" في أندرويد (protected broadcast)، معناها متقدرش تسجله
 * في AndroidManifest.xml من غير ما التطبيق يكون شغال. لازم يتسجل بالكود (registerReceiver)
 * في مكان بيفضل شغال، زي Application class أو أي Service شغال بالفعل بشكل مستمر.
 *
 * إزاي تسجله (مثال في Application.onCreate أو أي مكان تاني تحب):
 *
 *   IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
 *   registerReceiver(new ScreenUnlockThikrReceiver(), filter);
 */
public class ScreenUnlockThikrReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenUnlockThikrRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!Intent.ACTION_USER_PRESENT.equals(intent.getAction())) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // ✅ الميزة اختيارية - لازم المستخدم يفعّلها بنفسه من الإعدادات (مقفولة افتراضيًا)
        boolean featureEnabled = prefs.getBoolean("screen_unlock_thikr_enabled", false);
        if (!featureEnabled) {
            Log.d(TAG, "Screen-unlock thikr feature is disabled, skipping");
            return;
        }

        // ✅ متشتغلش وقت مكالمة
        if (isInCall(context)) {
            Log.d(TAG, "In call, skipping screen-unlock thikr");
            return;
        }

        // ✅ متشتغلش في وقت الراحة (نفس الإعداد المستخدم في التذكير الدوري)
        if (isTimeNowQuietTime(prefs)) {
            Log.d(TAG, "Quiet time, skipping screen-unlock thikr");
            return;
        }

        // ✅ نبعت الطلب لنفس ThikrService، وهو اللي هيتولى فحص "التهدئة" (7 دقايق)
        // واختيار الذكر وتشغيله - بنفس المنطق المستخدم للتذكير الدوري بالظبط
        Intent serviceIntent = new Intent(context, ThikrService.class);
        serviceIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", "screen_unlock_thikr");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    private boolean isInCall(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            return tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE;
        } catch (SecurityException e) {
            return false;
        }
    }

    // ✅ نفس منطق الوقت الهادئ الموجود في ThikrService، مكرر هنا عشان الملف مستقل بذاته
    private boolean isTimeNowQuietTime(SharedPreferences prefs) {
        boolean quietTimeChoice = prefs.getBoolean("quiet_time_choice", true);
        if (!quietTimeChoice) return false;

        String[] qStart = prefs.getString("quiet_time_start", "22:00").split(":", 2);
        String[] qEnd = prefs.getString("quiet_time_end", "22:00").split(":", 2);
        int quietStartMin = Integer.parseInt(qStart[0]) * 60 + Integer.parseInt(qStart[1]);
        int quietEndMin = Integer.parseInt(qEnd[0]) * 60 + Integer.parseInt(qEnd[1]);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (quietStartMin > quietEndMin) {
            // الفترة بتعدي منتصف الليل، زي 22:00 -> 06:00
            return (nowMin >= quietStartMin) || (nowMin < quietEndMin);
        } else {
            return (nowMin >= quietStartMin) && (nowMin < quietEndMin);
        }
    }
}
