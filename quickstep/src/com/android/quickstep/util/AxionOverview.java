/*
 * Copyright (C) 2026 Lunaris AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.quickstep.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

/** Axion overview layout selection and common task bounds for view and gesture simulator. */
public final class AxionOverview {
    private AxionOverview() {}

    public static boolean isEnabled(Context context, DeviceProfile dp) {
        return !dp.getDeviceProperties().isTablet()
                && LauncherPrefs.RECENTS_AXION_OVERVIEW.get(context);
    }

    public static int getActionsLayout(Context context, DeviceProfile dp) {
        if (isEnabled(context, dp)) return R.layout.overview_actions_container_axion;
        return LauncherPrefs.RECENTS_NEW_OVERVIEW_STYLE.get(context)
                ? R.layout.overview_actions_container : R.layout.overview_actions_container_legacy;
    }

    public static int claimedSpace(Context context, DeviceProfile dp) {
        Resources res = context.getResources();
        return dp.getOverviewActionsClaimedSpaceBelow() + dp.memInfoHeight
                + res.getDimensionPixelSize(R.dimen.axion_overview_actions_top_margin)
                + res.getDimensionPixelSize(R.dimen.axion_overview_actions_stack_height);
    }

    /** Axion's 63% width, capped by the actual usable display region. */
    public static void adjustTaskRect(Context context, DeviceProfile dp, Rect task) {
        int screenWidth = dp.getDeviceProperties().getWidthPx();
        int screenHeight = dp.getDeviceProperties().getHeightPx();
        if (screenWidth <= 0 || screenHeight <= 0 || task.isEmpty()) return;
        Rect insets = dp.getInsets();
        int topLimit = insets.top + dp.getOverviewProfile().getTaskThumbnailTopMarginPx();
        int bottomLimit = screenHeight - insets.bottom - claimedSpace(context, dp);
        int availableHeight = bottomLimit - topLimit;
        int availableWidth = screenWidth - insets.left - insets.right;
        if (availableHeight <= 0 || availableWidth <= 0) return;
        float scale = Math.min(0.63f, Math.min(availableHeight / (float) screenHeight,
                availableWidth / (float) screenWidth));
        int width = Math.max(1, Math.round(screenWidth * scale));
        int height = Math.max(1, Math.round(screenHeight * scale));
        int left = Math.max(insets.left, Math.min(screenWidth - insets.right - width,
                (screenWidth - width) / 2));
        int top = Math.max(topLimit, Math.min(bottomLimit - height, task.centerY() - height / 2));
        task.set(left, top, left + width, top + height);
    }
}
