package com.alaaeltaweel.thikrallah.Notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.ThikrMediaPlayerService;

 // ✅ نُقل هنا من AthanScreenActivity عشان الدعاء يشتغل بمعزل تام عن فتح الشاشة
public class DuaPlayerHelper {

    private static final String TAG = "DuaPlayerHelper";
   private static MediaPlayer duaMediaPlayer;
    private static long lastPlayStartTime = 0; // ✅ لمنع نداءين متقاربين يشغلوا الدعاء فوق بعض

    public static void playDuaAfterAthan(Context context) {
        long nowMs = System.currentTimeMillis();
        if (duaMediaPlayer != null && (nowMs - lastPlayStartTime) < 2000) {
            return; // ✅ نداء مكرر جه في نفس اللحظة تقريبًا - نتجاهله
        }
        lastPlayStartTime = nowMs;
        boolean isDuaEnabled = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context).getBoolean("isDuaAfterAthan", false);
        if (!isDuaEnabled) return;

        // ✅ منع الدعاء لو في مكالمة شغالة دلوقتي
        if (isInCallNow(context)) {
            Log.d(TAG, "مكالمة شغالة - تم تخطي الدعاء بعد الأذان");
            return;
        }

        // ✅ منع الدعاء لو الوقت الهادئ مفعّل
        if (isQuietTimeForDua(context)) {
            Log.d(TAG, "الوقت الهادئ مفعّل - تم تخطي الدعاء بعد الأذان");
            return;
        }

        // ✅ وقف أي ذكر عام شغال دلوقتي - الدعاء له الأولوية
        Bundle stopThikrData = new Bundle();
        stopThikrData.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_STOP);
        context.startService(new Intent(context, ThikrMediaPlayerService.class).putExtras(stopThikrData));

        // وقف أي تشغيل سابق قبل ما نبدأ واحد جديد
        if (duaMediaPlayer != null) {
            try {
                if (duaMediaPlayer.isPlaying()) duaMediaPlayer.stop();
            } catch (IllegalStateException ignored) {}
            duaMediaPlayer.release();
            duaMediaPlayer = null;
        }

        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        duaMediaPlayer = MediaPlayer.create(context, R.raw.dua_after_athan);
        if (duaMediaPlayer == null) {
            Log.e(TAG, "فشل تحميل ملف الدعاء dua_after_athan.mp3");
            if (am != null) am.abandonAudioFocus(null);
            return;
        }
        duaMediaPlayer.setOnCompletionListener(mp -> {
            mp.release();
            duaMediaPlayer = null;
            if (am != null) am.abandonAudioFocus(null); // ✅ سيب الميكروفون بعد ما الدعاء يخلص
            android.app.NotificationManager nmDone = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nmDone != null) nmDone.cancel(9911);
        });
        duaMediaPlayer.start();
        showDuaStopNotification(context);
    }

    public static void stopDua(Context context) {
        if (duaMediaPlayer != null) {
            try { if (duaMediaPlayer.isPlaying()) duaMediaPlayer.stop(); } catch (Exception ignored) {}
            try { duaMediaPlayer.release(); } catch (Exception ignored) {}
            duaMediaPlayer = null;
        }
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
      if (am != null) am.abandonAudioFocus(null);// ✅ سيب الميكروفون هنا كمان لو المستخدم وقف الدعاء يدوي
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(9911);
    }

    public static boolean isDuaPlaying() {
        return duaMediaPlayer != null;
    }

    // ✅ التحقق من وجود مكالمة هاتفية دلوقتي
    private static boolean isInCallNow(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null && tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                return true;
            }
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getMode() == AudioManager.MODE_IN_COMMUNICATION) {
                return true;
            }
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    // ✅ التحقق من الوقت الهادئ (من غير فحص "قرب الأذان" لأن الدعاء أصلاً بيشتغل جنب الأذان)
    private static boolean isQuietTimeForDua(Context context) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean("quiet_time_choice", true)) return false;

        try {
            String[] startParts = prefs.getString("quiet_time_start", "22:00").split(":");
            String[] endParts = prefs.getString("quiet_time_end", "22:00").split(":");
            int startMinutes = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);
            int endMinutes = Integer.parseInt(endParts[0]) * 60 + Integer.parseInt(endParts[1]);

            java.util.Calendar now = java.util.Calendar.getInstance();
            int nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);

            if (startMinutes > endMinutes) {
                // الفترة بتعدي منتصف الليل، زي 22:00 لـ 06:00
                return nowMinutes >= startMinutes || nowMinutes < endMinutes;
            } else {
                return nowMinutes >= startMinutes && nowMinutes < endMinutes;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void showDuaStopNotification(Context context) {
        String channelId = "dua_stop_channel_v3";
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, "الدعاء بعد الأذان", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
        Intent stopIntent = new Intent(context, ThikrAlarmReceiver.class);
        stopIntent.setAction("com.alaaeltaweel.thikrallah.STOP_DUA");
        PendingIntent stopPi = PendingIntent.getBroadcast(context, 9911, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("الدعاء بعد الأذان")
            .setContentText("جاري تشغيل الدعاء - اضغط للإيقاف")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "إيقاف", stopPi)
            .setContentIntent(stopPi);
        // ✅ من غير setFullScreenIntent خالص - ده كان سبب توقف الذكر العام قبل كده
        nm.notify(9911, builder.build());
    }
}
