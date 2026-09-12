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
import android.view.KeyEvent;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import androidx.core.app.NotificationCompat;

public class AthanScreenActivity extends AppCompatActivity {

     // ✅ متغيرات قفل الأذان بالقلب / أزرار الصوت
    private boolean isMutedByFlip = false;
    private boolean wasExplicitlyStopped = false; // ✅ true لو المستخدم دوس إيقاف بنفسه
    // ✅ علامة يقدر ThikrAlarmReceiver يشوفها من برة عشان يعرف الشاشة العادية فاتحة فعلاً
    // ولا محتاج يلجأ للنافذة العائمة (AthanOverlayService)
    public static volatile boolean isActivityShowing = false;
    // ✅ دي بتفضل true طول ما الأذان ده شغال حتى لو خرجت من الشاشة (بعكس isActivityShowing
    // اللي بترجع false فورًا لما تعمل onPause) - عشان ThikrAlarmReceiver يعرف إن الشاشة
    // فعلاً فتحت بنجاح قبل كده، ومترجعش تشغل النافذة العائمة زيادة لمجرد إنك خرجت بسرعة
    public static volatile boolean hasOpenedSuccessfully = false;
    private Runnable showReturnNotifRunnable; // ✅ لتأجيل إشعار الرجوع ومنع الفلاش السريع

    private static final int AUTO_DISMISS_DELAY  = 10 * 60 * 1000;
    private static final int SLIDESHOW_INTERVAL  = 30 * 1000; // 30 ثانية
    private static final String TAG = "AthanScreenActivity";
    // ✅ معرّف جديد (v2) عشان إعداد "بدون صوت" يتطبق حتى على اللي مثبتين التطبيق بالفعل
    private static final String NOTIF_CHANNEL_ID = "athan_screen_channel_v2";
    private static final int NOTIF_ID = 774411; // ✅ اتغيّر عشان ميتعارضش مع إشعار الدعاء بعد الأذان (كان بيستخدم نفس الرقم 9911)
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
            closeScreenOnly(); // ✅ الخدمة هي اللي بعتت الإشارة دي، متبعتلهاش أمر إيقاف تاني
        }
    };

    // ✅ الأذان لو بدأ وقت مكالمة شغالة، المفروض يفضل مكتوم لحد ما هو نفسه يخلص وقته الطبيعي
    // مش لحد ما المكالمة تخلص - عشان كده مبقاش فيه رجّوع صوت لما المكالمة تخلص
    private PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            // مقصودة فاضية - إبقاء الأذان مكتوم طول الوقت لحد ما stopAthanAndClose يشتغل عادي
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

        // ✅ أذان جديد بيبدأ - نصفّر العلامة دي عشان تعكس الجلسة الحالية بس
        hasOpenedSuccessfully = false;

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

        boolean isResume = getIntent().getBooleanExtra("isResume", false);

        if (isResume) {
            // راجعين للشاشة من الإشعار والأذان شغال بالفعل - مترجعش من غير ما نشغله تاني من الأول
            Log.d(TAG, "Resuming athan screen - athan already playing, not restarting it");
            athanPlayed = true;
            autoHandler.postDelayed(this::stopAthanAndClose, AUTO_DISMISS_DELAY);
        } else if (isCallInProgress) {
            // ✅ في مكالمة — شغّل الأذان فعليًا لكن بصوت مكتوم فورًا
            // بكده هيخلص في نفس توقيته الطبيعي والشاشة هتقفل تلقائي مع الـ ATHAN_COMPLETE
            Log.d(TAG, "Call in progress, playing athan silently");
            registerPhoneStateListener();
            athanPlayed = true;
            playAthan();
            autoHandler.postDelayed(() -> sendMuteAction(true), 300); // نستنى الـ player يتجهز قبل ما نكتمه
            autoHandler.postDelayed(this::stopAthanAndClose, AUTO_DISMISS_DELAY); // شبكة أمان لو حصل أي خطأ
        } else {
            // ✅ مفيش مكالمة — شغل الأذان عادي
            athanPlayed = true;
            playAthan();
            autoHandler.postDelayed(this::stopAthanAndClose, AUTO_DISMISS_DELAY);
        }

        // ✅ تسجيل الاستقبال مرة واحدة بس طول عمر الشاشة، مش مرتبط بكونها في المقدمة
        // (عشان الشاشة تتقفل لوحدها لما الأذان يخلص حتى لو المستخدم في تطبيق تاني)
        // ✅ من أندرويد 13 (API 33) لازم نحدد صراحة إنه مش exported، وإلا التسجيل ممكن يعمل Crash
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(athanCompleteReceiver,
                    new IntentFilter("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(athanCompleteReceiver,
                    new IntentFilter("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"));
        }
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
        isActivityShowing = true; // ✅ عشان ThikrAlarmReceiver يعرف إن الشاشة العادية فتحت صح ومحتاجش النافذة العائمة
        hasOpenedSuccessfully = true; // ✅ توثيق دائم إن الشاشة فتحت بنجاح، مبترجعش false لمجرد الخروج منها

        // ✅ الشاشة العادية نجحت تفتح فعليًا - اقفل النافذة العائمة الاحتياطية لو كانت ظاهرة
        Intent dismissOverlay = new Intent(this, com.alaaeltaweel.thikrallah.Notification.AthanOverlayService.class);
        dismissOverlay.setAction("com.alaaeltaweel.thikrallah.DISMISS_OVERLAY");
        try {
            startService(dismissOverlay);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityShowing = false; // ✅ مش شغالة دلوقتي - النافذة العائمة تقدر تشتغل لو الأذان لسه شغال
    }

    @Override
    protected void onStart() {
        super.onStart();
        // ✅ الشاشة بانت خالص (مش مجرد رجوع تركيز مؤقت) - الغي إشعار الرجوع لو لسه مجدول أو ظاهر
        if (showReturnNotifRunnable != null) {
            autoHandler.removeCallbacks(showReturnNotifRunnable);
            showReturnNotifRunnable = null;
        }
        cancelReturnToAthanNotification();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ✅ تم إيقاف إشعار "الأذان لسه شغال" نهائيًا (مش محتاجينه بعد تعديلات تانية) -
        // سايبين الكود والمتغيرات المرتبطة بيه زي ما هي عشان الإلغاء (cancelReturnToAthanNotification)
        // يفضل شغال لو فيه إشعار قديم لسه ظاهر من نسخة سابقة قبل التحديث
        /*
        if (!wasExplicitlyStopped) {
            showReturnNotifRunnable = this::showReturnToAthanNotification;
            autoHandler.postDelayed(showReturnNotifRunnable, 3000);
        }
        */
    }

    private void sendMuteAction(boolean mute) {
        Bundle data = new Bundle();
        data.putInt("ACTION", mute ? ThikrMediaPlayerService.MEDIA_PLAYER_MUTE_BY_FLIP : ThikrMediaPlayerService.MEDIA_PLAYER_UNMUTE_BY_FLIP);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        Intent muteIntent = new Intent(this, ThikrMediaPlayerService.class).putExtras(data);
        startService(muteIntent);
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

    // ✅ إشعار يفضل ظاهر لما نخرج من شاشة الأذان والأذان لسه شغال، يرجعنا للشاشة عند الضغط عليه
    private void showReturnToAthanNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = nm.getNotificationChannel(NOTIF_CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(NOTIF_CHANNEL_ID, "شاشة الأذان", NotificationManager.IMPORTANCE_HIGH);
                // ✅ من غير صوت خالص - عشان مايتضاربش مع صوت الأذان نفسه اللي شغال بالفعل
                channel.setSound(null, null);
                nm.createNotificationChannel(channel);
            }
        }

        Intent reopenIntent = new Intent(this, AthanScreenActivity.class);
        reopenIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", dataType);
        reopenIntent.putExtra("isCallInProgress", isCallInProgress);
        reopenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, reopenIntent, piFlags);

        Notification notification = new NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("الأذان لسه شغال")
                .setContentText("اضغط للرجوع لشاشة الأذان")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        nm.notify(NOTIF_ID, notification);
    }

    private void cancelReturnToAthanNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
        }
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
        // ✅ لو الأذان شغال فعليًا دلوقتي (من المنبه أو من محاولة سابقة)، متشغلوش تاني -
        // ده أدق من الاعتماد على وقت ثابت بس، لأن النظام ممكن يأخر فتح الشاشة لأكتر من 10 ثواني
        if (com.alaaeltaweel.thikrallah.ThikrMediaPlayerService.isAthanSoundActive) {
            Log.d(TAG, "Athan sound already active, skipping duplicate play");
            return;
        }
        // ✅ لو المنبه شغّل الصوت أصلاً، متشغلوش تاني من هنا (حماية إضافية لأول لحظات قبل ما العلامة تتحدث)
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
        wasExplicitlyStopped = true; // ✅ ده إيقاف مقصود من المستخدم، مش خروج عادي
        Bundle data = new Bundle();
        data.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_STOP);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        Intent stopMedia = new Intent(this, ThikrMediaPlayerService.class).putExtras(data);
        startService(stopMedia);
        
        Intent stopThikr = new Intent(this, ThikrService.class);
        stopService(stopThikr);

        closeScreenOnly();
   // جدد الأذان الجاي
