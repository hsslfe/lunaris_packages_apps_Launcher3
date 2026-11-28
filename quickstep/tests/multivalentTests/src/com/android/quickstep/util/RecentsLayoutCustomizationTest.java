package com.android.quickstep.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecentsLayoutCustomizationTest {
    private final Rect mStock = new Rect(100, 100, 400, 700);
    private final Rect mSafe = new Rect(20, 40, 480, 730);

    @Test
    public void defaultsPreserveStockBounds() {
        Rect out = new Rect();
        RecentsLayoutCustomization.resolveCard(mStock, mSafe, 100, 0, out);
        assertEquals(mStock, out);
        assertEquals(100, RecentsLayoutCustomization.resolveButtonBottom(
                100, 1000, 140, 730, 40, 0));
    }

    @Test
    public void cardMarginDoesNotResizeEvenAtBounds() {
        Rect expected = new Rect();
        Rect actual = new Rect();
        for (int size = 75; size <= 110; size++) {
            RecentsLayoutCustomization.resolveCard(mStock, mSafe, size, 0, expected);
            for (int offset = -200; offset <= 200; offset++) {
                RecentsLayoutCustomization.resolveCard(mStock, mSafe, size, offset, actual);
                assertEquals(expected.width(), actual.width());
                assertEquals(expected.height(), actual.height());
                assertTrue(mSafe.contains(actual));
            }
        }
    }

    @Test
    public void cardAndButtonsRemainInIndependentRegions() {
        int boundary = RecentsLayoutCustomization.cardBoundary(700, 760);
        assertEquals(730, boundary);
        Rect actual = new Rect();
        for (int cardOffset = -40; cardOffset <= 40; cardOffset++) {
            RecentsLayoutCustomization.resolveCard(mStock, mSafe, 100, cardOffset, actual);
            assertTrue(actual.bottom <= boundary);
            for (int buttonOffset = -40; buttonOffset <= 40; buttonOffset++) {
                int bottom = RecentsLayoutCustomization.resolveButtonBottom(
                        100, 1000, 140, boundary, 40, buttonOffset);
                assertTrue(1000 - bottom - 140 >= boundary);
                assertTrue(bottom >= 40);
            }
        }
    }

    @Test
    public void noFreeSpaceDoesNotResizeCardToCreateTravel() {
        Rect out = new Rect();
        RecentsLayoutCustomization.resolveCard(mStock, mStock, 100, 200, out);
        assertEquals(mStock, out);
        RecentsLayoutCustomization.resolveCard(mStock, mStock, 100, -200, out);
        assertEquals(mStock, out);
    }
}
