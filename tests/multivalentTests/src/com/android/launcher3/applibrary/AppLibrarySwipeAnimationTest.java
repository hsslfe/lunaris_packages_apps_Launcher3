package com.android.launcher3.applibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppLibrarySwipeAnimationTest {
    private static final float EPSILON = 0.00001f;

    @Test
    public void settleCurve_startsAtReleasePosition_andEndsAtTarget() {
        for (float start : new float[] {0.1f, 0.15f, 0.5f, 0.9f}) {
            for (float end : new float[] {0f, 1f}) {
                for (float slope : new float[] {0f, 1f, 2f, 3f}) {
                    assertEquals(
                            start,
                            start + (end - start) * AppLibrarySwipeAnimation.interpolate(0f, slope),
                            EPSILON);
                    assertEquals(
                            end,
                            start + (end - start) * AppLibrarySwipeAnimation.interpolate(1f, slope),
                            EPSILON);
                }
            }
        }
    }

    @Test
    public void settleCurve_neverReversesOrOvershoots() {
        for (float slope : new float[] {0f, 0.5f, 1f, 2f, 3f}) {
            float previous = 0f;
            for (int i = 0; i <= 1000; i++) {
                float value = AppLibrarySwipeAnimation.interpolate(i / 1000f, slope);
                assertTrue(value >= previous - EPSILON);
                assertTrue(value >= 0f && value <= 1f + EPSILON);
                previous = value;
            }
        }
    }

    @Test
    public void settleCurve_endsAtRest_andStationaryReleaseStartsAtRest() {
        float step = 0.0001f;
        assertEquals(0f, AppLibrarySwipeAnimation.interpolate(step, 0f) / step, 0.002f);
        for (float slope : new float[] {0f, 1f, 2f, 3f}) {
            assertEquals(
                    0f,
                    (1f - AppLibrarySwipeAnimation.interpolate(1f - step, slope)) / step,
                    0.005f);
        }
    }

    @Test
    public void smallSwipeReturn_hasTimeToEaseBack() {
        assertTrue(AppLibrarySwipeAnimation.getDuration(-0.1f, 0f) >= 160);
        assertTrue(AppLibrarySwipeAnimation.getDuration(-0.15f, 0f) >= 160);
        assertEquals(0, AppLibrarySwipeAnimation.getDuration(0f, 0.1f));
    }

    @Test
    public void releaseSpeed_isPreservedWithinMonotonicLimit() {
        assertEquals(1f, AppLibrarySwipeAnimation.getInitialSlope(0.2f, 0.001f, 200), EPSILON);
        assertEquals(1f, AppLibrarySwipeAnimation.getInitialSlope(-0.2f, -0.001f, 200), EPSILON);
        assertEquals(-1f, AppLibrarySwipeAnimation.getInitialSlope(-0.2f, 0.001f, 200), EPSILON);
        assertEquals(3f, AppLibrarySwipeAnimation.getInitialSlope(0.1f, 1f, 200), EPSILON);
    }

    @Test
    public void flingDuration_isBounded_andSymmetric() {
        long fling = AppLibrarySwipeAnimation.getDuration(0.5f, 0.01f);
        assertTrue(fling >= 100 && fling <= 320);
        assertTrue(fling < AppLibrarySwipeAnimation.getDuration(0.5f, 0f));
        assertEquals(fling, AppLibrarySwipeAnimation.getDuration(-0.5f, -0.01f));
    }

    @Test
    public void cancelledSwipe_preservesOutwardMomentumBeforeReturning() {
        float start = .15f;
        float end = 0f;
        float velocity = .0003f;
        long duration = AppLibrarySwipeAnimation.getDuration(end - start, velocity);
        float slope = AppLibrarySwipeAnimation.getInitialSlope(end - start, velocity, duration);
        float step = .0001f;
        float first = start + (end - start) * AppLibrarySwipeAnimation.interpolate(step, slope);
        assertTrue(first > start);
        assertEquals(velocity, (first - start) / (step * duration), .00001f);
        for (int i = 0; i <= 1000; i++) {
            float position =
                    start + (end - start) * AppLibrarySwipeAnimation.interpolate(i / 1000f, slope);
            assertTrue(position >= -EPSILON && position < .2f);
        }
        assertEquals(
                end,
                start + (end - start) * AppLibrarySwipeAnimation.interpolate(1f, slope),
                EPSILON);
    }
}
