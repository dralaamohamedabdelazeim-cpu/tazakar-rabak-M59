package com.alaaeltaweel.thikrallah.Fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_general);
        initSummary(getPreferenceScreen());
        mContext = this.getContext();
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
