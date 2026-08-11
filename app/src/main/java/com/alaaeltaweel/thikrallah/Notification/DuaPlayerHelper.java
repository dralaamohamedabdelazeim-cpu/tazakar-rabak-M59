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

    public static boolean playDuaAfterAthan(Context context) {
        long nowMs = System.currentTimeMillis();
        if (duaMediaPlayer != null && (nowMs - lastPlayStartTime) < 2000) {
            return true; // ✅ نداء مكرر جه في نفس اللحظة تقريبًا - نتجاهله، لكن الدعاء أصلاً شغال فعلاً
        }
        lastPlayStartTime = nowMs;
        boolean isDuaEnabled = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context).getBoolean("isDuaAfterAthan", false);
        if (!isDuaEnabled) return false;

        // ✅ منع الدعاء لو في مكالمة شغالة دلوقتي
        if (isInCallNow(context)) {
            Log.d(TAG, "مكالمة شغالة - تم تخطي الدعاء بعد الأذان");
            return false;
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
            return false;
        }
        duaMediaPlayer.setOnCompletionListener(mp -> {
            mp.release();
            duaMediaPlayer = null;
            if (am != null) am.abandonAudioFocus(null); // ✅ سيب الميكروفون بعد ما الدعاء يخلص
            android.app.NotificationManager nmDone = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nmDone != null) nmDone.cancel(9911);
            notifyDuaEnded(context); // ✅ خلاص، الخدمة تقدر تقفل نفسها دلوقتي
        });
        duaMediaPlayer.start();
        showDuaStopNotification(context);

        // ✅ نخلي الخدمة تفضل محمية (foreground) طول ما الدعاء شغال، عشان أندرويد ميقفلهاش قبل ما يخلص
        Bundle duaStartedData = new Bundle();
        duaStartedData.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_DUA_STARTED);
        context.startService(new Intent(context, ThikrMediaPlayerService.class).putExtras(duaStartedData));

        return true;
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
        notifyDuaEnded(context); // ✅ خلاص، الخدمة تقدر تقفل نفسها دلوقتي
    }

    // ✅ يبعت للخدمة إنها تقدر تقفل نفسها بأمان دلوقتي
    private static void notifyDuaEnded(Context context) {
        Bundle duaEndedData = new Bundle();
        duaEndedData.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_DUA_ENDED);
        context.startService(new Intent(context, ThikrMediaPlayerService.class).putExtras(duaEndedData));
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

    private static void showDuaStopNotification(Context context) {
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.Notification notif = buildDuaNotification(context);
        if (nm != null && notif != null) {
            nm.notify(9911, notif);
        }
    }

    // ✅ نفس إشعار الدعاء، لكن كدالة قابلة لإعادة الاستخدام (مع startForeground برضو)
    public static android.app.Notification buildDuaNotification(Context context) {
        String channelId = "dua_stop_channel_v3";
        android.app.NotificationManager nm = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, "الدعاء بعد الأذان", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            channel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
        Intent stopIntent = new Intent(context, ThikrAlarmReceiver.class);
        stopIntent.setAction("com.alaaeltaweel.thikrallah.STOP_DUA");
        PendingIntent stopPi = PendingIntent.getBroadcast(context, 9911, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("الدعاء بعد الأذان")
            .setContentText("جاري تشغيل الدعاء - اضغط للإيقاف")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, "إيقاف", stopPi)
            .setContentIntent(stopPi)
            // ✅ من غير setFullScreenIntent خالص - ده كان سبب توقف الذكر العام قبل كده
            .build();
    }
}
