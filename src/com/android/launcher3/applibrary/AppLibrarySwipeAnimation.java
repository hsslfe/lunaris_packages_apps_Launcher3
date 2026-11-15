package com.android.launcher3.applibrary;

/** Motion policy for settling an App Library swipe without a release-position jump or overshoot. */
public final class AppLibrarySwipeAnimation {
    private AppLibrarySwipeAnimation() { }

    /** Distance is signed progress; velocity is progress per millisecond. */
    public static long getDuration(float distance, float velocity) {
        float remaining = Math.abs(distance);
        if (remaining == 0f) {
            return 0;
        }
        float duration = 120f + 200f * (float) Math.sqrt(Math.min(remaining, 1f));
        float towardTargetVelocity = velocity * Math.signum(distance);
        if (towardTargetVelocity > 0f) {
            duration = Math.min(duration, 2f * remaining / towardTargetVelocity);
        }
        // Give a short cancelled swipe time to ease back instead of snapping shut.
        return Math.max(100, Math.min(320, Math.round(duration)));
    }

    /** Normalized initial speed, bounded to keep the cubic monotonic. */
    public static float getInitialSlope(float distance, float velocity, long duration) {
        if (distance == 0f) {
            return 0f;
        }
        return Math.max(0f, Math.min(3f, velocity * duration / distance));
    }

    /** Cubic Hermite curve: preserves bounded release speed and ends at rest without bouncing. */
    public static float interpolate(float time, float initialSlope) {
        float t = Math.max(0f, Math.min(1f, time));
        float slope = Math.max(0f, Math.min(3f, initialSlope));
        return t * t * (3f - 2f * t) + slope * t * (1f - t) * (1f - t);
    }
}
