package com.alaaeltaweel.thikrallah.Notification;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.ThikrMediaPlayerService;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.KeyEvent;

public class AthanScreenActivity extends AppCompatActivity implements SensorEventListener {

     // ✅ متغيرات قفل الأذان بالقلب / أزرار الصوت
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private boolean isMutedByFlip = false;

    private static final int AUTO_DISMISS_DELAY  = 10 * 60 * 1000;
    private static final int SLIDESHOW_INTERVAL  = 30 * 1000; // 30 ثانية
    private static final int CALL_DISMISS_DELAY  = 4 * 60 * 1000; // 4 دقايق لو في مكالمة
    private static final String TAG = "AthanScreenActivity";
    private Handler autoHandler = new Handler();
    private Handler slideshowHandler = new Handler();
    private Handler athanTextHandler = new Handler();
    private String dataType;
    private boolean isCallInProgress = false;
    private boolean athanPlayed = false;
    private TelephonyManager telephonyManager;
    private ImageView fatherBgView;
    private TextView athanLinesText;
    private int currentPhotoIndex = 0;
    private int currentAthanLine = 0;

    // كلمات الأذان
    private String[] athanLines = {
        "الله أكبر .. الله أكبر",
        "الله أكبر .. الله أكبر",
        "أشهد أن لا إله إلا الله",
        "أشهد أن لا إله إلا الله",
        "أشهد أن محمداً رسول الله",
        "أشهد أن محمداً رسول الله",
        "حي على الصلاة", 
        "حي على الصلاة", 
        "حي على الفلاح", 
        "حي على الفلاح",
        "الله أكبر .. الله أكبر",
        "لا إله إلا الله"
    };

    // قائمة صور والدك رحمه الله
    private int[] photos = {
        R.drawable.father_bg,
        R.drawable.father_bg2,
        R.drawable.father_bg3,
        R.drawable.father_bg4,
        R.drawable.father_bg5,
        R.drawable.father_bg6,
        R.drawable.father_bg7
    };

    

    private BroadcastReceiver athanCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stopAthanAndClose();
        }
    };

    // ✅ مراقبة حالة المكالمة
    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE && isCallInProgress && !athanPlayed) {
                Log.d(TAG, "Call ended, playing athan now");
                isCallInProgress = false;
                athanPlayed = true;
                autoHandler.removeCallbacksAndMessages(null);
                playAthan();
                autoHandler.postDelayed(() -> stopAthanAndClose(), AUTO_DISMISS_DELAY);
            }
        }
    };

    private Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            currentPhotoIndex = (currentPhotoIndex + 1) % photos.length;
            changePhotoWithAnimation(photos[currentPhotoIndex]);
            slideshowHandler.postDelayed(this, SLIDESHOW_INTERVAL);
        }
    };

    private Runnable athanTextRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentAthanLine < athanLines.length) {
                showAthanLineWithAnimation(athanLines[currentAthanLine]);
                currentAthanLine++;
                athanTextHandler.postDelayed(this, 18000); // كل 07 ثانية
            }
        }
    };

    private void showAthanLineWithAnimation(final String line) {
        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setDuration(800);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                athanLinesText.setText(line);
                AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
                fadeIn.setDuration(800);
                athanLinesText.startAnimation(fadeIn);
                athanLinesText.setAlpha(1f);
            }
        });
        athanLinesText.startAnimation(fadeOut);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ تهيئة حساس القلب
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            );
        }

        // ✅ خلي الشاشة صاحية طول الأذان
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_athan_screen);

        dataType = getIntent().getStringExtra("com.alaaeltaweel.thikrallah.datatype");
        isCallInProgress = getIntent().getBooleanExtra("isCallInProgress", false);

        fatherBgView = findViewById(R.id.father_bg);
        athanLinesText = findViewById(R.id.allahu_akbar_text);
        TextView prayerNameText = findViewById(R.id.prayer_name_text);
        TextView athanText = findViewById(R.id.athan_text);
        Button stopButton = findViewById(R.id.stop_athan_button);

        String prayerName = getPrayerName(dataType);
        prayerNameText.setText(prayerName);
        athanText.setText("حان وقت صلاة " + prayerName);

        stopButton.setOnClickListener(v -> stopAthanAndClose());

        // ابدأ الـ slideshow بعد 30 ثانية
        slideshowHandler.postDelayed(slideshowRunnable, SLIDESHOW_INTERVAL);

        // ابدأ animation كلمات الأذان بعد ثانيتين
        athanTextHandler.postDelayed(athanTextRunnable, 2000);

        if (isCallInProgress) {
            // ✅ في مكالمة — افتح الشاشة بدون صوت وراقب المكالمة
            Log.d(TAG, "Call in progress, opening screen silently");
            registerPhoneStateListener();
            autoHandler.postDelayed(() -> stopAthanAndClose(), CALL_DISMISS_DELAY);
        } else {
            // ✅ مفيش مكالمة — شغل الأذان عادي
            athanPlayed = true;
            playAthan();
            autoHandler.postDelayed(this::stopAthanAndClose, AUTO_DISMISS_DELAY);
        }

        // ✅ تسجيل الاستقبال مرة واحدة بس طول عمر الشاشة، مش مرتبط بكونها في المقدمة
        // (عشان الشاشة تتقفل لوحدها لما الأذان يخلص حتى لو المستخدم في تطبيق تاني)
        registerReceiver(athanCompleteReceiver,
                new IntentFilter("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));
    }

    private void registerPhoneStateListener() {
        try {
            telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            }
        } catch (SecurityException e) {
            Log.d(TAG, "Cannot listen to phone state: " + e.getMessage());
        }
    }

    private void unregisterPhoneStateListener() {
        try {
            if (telephonyManager != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception e) {
            Log.d(TAG, "Error unregistering phone listener");
        }
    }

    private void changePhotoWithAnimation(final int newPhotoRes) {
        AlphaAnimation fadeOut = new AlphaAnimation(0.18f, 0f);
        fadeOut.setDuration(1000);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                fatherBgView.setImageResource(newPhotoRes);
                AlphaAnimation fadeIn = new AlphaAnimation(0f, 0.18f);
                fadeIn.setDuration(1000);
                fatherBgView.startAnimation(fadeIn);
                fatherBgView.setAlpha(0.18f);
            }
        });
        fatherBgView.startAnimation(fadeOut);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ تفعيل حساس القلب لو المستخدم مفعّل الخاصية
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean lockOnFlip = prefs.getBoolean("lock_athan_on_flip", false);
        if (lockOnFlip && sensorManager != null && accelerometerSensor != null) {
            isMutedByFlip = false;
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        // ✅ لو الشاشة اتقفلت والصوت كان مكتوم بسبب القلب، نرجّعه عادي (الكتم بس مرتبط بشاشة الأذان مفتوحة)
        if (isMutedByFlip) {
            sendMuteAction(false);
            isMutedByFlip = false;
        }
    }

    // ✅ كتم صوت الأذان مرة واحدة بس عند قلب الهاتف (مفيش إرجاع تلقائي)
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isMutedByFlip) return; // اتكتم قبل كده، متعملش حاجة تاني
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float z = event.values[2];
            boolean isFaceDown = z < -9.0f;
            if (isFaceDown) {
                isMutedByFlip = true;
                sendMuteAction(true);
            }
        }
    }

    private void sendMuteAction(boolean mute) {
        Bundle data = new Bundle();
        data.putInt("ACTION", mute ? ThikrMediaPlayerService.MEDIA_PLAYER_MUTE_BY_FLIP : ThikrMediaPlayerService.MEDIA_PLAYER_UNMUTE_BY_FLIP);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        Intent muteIntent = new Intent(this, ThikrMediaPlayerService.class).putExtras(data);
        startService(muteIntent);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // مش محتاجين نعمل حاجة هنا
    }

