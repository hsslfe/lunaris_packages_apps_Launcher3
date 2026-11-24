package com.android.launcher3.smartspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/** Supplies the vertical Quickspace battery row while the workspace is visible. */
public final class QuickspaceBatteryController {
    private final Context mContext;
    private final Runnable mChanged;
    private final SmartspaceBluetoothController mBluetooth;
    private String mPhoneText;
    private SmartspaceCard mAccessory;
    private boolean mStarted;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            String name = Settings.Global.getString(context.getContentResolver(),
                    Settings.Global.DEVICE_NAME);
            mPhoneText = level >= 0 && scale > 0 ? context.getString(
                    R.string.quickspace_vertical_battery_value,
                    TextUtils.isEmpty(name) ? Build.MODEL : name, Math.round(100f * level / scale))
                    : null;
            mChanged.run();
        }
    };

    public QuickspaceBatteryController(Context context, Runnable changed) {
        mContext = context.getApplicationContext();
        mChanged = changed;
        mBluetooth = new SmartspaceBluetoothController(mContext, cards -> {
            mAccessory = cards.stream().filter(card -> !TextUtils.isEmpty(card.details))
                    .findFirst().orElse(null);
            mChanged.run();
        });
    }

    public void onResume() {
        if (mStarted || !LauncherPrefs.SHOW_QUICKSPACE_BATTERY.get(mContext)) return;
        mStarted = true;
        mContext.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        mBluetooth.start();
    }

    public void onPause() {
        if (!mStarted) return;
        mStarted = false;
        mContext.unregisterReceiver(mReceiver);
        mBluetooth.stop();
        mAccessory = null;
        mPhoneText = null;
    }

    public String getText() {
        return mAccessory == null ? mPhoneText : mAccessory.title + " · " + mAccessory.details;
    }

    public void openSettings(View view) {
        if (mAccessory != null) {
            mAccessory.action.onClick(view);
        } else {
            Launcher.getLauncher(view.getContext()).startActivitySafely(view,
                    new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), null);
        }
    }
}
