package com.android.launcher3.applibrary.view;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfoWithIcon;

/** Library-specific labels and sizing, independent of the normal drawer's label preference. */
public class AppLibraryIconView extends BubbleTextView {
    private int mAvailableIconSize = Integer.MAX_VALUE;

    public AppLibraryIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setCenterVertically(true);
    }

    @Override
    public boolean shouldShowLabel() {
        return LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext());
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width =
                MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                        ? Integer.MAX_VALUE
                        : MeasureSpec.getSize(widthSpec);
        int height =
                MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                        ? Integer.MAX_VALUE
                        : MeasureSpec.getSize(heightSpec);
        int labelHeight =
                shouldShowLabel()
                        ? (int) Math.ceil(getPaint().getFontSpacing())
                                + Utilities.dpToPx(4, getContext())
                        : 0;
        if (width > 0 && height > 0) {
            mAvailableIconSize =
                    Math.max(
                            1,
                            Math.min(width, height - labelHeight) - Utilities.dpToPx(4, getContext()));
        }
        setCompoundDrawablePadding(shouldShowLabel() ? Utilities.dpToPx(4, getContext()) : 0);
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override
    protected int getIconSizeForItem(ItemInfoWithIcon info) {
        return Math.min(super.getIconSizeForItem(info), mAvailableIconSize);
    }

    @Override
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        return false;
    }
}
