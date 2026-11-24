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

import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import android.media.MediaPlayer;

import com.alaaeltaweel.thikrallah.MainActivity;

import com.alaaeltaweel.thikrallah.WakeUpActivity;

import com.alaaeltaweel.thikrallah.R;

import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import com.alaaeltaweel.thikrallah.ThikrMediaPlayerService;


public class ThikrAlarmReceiver extends BroadcastReceiver {
    String TAG = "ThikrAlarmReceiver";


    @Override
     public void onReceive(Context context, Intent intent) {

        Log.d(TAG, "onrecieve called");

        // ✅ حديث اليوم: إشعار هادي، لازم يتعالج قبل قفل الإضاءة (WakeLock) اللي تحت عشان مايصحيش الشاشة
        try {
            if (intent != null && DailyHadithHelper.DATA_TYPE.equals(
                    intent.getStringExtra("com.alaaeltaweel.thikrallah.datatype"))) {
                DailyHadithHelper.handleAlarm(context, goAsync(),
                        intent.getIntExtra(DailyHadithHelper.EXTRA_SLOT, 1));
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "daily hadith branch failed: " + e.getMessage());
        }
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            // ✅ نتأكد الأول لو فيه مكالمة شغالة، عشان مانصحيش الشاشة بالقوة ونعمل تقطيع فيها
            boolean isInCallAtTop = false;
            try {
                TelephonyManager tmTop = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tmTop != null && tmTop.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    isInCallAtTop = true;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Cannot check call state at top");
            }
            isInCallAtTop = isInCallAtTop || CallStateHelper.isInCall(context, false);

            PowerManager.WakeLock wakeLock;
            if (isInCallAtTop) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tazakar:ThikrReceiverWakeLockCall");
            } else {
                wakeLock = pm.newWakeLock(
        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "tazakar:ThikrReceiverWakeLock");
            }
            wakeLock.acquire(60 * 1000L);

if (PrayerReminderSoundService.ACTION_STOP.equals(intent.getAction())) {
            context.stopService(new Intent(context, PrayerReminderSoundService.class));
            NotificationManager stopNm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (stopNm != null) stopNm.cancel(7421);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }
if ("com.alaaeltaweel.thikrallah.STOP_DUA".equals(intent.getAction())) {
            DuaPlayerHelper.stopDua(context);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
}
         
        Bundle data = intent.getExtras();

