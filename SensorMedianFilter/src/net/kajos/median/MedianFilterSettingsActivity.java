package net.kajos.median;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class MedianFilterSettingsActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
	   super.onCreate(savedInstanceState);
	   addPreferencesFromResource(R.xml.pref_settings);
	}

}
