package com.alaaeltaweel.thikrallah.Notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.WakeUpActivity;

/**
 * مشغّل مستقل لصوت "اقتربت الصلاة" و"الإقامة".
 *
 * ليه موجود: الصوت كان بيتشغل عن طريق قناة الإشعار (صوت النظام). النظام بيوقف صوت أي إشعار
 * شغال أول ما إشعار تاني (من أي تطبيق) يوصل بصوت، وكمان لو القناة اتعملت مرة والتركيز الصوتي
 * كان مرفوض بتفضل صامتة للأبد. هنا الصوت بيتشغل بـ MediaPlayer جوه Foreground Service،
 * والإشعار نفسه صامت، فمفيش حاجة تقدر تقطع الصوت غير خلاصه أو المستخدم.
 */
public class PrayerReminderSoundService extends Service {
    private static final String TAG = "PrayerReminderSound";

    public static final String EXTRA_KIND = "kind";               // "pre_athan" | "iqama"
    public static final String EXTRA_PRAYER = "prayer_name";
    public static final String EXTRA_IQAMA_SOUND = "iqama_sound";
    public static final String KIND_PRE_ATHAN = "pre_athan";
    public static final String KIND_IQAMA = "iqama";
    public static final String ACTION_STOP = "com.alaaeltaweel.thikrallah.STOP_SOUND";

    private static final String CHANNEL_ID = "prayer_reminder_silent_v1";
    private static final int NOTIFICATION_ID = 7421;
    private static final long MAX_PLAY_MS = 3 * 60 * 1000L;

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hardStop = this::finishAndStop;

