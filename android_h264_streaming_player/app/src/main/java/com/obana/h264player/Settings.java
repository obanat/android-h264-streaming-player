package com.obana.h264player;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;

/* loaded from: classes.dex */
public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
    private static final String SP_KEY_MAC= "clientId";
    private static final String SP_KEY_REDIS_IP= "serverIp";
    private static final String SP_KEY_REDIS_PORT= "serverPort";
    private static final String SP_KEY_NETWORK_TYPE= "networkType";

    @Override // android.preference.PreferenceActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        addPreferencesFromResource(R.xml.settings);
        setSummaries();
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SP_KEY_MAC)
                || key.equals(SP_KEY_REDIS_IP)
                || key.equals(SP_KEY_REDIS_PORT)) {

            Preference pref = findPreference(key);
            // Set summary to be the user-description for the selected value
            pref.setSummary(sharedPreferences.getString(key, ""));

        }
        //here the others preferences

        if (key.equals(SP_KEY_NETWORK_TYPE)) {
            Preference pref = findPreference(key);
            // Set summary to be the user-description for the selected value
            pref.setSummary(sharedPreferences.getString(key, ""));
        }
    }

    public void setSummaries(){

        final SharedPreferences sp = getPreferenceManager().getSharedPreferences() ;


        //Pref1
        Preference stylePref = findPreference(SP_KEY_MAC);
        stylePref.setSummary(sp.getString(SP_KEY_MAC, ""));

        stylePref = findPreference(SP_KEY_REDIS_IP);
        stylePref.setSummary(sp.getString(SP_KEY_REDIS_IP, ""));

        stylePref = findPreference(SP_KEY_REDIS_PORT);
        stylePref.setSummary(sp.getString(SP_KEY_REDIS_PORT, ""));

        stylePref = findPreference(SP_KEY_NETWORK_TYPE);
        stylePref.setSummary(sp.getString(SP_KEY_NETWORK_TYPE, ""));
    }
}
