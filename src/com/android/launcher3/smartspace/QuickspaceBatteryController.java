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

import java.util.List;
import java.util.stream.Collectors;

/** Supplies the Voltage Quickspace battery row while the workspace is visible. */
public final class QuickspaceBatteryController {
    private final Context mContext;
    private final Runnable mChanged;
    private final SmartspaceBluetoothController mBluetooth;
    private String mPhoneText;
    private List<SmartspaceCard> mAccessories = List.of();
    private String mSelectedId;
    private int mPhoneLevel = -1;
    private boolean mStarted;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            String name = Settings.Global.getString(context.getContentResolver(),
                    Settings.Global.DEVICE_NAME);
            mPhoneLevel = level >= 0 && scale > 0 ? Math.min(100, Math.round(100f * level / scale)) : -1;
            mPhoneText = mPhoneLevel >= 0 ? context.getString(
                    R.string.quickspace_vertical_battery_value,
                    TextUtils.isEmpty(name) ? Build.MODEL : name, mPhoneLevel)
                    : null;
            mChanged.run();
        }
    };

    public QuickspaceBatteryController(Context context, Runnable changed) {
        mContext = context.getApplicationContext();
        mChanged = changed;
        mBluetooth = new SmartspaceBluetoothController(mContext, cards -> {
            mAccessories = cards.stream().filter(card -> !TextUtils.isEmpty(card.details))
                    .collect(Collectors.toList());
            if (mSelectedId != null && selectedAccessory() == null) mSelectedId = null;
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
        mAccessories = List.of();
        mSelectedId = null;
        mPhoneLevel = -1;
        mPhoneText = null;
    }

    public String getText() {
        return textFor(mAccessories.isEmpty() ? null : mAccessories.get(0));
    }

    private SmartspaceCard selectedAccessory() {
        if (mSelectedId == null) return null;
        for (SmartspaceCard card : mAccessories) {
            if (mSelectedId.equals(card.id)) return card;
        }
        return null;
    }

    private String textFor(SmartspaceCard card) {
        return card == null ? mPhoneText : card.title + " · " + card.details;
    }

    public String getPagedText() {
        if (mPhoneText == null && mSelectedId == null && !mAccessories.isEmpty()) {
            mSelectedId = mAccessories.get(0).id;
        }
        return textFor(selectedAccessory());
    }

    public int getPagedPhoneLevel() {
        return mSelectedId == null ? mPhoneLevel : -1;
    }

    public void cycleOrOpen(View view) {
        if (mAccessories.size() + (mPhoneText == null ? 0 : 1) <= 1) {
            openPagedSettings(view);
            return;
        }
        int next = mSelectedId == null ? 0 : mAccessories.indexOf(selectedAccessory()) + 1;
        if (next == mAccessories.size()) next = mPhoneText == null ? 0 : -1;
        mSelectedId = next < 0 ? null : mAccessories.get(next).id;
        mChanged.run();
    }

    public void openPagedSettings(View view) {
        openSettings(view, selectedAccessory());
    }

    public void openSettings(View view) {
        openSettings(view, mAccessories.isEmpty() ? null : mAccessories.get(0));
    }

    private void openSettings(View view, SmartspaceCard accessory) {
        if (accessory != null) {
            accessory.action.onClick(view);
        } else {
            Launcher.getLauncher(view.getContext()).startActivitySafely(view,
                    new Intent(Intent.ACTION_POWER_USAGE_SUMMARY), null);
        }
    }
}
