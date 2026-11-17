package com.android.quickstep;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.SandboxApplication;
import com.android.quickstep.util.HomeUnlockReveal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class HomeUnlockRevealControllerTest {
    @Rule public SandboxApplication mContext = new SandboxApplication();

    private final QuickstepLauncher mLauncher = mock(QuickstepLauncher.class);
    private final HomeUnlockReveal mAnimation = mock(HomeUnlockReveal.class);
    private final DragLayer mDragLayer = mock(DragLayer.class);
    private final List<Runnable> mFrames = new ArrayList<>();
    private final List<Runnable> mTimeouts = new ArrayList<>();
    private HomeUnlockRevealController mController;

    @Before
    public void setUp() {
        when(mLauncher.getApplicationContext()).thenReturn(mContext);
        when(mLauncher.getDragLayer()).thenReturn(mDragLayer);
        when(mAnimation.prepareForUnlock()).thenReturn(true);
        when(mAnimation.isPrepared()).thenReturn(true);
        when(mAnimation.finishPreparation()).thenReturn(true);
        when(mAnimation.getDuration()).thenReturn(600L);
        doAnswer(
                        call -> {
                            mFrames.add(call.getArgument(0));
                            return null;
                        })
                .when(mDragLayer)
                .postOnAnimation(any(Runnable.class));
        doAnswer(
                        call -> {
                            mTimeouts.add(call.getArgument(0));
                            return true;
                        })
                .when(mDragLayer)
                .postDelayed(any(Runnable.class), anyLong());
        doAnswer(
                        call -> {
                            Runnable task = call.getArgument(0);
                            mFrames.remove(task);
                            mTimeouts.remove(task);
                            return true;
                        })
                .when(mDragLayer)
                .removeCallbacks(any(Runnable.class));
        LauncherPrefs.get(mContext).put(LauncherPrefs.WORKSPACE_UNLOCK_ANIMATION, true);
        mController = new HomeUnlockRevealController(mLauncher, mAnimation);
    }

    @After
    public void tearDown() {
        mController.cancel();
    }

    @Test
    public void screenOnAndUserPresent_doNotQueueFallbackAnimation() {
        mController.onScreenOnChanged(false);
        mController.onScreenOnChanged(true);
        mController.onUserPresent();
        assertEquals(0, mFrames.size());
        assertEquals(0, mTimeouts.size());
        verify(mAnimation, never()).prepareForUnlock();
    }

    @Test
    public void userPresent_preservesInteractiveLockscreenReveal() {
        mController.prepareForUnlock(false, null, 0);
        mController.setUnlockAmount(.4f, false);
        clearInvocations(mAnimation);
        mController.onUserPresent();
        verify(mAnimation, never()).reset();
        verify(mAnimation, never()).prepareForUnlock();
        assertEquals(0, mFrames.size());
    }

    @Test
    public void normalSystemUiUnlock_keepsWorkspaceTimelineWithoutReplay() {
        mController.prepareForUnlock(false, null, 0);
        mController.playUnlockAnimation(true, 300, 0);
        mController.onUserPresent();
        verify(mAnimation, times(1)).prepareForUnlock();
        verify(mAnimation).getDuration();
        assertEquals(0, mFrames.size());
        assertEquals(0, mTimeouts.size());
    }

    @Test
    public void playWithoutPreparation_doesNotStartSyntheticReveal() {
        mController.playUnlockAnimation(true, 300, 0);
        verify(mAnimation, never()).prepareForUnlock();
        verify(mAnimation, never()).finishPreparation();
        verify(mAnimation).reset();
        assertEquals(0, mFrames.size());
    }

    @Test
    public void zeroDurationCallback_restoresPreparedSceneWithoutAnimation() {
        mController.prepareForUnlock(false, null, 0);
        clearInvocations(mAnimation);
        mController.playUnlockAnimationWithWallpaperDepth(true, 0, 0, true);
        verify(mAnimation, never()).getDuration();
        verify(mAnimation).reset();
        assertEquals(0, mTimeouts.size());
    }

    @Test
    public void screenOff_cancelsPreparedSceneAndTimeout() {
        mController.prepareForUnlock(false, null, 0);
        assertEquals(1, mTimeouts.size());
        clearInvocations(mAnimation);
        mController.onScreenOnChanged(false);
        verify(mAnimation).reset();
        assertEquals(0, mTimeouts.size());
        mController.onScreenOnChanged(true);
        verify(mAnimation, never()).prepareForUnlock();
    }

    @Test
    public void preparationTimeout_restoresIconsAndRemovesCallback() {
        mController.prepareForUnlock(false, null, 0);
        assertEquals(1, mTimeouts.size());
        clearInvocations(mAnimation);
        mTimeouts.get(0).run();
        verify(mAnimation).reset();
        assertEquals(0, mTimeouts.size());
    }

    @Test
    public void invalidUnlockAmount_doesNotChangePreparedScene() {
        mController.prepareForUnlock(false, null, 0);
        clearInvocations(mAnimation);
        mController.setUnlockAmount(Float.NaN, false);
        verify(mAnimation, never()).finishPreparation();
        verify(mAnimation, never()).reset();
        assertEquals(1, mTimeouts.size());
    }

    @Test
    public void completedInteractiveUnlock_restoresScene() {
        mController.prepareForUnlock(false, null, 0);
        mController.setUnlockAmount(.5f, false);
        verify(mAnimation).setUnlockAmount(.5f);
        clearInvocations(mAnimation);
        mController.setUnlockAmount(1f, true);
        verify(mAnimation).reset();
        assertEquals(0, mTimeouts.size());
    }

    @Test
    public void failedPreparation_doesNotScheduleTimeout() {
        when(mAnimation.prepareForUnlock()).thenReturn(false);
        mController.prepareForUnlock(false, null, 0);
        assertEquals(0, mTimeouts.size());
        mController.playUnlockAnimation(true, 300, 0);
        verify(mAnimation, never()).finishPreparation();
    }

    @Test
    public void disabledPreference_doesNotPrepareOrAnimate() {
        LauncherPrefs.get(mContext).put(LauncherPrefs.WORKSPACE_UNLOCK_ANIMATION, false);
        mController.prepareForUnlock(false, null, 0);
        mController.playUnlockAnimation(true, 300, 0);
        mController.onUserPresent();
        assertEquals(0, mFrames.size());
        assertEquals(0, mTimeouts.size());
        verify(mAnimation, never()).prepareForUnlock();
    }
}
