package com.android.launcher3.applibrary.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.R;
import com.android.launcher3.applibrary.model.AppCategory;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.SpringEdgeEffectFactory;
import com.android.launcher3.util.TestActivityContext;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryScrollingTest {
    @Rule public SandboxApplication mApp = new SandboxApplication();

    @Rule
    public TestActivityContext mContext = new TestActivityContext(mApp, R.style.LauncherTheme);

    private AppLibraryContainerView createLibrary() {
        AppLibraryContainerView library =
                (AppLibraryContainerView)
                        LayoutInflater.from(mContext)
                                .inflate(R.layout.app_library, new FrameLayout(mContext), false);
        library.setVisibility(View.VISIBLE);
        List<AppCategoryGroup> groups = new ArrayList<>();
        for (AppCategory category : AppCategory.values()) {
            groups.add(new AppCategoryGroup(category, List.of()));
        }
        library.onCategoriesUpdated(groups);
        AppInfo app = new AppInfo();
        app.title = "Recent app";
        app.user = Process.myUserHandle();
        app.componentName = new ComponentName("test.app", "test.app.Main");
        app.intent = new Intent().setComponent(app.componentName);
        app.bitmap = BitmapInfo.fromBitmap(Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888));
        library.onRecentAppsUpdated(List.of(app));
        return library;
    }

    @Test
    public void recentApps_scrollWithCategories_belowFixedSearchBar() {
        AppLibraryContainerView library = createLibrary();
        try {
            library.measure(
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
            library.layout(0, 0, 480, 800);
            RecyclerView grid = library.getGridView();
            View recent = grid.findViewById(R.id.app_library_recent_apps);
            assertNotNull(recent);
            assertEquals(grid, recent.getParent());
            Rect searchBounds =
                    new Rect(
                            0,
                            0,
                            library.getSearchBar().getWidth(),
                            library.getSearchBar().getHeight());
            Rect gridBounds = new Rect(0, 0, grid.getWidth(), grid.getHeight());
            library.offsetDescendantRectToMyCoords(library.getSearchBar(), searchBounds);
            library.offsetDescendantRectToMyCoords(grid, gridBounds);
            assertEquals(searchBounds.bottom, gridBounds.top);
            int searchTop = library.getSearchBar().getTop();
            int recentTop = recent.getTop();
            grid.scrollBy(0, 20);
            assertTrue(recent.getTop() < recentTop);
            assertEquals(searchTop, library.getSearchBar().getTop());
            library.onSearchStateChanged(true);
            assertEquals(View.INVISIBLE, grid.getVisibility());
            library.onSearchStateChanged(false);
            assertEquals(View.VISIBLE, grid.getVisibility());
        } finally {
            library.reset();
        }
    }

    @Test
    public void recentApps_takeFullRow_inTwoAndThreeColumnLayouts_andLeaveNoEmptyRow() {
        AppLibraryContainerView library = createLibrary();
        try {
            RecyclerView grid = library.getGridView();
            GridLayoutManager layout = (GridLayoutManager) grid.getLayoutManager();
            AppLibraryAdapter adapter = (AppLibraryAdapter) grid.getAdapter();
            for (int columns : new int[] {2, 3}) {
                layout.setSpanCount(columns);
                assertEquals(columns, layout.getSpanSizeLookup().getSpanSize(0));
                assertEquals(1, layout.getSpanSizeLookup().getSpanSize(1));
            }
            assertEquals(AppCategory.values().length + 1, adapter.getItemCount());
            library.onRecentAppsUpdated(List.of());
            assertEquals(AppCategory.values().length, adapter.getItemCount());
            assertEquals(1, layout.getSpanSizeLookup().getSpanSize(0));
        } finally {
            library.reset();
        }
    }

    @Test
    public void appDataRefresh_updatesAnOpenFilteredList_withoutClearingItsQuery() {
        AppLibraryContainerView library = createLibrary();
        AppLibraryModel model = mock(AppLibraryModel.class);
        AppInfo original = new AppInfo();
        original.title = "Camera";
        AppInfo updated = new AppInfo();
        updated.title = "Camera";
        when(model.getAllApps()).thenReturn(List.of(original));
        when(model.searchApps("cam")).thenReturn(List.of(original));
        library.setModel(model);
        try {
            library.getSearchBar().setSearching(true);
            library.getSearchBar().getEditText().setText("cam");
            AppLibrarySearchAdapter adapter =
                    (AppLibrarySearchAdapter) library.getSearchResultsView().getAdapter();
            assertSame(original, adapter.getApps().get(0));
            when(model.searchApps("cam")).thenReturn(List.of(updated));
            library.onAllAppsUpdated(List.of(updated));
            assertSame(updated, adapter.getApps().get(0));
            assertEquals("cam", library.getSearchBar().getEditText().getText().toString());
        } finally {
            library.reset();
            library.setModel(null);
        }
    }

    @Test
    public void allLibraryLists_useLauncherSpringPhysics() {
        AppLibraryContainerView library = createLibrary();
        try {
            RecyclerView expanded = library.findViewById(R.id.expanded_sheet_grid);
            for (RecyclerView list :
                    new RecyclerView[] {
                        library.getGridView(), library.getSearchResultsView(), expanded
                    }) {
                assertTrue(list.getEdgeEffectFactory() instanceof SpringEdgeEffectFactory);
            }
        } finally {
            library.reset();
        }
    }
}
