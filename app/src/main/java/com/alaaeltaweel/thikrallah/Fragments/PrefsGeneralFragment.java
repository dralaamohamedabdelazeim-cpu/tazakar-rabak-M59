package com.alaaeltaweel.thikrallah.Fragments;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.alaaeltaweel.thikrallah.MainActivity;
import com.alaaeltaweel.thikrallah.Notification.MyAlarmsManager;
import com.alaaeltaweel.thikrallah.R;
import com.alaaeltaweel.thikrallah.Utilities.TimePreference;

import timber.log.Timber;

public class PrefsGeneralFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
    Context mContext;

    // ✅ نفس قائمة شاشات الشركات الموجودة في MainActivity - نسخة محلية عشان الزرار يشتغل
    // بغض النظر عن نوع الـ Activity المضيفة لشاشة الإعدادات
    private static final Intent[] POWERMANAGER_INTENTS = {
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            new Intent().setComponent(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity")),
            new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.ui.activity.PowerSecondActivity")),
            new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.itel.autobootmanager.ui.AutoBootMgrActivity")),
            new Intent().setComponent(new ComponentName("com.transsion.phonemanager", "com.transsion.phonemanager.ui.activity.AutoBootManagerActivity"))};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_general);
        initSummary(getPreferenceScreen());
        mContext = this.getContext();

        // ✅ زرار "تحسين إعدادات الجهاز" - شغال بنفسه من غير ما يعتمد على نوع الـ Activity المضيفة
        Preference deviceOptimizationPref = findPreference("device_optimization_button");
        if (deviceOptimizationPref != null) {
            deviceOptimizationPref.setOnPreferenceClickListener(preference -> {
                showDeviceOptimizationDialog();
                return true;
            });
        }
    }

    private void showDeviceOptimizationDialog() {
        Context context = requireContext();

        // 1) شاشة الشركة المخصصة (لو الجهاز عنده واحدة معروفة)
        boolean foundOemIntent = false;
        for (final Intent intent : POWERMANAGER_INTENTS) {
            if (context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                foundOemIntent = true;
                new AlertDialog.Builder(context)
                        .setTitle(R.string.autostart)
                        .setMessage(R.string.autostart_message)
                        .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                            try {
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .create().show();
                break;
            }
        }

        // 2) لو مفيش شاشة شركة معروفة - نوريله صفحة إعدادات التطبيق العامة
        if (!foundOemIntent) {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.battery_manual_title)
                    .setMessage(R.string.battery_manual_message)
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                        try {
                            Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            appSettingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
                            startActivity(appSettingsIntent);
                        } catch (Exception ignored) {}
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .create().show();
        }

        // 3) استثناء البطارية القياسي (مش خاص بشركة معينة)
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.getPackageName())) {
            try {
                Intent batteryIntent = new Intent();
                batteryIntent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                batteryIntent.setData(Uri.parse("package:" + context.getPackageName()));
                startActivity(batteryIntent);
            } catch (Exception ignored) {}
        }

        // 4) المنبه الدقيق (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                try {
                    Intent exactAlarmIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    exactAlarmIntent.setData(Uri.parse("package:" + context.getPackageName()));
                    startActivity(exactAlarmIntent);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        MainActivity.setLocale(context);
    }

    private void updatePrefSummary(Preference pref) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity().getApplicationContext());
        if (pref instanceof ListPreference) {
            Timber.tag("prefs").d("pref is instance of listpreference");
            ListPreference listPref = (ListPreference) pref;
            pref.setSummary(listPref.getEntry());
        }
        if (pref instanceof TimePreference) {
            Timber.tag("prefs").d("pref is instance of TimePreference");
            String time = sharedPreferences.getString(pref.getKey(), "00:00");
            String AMPM = "AM";
            int hour = TimePreference.getHour(time);
            if (hour > 12) {
                hour = hour - 12;
                AMPM = "PM";
            }
            if (hour == 0) {
                hour = 12;
            }
            String hourString = "";
            if (hour < 10) {
                hourString = "0" + hour;
            } else {
                hourString = "" + hour;
            }
            int minutes = TimePreference.getMinute(time);
            String minutesString = "";
            if (minutes < 10) {
                minutesString = "0" + minutes;
            } else {
                minutesString = "" + minutes;
            }
            pref.setSummary(hourString + ":" + minutesString + " " + AMPM);
        }
    }

    private void initSummary(PreferenceScreen p) {
        if (p != null) {
            for (int i = 0; i < ((PreferenceGroup) p).getPreferenceCount(); i++) {
                initSummary(((PreferenceGroup) p).getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    private void initSummary(Preference p) {
        if (p instanceof PreferenceGroup) {
            PreferenceGroup pGrp = (PreferenceGroup) p;
            for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
                initSummary(pGrp.getPreference(i));
            }
        } else {
            updatePrefSummary(p);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // ✅ بعض الأجهزة بتنادي الدالة دي أحيانًا بمفتاح فاضي (null) - مثلاً لو كل الإعدادات
        // اتمسحت مرة واحدة - من غير الشرط ده كان بيعمل Crash فوري
        if (key == null) {
            return;
        }
        if (key.equalsIgnoreCase("volume")) {
            return;
        }
        MyAlarmsManager manager = new MyAlarmsManager(this.getActivity().getApplicationContext());
        manager.UpdateAllApplicableAlarms();
        Preference pref = findPreference(key);
        updatePrefSummary(pref);
        if (key.equalsIgnoreCase("language")) {
            Intent intent = new Intent();
            intent.setClass(this.getActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("FromPreferenceActivity", true);
            this.startActivity(intent);
        }
    }
}
