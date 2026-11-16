package com.android.launcher3.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryPreferenceVisibilityTest {
    @Test
    public void disablingLibrary_hidesOnlyItsOptions_andEnablingRestoresTheirValues() {
        Context context = InstrumentationRegistry.getTargetContext();
        PreferenceScreen screen = new PreferenceManager(context).createPreferenceScreen(context);
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);
        SwitchPreferenceCompat master = new SwitchPreferenceCompat(context);
        master.setKey(LauncherPrefs.APP_LIBRARY_ENABLED.getSharedPrefKey());
        master.setPersistent(false);
        category.addPreference(master);
        String[] keys = {
            LauncherPrefs.APP_LIBRARY_SHOW_LABELS.getSharedPrefKey(),
            LauncherPrefs.APP_LIBRARY_SINGLE_PAGE.getSharedPrefKey(),
            LauncherPrefs.APP_LIBRARY_HIDE_APP_DRAWER.getSharedPrefKey()
        };
        for (String key : keys) {
            SwitchPreferenceCompat option = new SwitchPreferenceCompat(context);
            option.setKey(key);
            option.setPersistent(false);
            option.setChecked(true);
            category.addPreference(option);
        }
        SettingsHomescreen.HomescreenSettingsFragment.setAppLibraryOptionsVisible(screen, false);
        assertTrue(master.isVisible());
        assertTrue(category.isVisible());
        for (String key : keys) assertFalse(screen.findPreference(key).isVisible());
        SettingsHomescreen.HomescreenSettingsFragment.setAppLibraryOptionsVisible(screen, true);
        for (String key : keys) {
            SwitchPreferenceCompat option = screen.findPreference(key);
            assertTrue(option.isVisible());
            assertTrue(option.isChecked());
        }
    }
}