// ✅ كتم صوت الأذان بس (زي القلب بالظبط) عند الضغط على زرار الصوت
@Override
public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
        SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        boolean lockOnVolume = prefs.getBoolean("lock_athan_on_volume_buttons", false);
        if (lockOnVolume) {
            if (!isMutedByFlip) {
                isMutedByFlip = true;
                sendMuteAction(true);
            }
            return true; // نمنع تغيير صوت الموبايل الفعلي
        }
    }
    return super.onKeyDown(keyCode, event);
}

    private String getPrayerName(String dataType) {
        if (dataType == null) return "الصلاة";
        switch (dataType) {
            case MainActivity.DATA_TYPE_ATHAN1: return "الفجر";
            case MainActivity.DATA_TYPE_ATHAN2:
                java.util.Calendar cal = java.util.Calendar.getInstance();
                if (cal.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.FRIDAY) {
                    return "الجمعة";
                }
                return "الظهر";
            case MainActivity.DATA_TYPE_ATHAN3: return "العصر";
            case MainActivity.DATA_TYPE_ATHAN4: return "المغرب";
            case MainActivity.DATA_TYPE_ATHAN5: return "العشاء";
            default: return "الصلاة";
        }
    }

    private void playAthan() {
        // ✅ لو المنبه شغّل الصوت أصلاً، متشغلوش تاني من هنا
        SharedPreferences soundPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
        long triggeredTime = soundPrefs.getLong("athan_sound_triggered_" + dataType, 0);
        if (System.currentTimeMillis() - triggeredTime < 10 * 1000L) {
            Log.d(TAG, "Athan sound already triggered by receiver, skipping duplicate play");
            return;
        }
        soundPrefs.edit().putLong("athan_sound_triggered_" + dataType, System.currentTimeMillis()).commit();

        android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            am.requestAudioFocus(null,
                android.media.AudioManager.STREAM_ALARM,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        Bundle data = new Bundle();
        data.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_PLAY);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        data.putBoolean("isUserAction", false);

        Intent intent = new Intent(this, ThikrService.class).putExtras(data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
            }

    private void stopAthanAndClose() {
        Bundle data = new Bundle();
        data.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_STOP);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        Intent stopMedia = new Intent(this, ThikrMediaPlayerService.class).putExtras(data);
        startService(stopMedia);
        
        Intent stopThikr = new Intent(this, ThikrService.class);
        stopService(stopThikr);

        slideshowHandler.removeCallbacksAndMessages(null);
        athanTextHandler.removeCallbacksAndMessages(null);
        autoHandler.removeCallbacksAndMessages(null);
        unregisterPhoneStateListener();
        finish();
   // جدد الأذان الجاي
MainActivity.startAthanTimer(getApplicationContext());
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(athanCompleteReceiver);
        } catch (IllegalArgumentException e) {
            // في حالة مش مسجل أصلاً
        }
        slideshowHandler.removeCallbacksAndMessages(null);
        athanTextHandler.removeCallbacksAndMessages(null);
        autoHandler.removeCallbacksAndMessages(null);
        unregisterPhoneStateListener();
        super.onDestroy();
    }
}