        if (data == null) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;
        }

        String dataType = data.getString("com.alaaeltaweel.thikrallah.datatype");

        // ✅ تنبيه قبل الصلاة بـ 15 دقيقة
        if (dataType != null && dataType.startsWith(MyAlarmsManager.DATA_TYPE_PRE_ATHAN)) {

            String prayerName = data.getString("prayer_name", "fajr");
            Log.d("ThikrAlarmReceiver", "pre-athan prayer_name: " + prayerName);

            // ✅ حماية تابعة لإعداد المستخدم - لو غيّر عدد الدقايق، تسمح للتنبيه يشتغل تاني حتى في نفس اليوم
            SharedPreferences prePrefs = PreferenceManager.getDefaultSharedPreferences(context);
            long lastPreTime = prePrefs.getLong("last_preathan_time_" + prayerName, 0);
            String currentPreMinutes = prePrefs.getString("preAthanMinutes_" + prayerName, "15");
            String lastPreMinutesUsed = prePrefs.getString("last_preathan_minutes_used_" + prayerName, "");
            Calendar lastPreCal = Calendar.getInstance();
            lastPreCal.setTimeInMillis(lastPreTime);
            Calendar nowPreCal = Calendar.getInstance();
            boolean samePreOccurrence = lastPreTime > 0 &&
                lastPreCal.get(Calendar.DAY_OF_YEAR) == nowPreCal.get(Calendar.DAY_OF_YEAR) &&
                lastPreCal.get(Calendar.YEAR) == nowPreCal.get(Calendar.YEAR) &&
                currentPreMinutes.equals(lastPreMinutesUsed);
            if (samePreOccurrence) {
                Log.d(TAG, "Pre-athan already shown for this exact setting today, skipping: " + prayerName);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
            prePrefs.edit()
                    .putLong("last_preathan_time_" + prayerName, System.currentTimeMillis())
                    .putString("last_preathan_minutes_used_" + prayerName, currentPreMinutes)
                    .apply();

            showPreAthanNotification(context, prayerName);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            return;

        }
         // ✅ الإقامة
        if ("iqama".equals(dataType)) {
            String prayerName = data.getString("prayer_name", "fajr");
            int iqamaSound = data.getInt("iqama_sound", 1);

            // ✅ حماية تابعة لإعداد المستخدم - لو غيّر عدد الدقايق، تسمح للإقامة تشتغل تاني حتى في نفس اليوم
            SharedPreferences iqamaPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            long lastIqamaTime = iqamaPrefs.getLong("last_iqama_time_" + prayerName, 0);
            String currentIqamaMinutes = iqamaPrefs.getString("iqamaMinutes_" + prayerName, "10");
            String lastIqamaMinutesUsed = iqamaPrefs.getString("last_iqama_minutes_used_" + prayerName, "");
            Calendar lastIqamaCal = Calendar.getInstance();
            lastIqamaCal.setTimeInMillis(lastIqamaTime);
            Calendar nowIqamaCal = Calendar.getInstance();
            boolean sameIqamaOccurrence = lastIqamaTime > 0 &&
                lastIqamaCal.get(Calendar.DAY_OF_YEAR) == nowIqamaCal.get(Calendar.DAY_OF_YEAR) &&
                lastIqamaCal.get(Calendar.YEAR) == nowIqamaCal.get(Calendar.YEAR) &&
                currentIqamaMinutes.equals(lastIqamaMinutesUsed);
            if (sameIqamaOccurrence) {
                Log.d(TAG, "Iqama already shown for this exact setting today, skipping: " + prayerName);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
            iqamaPrefs.edit()
                    .putLong("last_iqama_time_" + prayerName, System.currentTimeMillis())
                    .putString("last_iqama_minutes_used_" + prayerName, currentIqamaMinutes)
                    .apply();
            showIqamaNotification(context, prayerName, iqamaSound);
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
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

            // ✅ [مؤقت للاختبار] زرار اختبار الأذان من التطبيق - يتجاوز المنع دايمًا
            boolean isTestTrigger = data.getBoolean("isTestTrigger", false);

            if (!isTestTrigger && lastAthanTime > 0 &&
                lastCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR) &&
                lastCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)) {
                Log.d(TAG, "Athan already played today, skipping: " + dataType);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
            // ✅ زرار الاختبار ميسجلش نفسه كـ"آخر أذان شغل"، عشان الأذان الحقيقي المجدول لنفس اليوم يفضل يشتغل عادي في معاده
            if (!isTestTrigger) {
                prefs.edit().putLong("last_athan_time_" + dataType, nowMs).apply();
            }

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
            isInCall = isInCall || CallStateHelper.isInCall(context, true);   // مكالمات الإنترنت كمان

            // ✅ شغّل صوت الأذان مباشرة من المنبه نفسه - مستقل عن نجاح فتح الشاشة
            // القفل ده مشترك مع AthanScreenActivity عشان الصوت ميتكررش لو الشاشة فتحت بعده
            if (!isInCall) {
                SharedPreferences soundPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                soundPrefs.edit().putLong("athan_sound_triggered_" + dataType, nowMs).apply();

                // ✅ إعلان "حان الآن موعد صلاة ..." الأول، وبعده الأذان (الخدمة بتبدأه، وده احتياطي لو اتأخرت)
                boolean announced = startAnnouncementIfEnabled(context, dataType);
                if (announced) {
                    final Context athanCtx = context.getApplicationContext();
                    final String athanType = dataType;
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        if (!CallStateHelper.isInCall(athanCtx, true)) {
                            PrayerReminderSoundService.startAthanOnce(athanCtx, athanType);
                        }
                    }, 3800);
                } else {
                Bundle soundData = new Bundle();
                soundData.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_PLAY);
                soundData.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
                soundData.putBoolean("isUserAction", false);
                Intent soundIntent = new Intent(context, ThikrService.class).putExtras(soundData);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(soundIntent);
                } else {
                    context.startService(soundIntent);
                }
                }
            }

            Intent athanIntent = new Intent(context, AthanScreenActivity.class);
            athanIntent.putExtras(data);
            athanIntent.putExtra("isCallInProgress", isInCall);
            athanIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // ✅ محاولة فتح الشاشة مباشرة - بتنجح غالبًا لو النظام سامح بالفتح من الخلفية
            try {
                context.startActivity(athanIntent);
            } catch (Exception e) {
                Log.e(TAG, "Direct startActivity for athan screen failed: " + e.getMessage());
            }

            // ✅ خط دفاع إضافي مستقل - نافذة عائمة، بس بس لو الشاشة العادية فعلاً فشلت تفتح.
            // بدل ما نشغلها فورًا كل مرة (وده كان بيعمل إشعار زيادة يظهر ويختفي بسرعة حتى
            // لو الشاشة فتحت تمام)، بنستنى شوية ونتأكد إن AthanScreenActivity معملتش onResume
            // فعلاً - ولو فتحت، منشغلش النافذة العائمة خالص.
            final Context appContext = context.getApplicationContext();
            final Bundle overlayData = data;
            final PendingResult pendingResult = goAsync();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (!com.alaaeltaweel.thikrallah.Notification.AthanScreenActivity.hasOpenedSuccessfully) {
                        Log.d(TAG, "Real athan screen did not open in time - starting overlay fallback");
                        Intent overlayIntent = new Intent(appContext, com.alaaeltaweel.thikrallah.Notification.AthanOverlayService.class);
                        overlayIntent.putExtras(overlayData);
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                appContext.startForegroundService(overlayIntent);
                            } else {
                                appContext.startService(overlayIntent);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to start athan overlay fallback: " + e.getMessage());
                        }
                    } else {
                        Log.d(TAG, "Real athan screen opened successfully - overlay fallback not needed");
                    }
                } finally {
                    pendingResult.finish();
                }
            }, 2500);

        } else {

            // ✅ الأذكار العادية — لا تشتغل أثناء المكالمات (فحص المكالمة الأول)
            boolean isInCallForThikr = false;
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    isInCallForThikr = true;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "Cannot check call state");
            }
            // ✅ كمان مكالمات واتساب والإنترنت (مش بتظهر في TelephonyManager)، ومن غير ما نحتاج إذن الهاتف
            isInCallForThikr = isInCallForThikr || CallStateHelper.isInCall(context, true);
            if (isInCallForThikr) {
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
                    System.currentTimeMillis() + (10 * 60 * 1000),
                    pendingIntent);
                if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                return;
            }
            

            // ✅ حماية من تكرار الذكر العام لو المنبه الحقيقي والحارس الذاتي اشتغلوا مع بعض
            if (MainActivity.DATA_TYPE_GENERAL_THIKR.equals(dataType)) {
                SharedPreferences generalPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                long lastGeneralAttempt = generalPrefs.getLong("last_general_thikr_receiver_time", 0);
                long nowMs2 = System.currentTimeMillis();
                if (nowMs2 - lastGeneralAttempt < 60 * 1000L) {
                    Log.d(TAG, "General thikr fired too close to last one, skipping duplicate");
                    if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
                    return;
                }
                generalPrefs.edit().putLong("last_general_thikr_receiver_time", nowMs2).apply();
           new MyAlarmsManager(context).UpdateAllApplicableAlarms();
            }

