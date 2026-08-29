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
    // ✅ قفل بمستوى "الحالة" مش بمستوى "الوقت" - بيتصفر بس لما أذان جديد فعلي يبدأ
    // (مش زي القفل القديم اللي كان بيعتمد على فرق 6 ثواني، وده كان بيسمح بتشغيل الدعاء
    // مرتين لو المسارات المختلفة اللي بتنادي الدالة دي حصلت بفارق أكتر من 6 ثواني)
    private static volatile boolean duaTriggeredForCurrentAthan = false;

    // ✅ ينادَى لما أذان جديد فعلي يبدأ (من ThikrMediaPlayerService.play) عشان يسمح للدعاء
    // بعده يشتغل، مهما كان عدد المرات اللي هتتنادى فيها playDuaAfterAthan لنفس الأذان ده
    public static void resetGuardForNewAthan() {
        duaTriggeredForCurrentAthan = false;
    }

    public static synchronized boolean playDuaAfterAthan(Context context) {
        if (duaTriggeredForCurrentAthan) {
            return true; // ✅ الدعاء اتشغل (أو بيتشغل) بالفعل لنفس الأذان ده - تجاهل أي نداء تاني
        }
        duaTriggeredForCurrentAthan = true;
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
        // ✅ نبني الـ MediaPlayer يدويًا (بدل MediaPlayer.create الافتراضي) عشان نحطه
        // على نفس قناة صوت الأذان (الإشعارات) مش قناة الميديا، عشان يتبع نفس مستوى الصوت
        duaMediaPlayer = new MediaPlayer();
        duaMediaPlayer.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        try {
            android.content.res.AssetFileDescriptor afd = context.getResources().openRawResourceFd(R.raw.dua_after_athan);
            duaMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            duaMediaPlayer.prepare();
        } catch (Exception e) {
            Log.e(TAG, "فشل تحميل ملف الدعاء dua_after_athan.mp3: " + e.getMessage());
            duaMediaPlayer = null;
        }
        if (duaMediaPlayer == null) {
            Log.e(TAG, "فشل تحميل ملف الدعاء dua_after_athan.mp3");
            if (am != null) am.abandonAudioFocus(null);
            return false;
        }
        // ✅ لو الأذان كان مكتوم (بالقلب أو زرار الصوت)، الدعاء يفضل مكتوم كمان بدل ما يرجع عالي فجأة
        // ولو مش مكتوم، الدعاء بيشتغل بنفس مستوى "صوت الأذان" اللي المستخدم حدده في الإعدادات
        if (ThikrMediaPlayerService.lastAthanWasMuted) {
            try { duaMediaPlayer.setVolume(0f, 0f); } catch (Exception ignored) {}
        } else {
            float duaVolume = getAthanVolumeFloat(context);
            try { duaMediaPlayer.setVolume(duaVolume, duaVolume); } catch (Exception ignored) {}
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

    // ✅ نفس معادلة حساب مستوى صوت الأذان (خطية 1:1) المستخدمة في ThikrMediaPlayerService
    // عشان الدعاء يتبع نفس المستوى بالظبط
    private static float getAthanVolumeFloat(Context context) {
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(context);
        int athanVolumeLevel = prefs.getInt("athan_volume", 100);
        float vol = athanVolumeLevel / 100f;
        if (vol < 0f) vol = 0f;
        else if (vol > 1f) vol = 1f;
        return vol;
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
