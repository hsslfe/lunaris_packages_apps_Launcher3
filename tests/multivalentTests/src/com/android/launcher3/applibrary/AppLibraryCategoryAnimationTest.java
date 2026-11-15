package com.android.launcher3.applibrary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppLibraryCategoryAnimationTest {
    @Test
    public void firstFrame_matchesPreviewCenter_evenWithPanelScalingAndInsets() {
        for (float panelCenter : new float[] {180f, 600f, 1100f}) {
            for (float source : new float[] {30f, 280f, 900f}) {
                for (float target : new float[] {60f, 300f, 1000f}) {
                    float translation =
                            AppLibraryCategoryAnimation.previewTranslation(
                                    source, target, panelCenter);
                    float actualCenter =
                            panelCenter
                                    + AppLibraryCategoryAnimation.START_SCALE
                                            * (target + translation - panelCenter);
                    assertEquals(source, actualCenter, .001f);
                }
            }
        }
    }

    @Test
    public void firstFrame_matchesLargeAndMiniIconPreviewSizes() {
        for (float preview : new float[] {24f, 28f, 48f, 64f}) {
            float targetSize = 56f;
            float scale = AppLibraryCategoryAnimation.previewScale(preview, targetSize);
            assertEquals(
                    preview, targetSize * scale * AppLibraryCategoryAnimation.START_SCALE, .001f);
        }
    }

    @Test
    public void missingOrExtremePreview_hasFiniteBoundedScale() {
        assertEquals(1f, AppLibraryCategoryAnimation.previewScale(28f, 0f), 0f);
        assertTrue(AppLibraryCategoryAnimation.previewScale(1f, 200f) >= .2f);
        assertTrue(AppLibraryCategoryAnimation.previewScale(200f, 1f) <= 2f);
    }
}
