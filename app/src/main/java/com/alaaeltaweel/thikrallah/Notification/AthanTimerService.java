package com.alaaeltaweel.thikrallah.Notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;


public class AthanTimerService extends Service {
	NotificationCompat.Builder notificationBuilder;
    String TAG = "AthanTimerService";
    private final static int NOTIFICATION_ID = 54;
	private Context mContext;
	boolean isStarted = false;
	private Timer timer;
    private PowerManager.WakeLock wakeLock;
	
    public static final int JOB_ID = 0x01;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mContext = this;
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
        boolean isTimer = sharedPrefs.getBoolean("foreground_athan_timer", true);
        Timber.tag(TAG).d("istimer is " + isTimer);
        initNotification();
        if (isTimer) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (wakeLock == null || !wakeLock.isHeld()) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tazakar:AthanTimerWakeLock");
                wakeLock.acquire(12 * 60 * 60 * 1000L);
            }
            if (!isStarted) {
                Timber.tag(TAG).d(TAG + "started");
                if (timer != null) {
                    timer.cancel();
                    timer = null;
                }
                timer = new Timer();
                isStarted = true;
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                    initNotification();
                    checkMissedPrayerEvents();
                    checkMissedGeneralThikr();
                }
                }, 0, 60 * 1000);
            }
        } else {
            this.stopSelf();
        }
        return START_STICKY;
    }

	SharedPreferences sharedPrefs;

	@Override
	public void onDestroy() {
		if (timer != null) {
			timer.cancel();
			timer = null;
		}
		isStarted = false;
		if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
		}
		super.onDestroy();
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(NOTIFICATION_ID);
	}

	private void initNotification() {
		Timber.tag(TAG).d("initiNotification started");

		Intent resultIntent = new Intent(mContext, MainActivity.class);
		resultIntent.putExtra("FromNotification", true);
		resultIntent.putExtra("DataType", MainActivity.DATA_TYPE_ATHAN);
		resultIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		PendingIntent launchAppPendingIntent = PendingIntent.getActivity(mContext,
				0, resultIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			String NOTIFICATION_CHANNEL_ID = "com.alaaeltaweel.thikrallah.Notification.AthanTimerService";
			String channelName = this.getResources().getString(R.string.athan_timer_notifiaction);
			NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT);
			chan.setSound(null, null);
			chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
			NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			assert manager != null;
			manager.createNotificationChannel(chan);
			notificationBuilder = new NotificationCompat.Builder(mContext, NOTIFICATION_CHANNEL_ID);
		} else {
			notificationBuilder = new NotificationCompat.Builder(mContext);
		}

		notificationBuilder
				.setSmallIcon(R.drawable.ic_launcher)
				.setAutoCancel(true)
				.setContentTitle(getString(R.string.my_app_name))
				.setPriority(Notification.PRIORITY_DEFAULT)
				.setContentText(getNextPrayer())
				.setContentIntent(launchAppPendingIntent);

		notificationBuilder = setVisibilityPublic(notificationBuilder);
		Timber.tag(TAG).d("started forground");
		Timber.tag(TAG).d("context is " + mContext);

		if (mContext != null) {
			startForeground(NOTIFICATION_ID, notificationBuilder.build());
		}
	}

	private NotificationCompat.Builder setVisibilityPublic(NotificationCompat.Builder inotificationBuilder) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			inotificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
		}
		return inotificationBuilder;
	}

	String getNextPrayer() {
		PrayTime prayers = PrayTime.instancePrayTime(this);
		prayers.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
		String[] prayerTimes = prayers.getPrayerTimes(this);
		ArrayList<String> prayerTimesList = new ArrayList<>();
		prayerTimesList.add(prayerTimes[0]);
		prayerTimesList.add(prayerTimes[1]);
		prayerTimesList.add(prayerTimes[2]);
		prayerTimesList.add(prayerTimes[3]);
		prayerTimesList.add(prayerTimes[5]);
		prayerTimesList.add(prayerTimes[6]);

		Date dat = new Date();
		Calendar now = Calendar.getInstance();
		now.setTime(dat);

		long min = Long.MAX_VALUE;
		int nextPrayer = 12;
		String prayerName = "NA";
		long[] PrayerTimesInMilliseconds = new long[6];

		for (int i = 0; i < prayerTimesList.size(); i++) {
			if (prayerTimesList.get(i).equalsIgnoreCase(prayers.getInvalidTime())) {
				return null;
			} else {
				Calendar prayer_time = Calendar.getInstance();
				prayer_time.set(Calendar.HOUR_OF_DAY, Integer.parseInt(prayerTimesList.get(i).split(":", 3)[0]));
				prayer_time.set(Calendar.MINUTE, Integer.parseInt(prayerTimesList.get(i).split(":", 3)[1]));
				prayer_time.set(Calendar.SECOND, 0);
				if (prayer_time.before(now)) {
					prayer_time.add(Calendar.HOUR, 24);
				}
				PrayerTimesInMilliseconds[i] = prayer_time.getTimeInMillis() - now.getTimeInMillis();
				if (PrayerTimesInMilliseconds[i] < min) {
					min = PrayerTimesInMilliseconds[i];
					nextPrayer = i;
				}
			}
		}

		switch (nextPrayer) {
			case 0:
				prayerName = getResources().getString(R.string.prayer1);
				break;
			case 1:
				prayerName = getResources().getString(R.string.sunrise);
				break;
			case 2:
				prayerName = getResources().getString(R.string.prayer2);
				break;
			case 3:
				prayerName = getResources().getString(R.string.prayer3);
				break;
			case 4:
				prayerName = getResources().getString(R.string.prayer4);
				break;
			case 5:
				prayerName = getResources().getString(R.string.prayer5);
				break;
			default:
				prayerName = "NA";
		}

		min = min / 1000;
		int hours = (int) Math.floor(((double) min) / 3600);
		long minutes = (min - hours * 3600) / 60;

		String hoursText = "";
		String minutesText = "";

		if (hours == 1) {
			hoursText = hours + " " + getResources().getString(R.string.hour) + " ";
		} else if (hours > 1) {
			hoursText = hours + " " + getResources().getString(R.string.hours) + " ";
		} else {
			hoursText = "";
		}

		minutesText = minutes + " " + getResources().getString(R.string.minute);

		if (hours == 0 && minutes <= 1) {
            long seconds = (min % 60) + 1;
            String secondsText = seconds + " " + getResources().getString(R.string.second);
            return secondsText + " " + getResources().getString(R.string.until) + " " + prayerName;
        }
        return hoursText + minutesText + " " + getResources().getString(R.string.until) + " " + prayerName;
	}
	private interface FireAction { void run(); }

	private void fireIfMissed(String prefKey, Calendar targetTime, Calendar now, long graceMs, FireAction action) {
		if (!now.after(targetTime)) return;
		long lateBy = now.getTimeInMillis() - targetTime.getTimeInMillis();
		// ✅ منديش المنبه الحقيقي (اللي بيشتغل في المعاد بالظبط عن طريق AlarmManager) فرصة
		// كافية إنه يشتغل الأول - الحارس (Watchdog) ده احتياطي بس لو المنبه الحقيقي "اتمنع"
		// فعلاً (الجهاز كان مقفول تمامًا مثلاً)، مش لأي تأخير عادي بسيط في أول دقيقة أو اتنين
		long MIN_LATE_MS = 3 * 60 * 1000L;
		if (lateBy < MIN_LATE_MS) return;
		if (lateBy > graceMs) return;

		long lastAttempt = sharedPrefs.getLong(prefKey, 0);
		if (now.getTimeInMillis() - lastAttempt < 5 * 60 * 1000L) return;
		sharedPrefs.edit().putLong(prefKey, now.getTimeInMillis()).commit();

		action.run();
	}

	private void checkMissedPrayerEvents() {
		try {
			if (mContext == null) return;
			String[] prayerKeys    = {"fajr", "dhuhr", "asr", "maghrib", "isha"};
			String[] reminderPrefs = {"isFajrReminder", "isDuhrReminder", "isAsrReminder", "isMaghribReminder", "isIshaaReminder"};
			int[] prayerPositions  = {0, 2, 3, 5, 6};
			String[] athanDataTypes = {
					MainActivity.DATA_TYPE_ATHAN1, MainActivity.DATA_TYPE_ATHAN2,
					MainActivity.DATA_TYPE_ATHAN3, MainActivity.DATA_TYPE_ATHAN4,
					MainActivity.DATA_TYPE_ATHAN5
			};

			PrayTime prayers = PrayTime.instancePrayTime(mContext);
			prayers.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
			String[] prayerTimes = prayers.getPrayerTimes(mContext);
			if (prayerTimes == null) return;

			Calendar now = Calendar.getInstance();
			long GRACE_MS = 10 * 60 * 1000L;

			for (int i = 0; i < prayerKeys.length; i++) {
				String key = prayerKeys[i];
				if (prayerTimes[prayerPositions[i]].equalsIgnoreCase(prayers.getInvalidTime())) continue;
				if (!sharedPrefs.getBoolean(reminderPrefs[i], true)) continue;

				String[] hm = prayerTimes[prayerPositions[i]].split(":", 3);
				Calendar prayerCal = Calendar.getInstance();
				prayerCal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(hm[0]));
				prayerCal.set(Calendar.MINUTE, Integer.parseInt(hm[1]));
				prayerCal.set(Calendar.SECOND, 0);

				final String fKey = key;
				final int fIndex = i;

				// اقتراب الصلاة
				if (sharedPrefs.getBoolean("isPreAthanReminder_" + key, true)) {
					int preMin;
					try { preMin = Integer.parseInt(sharedPrefs.getString("preAthanMinutes_" + key, "15")); } catch (Exception e) { preMin = 15; }
					Calendar preCal = (Calendar) prayerCal.clone();
					preCal.add(Calendar.MINUTE, -preMin);
					fireIfMissed("watchdog_gate_pre_" + key, preCal, now, GRACE_MS, () -> {
						Intent i2 = new Intent(mContext, ThikrAlarmReceiver.class);
						i2.putExtra("com.alaaeltaweel.thikrallah.datatype", MyAlarmsManager.DATA_TYPE_PRE_ATHAN + "_" + fKey);
						i2.putExtra("prayer_name", fKey);
						mContext.sendBroadcast(i2);
						Timber.tag(TAG).d("Watchdog fired pre-athan for " + fKey);
					});
				}

				// الأذان
				fireIfMissed("watchdog_gate_athan_" + key, prayerCal, now, GRACE_MS, () -> {
					Intent i2 = new Intent(mContext, ThikrAlarmReceiver.class);
					i2.putExtra("com.alaaeltaweel.thikrallah.datatype", athanDataTypes[fIndex]);
					mContext.sendBroadcast(i2);
					Timber.tag(TAG).d("Watchdog fired athan for " + fKey);
				});

				// الإقامة
				int iqamaMin;
				try { iqamaMin = Integer.parseInt(sharedPrefs.getString("iqamaMinutes_" + key, "10")); } catch (Exception e) { iqamaMin = 10; }
				int iqamaSound = sharedPrefs.getInt("iqamaSoundChoice_" + key, 1);
				Calendar iqamaCal = (Calendar) prayerCal.clone();
				iqamaCal.add(Calendar.MINUTE, iqamaMin);
				final int fSound = iqamaSound;
				fireIfMissed("watchdog_gate_iqama_" + key, iqamaCal, now, GRACE_MS, () -> {
					Intent i2 = new Intent(mContext, ThikrAlarmReceiver.class);
					i2.putExtra("com.alaaeltaweel.thikrallah.datatype", "iqama");
					i2.putExtra("prayer_name", fKey);
					i2.putExtra("iqama_sound", fSound);
					mContext.sendBroadcast(i2);
					Timber.tag(TAG).d("Watchdog fired iqama for " + fKey);
				});
			}
		} catch (Exception e) {
			Timber.tag(TAG).e(e, "checkMissedPrayerEvents error");
		}
	}
	private void checkMissedGeneralThikr() {
		try {
			if (mContext == null) return;
			boolean remindThroughDay = sharedPrefs.getBoolean("RemindmeThroughTheDay", true);
			if (!remindThroughDay) return;

			int intervalMinutes;
			try { intervalMinutes = Integer.parseInt(sharedPrefs.getString("RemindMeEvery", "60")); } catch (Exception e) { intervalMinutes = 60; }
			if (intervalMinutes < 1) intervalMinutes = 60;

			long lastFired = sharedPrefs.getLong("last_general_thikr_time", 0);
			if (lastFired == 0) return;

			long nowMs = System.currentTimeMillis();
			long intervalMs = intervalMinutes * 60 * 1000L;
			long graceMs = 5 * 60 * 1000L;

			if ((nowMs - lastFired) > (intervalMs + graceMs)) {
				sharedPrefs.edit().putLong("last_general_thikr_time", nowMs).commit();
				Intent i2 = new Intent(mContext, ThikrAlarmReceiver.class);
				i2.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_GENERAL_THIKR);
				mContext.sendBroadcast(i2);
				Timber.tag(TAG).d("Watchdog fired general thikr (missed interval)");
			}
		} catch (Exception e) {
			Timber.tag(TAG).e(e, "checkMissedGeneralThikr error");
		}
	}
	}
