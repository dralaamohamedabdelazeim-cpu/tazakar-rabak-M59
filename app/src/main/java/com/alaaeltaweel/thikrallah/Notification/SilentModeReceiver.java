package com.alaaeltaweel.thikrallah.Notification;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.preference.PreferenceManager;
import android.util.Log;

public class SilentModeReceiver extends BroadcastReceiver {
    private static final String TAG = "SilentModeReceiver";
    public static final String ACTION_SILENT_ON = "com.alaaeltaweel.thikrallah.SILENT_ON";
    public static final String ACTION_SILENT_OFF = "com.alaaeltaweel.thikrallah.SILENT_OFF";
    public static final String PREF_PREVIOUS_RINGER_MODE = "previousRingerModeBeforeSilent";
    public static final String PREF_SILENT_ACTIVE_BY_APP = "silentModeActiveByApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || intent.getAction() == null) return;

        AudioManager audioManager =
            (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // ✅ تغيير وضع الرنين محتاج إذن "عدم الإزعاج" خاص - لو المستخدم فعّل الميزة من غير
        // ما يمنح الإذن ده، setRingerMode() كانت بترمي Crash. دلوقتي بنتأكد الأول ونطلع بهدوء
        if ((ACTION_SILENT_ON.equals(intent.getAction()) || ACTION_SILENT_OFF.equals(intent.getAction()))) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || !nm.isNotificationPolicyAccessGranted()) {
                Log.w(TAG, "DND access not granted - skipping ringer mode change to avoid crash");
                return;
            }
        }

        if (ACTION_SILENT_ON.equals(intent.getAction())) {
            boolean alreadyActive = prefs.getBoolean(PREF_SILENT_ACTIVE_BY_APP, false);
            if (alreadyActive) {
                // ✅ إحنا فعلنا الصمت بالفعل ولسه ما رجعناهوش - متسجلش وضع جديد فوق المسجل الأصلي
                Log.d(TAG, "Silent mode already active by app, keeping original saved mode");
                try { audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); } catch (Exception e) { Log.e(TAG, "setRingerMode failed", e); }
                return;
            }
            int currentMode = audioManager.getRingerMode();
            prefs.edit()
                .putInt(PREF_PREVIOUS_RINGER_MODE, currentMode)
                .putBoolean(PREF_SILENT_ACTIVE_BY_APP, true)
                .commit();
            try { audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE); } catch (Exception e) { Log.e(TAG, "setRingerMode failed", e); }
            Log.d(TAG, "Silent mode ON, previous mode was " + currentMode);
        } else if (ACTION_SILENT_OFF.equals(intent.getAction())) {
            int previousMode = prefs.getInt(PREF_PREVIOUS_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);
            try {
                audioManager.setRingerMode(previousMode);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            } catch (Exception e) {
                Log.e(TAG, "setRingerMode failed", e);
            }
            prefs.edit().putBoolean(PREF_SILENT_ACTIVE_BY_APP, false).commit();
            Log.d(TAG, "Silent mode OFF, restored mode " + previousMode);
        } else if ("com.alaaeltaweel.thikrallah.STOP_SOUND".equals(intent.getAction())) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, 0, 0);
        }
    }
}
