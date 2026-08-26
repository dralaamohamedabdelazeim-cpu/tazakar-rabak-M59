package com.alaaeltaweel.thikrallah.Notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.ThikrMediaPlayerService;

import java.lang.ref.WeakReference;
import java.util.Locale;

public class ChatHeadService extends Service implements View.OnTouchListener {

	private WindowManager windowManager;
	private TextView chatHead;
	String TAG = "ChatHeadService";
	private final static int NOTIFICATION_ID = 235;
	WindowManager.LayoutParams params;
	private String thikr;
	private boolean isAthan;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void startnotification() {
		// ✅ قناة جديدة بالكامل (v2) عشان أهمية القناة الجديدة (HIGH) تتطبق على الكل، حتى اللي
		// عندهم التطبيق مثبت فعلاً - أندرويد بيحمي إعدادات القناة القديمة ومبيغيّرهاش لوحده
		String NOTIFICATION_CHANNEL_ID = "com.alaaeltaweel.thikrallah.Notification.ChatHeadService.v2";
		String channelName = this.getResources().getString(R.string.floating_notification);
		NotificationCompat.Builder mBuilder;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			// ✅ IMPORTANCE_HIGH بدل DEFAULT - عشان الشاشة تنور تلقائيًا لما الذكر يوصل، زي ما
			// كانت شغالة قبل كده
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
			chan.setSound(null, null);
			chan.enableVibration(true);
			chan.setVibrationPattern(new long[]{0, 250});
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			mBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
		} else {
			mBuilder = new NotificationCompat.Builder(this);
		}
		if (thikr == null) {
			this.thikr = getResources().getString(R.string.remember_notification);
		}
		mBuilder.setContentTitle(this.getString(R.string.my_app_name))
				.setContentText(thikr)
				.setSmallIcon(R.drawable.ic_launcher)
				// ✅ للأجهزة الأقدم من أندرويد 8 (اللي مالهاش قنوات إشعارات أصلاً)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true);
		mBuilder = setVisibilityPublic(mBuilder);
		Intent launchAppIntent = new Intent(this, MainActivity.class);
		PendingIntent launchAppPendingIntent = PendingIntent.getActivity(this,
				0, launchAppIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
		mBuilder.setContentIntent(launchAppPendingIntent);
		startForeground(NOTIFICATION_ID, mBuilder.build());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

		// ✅ إزالة أي view قديم بأمان
		if (chatHead != null) {
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					if (chatHead.isAttachedToWindow()) {
						windowManager.removeView(chatHead);
					}
				} else {
					windowManager.removeView(chatHead);
				}
			} catch (Exception e) {
				Log.e(TAG, "Error removing old chatHead: " + e.getMessage());
			}
			chatHead = null;
		}

		SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		String lang = mPrefs.getString("language", null);
		Log.d(TAG, "chatheadservice started");
		if (lang != null) {
			Locale locale = new Locale(lang);
			Locale.setDefault(locale);
			Configuration config = new Configuration();
			config.locale = locale;
			getBaseContext().getResources().updateConfiguration(config,
					getBaseContext().getResources().getDisplayMetrics());
		}
		if (intent == null) {
			Log.d(TAG, "starting foreground (null intent?)");
			startnotification();
			this.stopSelf();
			return START_NOT_STICKY;
		}
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
		int reminderType = Integer.parseInt(sharedPrefs.getString("RemindmeThroughTheDayType", "1"));
		// ✅ تذكير فتح الشاشة نص دايمًا بتصميمه، فمن حقه يتجاوز فحص "طريقة التذكير" العام
		// (اللي ممكن يكون مظبوط على "صوتي فقط" مثلاً)
		boolean isScreenUnlockThikr = intent.getBooleanExtra("isScreenUnlockThikr", false);
		if (isScreenUnlockThikr || reminderType == 1 || reminderType == 3) {

			String thikr = intent.getStringExtra("thikr");
			isAthan = intent.getBooleanExtra("isAthan", false);

			if (isAthan) {
				NotificationCompat.Builder mBuilder;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					String NOTIFICATION_CHANNEL_ID = "com.alaaeltaweel.thikrallah.Notification.AthanTimerService";
					String channelName = this.getResources().getString(R.string.athan_timer_notifiaction);
					NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
					chan.setSound(null, null);
					chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
					NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
					assert manager != null;
					manager.createNotificationChannel(chan);
					mBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
				} else {
					mBuilder = new NotificationCompat.Builder(this);
				}
				mBuilder.setContentTitle(this.getString(R.string.my_app_name))
						.setContentText(thikr)
						.setSmallIcon(R.drawable.ic_launcher)
						.setAutoCancel(true);
				mBuilder = setVisibilityPublic(mBuilder);
				Intent launchAppIntent = new Intent(this, MainActivity.class);
				launchAppIntent.putExtra("FromNotification", true);
				launchAppIntent.putExtra("DataType", MainActivity.DATA_TYPE_ATHAN);
				PendingIntent launchAppPendingIntent = PendingIntent.getActivity(this,
						0, launchAppIntent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
				mBuilder.setContentIntent(launchAppPendingIntent);
				Log.d(TAG, "starting foreground (athan)");
				startForeground(NOTIFICATION_ID, mBuilder.build());
			} else {
				Log.d(TAG, "starting foreground (not athan)");
				startnotification();
			}

			chatHead = new TextView(this);
			chatHead.setTextAppearance(this.getApplicationContext(), android.R.style.TextAppearance_Large);
			chatHead.setText(thikr, TextView.BufferType.SPANNABLE);
			chatHead.setBackgroundResource(R.drawable.chat_head);
			chatHead.setTextColor(Color.BLACK);
			chatHead.setGravity(Gravity.CENTER);

			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
				params = new WindowManager.LayoutParams(
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
						WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
			} else {
				params = new WindowManager.LayoutParams(
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_PHONE,
						WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
			}

			params.gravity = Gravity.CENTER;
			params.x = 0;
			params.y = 100;

			chatHead.setOnTouchListener(this);

			// ✅ try-catch عشان التطبيق ميعلقش لو في مشكلة
			try {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					if (Settings.canDrawOverlays(this)) {
						windowManager.addView(chatHead, params);
						if (!isAthan) {
							new Handler().postDelayed(new DestroyRunnable(this), 10000);
						}
					} else {
    Log.d(TAG, "No overlay permission - stopping service");
    stopForeground(true);
    this.stopSelf();
}  
				} else {
					windowManager.addView(chatHead, params);
					if (!isAthan) {
						new Handler().postDelayed(new DestroyRunnable(this), 10000);
					}
				}
			} catch (Exception e) {
				Log.e(TAG, "Error adding chatHead view: " + e.getMessage());
				this.stopSelf();
			}

		} else {
			Log.d(TAG, "not reminder type 1 or 3? what then? It is: " + reminderType);
			startnotification();
			this.stopSelf();
		}
		return START_NOT_STICKY;
	}

	static class DestroyRunnable implements Runnable {
		private final WeakReference<ChatHeadService> mService;

		DestroyRunnable(ChatHeadService service) {
			mService = new WeakReference<>(service);
		}

		@Override
		public void run() {
			if (mService.get() != null) {
				mService.get().stopSelf();
			}
		}
	}

	private NotificationCompat.Builder setVisibilityPublic(NotificationCompat.Builder inotificationBuilder) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			inotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		}
		return inotificationBuilder;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (isAthan) {
			Bundle data = new Bundle();
			data.putInt("ACTION", ThikrMediaPlayerService.MEDIA_PLAYER_RESET);
			data.putString("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_ATHAN1);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				this.startForegroundService(new Intent(this, ThikrMediaPlayerService.class).putExtras(data));
			} else {
				this.startService(new Intent(this, ThikrMediaPlayerService.class).putExtras(data));
			}
		}
		stopSelf();
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "ondestroy called");
		if (chatHead != null) {
			try {
				windowManager.removeView(chatHead);
			} catch (Exception e) {
				Log.e(TAG, "Error removing chatHead on destroy: " + e.getMessage());
			}
		}
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancel(NOTIFICATION_ID);
	}
	
}
