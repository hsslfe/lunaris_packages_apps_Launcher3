package com.android.launcher3.applibrary.view;

import static com.android.app.animation.Interpolators.EMPHASIZED;
import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.CrossWindowBlurListeners;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.SpringEdgeEffectFactory;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

public class AppLibraryContainerView extends FrameLayout
        implements AppLibraryModel.AppLibraryModelListener,
                AppLibrarySearchBar.OnSearchListener,
                AppCategoryCardView.OnCategoryExpandListener,
                Insettable,
                LauncherPrefChangeListener,
                ThemeManager.ThemeChangeListener {

    private static final float CATEGORY_BLUR_RADIUS = 35f;
    private static final int PULL_TO_SEARCH_DISTANCE_DP = 96;

    private View mAppLibraryContent;
    private AppLibrarySearchBar mSearchBar;
    private RecyclerView mGridView;
    private RecyclerView mSearchResultsView;
    private AppLibraryAlphabetIndexer mAlphabetIndexer;
    private AppCategoryExpandedSheet mExpandedSheet;

    private AppLibraryAdapter mCategoryAdapter;
    private AppLibrarySearchAdapter mSearchAdapter;
    private AppLibraryModel mModel;
    private List<AppInfo> mRecentApps = List.of();
    private final ActivityContext mActivityContext;
    private final Rect mInsets = new Rect();
    private ValueAnimator mBlurAnimator;
    private float mCategoryBlurRadius;
    private boolean mPendingRecentAppsBind;
    private boolean mShowLabels;
    private boolean mTouchInProgress;
    private final int mTouchSlop;

    private float mDownX;
    private float mDownY;
    private boolean mPullToSearchEligible = false;
    private final Runnable mBindPendingRecentApps = this::bindPendingRecentApps;
    private final Runnable mRefreshIcons = () -> {
        if (!isAttachedToWindow()) return;
        mCategoryAdapter.notifyDataSetChanged();
        mSearchAdapter.notifyDataSetChanged();
        mExpandedSheet.refreshIcons();
        mExpandedSheet.updateBackgroundForTheme();
        updateAlphabetIndexerVisibility();
    };

    public AppLibraryContainerView(Context context) {
        this(context, null);
    }

    public AppLibraryContainerView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryContainerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mAppLibraryContent = findViewById(R.id.app_library_content);
        mSearchBar = findViewById(R.id.app_library_search_bar);
        mGridView = findViewById(R.id.app_library_grid_view);
        mSearchResultsView = findViewById(R.id.app_library_search_results_view);
        mAlphabetIndexer = findViewById(R.id.app_library_alphabet_indexer);
        mExpandedSheet = findViewById(R.id.app_library_expanded_sheet);
        mShowLabels = LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext());

        if (mAlphabetIndexer != null) {
            mAlphabetIndexer.setOnSectionSelectedListener(section -> {
                if (mSearchAdapter != null && mSearchResultsView != null) {
                    int pos = mSearchAdapter.getPositionForSection(section);
                    if (pos >= 0) {
                        RecyclerView.LayoutManager lm = mSearchResultsView.getLayoutManager();
                        if (lm instanceof LinearLayoutManager) {
                            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
                        } else {
                            mSearchResultsView.scrollToPosition(pos);
                        }
                    }
                }
            });
        }

        if (mSearchBar != null) {
            mSearchBar.setOnSearchListener(this);
        }

        mCategoryAdapter = new AppLibraryAdapter(mActivityContext);
        mCategoryAdapter.setOnCategoryExpandListener(this);

        int spanCount = 2;
        if (mActivityContext instanceof Launcher) {
            DeviceProfile dp = ((Launcher) mActivityContext).getDeviceProfile();
            if (dp.getDeviceProperties().isTablet() || dp.getDeviceProperties().isLandscape()) {
                spanCount = 3;
            }
        }

        if (mGridView != null) {
            GridLayoutManager layoutManager = new GridLayoutManager(getContext(), spanCount);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return mCategoryAdapter.isRecentAppsPosition(position)
                            ? layoutManager.getSpanCount() : 1;
                }
            });
            mGridView.setLayoutManager(layoutManager);
            mGridView.setAdapter(mCategoryAdapter);
            mGridView.setItemAnimator(null);
            mGridView.setEdgeEffectFactory(new SpringEdgeEffectFactory());
            mGridView.setNestedScrollingEnabled(true);
            mGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView view, int state) {
                    if (state == RecyclerView.SCROLL_STATE_IDLE) {
                        removeCallbacks(mBindPendingRecentApps);
                        post(mBindPendingRecentApps);
                    }
                }
            });
        }

        mSearchAdapter = new AppLibrarySearchAdapter(mActivityContext);
        mSearchAdapter.setOnSearchItemClickListener((view, app) -> {
            if (mSearchBar != null) {
                mSearchBar.hideKeyboard();
                if (mSearchBar.getEditText() != null) {
                    mSearchBar.getEditText().clearFocus();
                }
            }
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            mActivityContext.getItemOnClickListener().onClick(view);
        });

        if (mSearchResultsView != null) {
            mSearchResultsView.setLayoutManager(new LinearLayoutManager(getContext()));
            mSearchResultsView.setAdapter(mSearchAdapter);
            mSearchResultsView.setItemAnimator(null);
            mSearchResultsView.setEdgeEffectFactory(new SpringEdgeEffectFactory());
            mSearchResultsView.setNestedScrollingEnabled(true);
            mSearchResultsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        if (mSearchBar != null) {
                            mSearchBar.hideKeyboard();
                        }
                    }
                }
            });
        }

        if (mExpandedSheet != null) {
            mExpandedSheet.setOnExpandedSheetDismissListener(
                    new AppCategoryExpandedSheet.OnExpandedSheetDismissListener() {
                        @Override
                        public void onDismissStarted(long duration) {
                            applyCategoryBackgroundBlur(false, duration);
                        }

                        @Override
                        public void onDismissFinished() {
                            clearCategoryBackgroundBlur();
                        }
                    });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (!hasWindowFocus) {
            cancelPullToSearch();
            mTouchInProgress = false;
        } else {
            mPullToSearchEligible = false;
        }
        if (hasWindowFocus) {
            if (isShown()) onLibraryOpened();
            if (mSearchBar != null && !mSearchBar.isSearching()) {
                mSearchBar.hideKeyboard();
                if (mSearchBar.getEditText() != null) {
                    mSearchBar.getEditText().clearFocus();
                }
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mTouchInProgress = true;
            mDownX = ev.getX();
            mDownY = ev.getY();
            if (mExpandedSheet != null && mExpandedSheet.isOpen()) {
                mPullToSearchEligible = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (mSearchBar != null && mSearchBar.isSearching()) {
                mPullToSearchEligible = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (mGridView != null && isLibraryFullyOpen()) {
                // A horizontal entry swipe must not arm pull-to-search.
                Rect bounds = new Rect();
                mPullToSearchEligible =
                        mGridView.getGlobalVisibleRect(bounds)
                                && bounds.contains((int) ev.getRawX(), (int) ev.getRawY())
                                && !mGridView.canScrollVertically(-1);
            } else {
                mPullToSearchEligible = false;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - mDownX;
            float dy = ev.getY() - mDownY;
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);

            if (mExpandedSheet != null && mExpandedSheet.isOpen()) {
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (mSearchBar != null && mSearchBar.isSearching()) {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (dy > 20 && mSearchBar != null) {
                    mSearchBar.hideKeyboard();
                }
            } else {
                if (absDy > absDx && absDy > mTouchSlop) {
                    if (mGridView != null && (mGridView.canScrollVertically(1) || mGridView.canScrollVertically(-1))) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (mPullToSearchEligible && !isLibraryFullyOpen()) {
                    cancelPullToSearch();
                } else if (mPullToSearchEligible && absDx > mTouchSlop && absDx > absDy) {
                    // Let the horizontal library gesture win.
                    mPullToSearchEligible = false;
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            float dy = ev.getY() - mDownY;
            boolean openSearch = action == MotionEvent.ACTION_UP
                    && mPullToSearchEligible && isLibraryFullyOpen()
                    && mGridView != null && !mGridView.canScrollVertically(-1)
                    && dy > mTouchSlop + Utilities.dpToPx(PULL_TO_SEARCH_DISTANCE_DP, getContext())
                    && dy > Math.abs(ev.getX() - mDownX) * 1.2f;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            mPullToSearchEligible = false;
            mTouchInProgress = false;
            boolean result = super.dispatchTouchEvent(ev);
            // Finish RecyclerView's gesture before opening the list and requesting the keyboard.
            if (openSearch && mSearchBar != null) mSearchBar.setSearching(true);
            bindPendingRecentApps();
            return result;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_DOWN && !handled) {
            mTouchInProgress = false;
            bindPendingRecentApps();
        }
        return handled;
    }

    public void setModel(AppLibraryModel model) {
        if (mModel != null) {
            mModel.stopRecentAppsRefresh();
            mModel.removeListener(this);
        }
        mModel = model;
        if (mModel != null) {
            mModel.setEnabled(LauncherPrefs.APP_LIBRARY_ENABLED.get(getContext()));
            mModel.addListener(this);
            resumeRecentAppsIfVisible();
        }
    }

    @Override
    public void onCategoriesUpdated(List<AppCategoryGroup> groups) {
        if (mCategoryAdapter != null) {
            mCategoryAdapter.setGroups(groups);
        }
        if (mExpandedSheet != null) mExpandedSheet.onCategoriesUpdated(groups);
    }

    private boolean isLibraryFullyOpen() {
        if (!(mActivityContext instanceof Launcher)) return false;
        Launcher launcher = (Launcher) mActivityContext;
        return launcher.isInState(com.android.launcher3.LauncherState.APP_LIBRARY)
                && launcher.getAppLibraryController() != null
                && Float.compare(launcher.getAppLibraryController().getProgress(), 0f) == 0;
    }

    /** Prevent an in-flight pull gesture from opening search during a state transition. */
    public void cancelPullToSearch() {
        mPullToSearchEligible = false;
    }

    public void onLibraryOpening() {
        if (mCategoryAdapter != null) mCategoryAdapter.setRecentAnimationsEnabled(false);
        if (mModel != null && isAttachedToWindow() && getWindowVisibility() == View.VISIBLE) {
            mModel.refreshRecentApps();
        }
    }

    public void onLibraryOpened() {
        if (mCategoryAdapter != null) mCategoryAdapter.setRecentAnimationsEnabled(true);
        resumeRecentAppsIfVisible();
        bindPendingRecentApps();
    }

    public void onLibraryClosing() {
        if (mCategoryAdapter != null) mCategoryAdapter.setRecentAnimationsEnabled(false);
    }

    public void onLibraryClosed() {
        onLibraryClosing();
        if (mCategoryAdapter != null && mGridView != null) {
            mCategoryAdapter.finishRecentAnimations(mGridView);
        }
        if (mModel != null) mModel.stopRecentAppsRefresh();
        removeCallbacks(mBindPendingRecentApps);
        bindPendingRecentApps();
    }

    private void resumeRecentAppsIfVisible() {
        if (mModel == null || !isAttachedToWindow() || getWindowVisibility() != View.VISIBLE
                || !(mActivityContext instanceof Launcher launcher)
                || launcher.getAppLibraryController() == null) return;
        float progress = launcher.getAppLibraryController().getProgress();
        if (progress >= 1f) return;
        if (mCategoryAdapter != null) mCategoryAdapter.setRecentAnimationsEnabled(progress == 0f);
        mModel.refreshRecentApps();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (mModel == null) return;
        if (visibility == View.VISIBLE) {
            resumeRecentAppsIfVisible();
            bindPendingRecentApps();
        } else {
            // The library may remain the selected state behind a launched app or screen-off.
            onLibraryClosed();
        }
    }

    @Override
    public void onRecentAppsUpdated(List<AppInfo> apps) {
        if (mRecentApps.equals(apps)) return;
        mRecentApps = apps;
        if (shouldDeferRecentAppsBind()) {
            mPendingRecentAppsBind = true;
            return;
        }
        mPendingRecentAppsBind = false;
        bindRecentApps();
    }

    private boolean shouldDeferRecentAppsBind() {
        if (!isShown() || getWindowVisibility() != View.VISIBLE) return false;
        if (mTouchInProgress
                || (mGridView != null
                        && mGridView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE)) return true;
        if (mActivityContext instanceof Launcher
                && ((Launcher) mActivityContext).getAppLibraryController() != null) {
            float progress = ((Launcher) mActivityContext).getAppLibraryController().getProgress();
            // Bind silently while the opening surface is still almost entirely hidden.
            return progress > 0f && progress < 0.85f;
        }
        return false;
    }

    private void bindPendingRecentApps() {
        if (mPendingRecentAppsBind && !shouldDeferRecentAppsBind()) {
            mPendingRecentAppsBind = false;
            bindRecentApps();
        }
    }

    private void bindRecentApps() {
        if (mCategoryAdapter != null) {
            mCategoryAdapter.setRecentApps(mRecentApps);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ThemeManager.INSTANCE.get(getContext()).addChangeListener(this);
        LauncherPrefs.get(getContext()).addListener(this, LauncherPrefs.APP_LIBRARY_SHOW_LABELS,
                LauncherPrefs.ALLAPPS_ICON_CUSTOMIZATION, LauncherPrefs.APP_LIBRARY_ENABLED);
        if (mModel != null) mModel.setEnabled(LauncherPrefs.APP_LIBRARY_ENABLED.get(getContext()));
        resumeRecentAppsIfVisible();
        onThemeChanged();
        if (mShowLabels != LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext())) {
            onPrefChanged(LauncherPrefs.APP_LIBRARY_SHOW_LABELS.getSharedPrefKey());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        reset();
        removeCallbacks(mBindPendingRecentApps);
        removeCallbacks(mRefreshIcons);
        ThemeManager.INSTANCE.get(getContext()).removeChangeListener(this);
        LauncherPrefs.get(getContext()).removeListener(this,
                LauncherPrefs.APP_LIBRARY_SHOW_LABELS,
                LauncherPrefs.ALLAPPS_ICON_CUSTOMIZATION,
                LauncherPrefs.APP_LIBRARY_ENABLED);
        mTouchInProgress = false;
        super.onDetachedFromWindow();
    }

    @Override
    public void onPrefChanged(String key) {
        if (LauncherPrefs.APP_LIBRARY_ENABLED.getSharedPrefKey().equals(key)) {
            boolean enabled = LauncherPrefs.APP_LIBRARY_ENABLED.get(getContext());
            if (mModel != null) mModel.setEnabled(enabled);
            if (!enabled) {
                reset();
                mCategoryAdapter.setRecentApps(List.of());
                mPendingRecentAppsBind = false;
                mRecentApps = List.of();
                if (mActivityContext instanceof Launcher) {
                    ((Launcher) mActivityContext).getStateManager()
                            .goToState(com.android.launcher3.LauncherState.NORMAL);
                }
            }
            return;
        }
        if (LauncherPrefs.ALLAPPS_ICON_CUSTOMIZATION.getSharedPrefKey().equals(key)) {
            onThemeChanged();
            return;
        }
        if (LauncherPrefs.APP_LIBRARY_SHOW_LABELS.getSharedPrefKey().equals(key)) {
            mShowLabels = LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext());
            post(
                    () -> {
                        if (!isAttachedToWindow()) return;
                        mExpandedSheet.hide(false);
                        mCategoryAdapter.notifyDataSetChanged();
                        bindRecentApps();
                    });
        }
    }

    @Override
    public void onThemeChanged() {
        removeCallbacks(mRefreshIcons);
        post(mRefreshIcons);
    }

    @Override
    public void onAllAppsUpdated(List<AppInfo> allApps) {
        if (mSearchAdapter != null && mSearchBar != null && mSearchBar.isSearching()) {
            String query = mSearchBar.isSearching()
                    ? mSearchBar.getEditText().getText().toString().trim() : "";
            mSearchAdapter.setApps(query.isEmpty() || mModel == null
                    ? allApps : mModel.searchApps(query), query.isEmpty());
        }
    }

    /** Only show A-Z for the active, unfiltered search list, never over category cards. */
    private void updateAlphabetIndexerVisibility() {
        if (mAlphabetIndexer == null) return;
        boolean show = mSearchBar != null && mSearchBar.isSearching()
                && mSearchResultsView != null
                && mSearchResultsView.getVisibility() == View.VISIBLE
                && mSearchBar.getEditText() != null
                && mSearchBar.getEditText().getText().toString().trim().isEmpty();
        mAlphabetIndexer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSearchQueryChanged(String query) {
        if (mModel != null && mSearchAdapter != null) {
            boolean isEmpty = query == null || query.trim().isEmpty();
            if (isEmpty) {
                mSearchAdapter.setApps(mModel.getAllApps(), true);
            } else {
                List<AppInfo> results = mModel.searchApps(query);
                mSearchAdapter.setApps(results, false);
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.scrollToPosition(0);
            }
        }
        // resetSearch() can clear text even when search is already inactive.
        updateAlphabetIndexerVisibility();
    }

    @Override
    public void onSearchSubmit() {
        if (mModel == null || mSearchBar == null || !mSearchBar.isSearching()) return;
        String query = mSearchBar.getEditText().getText().toString().trim();
        if (query.isEmpty()) return;
        List<AppInfo> matches = mModel.searchApps(query);
        if (matches.isEmpty()) return;
        RecyclerView.ViewHolder holder = mSearchResultsView.findViewHolderForAdapterPosition(0);
        View icon = holder == null ? null : holder.itemView.findViewById(R.id.app_icon);
        if (icon != null && icon.getTag() == matches.get(0)) {
            icon.performClick();
            return;
        }
        mSearchBar.hideKeyboard();
        mSearchBar.getEditText().clearFocus();
        Object tag = mSearchBar.getTag();
        mSearchBar.setTag(matches.get(0));
        try {
            mActivityContext.getItemOnClickListener().onClick(mSearchBar);
        } finally {
            mSearchBar.setTag(tag);
        }
    }

    @Override
    public void onSearchStateChanged(boolean isSearching) {
        if (mExpandedSheet != null && mExpandedSheet.isOpen()) {
            mExpandedSheet.hide(false);
            if (mAppLibraryContent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mAppLibraryContent.setRenderEffect(null);
            }
        }

        if (mSearchResultsView != null) {
            mSearchResultsView.animate().setListener(null).cancel();
        }
        if (mGridView != null) {
            mGridView.animate().cancel();
        }

        if (isSearching) {
            if (mModel != null && mSearchAdapter != null) {
                mSearchAdapter.setApps(mModel.getAllApps(), true);
            }
            if (mGridView != null) {
                // The list surface stays, but only wallpaper should be visible through it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mGridView.setRenderEffect(null);
                mGridView.setAlpha(0f);
                mGridView.setVisibility(View.INVISIBLE);
            }
            if (mSearchResultsView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mSearchResultsView.setRenderEffect(null);
                }
                if (mSearchResultsView.getVisibility() != View.VISIBLE) {
                    mSearchResultsView.setAlpha(0f);
                }
                mSearchResultsView.setVisibility(View.VISIBLE);
                mSearchResultsView.animate().alpha(1f).setDuration(280)
                        .setInterpolator(EMPHASIZED_DECELERATE).setListener(null).start();
            }
        } else {
            if (mGridView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mGridView.setRenderEffect(null);
                }
                mGridView.setVisibility(View.VISIBLE);
                mGridView.animate().alpha(1f).setDuration(220)
                        .setInterpolator(EMPHASIZED).setListener(null).start();
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.animate().alpha(0f).setDuration(180)
                        .setInterpolator(EMPHASIZED).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mSearchBar == null || !mSearchBar.isSearching()) {
                            mSearchResultsView.setVisibility(View.GONE);
                        }
                    }
                }).start();
            }
        }
        updateAlphabetIndexerVisibility();
    }

    @Override
    public void onExpandCategory(AppCategoryGroup group, View sourceCardView) {
        if (mExpandedSheet != null && group != null && !mExpandedSheet.isOpen()) {
            Rect sourceBounds = new Rect();
            if (sourceCardView != null) {
                sourceCardView.getGlobalVisibleRect(sourceBounds);
                int[] sheetPos = new int[2];
                mExpandedSheet.getLocationOnScreen(sheetPos);
                sourceBounds.offset(-sheetPos[0], -sheetPos[1]);
            }
            applyCategoryBackgroundBlur(true, 280);
            mExpandedSheet.show(group, sourceBounds);
        }
    }

    private boolean isBlurEnabled() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && LauncherPrefs.BLUR_DEPTH.get(getContext()) > 0
                && CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
    }

    private void clearCategoryBackgroundBlur() {
        if (mBlurAnimator == null && mCategoryBlurRadius == 0f) return;
        if (mBlurAnimator != null) {
            mBlurAnimator.removeAllListeners();
            mBlurAnimator.removeAllUpdateListeners();
            mBlurAnimator.cancel();
            mBlurAnimator = null;
        }
        mCategoryBlurRadius = 0f;
        if (mAppLibraryContent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mAppLibraryContent.setRenderEffect(null);
        }
    }

    private void applyCategoryBackgroundBlur(boolean enable, long duration) {
        if (!isBlurEnabled() || mAppLibraryContent == null) {
            clearCategoryBackgroundBlur();
            return;
        }
        if (mBlurAnimator != null) {
            mBlurAnimator.removeAllListeners();
            mBlurAnimator.removeAllUpdateListeners();
            mBlurAnimator.cancel();
        }
        mBlurAnimator = null;
        float endRadius = enable ? CATEGORY_BLUR_RADIUS : 0f;
        if (mCategoryBlurRadius == endRadius) return;
        ValueAnimator animator = ValueAnimator.ofFloat(mCategoryBlurRadius, endRadius);
        mBlurAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(EMPHASIZED);
        animator.addUpdateListener(value -> {
            mCategoryBlurRadius = (Float) value.getAnimatedValue();
            mAppLibraryContent.setRenderEffect(mCategoryBlurRadius > 0f
                    ? RenderEffect.createBlurEffect(mCategoryBlurRadius, mCategoryBlurRadius,
                            Shader.TileMode.CLAMP) : null);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mBlurAnimator == animation) mBlurAnimator = null;
            }
        });
        animator.start();
    }

    public boolean handleBack() {
        if (mExpandedSheet != null && mExpandedSheet.isOpen()) {
            mExpandedSheet.hide(true);
            return true;
        }
        if (mSearchBar != null && mSearchBar.isSearching()) {
            mSearchBar.resetSearch();
            return true;
        }
        return false;
    }

    public void reset() {
        onLibraryClosed();
        cancelPullToSearch();
        clearCategoryBackgroundBlur();
        if (mExpandedSheet != null) {
            mExpandedSheet.hide(false);
        }
        if (mAppLibraryContent != null) {
            mAppLibraryContent.setTranslationY(0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mAppLibraryContent.setRenderEffect(null);
            }
        }
        if (mSearchBar != null) {
            mSearchBar.resetSearch();
        }
        if (mSearchResultsView != null) {
            mSearchResultsView.animate().cancel();
            mSearchResultsView.setVisibility(View.GONE);
            mSearchResultsView.stopScroll();
            SpringEdgeEffectFactory.finishSpringAnimations(mSearchResultsView);
            mSearchResultsView.setAlpha(0f);
            if (mSearchAdapter.getItemCount() > 0) mSearchAdapter.setApps(List.of(), true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mSearchResultsView.setRenderEffect(null);
            }
        }
        if (mAlphabetIndexer != null) {
            mAlphabetIndexer.setVisibility(View.GONE);
        }
        if (mPendingRecentAppsBind) {
            mPendingRecentAppsBind = false;
            bindRecentApps();
        }
        if (mGridView != null) {
            mGridView.animate().cancel();
            mGridView.setVisibility(View.VISIBLE);
            mGridView.stopScroll();
            SpringEdgeEffectFactory.finishSpringAnimations(mGridView);
            mGridView.setAlpha(1f);
            mGridView.scrollToPosition(0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mGridView.setRenderEffect(null);
            }
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        mPullToSearchEligible = false;
        mTouchInProgress = false;
    }

    public AppLibrarySearchBar getSearchBar() {
        return mSearchBar;
    }

    public RecyclerView getGridView() {
        return mGridView;
    }

    public RecyclerView getSearchResultsView() {
        return mSearchResultsView;
    }

    @Override
    public void setInsets(Rect insets) {
        if (mInsets.equals(insets)) return;
        mInsets.set(insets);

        if (mAppLibraryContent != null) {
            mAppLibraryContent.setPadding(
                    insets.left, insets.top, insets.right, insets.bottom);
        }

        if (mExpandedSheet != null) {
            mExpandedSheet.setInsets(insets);
        }
    }
}
