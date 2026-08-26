package com.alaaeltaweel.thikrallah.Notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.alaaeltaweel.thikrallah.Models.UserThikr;
import com.alaaeltaweel.thikrallah.Utilities.MyDBHelper;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * ✅ محفّز "تذكير فتح الشاشة": بيسمع حدث فتح قفل الشاشة (ACTION_USER_PRESENT) ويظهر ذكر
 * مكتوب في فقاعة (ChatHeadService) - نص بس، بلا صوت خالص، وبلا المرور بـThikrService
 * (اللي مسجل كخدمة تشغيل صوت وممكن أندرويد الحديث يرفض يشغله من غير صوت فعلي).
 *
 * ملحوظة مهمة: الحدث ده "محمي" في أندرويد (protected broadcast)، معناها متقدرش تسجله
 * في AndroidManifest.xml من غير ما التطبيق يكون شغال. لازم يتسجل بالكود (registerReceiver)
 * في مكان بيفضل شغال، زي Application class - وده أصلاً متعمول في QuranApplication.onCreate().
 */
public class ScreenUnlockThikrReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenUnlockThikrRcvr";
    private static final long COOLDOWN_MS = 7 * 60 * 1000L; // 7 دقايق ثابتة

    @Override
    public void onReceive(Context context, Intent intent) {

        if (!Intent.ACTION_USER_PRESENT.equals(intent.getAction())) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // ✅ الميزة اختيارية - لازم المستخدم يفعّلها بنفسه من الإعدادات (مقفولة افتراضيًا)
        if (!prefs.getBoolean("screen_unlock_thikr_enabled", false)) {
            Log.d(TAG, "Screen-unlock thikr feature is disabled, skipping");
            return;
        }

        // ✅ لازم صلاحية "الظهور فوق التطبيقات الأخرى" ممنوحة، وإلا الفقاعة مش هتظهر أصلًا
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.d(TAG, "No overlay permission, skipping screen-unlock thikr");
            return;
        }

        if (isInCall(context)) {
            Log.d(TAG, "In call, skipping screen-unlock thikr");
            return;
        }

        if (isTimeNowQuietTime(prefs)) {
            Log.d(TAG, "Quiet time, skipping screen-unlock thikr");
            return;
        }

        // ✅ فحص التهدئة المشترك: لو أي ذكر (دوري أو فتح شاشة) اشتغل من أقل من 7 دقايق، تجاهل
        long lastGeneralThikrTime = prefs.getLong("last_general_thikr_time", 0);
        long sinceLast = System.currentTimeMillis() - lastGeneralThikrTime;
        if (lastGeneralThikrTime > 0 && sinceLast < COOLDOWN_MS) {
            Log.d(TAG, "Screen-unlock thikr skipped, still in 7-minute cooldown");
            return;
        }

        try {
            MyDBHelper db = new MyDBHelper(context);
            ArrayList<UserThikr> allThikrs = db.getAllEnabledThikrs();
            if (allThikrs == null || allThikrs.isEmpty()) {
                Log.d(TAG, "No enabled thikrs found");
                return;
            }
            // ✅ بنستخدم نفس عداد الدوران (thikr_current_index) اللي الذكر الدوري بيستخدمه،
            // عشان الاتنين يكملوا بعض بدل ما يبقى لكل واحد عداده المنفصل
            int currentIndex = prefs.getInt("thikr_current_index", 0) % allThikrs.size();
            UserThikr thikr = allThikrs.get(currentIndex);
            if (thikr == null) return;

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("thikr_current_index", currentIndex + 1);
            editor.putLong("last_general_thikr_time", System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Showing screen-unlock thikr bubble");
            Intent intentChatHead = new Intent(context.getApplicationContext(), ChatHeadService.class);
            intentChatHead.putExtra("thikr", thikr.getThikrText());
            intentChatHead.putExtra("isScreenUnlockThikr", true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intentChatHead);
            } else {
                context.startService(intentChatHead);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error showing screen-unlock thikr", e);
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

    // ✅ نفس منطق الوقت الهادئ الأساسي (فترة عدم التذكير اليومية)
    private boolean isTimeNowQuietTime(SharedPreferences prefs) {
        boolean quietTimeChoice = prefs.getBoolean("quiet_time_choice", true);
        if (!quietTimeChoice) return false;

        String[] qStart = prefs.getString("quiet_time_start", "22:00").split(":", 2);
        String[] qEnd = prefs.getString("quiet_time_end", "22:00").split(":", 2);
        int quietStartMin = Integer.parseInt(qStart[0]) * 60 + Integer.parseInt(qStart[1]);
        int quietEndMin = Integer.parseInt(qEnd[0]) * 60 + Integer.parseInt(qEnd[1]);

        Calendar now = Calendar.getInstance();
        int nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        if (quietStartMin == quietEndMin) return false;

        if (quietStartMin > quietEndMin) {
            // الفترة بتعدي منتصف الليل، زي 22:00 -> 06:00
            return (nowMin >= quietStartMin) || (nowMin < quietEndMin);
        } else {
            return (nowMin >= quietStartMin) && (nowMin < quietEndMin);
        }
    }
}
