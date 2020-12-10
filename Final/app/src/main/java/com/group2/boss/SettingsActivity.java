package com.group2.boss;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_PREF_SWITCH_1 = "example_switch";
    public static final String KEY_PREF_ENABLED_1 = "notifications_enabled_preference";
    public static final String KEY_PREF_THRESHOLD_1 = "threshold_preference";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

    }
}