package com.android.launcher3.applibrary;

/** Screen-space geometry shared by the category opening and closing icon morphs. */
public final class AppLibraryCategoryAnimation {
    public static final float START_SCALE = .92f;

    private AppLibraryCategoryAnimation() {}

    public static float previewTranslation(
            float sourceCenter, float targetCenter, float panelCenter) {
        return (sourceCenter - panelCenter) / START_SCALE - (targetCenter - panelCenter);
    }

    public static float previewScale(float sourceSize, float targetSize) {
        if (targetSize <= 0f) return 1f;
        return Math.max(.2f, Math.min(2f, sourceSize / targetSize / START_SCALE));
    }
}
