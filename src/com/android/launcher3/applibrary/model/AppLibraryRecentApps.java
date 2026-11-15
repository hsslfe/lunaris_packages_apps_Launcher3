package com.android.launcher3.applibrary.model;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Queries only launchable apps already visible to App Library, preserving profile boundaries. */
final class AppLibraryRecentApps {
    private AppLibraryRecentApps() {}

    static List<AppInfo> query(Context context, List<AppInfo> apps) {
        Map<PackageUserKey, Long> lastUsed = new HashMap<>();
        Set<UserHandle> profiles = new HashSet<>();
        for (AppInfo app : apps) profiles.add(app.user);
        UserManager users = context.getSystemService(UserManager.class);
        long now = System.currentTimeMillis();
        for (UserHandle profile : profiles) {
            try {
                if (users == null
                        || !users.isUserUnlocked(profile)
                        || users.isQuietModeEnabled(profile)) {
                    continue;
                }
                UsageStatsManager usage =
                        context.createContextAsUser(profile, 0)
                                .getSystemService(UsageStatsManager.class);
                if (usage == null) continue;
                List<UsageStats> stats =
                        usage.queryUsageStats(
                                UsageStatsManager.INTERVAL_DAILY,
                                now - TimeUnit.DAYS.toMillis(30),
                                now);
                if (stats == null) continue;
                for (UsageStats stat : stats) {
                    if (stat.getPackageName() != null
                            && stat.getLastTimeUsed() > 0
                            && stat.getLastTimeUsed() <= now) {
                        lastUsed.merge(
                                new PackageUserKey(stat.getPackageName(), profile),
                                stat.getLastTimeUsed(),
                                Math::max);
                    }
                }
            } catch (SecurityException | IllegalStateException | IllegalArgumentException ignored) {
                // Usage access can be unavailable for a profile. Keep the suggested-app fallback.
            }
        }
        List<AppInfo> ranked = new ArrayList<>(apps);
        ranked.sort(
                (a, b) ->
                        Long.compare(
                                lastUsed.getOrDefault(PackageUserKey.fromItemInfo(b), 0L),
                                lastUsed.getOrDefault(PackageUserKey.fromItemInfo(a), 0L)));
        Set<PackageUserKey> included = new HashSet<>();
        List<AppInfo> result = new ArrayList<>();
        for (AppInfo app : ranked) {
            if (included.add(PackageUserKey.fromItemInfo(app))) result.add(app);
            if (result.size() == 4) break;
        }
        return List.copyOf(result);
    }
}
