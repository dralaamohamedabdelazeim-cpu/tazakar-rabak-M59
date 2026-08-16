package com.alaaeltaweel.thikrallah.Notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.ThikrMediaPlayerService;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * ✅ خط دفاع إضافي مستقل عن AthanScreenActivity.
 * بعض الأجهزة (زي أوبو/ColorOS) بتمنع فتح شاشة كاملة (Activity) من الخلفية
 * حتى لو كل الصلاحيات مفعّلة. النافذة العائمة دي مش بتفتح Activity خالص،
 * فمش بتتأثر بالقيد ده، وبتشتغل طالما صلاحية "الظهور فوق التطبيقات الأخرى" مفعّلة.
 *
 * الصوت والدعاء والمنطق التاني كله بيفضل زي ما هو (بيتشغل من ThikrMediaPlayerService
 * بشكل مستقل تمامًا) - الخدمة دي شغلها الوحيد إنها تعرض واجهة كاملة وتوفر زرار إيقاف.
 */
public class AthanOverlayService extends Service {

    private static final String TAG = "AthanOverlayService";
    private static final long AUTO_DISMISS_DELAY = 10 * 60 * 1000; // شبكة أمان زي الشاشة العادية
    private static final long PHOTO_CHANGE_INTERVAL = 8000; // نفس فترة السلايدشو في الشاشة العادية تقريبًا
    private static final long CLOCK_TICK_INTERVAL = 1000;

    private final int[] photos = {
            R.drawable.father_bg,
            R.drawable.father_bg2,
            R.drawable.father_bg3,
            R.drawable.father_bg4,
            R.drawable.father_bg5,
            R.drawable.father_bg6,
            R.drawable.father_bg7
    };
    private int currentPhotoIndex = 0;

    private WindowManager windowManager;
    private FrameLayout overlayView;
    private ImageView backgroundImage;
    private TextView clockView;
    private String dataType;

    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private final Handler slideshowHandler = new Handler(Looper.getMainLooper());
    private final Handler clockHandler = new Handler(Looper.getMainLooper());

