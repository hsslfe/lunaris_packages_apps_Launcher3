package com.android.launcher3.applibrary.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryAppearanceTest {
    @Rule public SandboxApplication mApp = new SandboxApplication();

    @Rule
    public TestActivityContext mContext = new TestActivityContext(mApp, R.style.LauncherTheme);

    @Test
    public void labelsOn_growsCardHeight_withoutChangingItsWidth() {
        LauncherPrefs.get(mContext).put(LauncherPrefs.APP_LIBRARY_SHOW_LABELS, false);
        AppCategoryCardView card =
                (AppCategoryCardView)
                        LayoutInflater.from(mContext)
                                .inflate(
                                        R.layout.app_library_category_card,
                                        new FrameLayout(mContext),
                                        false);
        int widthSpec =
                View.MeasureSpec.makeMeasureSpec(
                        Utilities.dpToPx(180, mContext), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        card.measure(widthSpec, heightSpec);
        int heightWithoutLabels = card.getMeasuredHeight();
        int width = card.getMeasuredWidth();
        LauncherPrefs.get(mContext).put(LauncherPrefs.APP_LIBRARY_SHOW_LABELS, true);
        card.requestLayout();
        card.measure(widthSpec, heightSpec);
        assertEquals(width, card.getMeasuredWidth());
        assertTrue(card.getMeasuredHeight() > heightWithoutLabels);
    }

    @Test
    public void categoryTitle_isInsideTheOriginalCardSurface() {
        AppCategoryCardView card =
                (AppCategoryCardView)
                        LayoutInflater.from(mContext)
                                .inflate(
                                        R.layout.app_library_category_card,
                                        new FrameLayout(mContext),
                                        false);
        assertEquals(
                card.findViewById(R.id.category_card_content),
                card.findViewById(R.id.category_title).getParent());
    }

    @Test
    public void labelToggle_removesTextWithoutChangingAccessibleName() {
        LauncherPrefs.get(mContext).put(LauncherPrefs.APP_LIBRARY_SHOW_LABELS, false);
        AppLibraryIconView icon =
                (AppLibraryIconView)
                        LayoutInflater.from(mContext)
                                .inflate(
                                        R.layout.app_library_icon,
                                        new FrameLayout(mContext),
                                        false);
        icon.setContentDescription("Camera");
        icon.applyLabel("Camera");
        assertFalse(icon.shouldShowLabel());
        assertTrue(icon.getText().length() == 0);
        assertEquals("Camera", icon.getContentDescription());
        LauncherPrefs.get(mContext).put(LauncherPrefs.APP_LIBRARY_SHOW_LABELS, true);
        icon.applyLabel("Camera");
        assertTrue(icon.shouldShowLabel());
        assertEquals("Camera", icon.getText().toString());
    }
}
