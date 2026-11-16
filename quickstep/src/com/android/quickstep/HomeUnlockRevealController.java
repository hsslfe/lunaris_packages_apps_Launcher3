/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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

package com.android.quickstep;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.quickstep.util.HomeUnlockReveal;
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;
import com.android.systemui.shared.system.smartspace.SmartspaceState;

import java.util.concurrent.ExecutionException;

public final class HomeUnlockRevealController extends ILauncherUnlockAnimationController.Stub
        implements SharedPreferences.OnSharedPreferenceChangeListener,
                ScreenOnTracker.ScreenOnListener {
    private static final long PREPARE_TIMEOUT_MS = 2000;
    private static final long USER_PRESENT_TIMEOUT_MS = 400;
    private static final long WAKE_UNLOCK_DURATION_MS = 400;

    private QuickstepLauncher mLauncher;
    private HomeUnlockReveal mWorkspaceAnimation;
    private ValueAnimator mAnimator;
    private boolean mPrepared;
    private boolean mUnlockHandled;
    private boolean mUserPresentPending;
    private boolean mPlayingFallback;
    private final KeyguardManager mKeyguardManager;
    private final PowerManager mPowerManager;
    private final Runnable mPrepareTimeout = this::cancel;
    private final Runnable mUserPresentTimeout =
            () -> {
                // Do not leave prepared icons hidden or replay an expired wake after Home is
                // visible.
                cancel();
                mUnlockHandled = true;
            };
    private final Runnable mTryUserPresent = this::tryUserPresent;
    private final Runnable mSystemUiConnectionListener =
            () -> {
                if (mLauncher != null && !SystemUiProxy.INSTANCE.get(mLauncher).isActive()) {
                    cancel();
                }
            };

    public HomeUnlockRevealController(QuickstepLauncher launcher) {
        this(launcher, new HomeUnlockReveal(launcher));
        ScreenOnTracker.INSTANCE.get(launcher).addListener(this);
        LauncherPrefs.getPrefs(launcher).registerOnSharedPreferenceChangeListener(this);
        SystemUiProxy proxy = SystemUiProxy.INSTANCE.get(launcher);
        proxy.addOnStateChangeListener(mSystemUiConnectionListener);
        proxy.setLauncherUnlockAnimationController(
                launcher.getComponentName().getClassName(), this);
    }

    @VisibleForTesting
    HomeUnlockRevealController(QuickstepLauncher launcher, HomeUnlockReveal animation) {
        mLauncher = launcher;
        mWorkspaceAnimation = animation;
        mKeyguardManager = launcher.getSystemService(KeyguardManager.class);
        mPowerManager = launcher.getSystemService(PowerManager.class);
    }

    @Override
    public void onScreenOnChanged(boolean isOn) {
        if (!isOn) {
            cancel();
            mUnlockHandled = false;
        } else if (mKeyguardManager != null
                && !mKeyguardManager.isKeyguardLocked()
                && mPowerManager != null
                && mPowerManager.isInteractive()) {
            // Fingerprint authentication may finish before the screen-on broadcast is delivered.
            onUserPresent();
        }
    }

    @Override
    public void onUserPresent() {
        // Wake-and-unlock can bypass SystemUI's lock-screen animation callbacks entirely.
        if (!isEnabled() || mUnlockHandled || mUserPresentPending) return;
        mUserPresentPending = true;
        mLauncher.getDragLayer().postDelayed(mUserPresentTimeout, USER_PRESENT_TIMEOUT_MS);
        onLauncherReady();
    }

    public void onLauncherReady() {
        if (mLauncher != null && mUserPresentPending) {
            mLauncher.getDragLayer().removeCallbacks(mTryUserPresent);
            mLauncher.getDragLayer().postOnAnimation(mTryUserPresent);
        }
    }

    private void tryUserPresent() {
        if (!mUserPresentPending) return;
        if (!isEnabled() || mUnlockHandled) {
            clearUserPresent();
            return;
        }
        // Wait for Home's first usable frame, never animate an app or the still-locked screen.
        if (!mLauncher.hasBeenResumed() || !mLauncher.getDragLayer().hasWindowFocus()) return;
        if (mKeyguardManager != null
                && !mKeyguardManager.isKeyguardLocked()
                && mPowerManager != null
                && mPowerManager.isInteractive()) {
            mPlayingFallback = true;
            playUnlockAnimation(true, 0, 0);
        }
        if (mUserPresentPending) onLauncherReady();
    }

    private void clearUserPresent() {
        mUserPresentPending = false;
        if (mLauncher != null) {
            mLauncher.getDragLayer().removeCallbacks(mTryUserPresent);
            mLauncher.getDragLayer().removeCallbacks(mUserPresentTimeout);
        }
    }

    @Override
    public void prepareForUnlock(
            boolean animateSmartspace, Rect lockscreenSmartspaceBounds, int selectedPage) {
        Runnable prepare =
                () -> {
                    if (mUnlockHandled) return;
                    resetAnimation();
                    if (isEnabled()) {
                        mPrepared = mWorkspaceAnimation.prepareForUnlock();
                        if (mPrepared) {
                            mLauncher
                                    .getDragLayer()
                                    .postDelayed(mPrepareTimeout, PREPARE_TIMEOUT_MS);
                        }
                    }
                };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            prepare.run();
            return;
        }
        try {
            MAIN_EXECUTOR.submit(prepare).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while preparing workspace unlock", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to prepare workspace unlock", e.getCause());
        }
    }

    @Override
    public void setUnlockAmount(float amount, boolean forceIfAnimating) {
        MAIN_EXECUTOR.execute(
                () -> {
                    if (!isEnabled()) {
                        cancel();
                        return;
                    }
                    if (!Float.isFinite(amount) || (mAnimator != null && !forceIfAnimating)) {
                        return;
                    }
                    // Late SystemUI completion must not cut short the wake-and-unlock fallback.
                    if (mPlayingFallback && mAnimator != null) return;
                    mLauncher.getDragLayer().removeCallbacks(mPrepareTimeout);
                    stopAnimator();
                    if (amount >= 1f) {
                        if (mPrepared && mWorkspaceAnimation.getUnlockAmount() > 0f) {
                            mUnlockHandled = true;
                            clearUserPresent();
                        }
                        resetAnimation();
                    } else if (mPrepared && mWorkspaceAnimation.finishPreparation()) {
                        mWorkspaceAnimation.setUnlockAmount(Utilities.boundToRange(amount, 0f, 1f));
                        mLauncher.getDragLayer().postDelayed(mPrepareTimeout, PREPARE_TIMEOUT_MS);
                    } else {
                        resetAnimation();
                    }
                });
    }

    @Override
    public void playUnlockAnimation(boolean unlocked, long duration, long startDelay) {
        MAIN_EXECUTOR.execute(
                () -> {
                    if (!isEnabled()) {
                        cancel();
                        return;
                    }
                    if (unlocked && mUnlockHandled) return;
                    // Biometric wake-and-unlock may call play without a preceding prepare callback.
                    if (!mPrepared || !mWorkspaceAnimation.isPrepared()) {
                        mPrepared = mWorkspaceAnimation.prepareForUnlock();
                        if (!mPrepared) {
                            resetAnimation();
                            return;
                        }
                    }
                    mLauncher.getDragLayer().removeCallbacks(mPrepareTimeout);
                    stopAnimator();
                    if (!mWorkspaceAnimation.finishPreparation()) {
                        resetAnimation();
                        return;
                    }
                    float from = mWorkspaceAnimation.getUnlockAmount();
                    float to = unlocked ? 1f : 0f;
                    if ((!unlocked && duration <= 0) || from == to) {
                        resetAnimation();
                        return;
                    }
                    ValueAnimator animator = ValueAnimator.ofFloat(from, to);
                    mAnimator = animator;
                    if (unlocked) {
                        mUnlockHandled = true;
                        clearUserPresent();
                    }
                    boolean fastWake = unlocked && (mPlayingFallback || duration <= 0);
                    long revealDuration = mWorkspaceAnimation.getDuration();
                    if (fastWake)
                        revealDuration = Math.min(revealDuration, WAKE_UNLOCK_DURATION_MS);
                    animator.setDuration(Math.round(revealDuration * Math.abs(to - from)));
                    long revealStartDelay = fastWake ? 0L : Math.max(0L, startDelay);
                    animator.setStartDelay(unlocked ? revealStartDelay / 2 : revealStartDelay);
                    animator.setInterpolator(LINEAR);
                    animator.addUpdateListener(
                            value -> {
                                if (!isEnabled() || !mWorkspaceAnimation.isPrepared()) {
                                    cancel();
                                } else {
                                    mWorkspaceAnimation.setUnlockAmount(
                                            (Float) value.getAnimatedValue());
                                    if (unlocked && mWorkspaceAnimation.isFinished()) {
                                        cancel();
                                    }
                                }
                            });
                    animator.addListener(
                            new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (mAnimator == animation) {
                                        mAnimator = null;
                                        cancel();
                                    }
                                }
                            });
                    animator.start();
                });
    }

    @Override
    public void playUnlockAnimationWithWallpaperDepth(
            boolean unlocked, long duration, long startDelay, boolean animateWallpaperDepth) {
        playUnlockAnimation(unlocked, duration, startDelay);
    }

    @Override
    public void setSmartspaceSelectedPage(int selectedPage) {}

    @Override
    public void setSmartspaceVisibility(int visibility) {}

    @Override
    public void dispatchSmartspaceStateToSysui() {
        MAIN_EXECUTOR.execute(
                () -> {
                    if (mLauncher != null) {
                        SystemUiProxy.INSTANCE
                                .get(mLauncher)
                                .notifySysuiSmartspaceStateUpdated(new SmartspaceState());
                    }
                });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (key == null
                || LauncherPrefs.WORKSPACE_UNLOCK_ANIMATION.getSharedPrefKey().equals(key)) {
            MAIN_EXECUTOR.execute(
                    () -> {
                        if (!isEnabled()) {
                            cancel();
                        }
                    });
        }
    }

    private boolean isEnabled() {
        return mLauncher != null
                && ValueAnimator.areAnimatorsEnabled()
                && LauncherPrefs.WORKSPACE_UNLOCK_ANIMATION.get(mLauncher);
    }

    private void stopAnimator() {
        if (mAnimator != null) {
            ValueAnimator animator = mAnimator;
            mAnimator = null;
            animator.cancel();
        }
    }

    public void cancel() {
        clearUserPresent();
        resetAnimation();
    }

    private void resetAnimation() {
        stopAnimator();
        if (mLauncher != null) {
            mLauncher.getDragLayer().removeCallbacks(mPrepareTimeout);
        }
        if (mWorkspaceAnimation != null) {
            mWorkspaceAnimation.reset();
        }
        mPrepared = false;
        mPlayingFallback = false;
        if (mLauncher != null && mLauncher.getHotseat() != null) {
            mLauncher.getHotseat().postInvalidate();
        }
    }

    public void destroy() {
        if (mLauncher == null) {
            return;
        }
        cancel();
        ScreenOnTracker.INSTANCE.get(mLauncher).removeListener(this);
        LauncherPrefs.getPrefs(mLauncher).unregisterOnSharedPreferenceChangeListener(this);
        SystemUiProxy proxy = SystemUiProxy.INSTANCE.get(mLauncher);
        proxy.removeOnStateChangeListener(mSystemUiConnectionListener);
        proxy.clearLauncherUnlockAnimationController(this);
        mWorkspaceAnimation = null;
        mLauncher = null;
    }
}
