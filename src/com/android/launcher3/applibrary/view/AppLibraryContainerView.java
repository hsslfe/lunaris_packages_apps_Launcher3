package com.android.launcher3.applibrary.view;

import static com.android.app.animation.Interpolators.EMPHASIZED;

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
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.util.SpringEdgeEffectFactory;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

public class AppLibraryContainerView extends FrameLayout
        implements AppLibraryModel.AppLibraryModelListener,
                AppLibrarySearchBar.OnSearchListener,
                AppCategoryCardView.OnCategoryExpandListener,
                Insettable,
                LauncherPrefChangeListener {

    private static final float CATEGORY_BLUR_RADIUS = 35f;

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
    private boolean mPendingRecentAppsBind;
    private boolean mShowLabels;
    private final int mTouchSlop;

    private float mDownX;
    private float mDownY;
    private boolean mPullToSearchEligible = false;
    private boolean mKeyboardDismissedByScroll = false;
    private boolean mPendingSwipeSearchKeyboard = false;

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
            mSearchResultsView.setEdgeEffectFactory(new SpringEdgeEffectFactory());
            mSearchResultsView.setNestedScrollingEnabled(true);
            mSearchResultsView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        mKeyboardDismissedByScroll = true;
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
                            if (mBlurAnimator != null) mBlurAnimator.cancel();
                            if (mAppLibraryContent != null
                                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                mAppLibraryContent.setRenderEffect(null);
                            }
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
            mDownX = ev.getX();
            mDownY = ev.getY();
            if (mExpandedSheet != null && mExpandedSheet.isOpen()) {
                mPullToSearchEligible = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (mSearchBar != null && mSearchBar.isSearching()) {
                mPullToSearchEligible = false;
                getParent().requestDisallowInterceptTouchEvent(true);
            } else if (mGridView != null && isLibraryFullyOpen()) {
                // A horizontal entry swipe must not start the pull-to-search translation.
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
                    mKeyboardDismissedByScroll = true;
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
                } else if (mPullToSearchEligible && absDx > absDy) {
                    // Let the horizontal library gesture win.
                    mPullToSearchEligible = false;
                } else if (mPullToSearchEligible
                        && dy > mTouchSlop
                        && dy > absDx * 1.2f
                        && mAppLibraryContent != null) {
                    float pullDistance = dy - mTouchSlop;
                    float density = getResources().getDisplayMetrics().density;
                    // RecyclerView's shared spring handles motion; this only opens search.
                    if (pullDistance > 60f * density) {
                        mPullToSearchEligible = false;
                        mKeyboardDismissedByScroll = false;
                        mPendingSwipeSearchKeyboard = false;
                        if (mSearchBar != null) {
                            mSearchBar.setSearching(true);
                            mPendingSwipeSearchKeyboard = true;
                        }
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            mPullToSearchEligible = false;
            boolean showKeyboardAfterSuper = mPendingSwipeSearchKeyboard;
            mPendingSwipeSearchKeyboard = false;
            boolean result = super.dispatchTouchEvent(ev);
            if (showKeyboardAfterSuper && mSearchBar != null) {
                mSearchBar.showKeyboard();
            }
            return result;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void setModel(AppLibraryModel model) {
        if (mModel != null) {
            mModel.removeListener(this);
        }
        mModel = model;
        if (mModel != null) {
            mModel.addListener(this);
        }
    }

    @Override
    public void onCategoriesUpdated(List<AppCategoryGroup> groups) {
        if (mCategoryAdapter != null) {
            mCategoryAdapter.setGroups(groups);
        }
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

    public void onLibraryOpened() {
        if (mModel != null
                && isAttachedToWindow()
                && mActivityContext instanceof Launcher
                && ((Launcher) mActivityContext).getAppLibraryController().getProgress() == 0f) {
            mModel.refreshRecentApps();
        }
    }

    @Override
    public void onRecentAppsUpdated(List<AppInfo> apps) {
        if (mRecentApps.equals(apps)) return;
        mRecentApps = apps;
        // Usage stats arrive asynchronously: never resize the recent row during
        // an opening swipe or while the library is visible.
        if (isShown()
                && mActivityContext instanceof Launcher
                && ((Launcher) mActivityContext).getAppLibraryController() != null
                && ((Launcher) mActivityContext).getAppLibraryController().getProgress() < 1f) {
            mPendingRecentAppsBind = true;
            return;
        }
        mPendingRecentAppsBind = false;
        bindRecentApps();
    }

    private void bindRecentApps() {
        if (mCategoryAdapter != null) {
            mCategoryAdapter.setRecentApps(mRecentApps);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LauncherPrefs.get(getContext()).addListener(this, LauncherPrefs.APP_LIBRARY_SHOW_LABELS);
        if (mShowLabels != LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext())) {
            onPrefChanged(LauncherPrefs.APP_LIBRARY_SHOW_LABELS.getSharedPrefKey());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.get(getContext()).removeListener(this, LauncherPrefs.APP_LIBRARY_SHOW_LABELS);
        super.onDetachedFromWindow();
    }

    @Override
    public void onPrefChanged(String key) {
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
    public void onAllAppsUpdated(List<AppInfo> allApps) {
        if (isShown()
                && mActivityContext instanceof Launcher
                && ((Launcher) mActivityContext)
                        .isInState(com.android.launcher3.LauncherState.APP_LIBRARY)) {
            onLibraryOpened();
        }
        if (mSearchAdapter != null && mSearchBar != null && !mSearchBar.isSearching()) {
            mSearchAdapter.setApps(allApps, true);
        }
    }

    @Override
    public void onSearchQueryChanged(String query) {
        if (mModel != null && mSearchAdapter != null) {
            boolean isEmpty = query == null || query.trim().isEmpty();
            if (isEmpty) {
                mSearchAdapter.setApps(mModel.getAllApps(), true);
                if (mAlphabetIndexer != null) {
                    mAlphabetIndexer.setVisibility(View.VISIBLE);
                }
            } else {
            List<AppInfo> results = mModel.searchApps(query);
                mSearchAdapter.setApps(results, false);
                if (mAlphabetIndexer != null) {
                    mAlphabetIndexer.setVisibility(View.GONE);
                }
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.scrollToPosition(0);
            }
        }
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
            mSearchResultsView.animate().cancel();
        }
        if (mGridView != null) {
            mGridView.animate().cancel();
        }

        if (isSearching) {
            if (mModel != null && mSearchAdapter != null) {
                mSearchAdapter.setApps(mModel.getAllApps(), true);
            }
            if (mAlphabetIndexer != null) {
                mAlphabetIndexer.setVisibility(View.VISIBLE);
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
                mSearchResultsView.setVisibility(View.VISIBLE);
                mSearchResultsView.setAlpha(0f);
                mSearchResultsView.animate().alpha(1f).setDuration(180).setListener(null).start();
            }
        } else {
            mKeyboardDismissedByScroll = false;
            if (mAlphabetIndexer != null) {
                mAlphabetIndexer.setVisibility(View.GONE);
            }
            if (mGridView != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mGridView.setRenderEffect(null);
                }
                mGridView.setVisibility(View.VISIBLE);
                mGridView.animate().alpha(1f).setDuration(180).setListener(null).start();
            }
            if (mSearchResultsView != null) {
                mSearchResultsView.animate().alpha(0f).setDuration(140).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mSearchBar == null || !mSearchBar.isSearching()) {
                            mSearchResultsView.setVisibility(View.GONE);
                        }
                    }
                }).start();
            }
        }
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

    private void applyCategoryBackgroundBlur(boolean enable, long duration) {
        if (mBlurAnimator != null) {
            mBlurAnimator.cancel();
        }

        if (!isBlurEnabled() || mAppLibraryContent == null) {
            if (mAppLibraryContent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mAppLibraryContent.setRenderEffect(null);
            }
            return;
        }

        float startRadius = enable ? 0.1f : CATEGORY_BLUR_RADIUS;
        float endRadius = enable ? CATEGORY_BLUR_RADIUS : 0.1f;

        mBlurAnimator = ValueAnimator.ofFloat(startRadius, endRadius);
        mBlurAnimator.setDuration(duration);
        mBlurAnimator.setInterpolator(EMPHASIZED);
        mBlurAnimator.addUpdateListener(animation -> {
            if (mAppLibraryContent != null) {
                float r = (Float) animation.getAnimatedValue();
                mAppLibraryContent.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            }
        });
        mBlurAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!enable && mAppLibraryContent != null) {
                    mAppLibraryContent.setRenderEffect(null);
                }
            }
        });
        mBlurAnimator.start();
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
        cancelPullToSearch();
        if (mBlurAnimator != null) {
            mBlurAnimator.cancel();
        }
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
            mSearchResultsView.setAlpha(0f);
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
        mKeyboardDismissedByScroll = false;
        mPendingSwipeSearchKeyboard = false;
    }

    public boolean shouldContainerScroll(MotionEvent ev) {
        return false;
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
