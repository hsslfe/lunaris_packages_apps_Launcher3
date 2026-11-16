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

import android.app.KeyguardManager;
import android.os.PowerManager;

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
    private final KeyguardManager mKeyguard = mock(KeyguardManager.class);
    private final PowerManager mPower = mock(PowerManager.class);
    private final List<Runnable> mFrames = new ArrayList<>();
    private final List<Runnable> mTimeouts = new ArrayList<>();
    private HomeUnlockRevealController mController;

    @Before
    public void setUp() {
        when(mLauncher.getApplicationContext()).thenReturn(mContext);
        when(mLauncher.getDragLayer()).thenReturn(mDragLayer);
        when(mLauncher.getSystemService(KeyguardManager.class)).thenReturn(mKeyguard);
        when(mLauncher.getSystemService(PowerManager.class)).thenReturn(mPower);
        when(mLauncher.hasBeenResumed()).thenReturn(true);
        when(mDragLayer.hasWindowFocus()).thenReturn(true);
        when(mPower.isInteractive()).thenReturn(true);
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

    private void runFrame() {
        assertEquals(1, mFrames.size());
        mFrames.remove(0).run();
    }

    @Test
    public void screenOffFingerprint_startsWithoutAnySystemUiAnimationCallbacks() {
        mController.onScreenOnChanged(false);
        mController.onScreenOnChanged(true);
        runFrame();
        verify(mAnimation).prepareForUnlock();
        verify(mAnimation).finishPreparation();
        mController.onUserPresent();
        assertEquals(0, mFrames.size());
    }

    @Test
    public void userPresentBeforeHomeResumes_waitsForReadyFrame() {
        when(mLauncher.hasBeenResumed()).thenReturn(false);
        mController.onUserPresent();
        runFrame();
        verify(mAnimation, never()).prepareForUnlock();
        when(mLauncher.hasBeenResumed()).thenReturn(true);
        mController.onLauncherReady();
        runFrame();
        verify(mAnimation).prepareForUnlock();
    }

    @Test
    public void lockedOrNonInteractiveScreen_doesNotStartReveal() {
        when(mKeyguard.isKeyguardLocked()).thenReturn(true);
        mController.onUserPresent();
        runFrame();
        verify(mAnimation, never()).prepareForUnlock();
        when(mKeyguard.isKeyguardLocked()).thenReturn(false);
        when(mPower.isInteractive()).thenReturn(false);
        runFrame();
        verify(mAnimation, never()).prepareForUnlock();
        when(mPower.isInteractive()).thenReturn(true);
        runFrame();
        verify(mAnimation).prepareForUnlock();
    }

    @Test
    public void normalLockscreenUnlock_userPresentDoesNotReplayIt() {
        mController.prepareForUnlock(false, null, 0);
        mController.playUnlockAnimation(true, 300, 0);
        mController.onUserPresent();
        verify(mAnimation, times(1)).prepareForUnlock();
        assertEquals(0, mFrames.size());
    }

    @Test
    public void zeroDurationWithoutPrepare_stillUsesWorkspaceRevealTimeline() {
        mController.playUnlockAnimationWithWallpaperDepth(true, 0, 0, true);
        verify(mAnimation).prepareForUnlock();
        verify(mAnimation).finishPreparation();
        verify(mAnimation).getDuration();
    }

    @Test
    public void lateSystemUiCompletion_doesNotCancelFingerprintFallback() {
        mController.onUserPresent();
        runFrame();
        clearInvocations(mAnimation);
        mController.prepareForUnlock(false, null, 0);
        mController.setUnlockAmount(1f, true);
        verify(mAnimation, never()).reset();
        verify(mAnimation, never()).prepareForUnlock();
    }

    @Test
    public void timeoutOrScreenOff_discardsPendingUnlock() {
        when(mLauncher.hasBeenResumed()).thenReturn(false);
        mController.onUserPresent();
        runFrame();
        mTimeouts.get(0).run();
        when(mLauncher.hasBeenResumed()).thenReturn(true);
        mController.onLauncherReady();
        assertEquals(0, mFrames.size());
        mController.onScreenOnChanged(false);
        mController.onUserPresent();
        assertEquals(1, mFrames.size());
        mController.onScreenOnChanged(false);
        assertEquals(0, mFrames.size());
        verify(mAnimation, never()).prepareForUnlock();
    }

    @Test
    public void expiredWake_restoresPreparedIcons_andRejectsLateReplay() {
        mController.prepareForUnlock(false, null, 0);
        clearInvocations(mAnimation);
        when(mLauncher.hasBeenResumed()).thenReturn(false);
        mController.onUserPresent();
        runFrame();
        mTimeouts.get(mTimeouts.size() - 1).run();
        verify(mAnimation).reset();
        mController.prepareForUnlock(false, null, 0);
        mController.playUnlockAnimation(true, 300, 0);
        verify(mAnimation, never()).prepareForUnlock();
    }

    @Test
    public void disabledPreference_doesNotAnimateUserPresent() {
        LauncherPrefs.get(mContext).put(LauncherPrefs.WORKSPACE_UNLOCK_ANIMATION, false);
        mController.onUserPresent();
        assertEquals(0, mFrames.size());
        verify(mAnimation, never()).prepareForUnlock();
    }
}
