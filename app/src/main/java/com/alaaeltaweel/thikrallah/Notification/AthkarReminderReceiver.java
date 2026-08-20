package com.alaaeltaweel.thikrallah.Notification;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;

public class AthkarReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_ATHKAR_TYPE = "athkar_type";
    public static final String CHANNEL_ID = "athkar_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String athkarType = intent.getStringExtra(EXTRA_ATHKAR_TYPE);
        // ✅ لو الإشعار وصل من غير النوع لأي سبب، منمنعش القيمة دي تعمل Crash على .hashCode()
        if (athkarType == null) {
            athkarType = "morning";
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, athkarType.hashCode(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
                | PendingIntent.FLAG_IMMUTABLE);

        NotificationManager manager = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "تذكير الأذكار",
                NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        String title, body;
        if ("morning".equals(athkarType)) {
            title = "🌅 أذكار الصباح";
            body  = "لا تنسَ أذكار الصباح - ابدأ يومك بذكر الله";
        } else {
            title = "🌙 أذكار المساء";
            body  = "لا تنسَ أذكار المساء - اختم يومك بذكر الله";
        }

        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        manager.notify(athkarType.hashCode(), builder.build());

        // ✅ setExactAndAllowWhileIdle بتشتغل مرة واحدة بس - لازم نجدد الميعاد لبكرة يدويًا
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        if (hour != -1 && minute != -1) {
            rescheduleForTomorrow(context, athkarType, hour, minute);
        }
    }

    private void rescheduleForTomorrow(Context context, String athkarType, int hour, int minute) {
        android.app.AlarmManager alarmManager =
            (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, AthkarReminderReceiver.class);
        intent.putExtra(EXTRA_ATHKAR_TYPE, athkarType);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, athkarType.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, hour);
        calendar.set(java.util.Calendar.MINUTE, minute);
        calendar.set(java.util.Calendar.SECOND, 0);
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1);

        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            calendar.getTimeInMillis(),
            pendingIntent);
    }
}
