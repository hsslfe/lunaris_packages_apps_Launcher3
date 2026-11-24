package com.android.launcher3.applibrary;

import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.APP_LIBRARY;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.util.FloatProperty;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.applibrary.view.AppLibraryContainerView;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;

public class AppLibraryTransitionController
        implements StateHandler<LauncherState>, OnDeviceProfileChangeListener {

    public static final FloatProperty<AppLibraryTransitionController> APP_LIBRARY_PROGRESS =
            new FloatProperty<AppLibraryTransitionController>("appLibraryProgress") {
                @Override
                public Float get(AppLibraryTransitionController controller) {
                    return controller.mProgress;
                }

                @Override
                public void setValue(AppLibraryTransitionController controller, float progress) {
                    controller.setProgress(progress);
                }
            };

    private final Launcher mLauncher;
    private AppLibraryContainerView mAppLibraryView;
    private float mProgress = 1f;

    public AppLibraryTransitionController(Launcher launcher) {
        mLauncher = launcher;
        mLauncher.addOnDeviceProfileChangeListener(this);
    }

    public void setupViews(AppLibraryContainerView appLibraryView) {
        mAppLibraryView = appLibraryView;
    }

    public float getProgress() {
        return mProgress;
    }

    public void setProgress(float progress) {
        float previous = mProgress;
        mProgress = Utilities.boundToRange(progress, 0f, 1f);
        if (mAppLibraryView == null) {
            return;
        }

        if (previous == 1f && mProgress < 1f) {
            mAppLibraryView.onLibraryOpening();
        }

        // Closing a partly pulled library must not carry vertical translation
        // into the horizontal closing animation.
        if (previous == 0f && mProgress > 0f) {
            mAppLibraryView.cancelPullToSearch();
            mAppLibraryView.onLibraryClosing();
        }

        float width = mLauncher.getDeviceProfile().getDeviceProperties().getWidthPx();
        boolean isRtl = Utilities.isRtl(mLauncher.getResources());

        if (Float.compare(mProgress, 1f) == 0) {
            mAppLibraryView.setVisibility(View.INVISIBLE);
            mAppLibraryView.setTranslationX(isRtl ? -width : width);
            mAppLibraryView.setAlpha(0f);
            if (previous != 1f) mAppLibraryView.onLibraryClosed();
        } else {
            if (mAppLibraryView.getVisibility() != View.VISIBLE) {
                mAppLibraryView.setVisibility(View.VISIBLE);
            }
            float translationX = isRtl ? -width * mProgress : width * mProgress;
            mAppLibraryView.setTranslationX(translationX);
            mAppLibraryView.setAlpha(1f - mProgress);
        }

        if (mProgress == 0f && previous != 0f) mAppLibraryView.onLibraryOpened();
        // WorkspaceStateTransitionAnimation owns Home translation and opacity.
    }

    @Override
    public void setState(LauncherState state) {
        if (state == APP_LIBRARY) {
            setProgress(0f);
        } else {
            setProgress(1f);
            if (mAppLibraryView != null) {
                mAppLibraryView.reset();
            }
        }
    }

    public static void applySwipeConfig(StateAnimationConfig config) {
        int[] properties = {
            StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE,
            StateAnimationConfig.ANIM_WORKSPACE_PAGE_TRANSLATE_X,
            StateAnimationConfig.ANIM_WORKSPACE_SCALE,
            StateAnimationConfig.ANIM_WORKSPACE_FADE,
            StateAnimationConfig.ANIM_HOTSEAT_SCALE,
            StateAnimationConfig.ANIM_HOTSEAT_FADE,
            StateAnimationConfig.ANIM_SCRIM_FADE,
            StateAnimationConfig.ANIM_DEPTH
        };
        for (int property : properties) config.setInterpolator(property, LINEAR);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (toState == APP_LIBRARY) {
            if (mAppLibraryView != null && mProgress == 1f) {
                mAppLibraryView.onLibraryOpening();
                animation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(Animator animator) {
                        if (mAppLibraryView != null && mProgress == 1f) {
                            mAppLibraryView.onLibraryClosed();
                        }
                    }
                });
            }
            float targetProgress = 0f;
            Interpolator interpolator =
                    config.getInterpolator(
                            StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE,
                            config.isUserControlled() ? LINEAR : DECELERATE_1_7);
            animation.setFloat(this, APP_LIBRARY_PROGRESS, targetProgress, interpolator);
        } else {
            float targetProgress = 1f;
            Interpolator interpolator =
                    config.getInterpolator(
                            StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE,
                            config.isUserControlled() ? LINEAR : DECELERATE_1_7);
            animation.setFloat(this, APP_LIBRARY_PROGRESS, targetProgress, interpolator);
            animation.addListener(
                    AnimatorListeners.forSuccessCallback(
                            () -> {
                                if (mAppLibraryView != null && Float.compare(mProgress, 1f) == 0) {
                                    mAppLibraryView.reset();
                                }
                            }));
        }
    }

    @Override
    public void onDeviceProfileChanged(DeviceProfile dp) {
        setProgress(mProgress);
    }
}