// ✅ إيقاظ الشاشة بقوة لأي ذكر عادي - بس لو الشاشة فعلاً مقفولة/مطفية
            // (لو الشاشة شغالة أصلًا زي وقت استخدام تطبيق تاني، متفتحش حاجة فوقه)
            PowerManager wakeCheckPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean isScreenOff = wakeCheckPm == null || !wakeCheckPm.isInteractive();

            if (isScreenOff) {
                Intent generalWakeIntent = new Intent(context, WakeUpActivity.class);
                generalWakeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent generalWakePendingIntent = PendingIntent.getActivity(context,
                        dataType.hashCode() + 5555, generalWakeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                String wakeChannelId = "thikr_wakeup_v1";
                NotificationManager wakeNm =
                        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationChannel wakeChannel = new NotificationChannel(
                            wakeChannelId, "إيقاظ الشاشة للذكر", NotificationManager.IMPORTANCE_HIGH);
                    wakeChannel.setSound(null, null);
                    wakeNm.createNotificationChannel(wakeChannel);
                }
                NotificationCompat.Builder wakeBuilder = new NotificationCompat.Builder(context, wakeChannelId)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("تذكر ربك")
                        .setContentText("حان وقت الذكر")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setTimeoutAfter(3 * 1000L)
                        .setFullScreenIntent(generalWakePendingIntent, true);
                wakeNm.notify(("thikr_wake_" + dataType).hashCode(), wakeBuilder.build());
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

        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }
