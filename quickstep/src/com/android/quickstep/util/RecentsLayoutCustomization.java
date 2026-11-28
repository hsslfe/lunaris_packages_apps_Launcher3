/*
 * Copyright (C) 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.quickstep.orientation.RecentsPagedOrientationHandler;

/**
 * Independent layout controls for the phone carousel. Both regions are derived from the stock
 * layout, never from a customized card or action group. This also makes the task bounds used by
 * the gesture simulator identical to the bounds used by RecentsView.
 */
public final class RecentsLayoutCustomization {
    private RecentsLayoutCustomization() {}

    public static boolean isSupported(DeviceProfile dp, RecentsPagedOrientationHandler orientation) {
        return !dp.getDeviceProperties().isTablet() && orientation.isLayoutNaturalToLauncher();
    }

    public static int defaultButtonBottom(Resources res, DeviceProfile dp, Rect stockTask,
            boolean newStyle) {
        return newStyle
                ? dp.getDeviceProperties().getHeightPx() - stockTask.bottom
                        - dp.getOverviewProfile().getActionsTopMarginPx()
                        - dp.getOverviewProfile().getActionsHeight()
                : res.getDimensionPixelSize(R.dimen.overview_actions_bottom_margin_legacy);
    }

    private static int buttonHeight(Resources res, boolean newStyle) {
        return newStyle
                ? res.getDimensionPixelSize(R.dimen.recents_action_primary_row_height)
                        + res.getDimensionPixelSize(R.dimen.recents_action_secondary_row_height)
                        + 2 * res.getDimensionPixelSize(R.dimen.recents_action_row_bottom_margin)
                : res.getDimensionPixelSize(R.dimen.overview_actions_height_legacy);
    }

    /** Divide the original free gap into two fixed, non-intersecting travel regions. */
    public static int cardBoundary(int stockCardBottom, int stockButtonTop) {
        return stockCardBottom + Math.max(0, stockButtonTop - stockCardBottom) / 2;
    }

    private static int boundary(Resources res, DeviceProfile dp, Rect stockTask, boolean newStyle) {
        int buttonTop = dp.getDeviceProperties().getHeightPx()
                - defaultButtonBottom(res, dp, stockTask, newStyle) - buttonHeight(res, newStyle);
        return cardBoundary(stockTask.bottom, buttonTop);
    }

    private static int dpOffset(Resources res, int value) {
        return Math.round(clamp(value, -30, 40) * res.getDisplayMetrics().density);
    }

    public static void applyCard(Context context, DeviceProfile dp, Rect stockTask) {
        boolean newStyle = LauncherPrefs.RECENTS_NEW_OVERVIEW_STYLE.get(context);
        int size = (newStyle ? LauncherPrefs.RECENTS_REDESIGNED_CARD_SIZE
                : LauncherPrefs.RECENTS_LEGACY_CARD_SIZE).get(context);
        int margin = (newStyle ? LauncherPrefs.RECENTS_REDESIGNED_CARD_TOP_MARGIN
                : LauncherPrefs.RECENTS_LEGACY_CARD_TOP_MARGIN).get(context);
        if ((size == 100 && margin == 0) || stockTask.isEmpty()) {
            return;
        }

        Resources res = context.getResources();
        Rect insets = dp.getInsets();
        int horizontalMargin = dp.getOverviewProfile().getTaskMarginPx();
        // Include the icon/header space so the card's upper edge cannot hide it.
        Rect safe = new Rect(
                Math.min(stockTask.left, insets.left + horizontalMargin),
                Math.min(stockTask.top,
                        insets.top + dp.getOverviewProfile().getTaskThumbnailTopMarginPx()),
                Math.max(stockTask.right,
                        dp.getDeviceProperties().getWidthPx() - insets.right - horizontalMargin),
                boundary(res, dp, stockTask, newStyle));
        resolveCard(stockTask, safe, size, dpOffset(res, margin), stockTask);
    }

    /**
     * Size is calculated only from the size setting and fixed bounds. The margin changes only
     * position; it never participates in sizing, even when the requested travel cannot fit.
     */
    public static void resolveCard(Rect stock, Rect safe, int sizePercent, int offsetPx, Rect out) {
        if (stock.isEmpty() || safe.isEmpty()) {
            out.set(stock);
            return;
        }
        int centerX = stock.centerX();
        int centerY = stock.centerY();
        int maxWidth = Math.max(stock.width(),
                2 * Math.min(centerX - safe.left, safe.right - centerX));
        float scale = Math.min(clamp(sizePercent, 75, 110) / 100f,
                Math.min(maxWidth / (float) stock.width(), safe.height() / (float) stock.height()));
        int width = Math.max(1, Math.min(safe.width(), Math.round(stock.width() * scale)));
        int height = Math.max(1, Math.min(safe.height(), Math.round(stock.height() * scale)));
        int left = clamp(centerX - width / 2, safe.left, safe.right - width);
        // Center the size change on the original card, then apply only the signed offset.
        int top = clamp(centerY - height / 2 + offsetPx, safe.top, safe.bottom - height);
        out.set(left, top, left + width, top + height);
    }

    public static int buttonBottom(Context context, DeviceProfile dp, Rect stockTask,
            boolean newStyle) {
        int margin = (newStyle ? LauncherPrefs.RECENTS_REDESIGNED_BUTTON_TOP_MARGIN
                : LauncherPrefs.RECENTS_LEGACY_BUTTON_TOP_MARGIN).get(context);
        Resources res = context.getResources();
        int original = defaultButtonBottom(res, dp, stockTask, newStyle);
        int safeBottom = Math.max(dp.getInsets().bottom, dp.getOverviewActionsClaimedSpaceBelow())
                + dp.memInfoHeight;
        return resolveButtonBottom(original, dp.getDeviceProperties().getHeightPx(),
                buttonHeight(res, newStyle), boundary(res, dp, stockTask, newStyle),
                safeBottom, dpOffset(res, margin));
    }

    /** Positive offset moves the entire group down, without recalculating the card. */
    public static int resolveButtonBottom(int original, int screenHeight, int groupHeight,
            int boundary, int safeBottom, int offsetPx) {
        if (offsetPx == 0) {
            return original;
        }
        // Preserve the stock anchor at zero and never worsen an existing inset limitation.
        int min = Math.min(original, safeBottom);
        int max = Math.max(original, screenHeight - groupHeight - boundary);
        return clamp(original - offsetPx, min, max);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
