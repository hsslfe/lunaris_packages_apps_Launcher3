package com.android.launcher3.applibrary.model;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class AppLibraryModel implements AllAppsStore.OnUpdateListener {

    public interface AppLibraryModelListener {
        void onCategoriesUpdated(List<AppCategoryGroup> groups);

        void onAllAppsUpdated(List<AppInfo> allApps);

        default void onRecentAppsUpdated(List<AppInfo> apps) {}
    }

    private static final Comparator<AppInfo> APP_NAME_COMPARATOR =
            (a, b) -> {
                if (a == null && b == null) return 0;
                if (a == null) return 1;
                if (b == null) return -1;
                String titleA = a.title != null ? a.title.toString() : "";
                String titleB = b.title != null ? b.title.toString() : "";
                int res = String.CASE_INSENSITIVE_ORDER.compare(titleA, titleB);
                if (res != 0) return res;
                return a.user.hashCode() - b.user.hashCode();
            };

    private final Context mContext;
    private final AllAppsStore mAppsStore;
    private final List<AppLibraryModelListener> mListeners = new CopyOnWriteArrayList<>();
    private final Executor mBackgroundExecutor;
    private final Executor mMainExecutor;
    private volatile boolean mEnabled;
    private volatile int mBuildGeneration;
    private boolean mBuildRunning;
    private boolean mRecentRefreshRunning;
    private boolean mRecentSessionActive;
    private boolean mRecentRefreshRequested;

    private List<AppInfo> mRecentApps = Collections.emptyList();
    private volatile int mRecentGeneration;
    private volatile boolean mDestroyed;
    private List<AppInfo> mAllAppsList = Collections.emptyList();
    private List<AppCategoryGroup> mCategoryGroups = Collections.emptyList();

    public AppLibraryModel(Context context, AllAppsStore appsStore) {
        this(context, appsStore, true);
    }

    public AppLibraryModel(Context context, AllAppsStore appsStore, boolean enabled) {
        this(context, appsStore, enabled, Executors.ORDERED_BG_EXECUTOR, Executors.MAIN_EXECUTOR);
    }

    @VisibleForTesting
    AppLibraryModel(
            Context context,
            AllAppsStore appsStore,
            boolean enabled,
            Executor backgroundExecutor,
            Executor mainExecutor) {
        mContext = context.getApplicationContext();
        mAppsStore = appsStore;
        mEnabled = enabled;
        mBackgroundExecutor = backgroundExecutor;
        mMainExecutor = mainExecutor;
        if (mAppsStore != null) mAppsStore.addUpdateListener(this);
        scheduleRebuild();
    }

    public void setEnabled(boolean enabled) {
        if (mDestroyed || mEnabled == enabled) return;
        mEnabled = enabled;
        ++mBuildGeneration;
        ++mRecentGeneration;
        mRecentSessionActive = false;
        mRecentRefreshRequested = false;
        if (enabled) {
            scheduleRebuild();
        } else {
            clearData();
            notifyListeners();
        }
    }

    private void clearData() {
        mAllAppsList = Collections.emptyList();
        mCategoryGroups = Collections.emptyList();
        mRecentApps = Collections.emptyList();
    }

    public void addListener(AppLibraryModelListener listener) {
        if (!mDestroyed && listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onCategoriesUpdated(mCategoryGroups);
            listener.onAllAppsUpdated(mAllAppsList);
            listener.onRecentAppsUpdated(mRecentApps);
        }
    }

    public void removeListener(AppLibraryModelListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public void destroy() {
        mDestroyed = true;
        mRecentSessionActive = false;
        mRecentRefreshRequested = false;
        ++mRecentGeneration;
        ++mBuildGeneration;
        if (mAppsStore != null) {
            mAppsStore.removeUpdateListener(this);
        }
        mListeners.clear();
        clearData();
    }

    @Override
    public void onAppsUpdated() {
        if (mDestroyed || !mEnabled) return;
        ++mBuildGeneration;
        ++mRecentGeneration;
        mRecentRefreshRequested = mRecentSessionActive;
        scheduleRebuild();
    }

    private boolean isCurrentBuild(int generation) {
        return !mDestroyed && mEnabled && generation == mBuildGeneration;
    }

    private void scheduleRebuild() {
        if (mDestroyed || !mEnabled || mAppsStore == null || mBuildRunning) return;
        mBuildRunning = true;
        int generation = mBuildGeneration;
        AppInfo[] apps = mAppsStore.getApps();
        AppInfo[] snapshot = apps == null ? AppInfo.EMPTY_ARRAY : apps.clone();
        mBackgroundExecutor.execute(
                () -> {
                    try {
                        rebuildCategories(snapshot, generation);
                    } catch (RuntimeException e) {
                        Log.e("AppLibraryModel", "Unable to rebuild categories", e);
                        mMainExecutor.execute(
                                () -> {
                                    mBuildRunning = false;
                                    if (generation != mBuildGeneration) scheduleRebuild();
                                });
                    }
                });
    }

    private void rebuildCategories(AppInfo[] rawApps, int generation) {
        if (!isCurrentBuild(generation)) {
            postCategories(generation, List.of(), List.of());
            return;
        }
        // Metadata lookups and sorting run off the UI thread, once per coalesced rebuild.
        AppCategoryEngine.clearCache();
        List<AppInfo> allApps = new ArrayList<>(rawApps.length);
        for (AppInfo app : rawApps) {
            if (app != null && app.componentName != null) {
                allApps.add(app);
            }
        }
        allApps.sort(APP_NAME_COMPARATOR);
        EnumMap<AppCategory, List<AppInfo>> map = new EnumMap<>(AppCategory.class);
        for (AppCategory cat : AppCategory.values()) {
            map.put(cat, new ArrayList<>());
        }

        for (AppInfo app : allApps) {
            if (!isCurrentBuild(generation)) {
                postCategories(generation, List.of(), List.of());
                return;
            }
            AppCategory category = AppCategoryEngine.getCategoryForApp(mContext, app);
            List<AppInfo> list = map.get(category);
            if (list != null) {
                list.add(app);
            }
        }

        List<AppInfo> suggestions = new ArrayList<>();
        int maxSuggestions = Math.min(4, allApps.size());
        for (int i = 0; i < maxSuggestions; i++) {
            suggestions.add(allApps.get(i));
        }
        map.put(AppCategory.SUGGESTIONS, suggestions);

        List<AppInfo> appsByInstallTime = new ArrayList<>(allApps);
        appsByInstallTime.sort((a, b) -> Long.compare(b.firstInstallTime, a.firstInstallTime));
        List<AppInfo> recentlyAdded = new ArrayList<>();
        int maxRecent = Math.min(12, appsByInstallTime.size());
        for (int i = 0; i < maxRecent; i++) {
            recentlyAdded.add(appsByInstallTime.get(i));
        }
        map.put(AppCategory.RECENTLY_ADDED, recentlyAdded);

        List<AppCategoryGroup> groups = new ArrayList<>();
        for (AppCategory cat : AppCategory.values()) {
            List<AppInfo> appsInCat = map.get(cat);
            if (appsInCat != null && !appsInCat.isEmpty()) {
                groups.add(new AppCategoryGroup(cat, appsInCat));
            }
        }

        groups.sort(Comparator.comparingInt(g -> g.getCategory().getPriority()));
        postCategories(generation, allApps, groups);
    }

    private void postCategories(
            int generation, List<AppInfo> allApps, List<AppCategoryGroup> groups) {
        mMainExecutor.execute(
                () -> {
                    mBuildRunning = false;
                    if (!isCurrentBuild(generation)) {
                        scheduleRebuild();
                        return;
                    }
                    mAllAppsList = Collections.unmodifiableList(allApps);
                    mCategoryGroups = Collections.unmodifiableList(groups);
                    List<AppInfo> recentApps = new ArrayList<>(mRecentApps.size());
                    for (AppInfo recent : mRecentApps) {
                        for (AppInfo app : allApps) {
                            if (app.componentName.equals(recent.componentName)
                                    && app.user.equals(recent.user)) {
                                recentApps.add(app);
                                break;
                            }
                        }
                    }
                    mRecentApps = List.copyOf(recentApps);
                    notifyListeners();
                    scheduleRecentRefresh();
                });
    }

    /** Begin a single on-demand refresh when the opening transition starts. */
    public void refreshRecentApps() {
        if (mDestroyed || !mEnabled || mRecentSessionActive) return;
        mRecentSessionActive = true;
        mRecentRefreshRequested = true;
        scheduleRecentRefresh();
    }

    /** Invalidate unfinished work when the library is closed; retain the last valid row. */
    public void stopRecentAppsRefresh() {
        if (!mRecentSessionActive) return;
        mRecentSessionActive = false;
        mRecentRefreshRequested = false;
        ++mRecentGeneration;
    }

    private void scheduleRecentRefresh() {
        if (mDestroyed || !mEnabled || !mRecentSessionActive || !mRecentRefreshRequested
                || mBuildRunning || mRecentRefreshRunning || mAllAppsList.isEmpty()) return;
        mRecentRefreshRequested = false;
        mRecentRefreshRunning = true;
        int generation = mRecentGeneration;
        List<AppInfo> snapshot = mAllAppsList;
        List<AppInfo> previous = mRecentApps;
        mBackgroundExecutor.execute(
                () -> {
                    List<AppInfo> queried;
                    try {
                        queried =
                                !mDestroyed && mEnabled && generation == mRecentGeneration
                                        ? AppLibraryRecentApps.query(mContext, snapshot)
                                        : List.of();
                    } catch (RuntimeException e) {
                        Log.e("AppLibraryModel", "Unable to refresh recent apps", e);
                        queried = previous;
                    }
                    List<AppInfo> recent = queried;
                    mMainExecutor.execute(
                            () -> {
                                mRecentRefreshRunning = false;
                                if (mDestroyed || !mEnabled) return;
                                if (mRecentSessionActive && generation == mRecentGeneration
                                        && !mRecentApps.equals(recent)) {
                                    mRecentApps = recent;
                                    for (AppLibraryModelListener listener : mListeners) {
                                        listener.onRecentAppsUpdated(recent);
                                    }
                                }
                                // A new opening or app-store change may have superseded this query.
                                scheduleRecentRefresh();
                            });
                });
    }

    private void notifyListeners() {
        if (mDestroyed) return;
        for (AppLibraryModelListener listener : mListeners) {
            listener.onCategoriesUpdated(mCategoryGroups);
            listener.onAllAppsUpdated(mAllAppsList);
            listener.onRecentAppsUpdated(mRecentApps);
        }
    }

    public List<AppCategoryGroup> getCategoryGroups() {
        return mCategoryGroups;
    }

    public List<AppInfo> getAllApps() {
        return mAllAppsList;
    }

    public List<AppInfo> searchApps(String query) {
        if (TextUtils.isEmpty(query)) {
            return mAllAppsList;
        }

        String lowerQuery = query.toLowerCase(Locale.getDefault()).trim();
        List<AppInfo> results = new ArrayList<>();
        for (AppInfo app : mAllAppsList) {
            if (app.title != null
                    && app.title.toString().toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                results.add(app);
            }
        }
        return results;
    }
}
