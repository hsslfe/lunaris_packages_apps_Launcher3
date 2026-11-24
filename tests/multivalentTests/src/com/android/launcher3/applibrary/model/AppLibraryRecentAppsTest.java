package com.android.launcher3.applibrary.model;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.model.data.AppInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryRecentAppsTest {
    private final Context mContext = mock(Context.class);
    private final UserManager mUsers = mock(UserManager.class);
    private final UserHandle mPersonal = Process.myUserHandle();

    @Before
    public void setUp() {
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUsers);
    }

    private UsageStatsManager usageFor(UserHandle profile) {
        Context userContext = mock(Context.class);
        UsageStatsManager usage = mock(UsageStatsManager.class);
        when(mUsers.isUserUnlocked(profile)).thenReturn(true);
        when(mContext.createContextAsUser(profile, 0)).thenReturn(userContext);
        when(userContext.getSystemService(UsageStatsManager.class)).thenReturn(usage);
        return usage;
    }

    private UsageStats stat(String pkg, long lastUsed) {
        UsageStats stat = mock(UsageStats.class);
        when(stat.getPackageName()).thenReturn(pkg);
        when(stat.getLastTimeUsed()).thenReturn(lastUsed);
        return stat;
    }

    private AppInfo app(String pkg, String activity, UserHandle user) {
        AppInfo app = new AppInfo();
        app.componentName = new ComponentName(pkg, activity);
        app.user = user;
        return app;
    }

    @Test
    public void newestAppsComeFirst_andMultipleActivitiesDoNotDuplicateAnApp() {
        UsageStatsManager usage = usageFor(mPersonal);
        when(usage.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(stat("a", 1000), stat("b", 2000)));
        AppInfo a = app("a", "Main", mPersonal);
        AppInfo b = app("b", "Main", mPersonal);
        AppInfo duplicate = app("b", "Second", mPersonal);
        assertEquals(List.of(b, a), AppLibraryRecentApps.query(mContext, List.of(a, b, duplicate)));
    }

    @Test
    public void permissionUnavailable_doesNotInventRecentApps() {
        UsageStatsManager usage = usageFor(mPersonal);
        when(usage.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenThrow(new SecurityException());
        AppInfo a = app("a", "Main", mPersonal);
        AppInfo b = app("b", "Main", mPersonal);
        assertEquals(List.of(), AppLibraryRecentApps.query(mContext, List.of(a, b)));
    }

    @Test
    public void identicalPackagesInDifferentProfiles_areRankedIndependently() {
        UserHandle work = UserHandle.of(mPersonal.getIdentifier() + 10);
        UsageStatsManager personalUsage = usageFor(mPersonal);
        UsageStatsManager workUsage = usageFor(work);
        when(personalUsage.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(stat("mail", 1000)));
        when(workUsage.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(List.of(stat("mail", 2000)));
        AppInfo personalApp = app("mail", "Main", mPersonal);
        AppInfo workApp = app("mail", "Main", work);
        assertEquals(
                List.of(workApp, personalApp),
                AppLibraryRecentApps.query(mContext, List.of(personalApp, workApp)));
    }

    @Test
    public void recentSelection_isLimitedToFour_andTiesKeepInputOrder() {
        UsageStatsManager usage = usageFor(mPersonal);
        when(usage.queryUsageStats(anyInt(), anyLong(), anyLong()))
                .thenReturn(
                        List.of(
                                stat("a", 500),
                                stat("b", 500),
                                stat("c", 400),
                                stat("d", 300),
                                stat("e", 200)));
        AppInfo a = app("a", "Main", mPersonal);
        AppInfo b = app("b", "Main", mPersonal);
        AppInfo c = app("c", "Main", mPersonal);
        AppInfo d = app("d", "Main", mPersonal);
        AppInfo e = app("e", "Main", mPersonal);
        assertEquals(
                List.of(a, b, c, d), AppLibraryRecentApps.query(mContext, List.of(a, b, c, d, e)));
    }

    @Test
    public void usageCache_isWarmedWhenAppsArrive_beforeLibraryIsOpened() {
        when(mContext.getApplicationContext()).thenReturn(mContext);
        AllAppsStore store = mock(AllAppsStore.class);
        when(store.getApps()).thenReturn(new AppInfo[0]);
        ArrayDeque<Runnable> background = new ArrayDeque<>();
        RecordingModel model = new RecordingModel(mContext, store, background::add);
        try {
            assertEquals(0, model.refreshRequests);
            when(store.getApps()).thenReturn(new AppInfo[] {app("camera", "Main", mPersonal)});
            model.onAppsUpdated();
            assertEquals(0, model.refreshRequests);
            while (!background.isEmpty()) background.remove().run();
            assertEquals(1, model.refreshRequests);
        } finally {
            model.destroy();
            AppCategoryEngine.clearCache();
        }
    }

    private static class RecordingModel extends AppLibraryModel {
        int refreshRequests;

        RecordingModel(Context context, AllAppsStore store, Executor background) {
            super(context, store, true, background, Runnable::run);
        }

        @Override
        public void refreshRecentApps() {
            refreshRequests++;
        }
    }
}
