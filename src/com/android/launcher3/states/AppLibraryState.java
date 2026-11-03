package com.android.launcher3.states;

import static com.android.app.animation.Interpolators.ACCELERATE_2;
import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;

import android.graphics.Color;

import android.content.Context;
import com.android.quickstep.util.BaseDepthController;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherUiState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;

public class AppLibraryState extends LauncherState {

    private static final int STATE_FLAGS =
            FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS | FLAG_HOTSEAT_INACCESSIBLE;
    private static final int TRANSITION_DURATION_MS = 300;

    public AppLibraryState(int id) {
        super(id, LAUNCHER_STATE_HOME, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        return TRANSITION_DURATION_MS;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.app_library_title);
    }

    @Override
    public int getTitle() {
        return R.string.app_library_title;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        float width = launcher.getDeviceProfile().getDeviceProperties().getWidthPx();
        boolean isRtl = Utilities.isRtl(launcher.getResources());
        float shift = isRtl ? width * 0.35f : -width * 0.35f;
        return new ScaleAndTranslation(0.95f, shift, NO_OFFSET);
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        return getWorkspaceScaleAndTranslation(launcher);
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(ACCELERATE_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0f;
            }
        };
    }

    @Override
    public PageTranslationProvider getWorkspacePageTranslationProvider(Launcher launcher) {
        return new PageTranslationProvider(DECELERATE_2) {
            @Override
            public float getPageTranslation(int pageIndex) {
                float width = launcher.getDeviceProfile().getDeviceProperties().getWidthPx();
                boolean isRtl = Utilities.isRtl(launcher.getResources());
                return isRtl ? width * 0.35f : -width * 0.35f;
            }
        };
    }

    @Override
    public int getVisibleElements(LauncherUiState launcherUiState) {
        return NONE;
    }

    @Override
    protected <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
            float getDepthUnchecked(DEVICE_PROFILE_CONTEXT context) {
        if (context.getDeviceProfile().shouldShowAllAppsOnSheet(context)) {
            return context.getDeviceProfile().getBottomSheetProfile().getBottomSheetDepth();
        } else {
            return BaseDepthController.DEPTH_60_PERCENT;
        }
    }

    @Override
    public boolean shouldBlurWorkspace(LauncherState targetState) {
        return targetState == APP_LIBRARY || targetState == NORMAL;
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        int backgroundColor = Themes.getAttrColor(launcher, R.attr.allAppsScrimColor);
        return new ScrimColors(backgroundColor, Color.TRANSPARENT);
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        if (launcher.getAppLibraryView() != null && launcher.getAppLibraryView().handleBack()) {
            return;
        }
        if (launcher.getStateManager().getState() == LauncherState.APP_LIBRARY) {
            super.onBackInvoked(launcher);
        }
    }
}