    // نتجاهل تغيير التركيز الصوتي العادي (إشعار واتساب مثلاً)، لكن لو جت مكالمة نوقف الصوت فورًا
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change != AudioManager.AUDIOFOCUS_GAIN && isInCall()) {
            finishAndStop();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            finishAndStop();
            return START_NOT_STICKY;
        }

        String kind = intent != null ? intent.getStringExtra(EXTRA_KIND) : null;
        String prayer = intent != null ? intent.getStringExtra(EXTRA_PRAYER) : null;
        int iqamaSound = intent != null ? intent.getIntExtra(EXTRA_IQAMA_SOUND, 1) : 1;
        if (kind == null) kind = KIND_PRE_ATHAN;
        if (prayer == null) prayer = "fajr";

        // لازم startForeground بسرعة بعد startForegroundService
        try {
            Notification n = buildNotification(kind, prayer, true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, n);
            }
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed: " + e.getMessage());
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        long now = System.currentTimeMillis();
        if (KIND_IQAMA.equals(kind)) {
            prefs.edit().putLong("last_iqama_play_time", now).apply();
        } else {
            prefs.edit().putLong("last_pre_athan_play_time", now).apply();
        }

        // ✅ أثناء المكالمة (أو رنين مكالمة واردة) مفيش صوت: بنسيب إشعار صامت بس
        if (isInCall()) {
            Log.d(TAG, "Call active - skipping reminder sound");
            try {
                stopForeground(false);
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(kind, prayer, false));
            } catch (Exception e) {
                Log.e(TAG, "in-call notification failed: " + e.getMessage());
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        repairAlarmVolumeOnce(prefs);
        acquireWakeLock();
        playSound(kind, prayer, iqamaSound);
        return START_NOT_STICKY;
    }

    private boolean isInCall() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            int mode = am.getMode();
            return mode == AudioManager.MODE_IN_CALL
                    || mode == AudioManager.MODE_IN_COMMUNICATION
                    || mode == AudioManager.MODE_RINGTONE;
        } catch (Exception e) {
            return false;
        }
    }

    // النسخة القديمة كانت بتخلي صوت المنبه صفر لما المستخدم يضغط على الإشعار. بنصلحه مرة واحدة بس.
    private void repairAlarmVolumeOnce(SharedPreferences prefs) {
        try {
            if (prefs.getBoolean("alarm_volume_repaired_v1", false)) return;
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                am.setStreamVolume(AudioManager.STREAM_ALARM, Math.max(1, (int) (max * 0.6f)), 0);
            }
            prefs.edit().putBoolean("alarm_volume_repaired_v1", true).apply();
        } catch (Exception e) {
            Log.e(TAG, "repairAlarmVolumeOnce failed: " + e.getMessage());
        }
    }

    private void playSound(String kind, String prayer, int iqamaSound) {
        releasePlayer();
        int res = KIND_IQAMA.equals(kind) ? iqamaRes(iqamaSound) : preAthanRes(prayer);   // الجمعة: بيستخدم pre_jumuah لو الملف موجود
        requestFocus();
        try {
            MediaPlayer p = new MediaPlayer();
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            AssetFileDescriptor afd = getResources().openRawResourceFd(res);
            p.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            p.setOnCompletionListener(mp -> finishAndStop());
            p.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error " + what + "/" + extra);
                finishAndStop();
                return true;
            });
            p.prepare();
            p.start();
            player = p;
            handler.removeCallbacks(hardStop);
            handler.postDelayed(hardStop, MAX_PLAY_MS);
        } catch (Exception e) {
            Log.e(TAG, "playSound failed: " + e.getMessage());
            finishAndStop();
        }
    }

    private void requestFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
                audioManager.requestAudioFocus(focusRequest);
            } else {
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }
        } catch (Exception e) {
            Log.e(TAG, "requestFocus failed: " + e.getMessage());
        }
        // الصوت بيتشغل حتى لو التركيز اتمنع/اتأجل - مش بنعتمد على نتيجته
    }

    private void abandonFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                audioManager.abandonAudioFocus(focusListener);
            }
        } catch (Exception ignored) { }
        focusRequest = null;
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tazakar:PrayerReminderSound");
            wakeLock.acquire(MAX_PLAY_MS);
        } catch (Exception ignored) { }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.setOnCompletionListener(null); } catch (Exception ignored) { }
            try { if (player.isPlaying()) player.stop(); } catch (Exception ignored) { }
            try { player.release(); } catch (Exception ignored) { }
            player = null;
        }
    }

    private void finishAndStop() {
        handler.removeCallbacks(hardStop);
        releasePlayer();
        abandonFocus();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(hardStop);
        releasePlayer();
        abandonFocus();
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private Notification buildNotification(String kind, String prayerKey, boolean ongoing) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "تنبيهات الصلاة", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); // الصوت بيجي من المشغّل مش من القناة
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 500, 200, 500});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            // القنوات القديمة اللي كانت بتشغل الصوت بنفسها - ممكن تكون اتعملت صامتة للأبد
            String[] keys = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
            for (String k : keys) nm.deleteNotificationChannel("pre_athan_reminder_v2_" + k);
            for (int i = 1; i <= 3; i++) nm.deleteNotificationChannel("iqama_channel_v2_s" + i);
        }

        String prayerAr = ("dhuhr".equals(prayerKey) && isFridayToday()) ? "الجمعة" : prayerArabic(prayerKey);
        String title;
        String text;
        if (KIND_IQAMA.equals(kind)) {
            title = "إقامة صلاة " + prayerAr;
            text = "حان وقت إقامة الصلاة";
        } else {
            String mins = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString("preAthanMinutes_" + prayerKey, "15");
            title = "اقترب وقت صلاة " + prayerAr;
            text = "تبقى " + mins + " دقيقة على صلاة " + prayerAr;
        }

        int salt = KIND_IQAMA.equals(kind) ? 8888 : 7777;
        Intent wakeIntent = new Intent(this, WakeUpActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent wakePi = PendingIntent.getActivity(this, prayerKey.hashCode() + salt, wakeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // الضغط على الإشعار بيوقف الصوت (intent صريح للـ receiver)
        Intent stopIntent = new Intent(this, ThikrAlarmReceiver.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getBroadcast(this, prayerKey.hashCode() + salt + 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(ongoing)
                .setAutoCancel(true)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setContentIntent(stopPi)
                .addAction(0, "إيقاف الصوت", stopPi)
                .setFullScreenIntent(wakePi, true)
                .build();
    }

    private static String prayerArabic(String key) {
        switch (key) {
            case "fajr": return "الفجر";
            case "dhuhr": return "الظهر";
            case "asr": return "العصر";
            case "maghrib": return "المغرب";
            case "isha": return "العشاء";
            default: return "الصلاة";
        }
    }

    private static boolean isFridayToday() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY;
    }

    private int preAthanRes(String key) {
        // ✅ صوت خاص لظهر يوم الجمعة: ملف res/raw/pre_jumuah (اختياري). لو مش موجود بنستخدم صوت الظهر العادي
        if ("dhuhr".equals(key) && isFridayToday()) {
            int id = getResources().getIdentifier("pre_jumuah", "raw", getPackageName());
            if (id != 0) return id;
        }
        switch (key) {
            case "dhuhr": return R.raw.pre_dhuhr;
            case "asr": return R.raw.pre_asr;
            case "maghrib": return R.raw.pre_maghrib;
            case "isha": return R.raw.pre_isha;
            default: return R.raw.pre_fajr;
        }
    }

    private static int iqamaRes(int choice) {
        switch (choice) {
            case 2: return R.raw.iqama_2;
            case 3: return R.raw.iqama_3;
            default: return R.raw.iqama_1;
        }
    }
}
