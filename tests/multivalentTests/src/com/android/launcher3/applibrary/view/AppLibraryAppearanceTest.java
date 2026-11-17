package com.android.launcher3.applibrary.view;

import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.AppInfo;
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

    @Test
    public void appList_hidesCategoriesButKeepsItsSurface_andCancelRestoresCategories() {
        AppLibraryContainerView library =
                (AppLibraryContainerView)
                        LayoutInflater.from(mContext)
                                .inflate(R.layout.app_library, new FrameLayout(mContext), false);
        try {
            library.onSearchStateChanged(true);
            assertEquals(View.INVISIBLE, library.getGridView().getVisibility());
            assertEquals(0f, library.getGridView().getAlpha(), 0f);
            assertEquals(View.VISIBLE, library.getSearchResultsView().getVisibility());
            assertNotNull(library.getSearchResultsView().getBackground());
            library.onSearchStateChanged(false);
            assertEquals(View.VISIBLE, library.getGridView().getVisibility());
        } finally {
            library.reset();
        }
    }

    @Test
    public void allLibraryIcons_followThemePreference_withoutReinflating() {
        FrameLayout parent = new FrameLayout(mContext);
        View card =
                LayoutInflater.from(mContext)
                        .inflate(R.layout.app_library_category_card, parent, false);
        View row =
                LayoutInflater.from(mContext)
                        .inflate(R.layout.app_library_search_row, parent, false);
        AppLibraryIconView icon =
                (AppLibraryIconView)
                        LayoutInflater.from(mContext)
                                .inflate(R.layout.app_library_icon, parent, false);
        AppInfo app = new AppInfo();
        AppLibraryIconView[] icons = {
            icon,
            card.findViewById(R.id.card_slot_0),
            card.findViewById(R.id.mini_icon_0),
            card.findViewById(R.id.mini_icon_3),
            row.findViewById(R.id.app_icon)
        };
        for (boolean themed : new boolean[] {false, true, false}) {
            LauncherPrefs.get(mContext).put(LauncherPrefs.ALLAPPS_ICON_CUSTOMIZATION, themed);
            for (AppLibraryIconView view : icons) {
                assertEquals(
                        themed ? FLAG_THEMED : 0,
                        view.getIconCreationFlagsForInfo(app) & FLAG_THEMED);
            }
        }
        LauncherPrefs.get(mContext).put(LauncherPrefs.APP_LIBRARY_SHOW_LABELS, true);
        assertFalse(((AppLibraryIconView) row.findViewById(R.id.app_icon)).shouldShowLabel());
        assertFalse(((AppLibraryIconView) card.findViewById(R.id.mini_icon_0)).shouldShowLabel());
        assertTrue(icon.shouldShowLabel());
        assertNotNull(row.findViewById(R.id.app_title));
    }

    @Test
    public void expandedCategory_has85PercentOpaqueSurface() {
        View sheet =
                LayoutInflater.from(mContext)
                        .inflate(
                                R.layout.app_library_expanded_sheet,
                                new FrameLayout(mContext),
                                false);
        GradientDrawable background =
                (GradientDrawable) sheet.findViewById(R.id.expanded_sheet_card).getBackground();
        assertEquals(217, Color.alpha(background.getColor().getDefaultColor()));
    }
}