    private final Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            if (backgroundImage != null) {
                currentPhotoIndex = (currentPhotoIndex + 1) % photos.length;
                try {
                    backgroundImage.setImageResource(photos[currentPhotoIndex]);
                } catch (Exception ignored) {}
            }
            slideshowHandler.postDelayed(this, PHOTO_CHANGE_INTERVAL);
        }
    };

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (clockView != null) {
                String time = new SimpleDateFormat("hh:mm:ss a", new Locale("ar")).format(new java.util.Date());
                clockView.setText(time);
            }
            clockHandler.postDelayed(this, CLOCK_TICK_INTERVAL);
        }
    };

    private final BroadcastReceiver athanCompleteReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "ATHAN_COMPLETE received - dismissing overlay");
            stopSelf();
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showMinimalForegroundNotification();

        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // ✅ لو الشاشة العادية نجحت تفتح، هتنادي على هذا نفسه لقفل النافذة العائمة
        if ("com.alaaeltaweel.thikrallah.DISMISS_OVERLAY".equals(intent.getAction())) {
            Log.d(TAG, "Dismiss requested (real screen opened successfully)");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.d(TAG, "No overlay permission - cannot show fallback screen");
            stopSelf();
            return START_NOT_STICKY;
        }

        dataType = intent.getStringExtra("com.alaaeltaweel.thikrallah.datatype");
        String prayerName = getPrayerName(dataType);

        showOverlay(prayerName);

        registerReceiver(athanCompleteReceiver, new IntentFilter("com.alaaeltaweel.thikrallah.ATHAN_COMPLETE"),
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);

        autoHandler.removeCallbacksAndMessages(null);
        autoHandler.postDelayed(this::stopSelf, AUTO_DISMISS_DELAY);

        slideshowHandler.removeCallbacksAndMessages(null);
        slideshowHandler.postDelayed(slideshowRunnable, PHOTO_CHANGE_INTERVAL);

        clockHandler.removeCallbacksAndMessages(null);
        clockHandler.post(clockRunnable);

        return START_NOT_STICKY;
    }

    private String getPrayerName(String type) {
        if (type == null) return "الصلاة";
        if (type.contains(MainActivity.DATA_TYPE_ATHAN1)) return "الفجر";
        if (type.contains(MainActivity.DATA_TYPE_ATHAN2)) return "الظهر";
        if (type.contains(MainActivity.DATA_TYPE_ATHAN3)) return "العصر";
        if (type.contains(MainActivity.DATA_TYPE_ATHAN4)) return "المغرب";
        if (type.contains(MainActivity.DATA_TYPE_ATHAN5)) return "العشاء";
        return "الصلاة";
    }

    private static final int NOTIFICATION_ID = 236;

    private void showMinimalForegroundNotification() {
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "com.alaaeltaweel.thikrallah.Notification.AthanOverlayService";
            NotificationChannel chan = new NotificationChannel(channelId, "شاشة الأذان الاحتياطية", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(chan);
            builder = new NotificationCompat.Builder(this, channelId);
        } else {
            builder = new NotificationCompat.Builder(this);
        }
        builder.setContentTitle(getString(R.string.my_app_name))
                .setContentText("شاشة الأذان شغالة")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true);
        startForeground(NOTIFICATION_ID, builder.build());
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void showOverlay(String prayerName) {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // ✅ شيل أي نافذة قديمة قبل ما نضيف واحدة جديدة
        removeOverlayIfShown();

        overlayView = new FrameLayout(this);

        // ✅ الخلفية - صورة عشوائية زي الشاشة الأصلية بالظبط
        currentPhotoIndex = (int) (Math.random() * photos.length);
        backgroundImage = new ImageView(this);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try {
            backgroundImage.setImageResource(photos[currentPhotoIndex]);
        } catch (Exception ignored) {}
        overlayView.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ✅ طبقة غامقة شبه شفافة فوق الصورة عشان النص يبان واضح
        android.view.View dimOverlay = new android.view.View(this);
        dimOverlay.setBackgroundColor(Color.parseColor("#99000000"));
        overlayView.addView(dimOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // ✅ محتوى الشاشة (نص + زرار) في النص بالظبط
        LinearLayout contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        contentParams.gravity = Gravity.CENTER;
        overlayView.addView(contentLayout, contentParams);

        TextView appNameView = new TextView(this);
        appNameView.setText(getString(R.string.my_app_name));
        appNameView.setTextColor(Color.parseColor("#D9D9D9"));
        appNameView.setTextSize(16);
        appNameView.setGravity(Gravity.CENTER);
        contentLayout.addView(appNameView);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(22);
        clockView.setGravity(Gravity.CENTER);
        clockView.setPadding(0, dp(12), 0, dp(4));
        contentLayout.addView(clockView);

        TextView prayerNameView = new TextView(this);
        prayerNameView.setText("أذان " + prayerName);
        prayerNameView.setTextColor(Color.WHITE);
        prayerNameView.setTextSize(40);
        prayerNameView.setGravity(Gravity.CENTER);
        prayerNameView.setPadding(dp(24), dp(8), dp(24), dp(8));
        contentLayout.addView(prayerNameView);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("حان الآن موعد الصلاة، أعانك الله");
        subtitleView.setTextColor(Color.parseColor("#E0E0E0"));
        subtitleView.setTextSize(17);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(dp(24), 0, dp(24), dp(40));
        contentLayout.addView(subtitleView);

        Button stopButton = new Button(this);
        stopButton.setText("إيقاف");
        stopButton.setTextColor(Color.WHITE);
        stopButton.setTextSize(18);
        stopButton.setAllCaps(false);
        stopButton.setPadding(dp(48), dp(14), dp(48), dp(14));

        GradientDrawable buttonBg = new GradientDrawable();
        buttonBg.setColor(Color.parseColor("#C62828"));
        buttonBg.setCornerRadius(dp(28));
        stopButton.setBackground(buttonBg);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        stopButton.setLayoutParams(btnParams);
        stopButton.setOnClickListener(v -> stopAthanAndClose());
        contentLayout.addView(stopButton);

        int windowType = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        int windowFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                windowFlags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.CENTER;

        try {
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay athan screen shown");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay view: " + e.getMessage());
            stopSelf();
        }
    }

    private void stopAthanAndClose() {
        Bundle data = new Bundle();
        data.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_STOP);
        data.putString("com.alaaeltaweel.thikrallah.datatype", dataType);
        Intent stopMedia = new Intent(this, ThikrMediaPlayerService.class).putExtras(data);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(stopMedia);
            } else {
                startService(stopMedia);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send stop command: " + e.getMessage());
        }
        stopSelf();
    }

    private void removeOverlayIfShown() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
            backgroundImage = null;
            clockView = null;
        }
    }

    @Override
    public void onDestroy() {
        autoHandler.removeCallbacksAndMessages(null);
        slideshowHandler.removeCallbacksAndMessages(null);
        clockHandler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(athanCompleteReceiver);
        } catch (Exception ignored) {}
        removeOverlayIfShown();
        stopForeground(true);
        super.onDestroy();
    }
}
