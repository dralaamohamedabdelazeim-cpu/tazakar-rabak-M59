package com.alaaeltaweel.thikrallah.Notification;

import static android.content.Context.ALARM_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.PrayTime;

import java.util.Calendar;
import java.util.Date;

public class MyAlarmsManager {
    String TAG = "MyAlarmsManager";
    public static final int requestCodeMorningAlarm = 8;
    public static final int requestCodeMulkAlarm = 26;
    public static final int requestCodeNightAlarm = 20;
    public static final int requestCodeRandomAlarm = 1;
    public static final int requestCodeKahfAlarm = 25;
    public static final int requestCodeAthan1 = 100;
    public static final int requestCodeAthan2 = 101;
    public static final int requestCodeAthan3 = 102;
    public static final int requestCodeAthan4 = 103;
    public static final int requestCodeAthan5 = 104;

    // ✅ request codes للتنبيه قبل الصلاة بـ 15 دقيقة
    public static final int requestCodePreAthan1 = 200;
    public static final int requestCodePreAthan2 = 201;
    public static final int requestCodePreAthan3 = 202;
    public static final int requestCodePreAthan4 = 203;
    public static final int requestCodePreAthan5 = 204;
    
   // ✅ الوضع الصامت أثناء الصلاة
    public static final int requestCodeSilentOn1 = 400;
    public static final int requestCodeSilentOn2 = 401;
    public static final int requestCodeSilentOn3 = 402;
    public static final int requestCodeSilentOn4 = 403;
    public static final int requestCodeSilentOn5 = 404;
    public static final int requestCodeSilentOff1 = 410;
    public static final int requestCodeSilentOff2 = 411;
    public static final int requestCodeSilentOff3 = 412;
    public static final int requestCodeSilentOff4 = 413;
    public static final int requestCodeSilentOff5 = 414;
     // ✅ الإقامة
    public static final int requestCodeIqama1 = 500;
    public static final int requestCodeIqama2 = 501;
    public static final int requestCodeIqama3 = 502;
    public static final int requestCodeIqama4 = 503;
    public static final int requestCodeIqama5 = 504;
   
    // ✅ رمضان
    public static final int requestCodeCannon = 300;
    public static final int requestCodeMesaharaty = 301;
    
    // ✅ الـ datatype للتنبيه قبل الصلاة
    public static final String DATA_TYPE_PRE_ATHAN = "pre_athan";

    boolean isPermissionRequested = false;
    AlarmManager alarmMgr;
    Context context;
    private SharedPreferences sharedPrefs;

    public MyAlarmsManager(Context icontext) {
        context = icontext;
    }

