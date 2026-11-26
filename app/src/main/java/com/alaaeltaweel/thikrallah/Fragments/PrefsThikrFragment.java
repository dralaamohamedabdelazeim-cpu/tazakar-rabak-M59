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
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.TwoStatePreference;

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
		setupAccordion();
	}

	// ستارة "تخصيص": مقفولة في الأول عشان الصفحة مش تتزحم
	private boolean dailyExpanded = false;

	/** بتظهر/تخفي تفاصيل الفوائد اليومية حسب الستارة، وحسب الأوقات الثابتة وعدد الإشعارات */
	private void updateDailyContentVisibility() {
		try {
			SharedPreferences sp = getPreferenceManager().getSharedPreferences();
			boolean dOpen = isDailyOpen();
			boolean fixed = sp.getBoolean("dailyContent_fixed", false);
			int count = 4;
			try {
				count = Integer.parseInt(sp.getString("dailyContent_count", "4").trim());
			} catch (Exception ignored) {
			}
			Preference customize = findPreference("dailyContent_customize");
			if (customize != null) {
				customize.setTitle(dailyExpanded ? "تخصيص ▴" : "تخصيص ▾");
				customize.setVisible(dOpen);
			}
			Preference dailyEnabledPref = findPreference("dailyContent_enabled");
			if (dailyEnabledPref != null) dailyEnabledPref.setVisible(dOpen);
			String[] alwaysInside = {"dailyContent_count", "dailyContent_fixed", "dailyContent_types", "dailyContent_test"};
			for (String k : alwaysInside) {
				Preference x = findPreference(k);
				if (x != null) x.setVisible(dOpen && dailyExpanded);
			}
			for (int i = 1; i <= 6; i++) {
				Preference timePref = findPreference("dailyContent_time" + i);
				if (timePref != null) timePref.setVisible(dOpen && dailyExpanded && fixed && i <= count);
			}
			String[] windowKeys = {"dailyContent_start", "dailyContent_end", "dailyContent_random"};
			for (String k : windowKeys) {
				Preference w = findPreference(k);
				if (w != null) w.setVisible(dOpen && dailyExpanded && !fixed);
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

	// ---------- أقسام قابلة للطي (كل قسم سطر واحد بملخصه، وتفتح اللي محتاجه) ----------
	private final java.util.LinkedHashMap<PreferenceCategory, Preference> accHeaders = new java.util.LinkedHashMap<>();
	private final java.util.HashMap<PreferenceCategory, String> accTitles = new java.util.HashMap<>();
	private final java.util.HashSet<PreferenceCategory> accOpen = new java.util.HashSet<>();
	private PreferenceCategory dailyCategory = null;

	private boolean isDailyOpen() {
		return dailyCategory == null || !accHeaders.containsKey(dailyCategory) || accOpen.contains(dailyCategory);
	}

	private void setupAccordion() {
		try {
			PreferenceScreen screen = getPreferenceScreen();
			if (screen == null) return;
			Preference dailyEnabled = findPreference("dailyContent_enabled");
			if (dailyEnabled != null && dailyEnabled.getParent() instanceof PreferenceCategory) {
				dailyCategory = (PreferenceCategory) dailyEnabled.getParent();
			}
			Context ctx = getPreferenceManager().getContext();
			java.util.ArrayList<PreferenceCategory> cats = new java.util.ArrayList<>();
			for (int i = 0; i < screen.getPreferenceCount(); i++) {
				Preference p = screen.getPreference(i);
				if (p instanceof PreferenceCategory) cats.add((PreferenceCategory) p);
			}
			int n = 0;
			for (final PreferenceCategory cat : cats) {
				CharSequence t = cat.getTitle();
				if (t == null || t.length() == 0) continue;
				accTitles.put(cat, t.toString());
				cat.setLayoutResource(R.layout.pref_category_spacer);
				for (int j = 0; j < cat.getPreferenceCount(); j++) {
					Preference c = cat.getPreference(j);
					if (!(c instanceof com.alaaeltaweel.thikrallah.Utilities.SeekBarPreference)) {
						c.setLayoutResource(R.layout.pref_row_card);
					}
				}
				Preference header = new Preference(ctx);
				header.setKey("accordion_header_" + (n++));
				header.setPersistent(false);
				header.setIconSpaceReserved(false);
				header.setLayoutResource(R.layout.pref_accordion_header);
				header.setOrder(-1);
				header.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
					@Override
					public boolean onPreferenceClick(Preference preference) {
						if (accOpen.contains(cat)) {
							accOpen.remove(cat);
						} else {
							accOpen.add(cat);
						}
						applyAccordion();
						return true;
					}
				});
				cat.addPreference(header);
				accHeaders.put(cat, header);
			}
			applyAccordion();
		} catch (Exception e) {
			Log.d("PrefsThikr", "accordion setup failed: " + e.getMessage());
		}
	}

	private void applyAccordion() {
		try {
			for (java.util.Map.Entry<PreferenceCategory, Preference> e : accHeaders.entrySet()) {
				PreferenceCategory cat = e.getKey();
				Preference header = e.getValue();
				if (cat == dailyCategory) continue; // قسم الفوائد اليومية له منطقه الخاص في updateDailyContentVisibility
				boolean open = accOpen.contains(cat);
				for (int j = 0; j < cat.getPreferenceCount(); j++) {
					Preference c = cat.getPreference(j);
					if (c != header) c.setVisible(open);
				}
			}
			updateDailyContentVisibility();
			refreshAccordionHeaders();
		} catch (Exception e) {
			Log.d("PrefsThikr", "accordion apply failed: " + e.getMessage());
		}
	}

	private void refreshAccordionHeaders() {
		try {
			for (java.util.Map.Entry<PreferenceCategory, Preference> e : accHeaders.entrySet()) {
				PreferenceCategory cat = e.getKey();
				Preference header = e.getValue();
				boolean open = accOpen.contains(cat);
				header.setTitle(accTitles.get(cat) + (open ? "  ▴" : "  ▾"));
				header.setSummary(buildAccordionSummary(cat, header));
			}
		} catch (Exception e) {
			Log.d("PrefsThikr", "accordion refresh failed: " + e.getMessage());
		}
	}

	/** ملخص قصير لإعدادات القسم (مفعّل/متوقف + أول قيمتين) يظهر تحت عنوانه وهو مقفول */
	private String buildAccordionSummary(PreferenceCategory cat, Preference header) {
		StringBuilder sb = new StringBuilder();
		int parts = 0;
		boolean first = true;
		for (int j = 0; j < cat.getPreferenceCount() && parts < 3; j++) {
			Preference c = cat.getPreference(j);
			if (c == header) continue;
			String part = null;
			if (c instanceof TwoStatePreference) {
				if (first) part = ((TwoStatePreference) c).isChecked() ? "مفعّل" : "متوقف";
			} else if (c instanceof ListPreference) {
				CharSequence en = ((ListPreference) c).getEntry();
				if (en != null) part = en.toString();
			} else if (c instanceof TimePreference) {
				CharSequence sm = c.getSummary();
				if (sm != null) part = sm.toString();
			}
			first = false;
			if (part != null && part.length() > 0) {
				if (sb.length() > 0) sb.append("، ");
				sb.append(part);
				parts++;
			}
		}
		return sb.toString();
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
		refreshAccordionHeaders();
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
