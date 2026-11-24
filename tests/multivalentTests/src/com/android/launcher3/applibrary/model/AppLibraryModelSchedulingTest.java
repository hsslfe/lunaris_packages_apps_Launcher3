package com.android.launcher3.applibrary.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.os.Process;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.model.data.AppInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryModelSchedulingTest {
    private final Context mContext = mock(Context.class);
    private final AllAppsStore mStore = mock(AllAppsStore.class);
    private final ArrayDeque<Runnable> mBackground = new ArrayDeque<>();
    private final ArrayDeque<Runnable> mMain = new ArrayDeque<>();

    @Before
    public void setUp() {
        when(mContext.getApplicationContext()).thenReturn(mContext);
    }

    private AppInfo app(String name, long installTime) {
        AppInfo app = new AppInfo();
        app.title = name;
        app.componentName = new ComponentName(name, name + ".Main");
        app.user = Process.myUserHandle();
        app.firstInstallTime = installTime;
        return app;
    }

    private AppLibraryModel model(boolean enabled) {
        return new AppLibraryModel(mContext, mStore, enabled, mBackground::add, mMain::add);
    }

    private void drain() {
        int turns = 0;
        while (!mBackground.isEmpty() || !mMain.isEmpty()) {
            assertTrue("Refreshes must finish without a scheduling loop", ++turns < 30);
            if (!mBackground.isEmpty()) mBackground.remove().run();
            while (!mMain.isEmpty()) mMain.remove().run();
        }
    }

    @Test
    public void disabledFeature_doesNoLoading_andEnablingUsesCurrentApps() {
        AppInfo app = app("camera", 100);
        when(mStore.getApps()).thenReturn(new AppInfo[] {app});
        AppLibraryModel model = model(false);
        try {
            model.onAppsUpdated();
            model.refreshRecentApps();
            assertTrue(mBackground.isEmpty());
            assertTrue(model.getAllApps().isEmpty());
            model.setEnabled(true);
            drain();
            assertEquals(List.of(app), model.getAllApps());
            model.setEnabled(false);
            assertTrue(model.getAllApps().isEmpty());
            assertTrue(model.getCategoryGroups().isEmpty());
        } finally {
            model.destroy();
        }
    }

    @Test
    public void rapidUpdates_coalesce_andNeverPublishTheObsoleteSnapshot() {
        AppInfo old = app("old", 100);
        AppInfo latest = app("latest", 200);
        when(mStore.getApps()).thenReturn(new AppInfo[] {old});
        AppLibraryModel model = model(true);
        try {
            when(mStore.getApps()).thenReturn(new AppInfo[] {latest});
            for (int i = 0; i < 50; i++) model.onAppsUpdated();
            assertEquals(1, mBackground.size());
            mBackground.remove().run();
            mMain.remove().run();
            assertTrue(model.getAllApps().isEmpty());
            assertEquals(1, mBackground.size());
            drain();
            assertEquals(List.of(latest), model.getAllApps());
        } finally {
            model.destroy();
        }
    }

    @Test
    public void destroyBeforeQueuedWork_finishesWithoutNotifyingOrRetainingAppLists() {
        when(mStore.getApps()).thenReturn(new AppInfo[] {app("camera", 100)});
        AppLibraryModel model = model(true);
        AppLibraryModel.AppLibraryModelListener listener =
                mock(AppLibraryModel.AppLibraryModelListener.class);
        model.addListener(listener);
        clearInvocations(listener);
        model.destroy();
        drain();
        verifyNoInteractions(listener);
        verify(mStore).removeUpdateListener(model);
        assertTrue(model.getAllApps().isEmpty());
        assertTrue(model.getCategoryGroups().isEmpty());
    }

    @Test
    public void recentRefreshes_haveOnlyOnePendingQuery() {
        when(mStore.getApps()).thenReturn(new AppInfo[] {app("camera", 100)});
        AppLibraryModel model = model(true);
        try {
            mBackground.remove().run();
            mMain.remove().run();
            assertTrue("Closed library must not query recent apps", mBackground.isEmpty());
            for (int i = 0; i < 100; i++) model.refreshRecentApps();
            assertEquals(1, mBackground.size());
            drain();
        } finally {
            model.destroy();
        }
    }

    @Test
    public void closedLibrary_appStoreUpdatesDoNotQueryRecentApps() {
        when(mStore.getApps()).thenReturn(new AppInfo[] {app("camera", 100)});
        AppLibraryModel model = model(true);
        try {
            drain();
            model.refreshRecentApps();
            drain();
            model.stopRecentAppsRefresh();
            model.onAppsUpdated();
            mBackground.remove().run();
            mMain.remove().run();
            assertTrue(mBackground.isEmpty());
        } finally {
            model.destroy();
        }
    }

    @Test
    public void openingBeforeCategoriesLoad_queriesOnceAfterBuild() {
        when(mStore.getApps()).thenReturn(new AppInfo[] {app("camera", 100)});
        AppLibraryModel model = model(true);
        try {
            model.refreshRecentApps();
            assertEquals(1, mBackground.size());
            mBackground.remove().run();
            mMain.remove().run();
            assertEquals(1, mBackground.size());
            drain();
            model.refreshRecentApps();
            assertTrue(mBackground.isEmpty());
        } finally {
            model.destroy();
        }
    }

    @Test
    public void rapidReopen_invalidatesOldQueryAndSchedulesExactlyOneReplacement() {
        when(mStore.getApps()).thenReturn(new AppInfo[] {app("camera", 100)});
        AppLibraryModel model = model(true);
        try {
            drain();
            model.refreshRecentApps();
            model.stopRecentAppsRefresh();
            model.refreshRecentApps();
            assertEquals(1, mBackground.size());
            mBackground.remove().run();
            mMain.remove().run();
            assertEquals(1, mBackground.size());
            drain();
            assertTrue(mBackground.isEmpty());
            model.stopRecentAppsRefresh();
            model.refreshRecentApps();
            assertEquals("No five-second stale-data throttle on reopen", 1, mBackground.size());
            drain();
        } finally {
            model.destroy();
        }
    }

    @Test
    public void recentlyAdded_usesAlreadyLoadedInstallTimes() {
        AppInfo old = app("old", 100);
        AppInfo newest = app("newest", 900);
        when(mStore.getApps()).thenReturn(new AppInfo[] {old, newest});
        AppLibraryModel model = model(true);
        try {
            assertTrue(model.getAllApps().isEmpty());
            drain();
            AppCategoryGroup recent =
                    model.getCategoryGroups().stream()
                            .filter(group -> group.getCategory() == AppCategory.RECENTLY_ADDED)
                            .findFirst()
                            .orElseThrow();
            assertEquals(List.of(newest, old), recent.getApps());
        } finally {
            model.destroy();
        }
    }
}