    public void UpdateAllApplicableAlarms() {
        if (context == null) {
            return;
        }
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Long timestamp = Calendar.getInstance().getTimeInMillis();
        Long diff = timestamp - sharedPrefs.getLong("lastAlarmsUpdate", 0);
        if (diff < 3000) {
            Log.d(TAG, "last AlarmsUpdate less than 5 second" + diff);
            return;
        }
        sharedPrefs.edit().putLong("lastAlarmsUpdate", timestamp).commit();
        alarmMgr = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        Log.d("MyAlarmsManager", "UpdateAllApplicableAlarms called");
        setPeriodicAlarmManagerUpdates(alarmMgr);
        String[] MorningReminderTime = sharedPrefs.getString("daytReminderTime", "8:00").split(":", 3);
        String[] NightReminderTime = sharedPrefs.getString("nightReminderTime", "20:00").split(":", 3);
        String[] kahfReminderTime = sharedPrefs.getString("kahfReminderTime", "10:00").split(":", 3);
        String[] mulkReminderTime = sharedPrefs.getString("mulkReminderTime", "10:00").split(":", 3);
        String RandomReminderInterval = sharedPrefs.getString("RemindMeEvery", "60");
        boolean remindMeMorningThikr = sharedPrefs.getBoolean("remindMeMorningThikr", true);
        boolean remindMeNightThikr = sharedPrefs.getBoolean("remindMeNightThikr", true);
        boolean RemindmeThroughTheDay = sharedPrefs.getBoolean("RemindmeThroughTheDay", true);
        boolean Remindmekahf = sharedPrefs.getBoolean("remindMekahf", true);
        boolean Remindmemulk = sharedPrefs.getBoolean("remindMemulk", true);

        Intent launchIntent = new Intent("com.alaaeltaweel.thikrallah.Notification.ThikrAlarmReceiver");
        launchIntent.setClass(context, ThikrAlarmReceiver.class);

        Date dat = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(dat);

        // Mulk Reminder
        PendingIntent pendingIntentMulk = PendingIntent.getBroadcast(context, requestCodeMulkAlarm, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_QURAN_MULK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Remindmemulk) {
            Calendar calendar0 = Calendar.getInstance();
            calendar0.set(Calendar.HOUR_OF_DAY, Integer.parseInt(mulkReminderTime[0]));
            calendar0.set(Calendar.MINUTE, Integer.parseInt(mulkReminderTime[1]));
            calendar0.set(Calendar.SECOND, 0);
            if (calendar0.after(now)) {
                setAlarm(calendar0, pendingIntentMulk);
            } else {
                calendar0.add(Calendar.HOUR, 24);
                setAlarm(calendar0, pendingIntentMulk);
            }
        } else {
            alarmMgr.cancel(pendingIntentMulk);
        }

        // Morning Reminder
        PendingIntent pendingIntentMorningThikr = PendingIntent.getBroadcast(context, requestCodeMorningAlarm, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_DAY_THIKR), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (remindMeMorningThikr) {
            Calendar calendar0 = Calendar.getInstance();
            calendar0.set(Calendar.HOUR_OF_DAY, Integer.parseInt(MorningReminderTime[0]));
            calendar0.set(Calendar.MINUTE, Integer.parseInt(MorningReminderTime[1]));
            calendar0.set(Calendar.SECOND, 0);
            if (calendar0.after(now)) {
                setAlarm(calendar0, pendingIntentMorningThikr);
            } else {
                calendar0.add(Calendar.HOUR, 24);
                setAlarm(calendar0, pendingIntentMorningThikr);
            }
        } else {
            alarmMgr.cancel(pendingIntentMorningThikr);
        }

        // Night Reminder
        PendingIntent pendingIntentNightThikr = PendingIntent.getBroadcast(context, requestCodeNightAlarm, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_NIGHT_THIKR), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (remindMeNightThikr) {
            Calendar calendar1 = Calendar.getInstance();
            calendar1.set(Calendar.HOUR_OF_DAY, Integer.parseInt(NightReminderTime[0]));
            calendar1.set(Calendar.MINUTE, Integer.parseInt(NightReminderTime[1]));
            calendar1.set(Calendar.SECOND, 0);
            if (calendar1.after(now)) {
                setAlarm(calendar1, pendingIntentNightThikr);
            } else {
                calendar1.add(Calendar.HOUR, 24);
                setAlarm(calendar1, pendingIntentNightThikr);
            }
        } else {
            alarmMgr.cancel(pendingIntentNightThikr);
        }

        // Random Reminder
        PendingIntent pendingIntentGeneral = PendingIntent.getBroadcast(context, requestCodeRandomAlarm, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_GENERAL_THIKR), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (RemindmeThroughTheDay) {
            long storedNextTime = sharedPrefs.getLong("next_general_thikr_scheduled_time", 0);
            String storedInterval = sharedPrefs.getString("next_general_thikr_scheduled_interval", "");
            boolean intervalChanged = !RandomReminderInterval.equals(storedInterval);

            if (storedNextTime > now.getTimeInMillis() && !intervalChanged) {
                Log.d("MyAlarmsManager", "General thikr already scheduled, skipping reschedule");
            } else {
                alarmMgr.cancel(pendingIntentGeneral);
                Calendar calendar1 = Calendar.getInstance();
             calendar1.setTime(dat);
             calendar1.add(Calendar.MINUTE, Integer.parseInt(RandomReminderInterval));
            
                // ✅ لو دلوقتي (وقت الحساب نفسه) واقع جوه فترة الراحة، اقفز لآخرها فورًا
                boolean quietTimeChoice = sharedPrefs.getBoolean("quiet_time_choice", true);
                if (quietTimeChoice) {
                    String[] qStart = sharedPrefs.getString("quiet_time_start", "22:00").split(":", 2);
                    String[] qEnd = sharedPrefs.getString("quiet_time_end", "22:00").split(":", 2);
                    int quietStartMin = Integer.parseInt(qStart[0]) * 60 + Integer.parseInt(qStart[1]);
                    int quietEndMin = Integer.parseInt(qEnd[0]) * 60 + Integer.parseInt(qEnd[1]);
                    int nowMin = calendar1.get(Calendar.HOUR_OF_DAY) * 60 + calendar1.get(Calendar.MINUTE);

                    boolean nowIsWithinQuiet;
                    if (quietStartMin > quietEndMin) {
                        // الفترة بتعدي منتصف الليل، زي 22:00 -> 06:00
                        nowIsWithinQuiet = (nowMin >= quietStartMin) || (nowMin < quietEndMin);
                    } else {
                        // فترة في نفس اليوم، زي 2:00 -> 10:00
                        nowIsWithinQuiet = (nowMin >= quietStartMin) && (nowMin < quietEndMin);
                    }

                    if (nowIsWithinQuiet) {
                        boolean crossesMidnight = quietStartMin > quietEndMin && nowMin >= quietStartMin;
                        calendar1 = Calendar.getInstance();
                        calendar1.set(Calendar.HOUR_OF_DAY, Integer.parseInt(qEnd[0]));
                        calendar1.set(Calendar.MINUTE, Integer.parseInt(qEnd[1]));
                        calendar1.set(Calendar.SECOND, 0);
                        if (crossesMidnight) {
                            calendar1.add(Calendar.DATE, 1);
                        }
                    }
                }
                
                this.setAlarm(calendar1, pendingIntentGeneral);
                sharedPrefs.edit()
                        .putLong("next_general_thikr_scheduled_time", calendar1.getTimeInMillis())
                        .putString("next_general_thikr_scheduled_interval", RandomReminderInterval)
                        .apply();
            }
        } else {
            alarmMgr.cancel(pendingIntentGeneral);
            sharedPrefs.edit().remove("next_general_thikr_scheduled_time").apply();
        }

        // Kahf Reminder
        PendingIntent pendingIntentKahf = PendingIntent.getBroadcast(context, requestCodeKahfAlarm, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", MainActivity.DATA_TYPE_QURAN_KAHF), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Remindmekahf && Calendar.getInstance().get(Calendar.DAY_OF_MONTH) != sharedPrefs.getInt("lastKahfPlayed", -1)) {
            alarmMgr.cancel(pendingIntentKahf);
            Calendar calendar1 = Calendar.getInstance();
            calendar1.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
            calendar1.set(Calendar.HOUR_OF_DAY, Integer.parseInt(kahfReminderTime[0]));
            calendar1.set(Calendar.MINUTE, Integer.parseInt(kahfReminderTime[1]));
            calendar1.set(Calendar.SECOND, 0);
            if (calendar1.after(now)) {
                setAlarm(calendar1, pendingIntentKahf);
            } else {
                calendar1.add(Calendar.HOUR, 24 * 7);
                setAlarm(calendar1, pendingIntentKahf);
            }
        } else {
            alarmMgr.cancel(pendingIntentKahf);
        }

        updateAllPrayerAlarms();

         // ✅ جدولة المدفع والمسحراتي في رمضان
        updateRamadanAlarms();
    }

