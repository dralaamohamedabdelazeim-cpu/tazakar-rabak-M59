package com.alaaeltaweel.thikrallah.Notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.ramadan.RamadanSounds;

public class RamadanSoundService extends Service {

    private static final String TAG = "RamadanSoundService";
    public static final String ACTION_CANNON    = "com.alaaeltaweel.thikrallah.ACTION_CANNON";
    public static final String ACTION_MESAHARATY = "com.alaaeltaweel.thikrallah.ACTION_MESAHARATY";

    private MediaPlayer mediaPlayer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        showForegroundNotification();

        // ✅ مفيش صوت (مدفع/مسحراتي) أثناء المكالمات
        if (CallStateHelper.isInCall(this, true)) {
            Log.d(TAG, "call in progress - skipping ramadan sound");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        String soundFile = null;

        if (ACTION_CANNON.equals(action)) {
            soundFile = RamadanSounds.selected(this, RamadanSounds.TYPE_CANNON).assetPath;
            Log.d(TAG, "Playing cannon sound");
        } else if (ACTION_MESAHARATY.equals(action)) {
            soundFile = RamadanSounds.selected(this, RamadanSounds.TYPE_MESAHARATY).assetPath;
            Log.d(TAG, "Playing mesaharaty sound");
        }

        if (soundFile != null) {
            playSound(soundFile, ACTION_CANNON.equals(action) ? "cannon.mp3" : "mesaharaty.mp3");
        } else {
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    private void playSound(String fileName, String fallbackFile) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
                mediaPlayer = null;
            }

            mediaPlayer = new MediaPlayer();
            AssetFileDescriptor afd;
            try {
                afd = getAssets().openFd(fileName);
            } catch (Exception missing) {
                // لو الصوت المختار مش موجود نرجع للصوت الأصلي
                Log.e(TAG, "selected sound missing, using default: " + missing.getMessage());
                afd = getAssets().openFd(fallbackFile);
            }
            mediaPlayer.setDataSource(
                afd.getFileDescriptor(),
                afd.getStartOffset(),
                afd.getLength()
            );
            afd.close();
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                mediaPlayer = null;
                stopForeground(true);
                stopSelf();
            });

        } catch (Exception e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
            stopSelf();
        }
    }

    private void showForegroundNotification() {
        String channelId = "ramadan_sound_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId, "أصوات رمضان", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("رمضان كريم 🌙")
            .setContentText("...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        startForeground(77, notification);
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
