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

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.animation.AnimationUtils;
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
    private static final long INDICATOR_DURATION_MS = 260;

    private final Drawable mTrack;
    private final Paint mIndicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mIndicatorRect = new RectF();
    private ValueAnimator mIndicatorAnimator;
    private float mIndicatorPosition;
    private float mIndicatorTarget;
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
        mIndicatorPaint.setColor(context.getColor(R.color.materialColorSecondaryContainer));
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
        if (mIndicatorAnimator != null) {
            mIndicatorAnimator.cancel();
            mIndicatorAnimator = null;
        }
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

    /** Slides the selected pill to its new position using the current Monet color. */
    public void setCategoriesSelected(boolean categories, boolean animate) {
        float target = categories ? 1f : 0f;
        if (mIndicatorTarget == target) {
            return;
        }
        mIndicatorTarget = target;
        if (mIndicatorAnimator != null) {
            mIndicatorAnimator.cancel();
        }
        if (!animate || !isAttachedToWindow() || getWidth() == 0) {
            mIndicatorPosition = target;
            invalidate();
            return;
        }
        mIndicatorAnimator = ValueAnimator.ofFloat(mIndicatorPosition, target);
        mIndicatorAnimator.setDuration(INDICATOR_DURATION_MS);
        mIndicatorAnimator.setInterpolator(AnimationUtils.loadInterpolator(
                getContext(), android.R.interpolator.fast_out_slow_in));
        mIndicatorAnimator.addUpdateListener(animation -> {
            mIndicatorPosition = (float) animation.getAnimatedValue();
            invalidate();
        });
        mIndicatorAnimator.start();
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

        if (getChildCount() >= 2) {
            // Child layout positions also work in RTL; interpolate the complete pill
            // rectangle instead of moving a fixed-width drawable.
            android.view.View all = getChildAt(0);
            android.view.View categories = getChildAt(1);
            float progress = mIndicatorPosition;
            float pillLeft = all.getLeft() + (categories.getLeft() - all.getLeft()) * progress;
            float pillRight = all.getRight()
                    + (categories.getRight() - all.getRight()) * progress;
            float pillTop = all.getTop() + (categories.getTop() - all.getTop()) * progress;
            float pillBottom = all.getBottom()
                    + (categories.getBottom() - all.getBottom()) * progress;
            mIndicatorRect.set(pillLeft, pillTop, pillRight, pillBottom);
            canvas.drawRoundRect(mIndicatorRect, getHeight() / 2f,
                    getHeight() / 2f, mIndicatorPaint);
        }
        super.dispatchDraw(canvas);
    }
}