    // ===================== ✅ رمضان: مدفع ومسحراتي =====================
    private void updateRamadanAlarms() {
        if (context == null || alarmMgr == null) return;

        // تحقق إن دلوقتي رمضان
        android.icu.util.IslamicCalendar islamicCalendar = new android.icu.util.IslamicCalendar();
        int hijriMonth = islamicCalendar.get(android.icu.util.Calendar.MONTH);
        boolean isRamadan = (hijriMonth == 8);

        Intent cannonIntent = new Intent(context, RamadanAlarmReceiver.class);
        cannonIntent.setAction(RamadanSoundService.ACTION_CANNON);
        PendingIntent pendingCannon = PendingIntent.getBroadcast(context, requestCodeCannon,
                cannonIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent mesaharatyIntent = new Intent(context, RamadanAlarmReceiver.class);
        mesaharatyIntent.setAction(RamadanSoundService.ACTION_MESAHARATY);
        PendingIntent pendingMesaharaty = PendingIntent.getBroadcast(context, requestCodeMesaharaty,
                mesaharatyIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmMgr.cancel(pendingCannon);
        alarmMgr.cancel(pendingMesaharaty);

        if (!isRamadan) {
            Log.d(TAG, "Not Ramadan, Ramadan alarms cancelled");
            return;
        }

        boolean cannonEnabled = sharedPrefs.getBoolean("ramadan_cannon_enabled", true);
        boolean mesaharatyEnabled = sharedPrefs.getBoolean("ramadan_mesaharaty_enabled", true);

        Date dat = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(dat);

        // ✅ المدفع — وقت المغرب
        if (cannonEnabled) {
            PrayTime prayers = PrayTime.instancePrayTime(context);
            String[] times = prayers.getPrayerTimes(context);
            if (times != null && times.length >= 5) {
                try {
                    String[] maghribTime = times[5].split(":", 3);
                    Calendar cannonCal = Calendar.getInstance();
                    cannonCal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(maghribTime[0]));
                    cannonCal.set(Calendar.MINUTE, Integer.parseInt(maghribTime[1]));
                    cannonCal.add(Calendar.SECOND, -30);
                    if (!cannonCal.after(now)) {
                        cannonCal.add(Calendar.HOUR, 24);
                    }
                    setAlarm(cannonCal, pendingCannon);
                    Log.d(TAG, "Cannon alarm set at: " + cannonCal.getTime());
                } catch (Exception e) {
                    Log.e(TAG, "Error setting cannon alarm: " + e.getMessage());
                }
            }
        }

        // ✅ المسحراتي — الوقت اللي يحدده المستخدم
        if (mesaharatyEnabled) {
            String mesaharatyTimeStr = sharedPrefs.getString("mesaharaty_time", "03:00");
            try {
                String[] mesaharatyTimeParts = mesaharatyTimeStr.split(":", 3);
                Calendar mesaharatyCal = Calendar.getInstance();
                mesaharatyCal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(mesaharatyTimeParts[0]));
                mesaharatyCal.set(Calendar.MINUTE, Integer.parseInt(mesaharatyTimeParts[1]));
                mesaharatyCal.set(Calendar.SECOND, 0);
                if (!mesaharatyCal.after(now)) {
                    mesaharatyCal.add(Calendar.HOUR, 24);
                }
                setAlarm(mesaharatyCal, pendingMesaharaty);
                Log.d(TAG, "Mesaharaty alarm set at: " + mesaharatyCal.getTime());
            } catch (Exception e) {
                Log.e(TAG, "Error setting mesaharaty alarm: " + e.getMessage());
            }
        }
    }
    
