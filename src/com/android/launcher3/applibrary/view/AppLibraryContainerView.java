package com.android.launcher3.applibrary.view;

import static com.android.app.animation.Interpolators.EMPHASIZED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.CrossWindowBlurListeners;
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
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.allapps.CaddyStyle;
import com.android.launcher3.R;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.applibrary.model.AppLibraryModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.List;

public class AppLibraryContainerView extends FrameLayout
        implements AppLibraryModel.AppLibraryModelListener,
        AppLibrarySearchBar.OnSearchListener,
        AppCategoryCardView.OnCategoryExpandListener,
        Insettable, LauncherPrefChangeListener {

    private static final float SEARCH_BACKGROUND_BLUR_RADIUS = 25f;

    private View mAppLibraryContent;
    private AppLibrarySearchBar mSearchBar;
    private RecyclerView mGridView;
    private RecyclerView mSearchResultsView;
    private AppLibraryAlphabetIndexer mAlphabetIndexer;
    private AppCategoryExpandedSheet mExpandedSheet;

    private AppLibraryAdapter mCategoryAdapter;
    private AppLibrarySearchAdapter mSearchAdapter;
    private AppLibraryModel mModel;
    private final ActivityContext mActivityContext;
    private final Rect mInsets = new Rect();
    private int mLastCategoryBlurRadius;
    private final java.util.function.Consumer<Boolean> mBlurAvailabilityListener =
            enabled -> post(this::refreshAppearance);

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
            GridLayoutManager layout = new GridLayoutManager(getContext(), spanCount);
            layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return mCategoryAdapter.isRecentRow(position) ? layout.getSpanCount() : 1;
                }
            });
            mGridView.setLayoutManager(layout);
            mGridView.setAdapter(mCategoryAdapter);
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
            mExpandedSheet.setOnExpandedSheetDismissListener(new AppCategoryExpandedSheet.OnExpandedSheetDismissListener() {
                @Override
                public void onDismissStarted(long duration) {
                    // Backdrop blur follows the sheet animation, including interrupted opens.
                }

                @Override
                public void onExpansionProgress(float progress) {
                    updateCategoryBackdrop(progress);
                }

                @Override
                public void onDismissFinished() {
                    mAppLibraryContent.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    if (mAppLibraryContent != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        mPullToSearchEligible = false;
        if (hasWindowFocus) {
            if (getVisibility() == VISIBLE && mModel != null) mModel.refreshRecentApps();
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
            } else if (mGridView != null) {
                mPullToSearchEligible = !mGridView.canScrollVertically(-1);
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
                if (absDy > absDx && absDy > 10) {
                    if (mGridView != null && (mGridView.canScrollVertically(1) || mGridView.canScrollVertically(-1))) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (mPullToSearchEligible && dy > 10 && absDy > absDx && mAppLibraryContent != null) {
                    float pullDistance = dy - 10;
                    float density = getResources().getDisplayMetrics().density;
                    float maxTranslation = 50f * density;
                    float translationY = Math.min(pullDistance * 0.4f, maxTranslation);
                    mAppLibraryContent.setTranslationY(translationY);

                    float threshold = 60f * density;
                    if (pullDistance > threshold) {
                        mPullToSearchEligible = false;
                        mKeyboardDismissedByScroll = false;
                        mPendingSwipeSearchKeyboard = false;
                        mAppLibraryContent.animate().translationY(0f).setDuration(160).start();
                        if (mSearchBar != null) {
                            mSearchBar.setSearching(true);
                            mPendingSwipeSearchKeyboard = true;
                        }
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (mAppLibraryContent != null && mAppLibraryContent.getTranslationY() > 0) {
                mAppLibraryContent.animate().translationY(0f).setDuration(220).setInterpolator(EMPHASIZED).start();
            }
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
        if (mExpandedSheet != null && mExpandedSheet.isOpen()) mExpandedSheet.hide(false);
        if (mCategoryAdapter != null) {
            mCategoryAdapter.setGroups(groups);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (mGridView != null && mGridView.getLayoutManager() instanceof GridLayoutManager) {
            int columns = Math.max(1, Math.min(3, width / CaddyStyle.dp(getContext(), 160)));
            ((GridLayoutManager) mGridView.getLayoutManager()).setSpanCount(columns);
        }
    }

    @Override
    public void onRecentAppsUpdated(List<AppInfo> apps) {
        if (mCategoryAdapter != null) mCategoryAdapter.setRecentApps(apps);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && mModel != null) mModel.refreshRecentApps();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LauncherPrefs.get(getContext()).addListener(this, LauncherPrefs.APP_LIBRARY_SHOW_LABELS,
                LauncherPrefs.APP_DRAWER_OPACITY, LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED,
                LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT, LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK,
                LauncherPrefs.ALL_APPS_DARK_TEXT, LauncherPrefs.BLUR_DEPTH);
        CrossWindowBlurListeners.getInstance().addListener(
                getContext().getMainExecutor(), mBlurAvailabilityListener);
        refreshAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.get(getContext()).removeListener(this, LauncherPrefs.APP_LIBRARY_SHOW_LABELS,
                LauncherPrefs.APP_DRAWER_OPACITY, LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED,
                LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT, LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK,
                LauncherPrefs.ALL_APPS_DARK_TEXT, LauncherPrefs.BLUR_DEPTH);
        CrossWindowBlurListeners.getInstance().removeListener(mBlurAvailabilityListener);
        if (mExpandedSheet != null) mExpandedSheet.hide(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onPrefChanged(String key) {
        if (LauncherPrefs.APP_LIBRARY_SHOW_LABELS.getSharedPrefKey().equals(key)
                || LauncherPrefs.APP_DRAWER_OPACITY.getSharedPrefKey().equals(key)
                || LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_ENABLED.getSharedPrefKey().equals(key)
                || LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_LIGHT.getSharedPrefKey().equals(key)
                || LauncherPrefs.APP_DRAWER_CUSTOM_COLOR_DARK.getSharedPrefKey().equals(key)
                || LauncherPrefs.ALL_APPS_DARK_TEXT.getSharedPrefKey().equals(key)
                || LauncherPrefs.BLUR_DEPTH.getSharedPrefKey().equals(key)) {
            post(this::refreshAppearance);
        }
    }

    private void refreshAppearance() {
        if (!isAttachedToWindow()) return;
        if (mExpandedSheet != null) mExpandedSheet.hide(false);
        if (mCategoryAdapter != null) mCategoryAdapter.notifyDataSetChanged();
        if (mSearchBar != null) mSearchBar.refreshAppearance();
        if (mAlphabetIndexer != null) mAlphabetIndexer.refreshAppearance();
        if (mSearchResultsView != null) {
            mSearchResultsView.setBackground(CaddyStyle.background(getContext(), true));
            mSearchAdapter.notifyDataSetChanged();
        }
        requestLayout();
    }

    @Override
    public void onAllAppsUpdated(List<AppInfo> allApps) {
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
        if (mSearchBar == null || !mSearchBar.isSearching() || mModel == null
                || mSearchBar.getEditText() == null) {
            return;
        }
        String query = mSearchBar.getEditText().getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }
        List<AppInfo> results = mModel.searchApps(query);
        if (results.isEmpty()) {
            return;
        }
        mSearchBar.hideKeyboard();
        mSearchBar.getEditText().clearFocus();
        // Use an attached source view and the normal item click path (including profile and
        // disabled-app handling), even if RecyclerView has not laid out the results yet.
        Object previousTag = mSearchBar.getTag();
        mSearchBar.setTag(results.get(0));
        try {
            mActivityContext.getItemOnClickListener().onClick(mSearchBar);
        } finally {
            mSearchBar.setTag(previousTag);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mGridView.setRenderEffect(RenderEffect.createBlurEffect(
                            SEARCH_BACKGROUND_BLUR_RADIUS,
                            SEARCH_BACKGROUND_BLUR_RADIUS,
                            Shader.TileMode.CLAMP));
                }
                mGridView.animate().alpha(0.2f).setDuration(180).setListener(null).start();
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
                        mSearchResultsView.setVisibility(View.GONE);
                    }
                }).start();
            }
        }
    }

    @Override
    public void onExpandCategory(AppCategoryGroup group, View sourceCardView) {
        if (mExpandedSheet != null && group != null) {
            mExpandedSheet.show(group, sourceCardView instanceof AppCategoryCardView
                    ? (AppCategoryCardView) sourceCardView : null);
            mAppLibraryContent.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }

    private void updateCategoryBackdrop(float progress) {
        if (mAppLibraryContent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        int radius = 0;
        if (mAppLibraryContent.isHardwareAccelerated()
                && CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled()) {
            radius = Math.round(progress * Math.max(0, Math.min(
                    CaddyStyle.dp(getContext(), 24), LauncherPrefs.BLUR_DEPTH.get(getContext()))));
        }
        if (radius == mLastCategoryBlurRadius) return;
        mLastCategoryBlurRadius = radius;
        mAppLibraryContent.setRenderEffect(radius == 0 ? null : RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP));
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
        mLastCategoryBlurRadius = 0;
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
