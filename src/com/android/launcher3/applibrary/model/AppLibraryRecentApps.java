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
                                now - TimeUnit.DAYS.toMillis(7),
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
                // Usage access can be unavailable for a profile. Leave the recent row empty.
            }
        }
        if (lastUsed.isEmpty()) return List.of();
        // Only four results are needed: avoid sorting every app and allocating keys in a
        // comparator.
        Set<PackageUserKey> included = new HashSet<>();
        List<AppInfo> result = new ArrayList<>(4);
        long[] times = new long[4];
        for (AppInfo app : apps) {
            PackageUserKey key = PackageUserKey.fromItemInfo(app);
            Long lastTime = lastUsed.get(key);
            if (lastTime == null || !included.add(key)) continue;
            int position = 0;
            while (position < result.size() && times[position] >= lastTime) position++;
            if (position >= 4) continue;
            result.add(position, app);
            if (result.size() > 4) result.remove(4);
            for (int i = result.size() - 1; i > position; i--) times[i] = times[i - 1];
            times[position] = lastTime;
        }
        return List.copyOf(result);
    }
}
