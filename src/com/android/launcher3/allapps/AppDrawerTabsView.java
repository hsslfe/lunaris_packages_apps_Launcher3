/*
 * Copyright (C) 2026 The Android Open Source Project
 *
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
package com.android.launcher3.allapps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.CrossWindowBlurListeners;
import android.widget.LinearLayout;

import com.android.axion.blur.ui.view.AxViewBackdropBlur;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;

import java.util.function.Consumer;

/** The rounded backdrop for the All and Categories buttons. */
public class AppDrawerTabsView extends LinearLayout {
    private static final int BLURRED_TRACK_ALPHA = 170;

    private final Drawable mTrack;
    private final AxViewBackdropBlur mBackdropBlur = new AxViewBackdropBlur(this, false);
    private final Consumer<Boolean> mCrossWindowBlurListener = enabled -> updateBlur();
    private final LauncherPrefChangeListener mBlurDepthListener = key -> {
        if (LauncherPrefs.BLUR_DEPTH.getSharedPrefKey().equals(key)) {
            updateBlur();
        }
    };
    private boolean mVisibleToUser;

    public AppDrawerTabsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mTrack = context.getDrawable(R.drawable.all_apps_library_tabs_track).mutate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mBackdropBlur.onAttachedToWindow();
        mBackdropBlur.setSurfaceAlpha(255);
        CrossWindowBlurListeners.getInstance().addListener(
                getContext().getMainExecutor(), mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).addListener(mBlurDepthListener, LauncherPrefs.BLUR_DEPTH);
        updateBlur();
    }

    @Override
    protected void onDetachedFromWindow() {
        mVisibleToUser = false;
        CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).removeListener(mBlurDepthListener, LauncherPrefs.BLUR_DEPTH);
        mBackdropBlur.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        mVisibleToUser = isVisible;
        mBackdropBlur.onVisibilityAggregated(isVisible);
        updateBlur();
    }

    private void updateBlur() {
        int radius = Math.max(0, LauncherPrefs.BLUR_DEPTH.get(getContext()));
        mBackdropBlur.setBlurRadiusPx(radius);
        mBackdropBlur.setEnabled(radius > 0 && mVisibleToUser && isAttachedToWindow()
                && isHardwareAccelerated()
                && CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled());
        invalidate();
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who)
                || (mBackdropBlur != null && mBackdropBlur.verifyDrawable(who));
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        mTrack.setBounds(0, 0, width, height);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean blurred = canvas.isHardwareAccelerated() && mBackdropBlur.isEnabled()
                && mBackdropBlur.draw(canvas, 0, 0, getWidth(), getHeight(), getHeight() / 2f);
        mTrack.setAlpha(blurred ? BLURRED_TRACK_ALPHA : 255);
        mTrack.draw(canvas);
        super.dispatchDraw(canvas);
    }
}