MainActivity.startAthanTimer(getApplicationContext());
    }

    // ✅ قفل الشاشة بس من غير ما نبعت أي أمر إيقاف تاني للخدمة - عشان نستخدمها لما
    // الخدمة نفسها تكون هي اللي بعتت إشارة "الأذان خلص" (ATHAN_COMPLETE)، فمفيش داعي
    // نرد عليها بأمر إيقاف زيادة ممكن يوقف الخدمة فجأة وهي لسه بتشغل الدعاء
    private void closeScreenOnly() {
        wasExplicitlyStopped = true;
        slideshowHandler.removeCallbacksAndMessages(null);
        athanTextHandler.removeCallbacksAndMessages(null);
        autoHandler.removeCallbacksAndMessages(null);
        unregisterPhoneStateListener();
        finish();
    }

    @Override
    protected void onDestroy() {
        isActivityShowing = false; // ✅ شبكة أمان إضافية
        try {
            unregisterReceiver(athanCompleteReceiver);
        } catch (IllegalArgumentException e) {
            // في حالة مش مسجل أصلاً
        }
        slideshowHandler.removeCallbacksAndMessages(null);
        athanTextHandler.removeCallbacksAndMessages(null);
        autoHandler.removeCallbacksAndMessages(null);
        unregisterPhoneStateListener();
        cancelReturnToAthanNotification();

        super.onDestroy();
    }
        
}