private void showPreAthanNotification(Context context, String prayerKey) {
    // الصوت بيتشغل من خدمة مستقلة (MediaPlayer) والإشعار صامت - عشان إشعارات التطبيقات التانية
    // ماتقدرش تقطعه، وعشان قناة الإشعار ماتفضلش صامتة للأبد لو اتعملت مرة والتركيز مرفوض
    startReminderSound(context, PrayerReminderSoundService.KIND_PRE_ATHAN, prayerKey, 1);
}

private void startReminderSound(Context context, String kind, String prayerKey, int iqamaSound) {
    Intent i = new Intent(context, PrayerReminderSoundService.class)
            .putExtra(PrayerReminderSoundService.EXTRA_KIND, kind)
            .putExtra(PrayerReminderSoundService.EXTRA_PRAYER, prayerKey)
            .putExtra(PrayerReminderSoundService.EXTRA_IQAMA_SOUND, iqamaSound);
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    } catch (Exception e) {
        Log.e(TAG, "Failed to start reminder sound service: " + e.getMessage());
    }
}

    /** بيشغّل إعلان الصلاة لو مفعّل، وبيرجع true لو الإعلان بدأ فعلاً */
    private boolean startAnnouncementIfEnabled(Context context, String dataType) {
        try {
            SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(context);
            if (!p.getBoolean("announce_before_athan", true)) return false;
            android.media.AudioManager am = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL) return false;

            String key;
            if (MainActivity.DATA_TYPE_ATHAN1.equals(dataType)) key = "fajr";
            else if (MainActivity.DATA_TYPE_ATHAN2.equals(dataType)) {
                key = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY ? "jumuah" : "dhuhr";
            }
            else if (MainActivity.DATA_TYPE_ATHAN3.equals(dataType)) key = "asr";
            else if (MainActivity.DATA_TYPE_ATHAN4.equals(dataType)) key = "maghrib";
            else if (MainActivity.DATA_TYPE_ATHAN5.equals(dataType)) key = "isha";
            else return false;

            if (context.getResources().getIdentifier("announce_" + key, "raw", context.getPackageName()) == 0) {
                return false;      // ملف الصوت مش موجود في raw
            }
            Intent i = new Intent(context, PrayerReminderSoundService.class)
                    .putExtra(PrayerReminderSoundService.EXTRA_KIND, PrayerReminderSoundService.KIND_ANNOUNCE)
                    .putExtra(PrayerReminderSoundService.EXTRA_PRAYER, key)
                    .putExtra(PrayerReminderSoundService.EXTRA_ATHAN_TYPE, dataType);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "announcement start failed: " + e.getMessage());
            return false;
        }
    }

    private boolean isAthanType(String dataType) {
        if (dataType == null) return false;
        return dataType.equals(MainActivity.DATA_TYPE_ATHAN1) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN2) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN3) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN4) ||
               dataType.equals(MainActivity.DATA_TYPE_ATHAN5);
    }
    private void showIqamaNotification(Context context, String prayerKey, int soundChoice) {
        startReminderSound(context, PrayerReminderSoundService.KIND_IQAMA, prayerKey, soundChoice);
    }
}
