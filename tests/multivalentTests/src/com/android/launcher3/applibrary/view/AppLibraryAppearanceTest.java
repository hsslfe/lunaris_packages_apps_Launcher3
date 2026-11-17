package com.android.launcher3.applibrary.view;

import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.applibrary.model.AppCategory;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.TestActivityContext;
import com.android.launcher3.util.Themes;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

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
    public void lightMode_keepsLibraryTextWhite_andMatchesCategorySurface() {
        Configuration config = new Configuration(mContext.getResources().getConfiguration());
        config.uiMode =
                (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | Configuration.UI_MODE_NIGHT_NO;
        ContextThemeWrapper light = new ContextThemeWrapper(mContext, R.style.LauncherTheme);
        light.applyOverrideConfiguration(config);
        assertFalse(Utilities.isDarkTheme(light));
        LayoutInflater inflater = LayoutInflater.from(light);
        FrameLayout parent = new FrameLayout(light);
        View card = inflater.inflate(R.layout.app_library_category_card, parent, false);
        View row = inflater.inflate(R.layout.app_library_search_row, parent, false);
        View sheet = inflater.inflate(R.layout.app_library_expanded_sheet, parent, false);
        for (TextView text :
                new TextView[] {
                    card.findViewById(R.id.category_title), card.findViewById(R.id.card_slot_0),
                    row.findViewById(R.id.app_title), sheet.findViewById(R.id.expanded_sheet_title)
                }) {
            assertEquals(Color.WHITE, text.getCurrentTextColor());
        }
        GradientDrawable categoryBackground =
                (GradientDrawable) card.findViewById(R.id.category_card_content).getBackground();
        GradientDrawable expandedBackground =
                (GradientDrawable) sheet.findViewById(R.id.expanded_sheet_card).getBackground();
        assertEquals(
                categoryBackground.getColor().getDefaultColor(),
                expandedBackground.getColor().getDefaultColor());
    }

    @Test
    public void recyclingIcon_releasesItsAppAndDrawableReferences() {
        AppLibraryIconView icon =
                (AppLibraryIconView)
                        LayoutInflater.from(mContext)
                                .inflate(
                                        R.layout.app_library_icon,
                                        new FrameLayout(mContext),
                                        false);
        AppInfo app = new AppInfo();
        app.title = "Camera";
        app.componentName = new ComponentName("test.camera", "test.camera.Main");
        app.intent = new Intent().setComponent(app.componentName);
        app.bitmap = BitmapInfo.fromBitmap(Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888));
        icon.applyFromApplicationInfo(app);
        assertNotNull(icon.getIcon());
        icon.clearAppIcon();
        assertNull(icon.getTag());
        assertNull(icon.getIcon());
        for (android.graphics.drawable.Drawable drawable : icon.getCompoundDrawablesRelative()) {
            assertNull(drawable);
        }
    }

    @Test
    public void hidingExpandedCategory_releasesItsAppList() {
        AppCategoryExpandedSheet sheet =
                (AppCategoryExpandedSheet)
                        LayoutInflater.from(mContext)
                                .inflate(
                                        R.layout.app_library_expanded_sheet,
                                        new FrameLayout(mContext),
                                        false);
        androidx.recyclerview.widget.RecyclerView grid =
                sheet.findViewById(R.id.expanded_sheet_grid);
        sheet.show(new AppCategoryGroup(AppCategory.SOCIAL, List.of(new AppInfo())), null);
        assertEquals(1, grid.getAdapter().getItemCount());
        sheet.hide(false);
        assertEquals(0, grid.getAdapter().getItemCount());
        assertFalse(sheet.isOpen());
    }

    @Test
    public void darkMode_expandedCategory_keepsExistingSurface() {
        Configuration config = new Configuration(mContext.getResources().getConfiguration());
        config.uiMode =
                (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                        | Configuration.UI_MODE_NIGHT_YES;
        ContextThemeWrapper dark = new ContextThemeWrapper(mContext, R.style.LauncherTheme);
        dark.applyOverrideConfiguration(config);
        assertTrue(Utilities.isDarkTheme(dark));
        View sheet =
                LayoutInflater.from(dark)
                        .inflate(R.layout.app_library_expanded_sheet, new FrameLayout(dark), false);
        GradientDrawable background =
                (GradientDrawable) sheet.findViewById(R.id.expanded_sheet_card).getBackground();
        int expected =
                ColorUtils.blendARGB(
                        Themes.getAttrColor(dark, R.attr.allAppsSurfaceLow), Color.BLACK, .40f);
        assertEquals(
                ColorUtils.setAlphaComponent(expected, 217),
                background.getColor().getDefaultColor());
    }
}
