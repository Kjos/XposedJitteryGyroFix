package net.kajos.median;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class MedianFilterSettingsActivity extends PreferenceActivity {

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	   super.onCreate(savedInstanceState);
	   // Setup a non-default and world readable shared preferences, so that 1- we know the name (necessary for XSharedPreferences()), 2- the preferences are accessible from inside the hook.
	   PreferenceManager prefMgr = getPreferenceManager();
	   prefMgr.setSharedPreferencesName("pref_median");
	   prefMgr.setSharedPreferencesMode(MODE_WORLD_READABLE);
	   addPreferencesFromResource(R.xml.pref_settings);
	}

}
