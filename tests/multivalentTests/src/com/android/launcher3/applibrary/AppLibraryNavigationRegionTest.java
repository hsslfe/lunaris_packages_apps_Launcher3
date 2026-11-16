package com.android.launcher3.applibrary;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.view.MotionEvent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Ensures library gestures leave the navigation bar and its upper buffer to the system. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryNavigationRegionTest {
    private Launcher mLauncher;

    @Before
    public void setUp() {
        mLauncher = mock(Launcher.class, RETURNS_DEEP_STUBS);
        when(mLauncher.getResources())
                .thenReturn(InstrumentationRegistry.getTargetContext().getResources());
        when(mLauncher.getDragLayer().getHeight()).thenReturn(2400);
        when(mLauncher.getDeviceProfile().getInsets()).thenReturn(new Rect(0, 0, 0, 80));
    }

    @Test
    public void navigationBarAndUpperBuffer_areReserved() {
        assertTrue(isReserved(2399, 0));
        assertTrue(isReserved(2320, 0));
        int boundary = 2320 - Utilities.dpToPx(30, mLauncher);
        assertTrue(isReserved(boundary, 0));
        assertFalse(isReserved(boundary - 1, 0));
        assertFalse(isReserved(1200, 0));
    }

    @Test
    public void navigationEdgeFlag_isReservedRegardlessOfCoordinates() {
        assertTrue(isReserved(1200, Utilities.EDGE_NAV_BAR));
    }

    @Test
    public void zeroBottomInset_stillReservesGestureBuffer() {
        when(mLauncher.getDeviceProfile().getInsets()).thenReturn(new Rect());
        assertTrue(isReserved(2399, 0));
        assertFalse(isReserved(1200, 0));
    }

    private boolean isReserved(float y, int edgeFlags) {
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 100, y, 0);
        try {
            event.setEdgeFlags(edgeFlags);
            return AppLibraryTouchController.isTouchInNavigationRegion(mLauncher, event);
        } finally {
            event.recycle();
        }
    }
}
