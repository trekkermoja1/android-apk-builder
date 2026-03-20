package com.remoteaccess.educational.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {

    private static final String PREF_NAME = "RemoteAccessPrefs";
    private static final String KEY_CONSENT_GIVEN = "consent_given";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_DEVICE_REGISTERED = "device_registered";

    private SharedPreferences preferences;

    public PreferenceManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isConsentGiven() {
        return preferences.getBoolean(KEY_CONSENT_GIVEN, false);
    }

    public void setConsentGiven(boolean given) {
        preferences.edit().putBoolean(KEY_CONSENT_GIVEN, given).apply();
    }

    public String getServerUrl() {
        return preferences.getString(KEY_SERVER_URL, "http://localhost:5000");
    }

    public void setServerUrl(String url) {
        preferences.edit().putString(KEY_SERVER_URL, url).apply();
    }

    public boolean isDeviceRegistered() {
        return preferences.getBoolean(KEY_DEVICE_REGISTERED, false);
    }

    public void setDeviceRegistered(boolean registered) {
        preferences.edit().putBoolean(KEY_DEVICE_REGISTERED, registered).apply();
    }
}
