package com.alaaeltaweel.thikrallah.Fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.DialogFragment;
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
import com.alaaeltaweel.thikrallah.Utilities.TimePreferenceUI;

import timber.log.Timber;

public class PrefsThikrFragment extends PreferenceFragmentCompat implements OnSharedPreferenceChangeListener {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);
		initSummary(getPreferenceScreen());
		// زر "جرّب الإشعارات الآن" في قسم الفوائد اليومية
		Preference dailyTest = findPreference("dailyContent_test");
		if (dailyTest != null) {
			dailyTest.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					com.alaaeltaweel.thikrallah.Notification.DailyHadithHelper.testNow(getActivity().getApplicationContext());
					return true;
				}
			});
		}
		Preference customizeRow = findPreference("dailyContent_customize");
		if (customizeRow != null) {
			customizeRow.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					dailyExpanded = !dailyExpanded;
					updateDailyContentVisibility();
					return true;
				}
			});
		}
		updateDailyContentVisibility();
	}

	// ستارة "تخصيص": مقفولة في الأول عشان الصفحة مش تتزحم
	private boolean dailyExpanded = false;

	/** بتظهر/تخفي تفاصيل الفوائد اليومية حسب الستارة، وحسب الأوقات الثابتة وعدد الإشعارات */
	private void updateDailyContentVisibility() {
		try {
			SharedPreferences sp = getPreferenceManager().getSharedPreferences();
			boolean fixed = sp.getBoolean("dailyContent_fixed", false);
			int count = 4;
			try {
				count = Integer.parseInt(sp.getString("dailyContent_count", "4").trim());
			} catch (Exception ignored) {
			}
			Preference customize = findPreference("dailyContent_customize");
			if (customize != null) customize.setTitle(dailyExpanded ? "تخصيص ▴" : "تخصيص ▾");
			String[] alwaysInside = {"dailyContent_count", "dailyContent_fixed", "dailyContent_types", "dailyContent_test"};
			for (String k : alwaysInside) {
				Preference x = findPreference(k);
				if (x != null) x.setVisible(dailyExpanded);
			}
			for (int i = 1; i <= 6; i++) {
				Preference timePref = findPreference("dailyContent_time" + i);
				if (timePref != null) timePref.setVisible(dailyExpanded && fixed && i <= count);
			}
			String[] windowKeys = {"dailyContent_start", "dailyContent_end", "dailyContent_random"};
			for (String k : windowKeys) {
				Preference w = findPreference(k);
				if (w != null) w.setVisible(dailyExpanded && !fixed);
			}
			updateDailyTypesSummary();
		} catch (Exception e) {
			Log.d("PrefsThikr", "visibility update failed: " + e.getMessage());
		}
	}

	/** ملخص أنواع المحتوى المختارة */
	private void updateDailyTypesSummary() {
		try {
			androidx.preference.MultiSelectListPreference types =
					(androidx.preference.MultiSelectListPreference) findPreference("dailyContent_types");
			if (types == null) return;
			java.util.Set<String> values = types.getValues();
			CharSequence[] entries = types.getEntries();
			CharSequence[] entryValues = types.getEntryValues();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < entryValues.length; i++) {
				if (values.contains(entryValues[i].toString())) {
					if (sb.length() > 0) sb.append("، ");
					sb.append(entries[i]);
				}
			}
			types.setSummary(sb.length() == 0 ? "لا شيء مختار" : sb.toString());
		} catch (Exception e) {
			Log.d("PrefsThikr", "types summary failed: " + e.getMessage());
		}
	}

	@Override
	public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

	}
	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		MainActivity.setLocale(context);
	}
	@Override
	public void onDisplayPreferenceDialog(Preference preference) {
		DialogFragment dialogFragment = null;
		if (preference instanceof TimePreference) {
			dialogFragment = TimePreferenceUI.newInstance(preference);
		}
		if (dialogFragment != null && isAdded()) {
			dialogFragment.setTargetFragment(this, 0);
			dialogFragment.show(this.getParentFragmentManager(), "android.support.v7.preference" +
					".PreferenceFragment.DIALOG");
		} else {
			super.onDisplayPreferenceDialog(preference);
		}
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
			if (hour<10){
				hourString="0"+hour;
			}else{
				hourString=""+hour;
			}
			int minutes=TimePreference.getMinute(time);
			String minutesString="";
			if (minutes<10){
				minutesString="0"+minutes;
			}else{
				minutesString=""+minutes;
			}
			pref.setSummary(hourString+":"+minutesString+" "+AMPM);
		}
	}
	private void initSummary(PreferenceScreen p) {
		if (p != null) {
			PreferenceGroup pGrp = (PreferenceGroup) p;
			for (int i = 0; i < pGrp.getPreferenceCount(); i++) {
				initSummary(pGrp.getPreference(i));
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
        logScreen();
    }

    private void logScreen() {
    }


    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equalsIgnoreCase("volume")) {
			return;
		}
		MyAlarmsManager manager=new MyAlarmsManager(this.getActivity().getApplicationContext());
		manager.UpdateAllApplicableAlarms();
		
		Preference pref = findPreference(key);
		updatePrefSummary(pref);
		if (key.startsWith("dailyContent_")) {
			updateDailyContentVisibility();
		}
        if (key.equalsIgnoreCase("language")){
            Intent intent=new Intent();
            intent.setClass(this.getActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra("FromPreferenceActivity",true);
            this.startActivity(intent);
        }
	}
}