    @SuppressLint("NewApi")
    private void setAlarm(Calendar time, PendingIntent pendingIntent) {
        Long timeInMilliseconds = getFutureTimeIfTimeInPast(time.getTimeInMillis());
        Date dat = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(dat);
        Log.d("MyAlarmsManager", "setting alarm. is after?" + time.after(now) + " now is " + now.getTime() + " alarm is " + time.getTime());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            alarmMgr.set(AlarmManager.RTC_WAKEUP, timeInMilliseconds, pendingIntent);
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timeInMilliseconds, pendingIntent);
        } else {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                setAlarmClockHighPriority(timeInMilliseconds, pendingIntent);
            } else {
                if (alarmMgr.canScheduleExactAlarms()) {
                    setAlarmClockHighPriority(timeInMilliseconds, pendingIntent);
                } else {
                    requestExactAlarmPermission();
                }
            }
        }
    }
    private void setSilentAlarm(Calendar time, PendingIntent pendingIntent) {
    long timeInMs = time.getTimeInMillis();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMs, pendingIntent);
    } else {
        alarmMgr.setExact(AlarmManager.RTC_WAKEUP, timeInMs, pendingIntent);
    }
    }
@SuppressLint("NewApi")
private void setAlarmClockHighPriority(long timeInMilliseconds, PendingIntent operationIntent) {
    Intent showIntent = new Intent(context, MainActivity.class);
    showIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent showPendingIntent = PendingIntent.getActivity(context, 0,
            showIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

    AlarmManager.AlarmClockInfo alarmClockInfo =
            new AlarmManager.AlarmClockInfo(timeInMilliseconds, showPendingIntent);
    alarmMgr.setAlarmClock(alarmClockInfo, operationIntent);
}
    private boolean requestExactAlarmPermission() {
        Log.d(TAG, "requestExactAlarmPermission");
        if (!(context instanceof Activity)) {
            return false;
        } else {
            AlarmManager alarmManager = (AlarmManager) this.context.getSystemService(ALARM_SERVICE);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true;
            } else {
                if (alarmManager.canScheduleExactAlarms()) {
                    return true;
                } else {
                    if (isPermissionRequested == true) {
                        return false;
                    } else {
                        isPermissionRequested = true;
                        AlertDialog.Builder builder = new AlertDialog.Builder(this.context);
                        builder.setTitle(this.context.getResources().getString(R.string.exact_alarm_title))
                                .setMessage(this.context.getResources().getString(R.string.exact_alarm_message))
                                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        Intent intent = new Intent();
                                        intent.setAction(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                                        intent.setData(Uri.parse("package:" + context.getPackageName()));
                                        context.startActivity(intent);
                                    }
                                })
                                .setCancelable(false)
                                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                    }
                                })
                                .create().show();
                    }
                }
                return false;
            }
        }
    }

    void setPeriodicAlarmManagerUpdates(AlarmManager alarmmnager) {
        if (context == null) {
            return;
        }
        Intent launchIntent = new Intent(context, ThikrBootReceiver.class);
        launchIntent.setAction("com.alaaeltaweel.thikrallah.Notification.ThikrBootReceiver.android.action.broadcast");
        Date dat = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(dat);

        PendingIntent intent = PendingIntent.getBroadcast(context, 100, launchIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar1 = Calendar.getInstance();
        calendar1.set(Calendar.HOUR_OF_DAY, 1);
        calendar1.set(Calendar.MINUTE, 15);
        calendar1.set(Calendar.SECOND, 0);

        if (calendar1.after(now)) {
            alarmmnager.setRepeating(AlarmManager.RTC_WAKEUP, calendar1.getTimeInMillis(), 12 * 60 * 60 * 1000, intent);
        } else {
            calendar1.add(Calendar.HOUR, 24);
            alarmmnager.setRepeating(AlarmManager.RTC_WAKEUP, calendar1.getTimeInMillis(), 12 * 60 * 60 * 1000, intent);
        }
        // ✅ Watchdog — يعيد تشغيل AthanTimerService لو مات
        Intent watchdogIntent = new Intent(context, ThikrBootReceiver.class);
        watchdogIntent.putExtra("isWatchdog", true);
        PendingIntent watchdogPending = PendingIntent.getBroadcast(
            context, 9999, watchdogIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmmnager.setRepeating(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + (60 * 60 * 1000),
            60 * 60 * 1000, watchdogPending);
    }

    private Long getFutureTimeIfTimeInPast(Long time) {
        Long remainingTime = time - System.currentTimeMillis();
        if (remainingTime < 0) {
            return time + 24 * 60 * 60 * 1000;
        } else {
            return time;
        }
    }

    private void updateAllPrayerAlarms() {
        if (context == null) {
            return;
        }
        double latitude = Double.parseDouble(MainActivity.getLatitude(context));
        double longitude = Double.parseDouble(MainActivity.getLongitude(context));
        if (latitude == 0 && longitude == 0) {
            return;
        }
        updatePrayerAlarms(requestCodeAthan1, requestCodePreAthan1, requestCodeSilentOn1, requestCodeSilentOff1, requestCodeIqama1, "isFajrReminder", 0, MainActivity.DATA_TYPE_ATHAN1, "fajr");
        updatePrayerAlarms(requestCodeAthan2, requestCodePreAthan2, requestCodeSilentOn2, requestCodeSilentOff2, requestCodeIqama2, "isDuhrReminder", 2, MainActivity.DATA_TYPE_ATHAN2, "dhuhr");
        updatePrayerAlarms(requestCodeAthan3, requestCodePreAthan3, requestCodeSilentOn3, requestCodeSilentOff3, requestCodeIqama3, "isAsrReminder", 3, MainActivity.DATA_TYPE_ATHAN3, "asr");
        updatePrayerAlarms(requestCodeAthan4, requestCodePreAthan4, requestCodeSilentOn4, requestCodeSilentOff4, requestCodeIqama4, "isMaghribReminder", 5, MainActivity.DATA_TYPE_ATHAN4, "maghrib");
        updatePrayerAlarms(requestCodeAthan5, requestCodePreAthan5, requestCodeSilentOn5, requestCodeSilentOff5, requestCodeIqama5, "isIshaaReminder", 6, MainActivity.DATA_TYPE_ATHAN5, "isha");
    }
    private void updatePrayerAlarms(int requestCode, int preRequestCode, int silentOnCode, int silentOffCode, int iqamaCode, String isReminderPreference, int prayerPosition, String datatype, String prayerName) {
        if (context == null) {
            return;
        }
        PrayTime prayers = PrayTime.instancePrayTime(context);
        prayers.setTimeFormat(PrayTime.TIME_FORMAT_Time24);
        String[] prayerTimes = prayers.getPrayerTimes(context);

        if (prayerTimes[prayerPosition].equalsIgnoreCase(prayers.getInvalidTime())) {
            return;
        }
        boolean isAthanReminder = sharedPrefs.getBoolean(isReminderPreference, true);
        boolean isPreAthanReminder = sharedPrefs.getBoolean("isPreAthanReminder_" + prayerName, true);

        Intent launchIntent = new Intent(context, ThikrAlarmReceiver.class);

        Date dat = new Date();
        Calendar now = Calendar.getInstance();
        now.setTime(dat);

        // ✅ الأذان
        PendingIntent pendingIntentAthan = PendingIntent.getBroadcast(context, requestCode, launchIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", datatype), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmMgr.cancel(pendingIntentAthan);
        if (isAthanReminder) {
            Calendar calendar0 = Calendar.getInstance();
            calendar0.set(Calendar.HOUR_OF_DAY, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[0]));
            calendar0.set(Calendar.MINUTE, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[1]));
            calendar0.set(Calendar.SECOND, 0);
            if (calendar0.after(now)) {
                setAlarm(calendar0, pendingIntentAthan);
            } else {
                calendar0.add(Calendar.HOUR, 24);
                setAlarm(calendar0, pendingIntentAthan);
            }
        }

        // ✅ تنبيه قبل الصلاة بـ 15 دقيقة
        Intent preAthanIntent = new Intent(context, ThikrAlarmReceiver.class);
        preAthanIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", DATA_TYPE_PRE_ATHAN + "_" + prayerName);
        preAthanIntent.putExtra("prayer_name", prayerName);

        PendingIntent pendingIntentPreAthan = PendingIntent.getBroadcast(context, preRequestCode, preAthanIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmMgr.cancel(pendingIntentPreAthan);

        if (isAthanReminder && isPreAthanReminder) {
            Calendar calendarPre = Calendar.getInstance();
            calendarPre.set(Calendar.HOUR_OF_DAY, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[0]));
            calendarPre.set(Calendar.MINUTE, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[1]));
            calendarPre.set(Calendar.SECOND, 0);
            int preAthanMinutes;
try {
    preAthanMinutes = Integer.parseInt(sharedPrefs.getString("preAthanMinutes_" + prayerName, "15"));
    if (preAthanMinutes < 1) preAthanMinutes = 15;
} catch (NumberFormatException e) {
    preAthanMinutes = 15;
}
calendarPre.add(Calendar.MINUTE, -preAthanMinutes);

            if (calendarPre.after(now)) {
                setAlarm(calendarPre, pendingIntentPreAthan);
                Log.d(TAG, "pre-athan reminder set for " + prayerName + " at " + calendarPre.getTime());
            } else {
                calendarPre.add(Calendar.HOUR, 24);
                setAlarm(calendarPre, pendingIntentPreAthan);
            }
        }
        // ✅ الوضع الصامت أثناء الصلاة
        boolean isSilentModeEnabled = sharedPrefs.getBoolean("isSilentModeDuringPrayer", true);
        
        int silentDurationMinutes;
try {
    silentDurationMinutes = Integer.parseInt(sharedPrefs.getString("silentModeDurationMinutes", "15"));
    if (silentDurationMinutes < 1) silentDurationMinutes = 15;
} catch (NumberFormatException e) {
    silentDurationMinutes = 15;
}

        
Intent silentOnIntent = new Intent(context, SilentModeReceiver.class);
silentOnIntent.setAction(SilentModeReceiver.ACTION_SILENT_ON);
PendingIntent pendingSilentOn = PendingIntent.getBroadcast(context, silentOnCode, silentOnIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

Intent silentOffIntent = new Intent(context, SilentModeReceiver.class);
silentOffIntent.setAction(SilentModeReceiver.ACTION_SILENT_OFF);
PendingIntent pendingSilentOff = PendingIntent.getBroadcast(context, silentOffCode, silentOffIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

if (isAthanReminder && isSilentModeEnabled) {
    int iqamaMinutesForSilent;
    try {
        iqamaMinutesForSilent = Integer.parseInt(sharedPrefs.getString("iqamaMinutes_" + prayerName, "10"));
        if (iqamaMinutesForSilent < 1) iqamaMinutesForSilent = 10;
    } catch (NumberFormatException e) {
        iqamaMinutesForSilent = 10;
    }

    Calendar calendarSilentOnToday = Calendar.getInstance();
    calendarSilentOnToday.set(Calendar.HOUR_OF_DAY, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[0]));
    calendarSilentOnToday.set(Calendar.MINUTE, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[1]));
    calendarSilentOnToday.set(Calendar.SECOND, 0);
    calendarSilentOnToday.add(Calendar.MINUTE, iqamaMinutesForSilent);
    calendarSilentOnToday.add(Calendar.SECOND, 60); // ✅ هامش بسيط عشان صوت الإقامة ياخد فرصته الأول قبل ما الصمت يتفعل

    Calendar calendarSilentOffToday = (Calendar) calendarSilentOnToday.clone();
    calendarSilentOffToday.add(Calendar.MINUTE, silentDurationMinutes);

    if (now.after(calendarSilentOnToday) && now.before(calendarSilentOffToday)) {
        Log.d(TAG, "Inside active silent window for " + prayerName + ", keeping existing OFF alarm");
    } else {
        alarmMgr.cancel(pendingSilentOn);
        alarmMgr.cancel(pendingSilentOff);

        Calendar calendarSilentOn = (Calendar) calendarSilentOnToday.clone();
        if (!calendarSilentOn.after(now)) {
            calendarSilentOn.add(Calendar.DAY_OF_YEAR, 1);
        }
        setAlarm(calendarSilentOn, pendingSilentOn);

        Calendar calendarSilentOff = (Calendar) calendarSilentOn.clone();
        calendarSilentOff.add(Calendar.MINUTE, silentDurationMinutes);
        setAlarm(calendarSilentOff, pendingSilentOff);

        Log.d(TAG, "Silent window for " + prayerName + ": " + calendarSilentOn.getTime() + " -> " + calendarSilentOff.getTime());
    }
} else {
    alarmMgr.cancel(pendingSilentOn);
    alarmMgr.cancel(pendingSilentOff);
}
        // ✅ الإقامة
        boolean isIqamaEnabled = sharedPrefs.getBoolean("isIqamaReminder_" + prayerName, false);
        int iqamaSound = sharedPrefs.getInt("iqamaSoundChoice_" + prayerName, 1);

        Intent iqamaIntent = new Intent(context, ThikrAlarmReceiver.class);
        iqamaIntent.putExtra("com.alaaeltaweel.thikrallah.datatype", "iqama");
        iqamaIntent.putExtra("prayer_name", prayerName);
        iqamaIntent.putExtra("iqama_sound", iqamaSound);
        PendingIntent pendingIqama = PendingIntent.getBroadcast(context, iqamaCode, iqamaIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmMgr.cancel(pendingIqama);

        if (isAthanReminder && isIqamaEnabled) {
            int iqamaMinutes;
            try {
                iqamaMinutes = Integer.parseInt(sharedPrefs.getString("iqamaMinutes_" + prayerName, "10"));
                if (iqamaMinutes < 1) iqamaMinutes = 10;
            } catch (NumberFormatException e) {
                iqamaMinutes = 10;
            }
            Calendar calendarIqama = Calendar.getInstance();
            calendarIqama.set(Calendar.HOUR_OF_DAY, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[0]));
            calendarIqama.set(Calendar.MINUTE, Integer.parseInt(prayerTimes[prayerPosition].split(":", 3)[1]));
            calendarIqama.set(Calendar.SECOND, 0);
            calendarIqama.add(Calendar.MINUTE, iqamaMinutes);
            if (!calendarIqama.after(now)) calendarIqama.add(Calendar.HOUR, 24);
            setAlarm(calendarIqama, pendingIqama);
            }
    }
}
