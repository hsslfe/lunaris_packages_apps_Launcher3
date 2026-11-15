package com.android.launcher3.applibrary.view;

import static com.android.launcher3.applibrary.AppLibraryCategoryAnimation.START_SCALE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.OneShotPreDrawListener;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.CaddyStyle;
import com.android.launcher3.applibrary.AppLibraryCategoryAnimation;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** App Library's own category overlay, with preview-to-grid icon morphs and reversible motion. */
public class AppCategoryExpandedSheet extends FrameLayout implements Insettable {
    private static final PathInterpolator OPEN = new PathInterpolator(.25f, 0f, .15f, 1f);
    private static final PathInterpolator CLOSE = new PathInterpolator(.20f, 0f, .10f, 1f);

    public interface OnExpandedSheetDismissListener {
        void onDismissStarted(long duration);

        void onDismissFinished();

        void onExpansionProgress(float progress);
    }

    private View mCardView;
    private ImageButton mBackButton;
    private TextView mTitleView;
    private TextView mCountView;
    private TextView mEmptyView;
    private EditText mSearch;
    private RecyclerView mGridView;
    private ExpandedGridAdapter mAdapter;
    private final ActivityContext mActivityContext;
    private OnExpandedSheetDismissListener mDismissListener;
    private final Rect mInsets = new Rect();
    private final List<IconMotion> mIconMotions = new ArrayList<>();
    private AppCategoryCardView mSource;
    private ValueAnimator mAnimator;
    private OneShotPreDrawListener mPendingOpen;
    private float mProgress;
    private boolean mClosing;

    public AppCategoryExpandedSheet(Context context) {
        this(context, null);
    }

    public AppCategoryExpandedSheet(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppCategoryExpandedSheet(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCardView = findViewById(R.id.expanded_sheet_card);
        mBackButton = findViewById(R.id.expanded_sheet_back_button);
        mTitleView = findViewById(R.id.expanded_sheet_title);
        mCountView = findViewById(R.id.expanded_sheet_count);
        mEmptyView = findViewById(R.id.expanded_sheet_empty);
        mSearch = findViewById(R.id.expanded_sheet_search);
        mGridView = findViewById(R.id.expanded_sheet_grid);
        mBackButton.setOnClickListener(v -> hide(true));
        setOnClickListener(v -> hide(true));
        mCardView.setOnClickListener(v -> {});
        mCardView.setFocusableInTouchMode(true);
        mCardView.setBackground(CaddyStyle.background(getContext(), true));
        mTitleView.setTextColor(CaddyStyle.textColor(getContext()));
        mGridView.setLayoutManager(new GridLayoutManager(getContext(), 4));
        mAdapter = new ExpandedGridAdapter();
        mGridView.setAdapter(mAdapter);
        mSearch.setBackground(CaddyStyle.background(getContext(), false));
        mSearch.setTextColor(CaddyStyle.textColor(getContext()));
        mSearch.setHintTextColor(
                ColorUtils.setAlphaComponent(CaddyStyle.textColor(getContext()), 150));
        mSearch.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence text, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence text, int start, int before, int count) {
                        mAdapter.filter(text.toString());
                        mGridView.scrollToPosition(0);
                        mEmptyView.setVisibility(mAdapter.getItemCount() == 0 ? VISIBLE : GONE);
                        requestLayout();
                    }

                    @Override
                    public void afterTextChanged(Editable text) {}
                });
        mSearch.setOnEditorActionListener(
                (view, action, event) -> {
                    if (action != EditorInfo.IME_ACTION_DONE) return false;
                    hideKeyboard();
                    mCardView.requestFocus();
                    return true;
                });
        styleHeading();
    }

    private void styleHeading() {
        int text = CaddyStyle.textColor(getContext());
        mTitleView.setTextColor(text);
        mSearch.setBackground(CaddyStyle.background(getContext(), false));
        mSearch.setTextColor(text);
        mSearch.setHintTextColor(ColorUtils.setAlphaComponent(text, 150));
        mCountView.setTextColor(ColorUtils.setAlphaComponent(text, 140));
        mEmptyView.setTextColor(ColorUtils.setAlphaComponent(text, 160));
        mBackButton.setImageTintList(ColorStateList.valueOf(text));
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(ColorUtils.setAlphaComponent(text, 26));
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        mBackButton.setBackground(
                new RippleDrawable(
                        ColorStateList.valueOf(ColorUtils.setAlphaComponent(text, 50)),
                        circle,
                        mask));
    }

    private void hideKeyboard() {
        InputMethodManager ime = getContext().getSystemService(InputMethodManager.class);
        if (ime != null) ime.hideSoftInputFromWindow(getWindowToken(), 0);
        mSearch.clearFocus();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mClosing || isAnimating()) return true;
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return true;
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        requestLayout();
    }

    private int dp(float value) {
        return CaddyStyle.dp(getContext(), value);
    }

    private int cellHeight() {
        int icon = mActivityContext.getDeviceProfile().getAllAppsProfile().getIconSizePx();
        int text =
                LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext())
                        ? (int) Math.ceil(getResources().getDisplayMetrics().scaledDensity * 18)
                                + dp(4)
                        : 0;
        return Math.max(dp(88), icon + dp(16)) + text;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        WindowInsets wi = getRootWindowInsets();
        int keyboard = wi == null ? 0 : wi.getInsets(WindowInsets.Type.ime()).bottom;
        int availableWidth = Math.max(0, width - mInsets.left - mInsets.right - dp(40));
        int availableHeight =
                Math.max(0, height - mInsets.top - Math.max(mInsets.bottom, keyboard) - dp(48));
        int cardWidth = Math.min(dp(380), availableWidth);
        int count = mAdapter == null ? 0 : mAdapter.getItemCount();
        int spans =
                Math.max(
                        1,
                        Math.min(Math.min(4, Math.max(1, count)), (cardWidth - dp(40)) / dp(80)));
        GridLayoutManager layout = (GridLayoutManager) mGridView.getLayoutManager();
        if (layout.getSpanCount() != spans) layout.setSpanCount(spans);
        int rows = (count + spans - 1) / spans;
        int headerHeight =
                dp(94)
                        + (mSearch.getVisibility() == VISIBLE ? dp(56) : 0)
                        + (mEmptyView.getVisibility() == VISIBLE ? dp(60) : 0);
        int cardHeight =
                Math.min(availableHeight, headerHeight + Math.min(dp(420), rows * cellHeight()));
        mCardView.measure(
                MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY));
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        WindowInsets wi = getRootWindowInsets();
        int keyboard = wi == null ? 0 : wi.getInsets(WindowInsets.Type.ime()).bottom;
        int safeBottom = bottom - top - Math.max(mInsets.bottom, keyboard);
        int x =
                mInsets.left
                        + (right
                                        - left
                                        - mInsets.left
                                        - mInsets.right
                                        - mCardView.getMeasuredWidth())
                                / 2;
        int y = mInsets.top + (safeBottom - mInsets.top - mCardView.getMeasuredHeight()) / 2;
        mCardView.layout(x, y, x + mCardView.getMeasuredWidth(), y + mCardView.getMeasuredHeight());
    }

    public void show(@NonNull AppCategoryGroup group, @Nullable AppCategoryCardView source) {
        hide(false);
        mClosing = false;
        mSource = source;
        mTitleView.setText(group.getTitleRes());
        mTitleView.setTextColor(CaddyStyle.textColor(getContext()));
        mCardView.setBackground(CaddyStyle.background(getContext(), true));
        mAdapter.setApps(group.getApps());
        mSearch.setText("");
        mSearch.setVisibility(group.getAppCount() > 12 ? VISIBLE : GONE);
        mEmptyView.setVisibility(GONE);
        mCountView.setText(
                getResources()
                        .getQuantityString(
                                R.plurals.caddy_app_count,
                                group.getAppCount(),
                                group.getAppCount()));
        styleHeading();
        mGridView.scrollToPosition(0);
        setVisibility(VISIBLE);
        setAlpha(1f);
        mCardView.setAlpha(0f);
        mCardView.setScaleX(1f);
        mCardView.setScaleY(1f);
        requestLayout();
        mPendingOpen =
                OneShotPreDrawListener.add(
                        this,
                        () -> {
                            mPendingOpen = null;
                            if (!isOpen() || mClosing) return;
                            prepareIconMotions();
                            if (mSource != null)
                                mSource.getPreviewContent().setVisibility(INVISIBLE);
                            setContentClipping(false);
                            applyProgress(0f);
                            animateProgress(1f, 300);
                        });
    }

    private void prepareIconMotions() {
        mIconMotions.clear();
        int[] panelPos = new int[2];
        mCardView.getLocationOnScreen(panelPos);
        float centerX = panelPos[0] + mCardView.getWidth() / 2f;
        float centerY = panelPos[1] + mCardView.getHeight() / 2f;
        for (int i = 0; i < mGridView.getChildCount(); i++) {
            View child = mGridView.getChildAt(i);
            if (!(child instanceof BubbleTextView)) continue;
            int position = mGridView.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;
            BubbleTextView icon = (BubbleTextView) child;
            Rect target = iconRect(icon);
            if (target.isEmpty()) continue;
            View preview =
                    mSource == null
                            ? null
                            : mSource.getPreviewIcon(mAdapter.getOriginalIndex(position));
            Rect origin = preview != null ? iconRect(preview) : new Rect();
            boolean fromPreview = !origin.isEmpty();
            if (!fromPreview && mSource != null) {
                origin = iconRect(mSource.getOverflowPreview());
            }
            if (origin.isEmpty()) origin.set(target);
            Rect localIcon = new Rect();
            icon.getIconBounds(localIcon);
            icon.setPivotX(localIcon.exactCenterX());
            icon.setPivotY(localIcon.exactCenterY());
            IconMotion motion = new IconMotion();
            motion.icon = icon;
            motion.dx =
                    AppLibraryCategoryAnimation.previewTranslation(
                            origin.exactCenterX(), target.exactCenterX(), centerX);
            motion.dy =
                    AppLibraryCategoryAnimation.previewTranslation(
                            origin.exactCenterY(), target.exactCenterY(), centerY);
            motion.scale =
                    fromPreview
                            ? AppLibraryCategoryAnimation.previewScale(
                                    origin.width(), target.width())
                            : .4f;
            motion.preview = fromPreview;
            motion.stagger = Math.min(.18f, i * .035f);
            mIconMotions.add(motion);
        }
    }

    private Rect iconRect(View view) {
        Rect rect = new Rect();
        if (!view.isAttachedToWindow() || view.getVisibility() != VISIBLE) return rect;
        int[] pos = new int[2];
        view.getLocationOnScreen(pos);
        if (view instanceof BubbleTextView) {
            ((BubbleTextView) view).getIconBounds(rect);
        } else {
            rect.set(0, 0, view.getWidth(), view.getHeight());
        }
        rect.offset(pos[0], pos[1]);
        return rect;
    }

    private void applyProgress(float progress) {
        mProgress = progress;
        mCardView.setAlpha(1f);
        float scale = START_SCALE + (1f - START_SCALE) * progress;
        mCardView.setScaleX(scale);
        mCardView.setScaleY(scale);
        mCardView.getBackground().setAlpha(Math.round(255 * progress));
        float headerAlpha = Utilities.boundToRange((progress - .2f) / .8f, 0f, 1f);
        mTitleView.setAlpha(headerAlpha);
        mCountView.setAlpha(headerAlpha);
        mSearch.setAlpha(headerAlpha);
        mBackButton.setAlpha(headerAlpha);
        for (IconMotion motion : mIconMotions) {
            float p =
                    Utilities.boundToRange(
                            (progress - motion.stagger) / (1f - motion.stagger), 0f, 1f);
            motion.icon.setTranslationX(motion.dx * (1f - p));
            motion.icon.setTranslationY(motion.dy * (1f - p));
            motion.icon.setScaleX(motion.scale + (1f - motion.scale) * p);
            motion.icon.setScaleY(motion.scale + (1f - motion.scale) * p);
            motion.icon.setAlpha(
                    motion.preview ? 1f : Utilities.boundToRange((p - .35f) / .65f, 0f, 1f));
            BubbleTextView.TEXT_ALPHA_PROPERTY.set(
                    motion.icon, Utilities.boundToRange((p - .35f) / .65f, 0f, 1f));
        }
        if (mDismissListener != null) mDismissListener.onExpansionProgress(progress);
        invalidate();
    }

    private void animateProgress(float target, long duration) {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            applyProgress(target);
            if (target == 0f) finishClose();
            else finishOpen();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(mProgress, target);
        animator.setDuration(duration);
        animator.setInterpolator(target == 1f ? OPEN : CLOSE);
        animator.addUpdateListener(a -> applyProgress((Float) a.getAnimatedValue()));
        animator.addListener(
                new AnimatorListenerAdapter() {
                    private boolean mCancelled;

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        mCancelled = true;
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mCancelled || mAnimator != animation) return;
                        mAnimator = null;
                        if (target == 0f) finishClose();
                        else finishOpen();
                    }
                });
        mAnimator = animator;
        animator.start();
    }

    private void finishOpen() {
        for (IconMotion motion : mIconMotions) resetIcon(motion.icon);
        setContentClipping(true);
        mCardView.requestFocus();
        announceForAccessibility(mTitleView.getText());
    }

    private void resetIcon(BubbleTextView icon) {
        icon.setTranslationX(0f);
        icon.setTranslationY(0f);
        icon.setScaleX(1f);
        icon.setScaleY(1f);
        icon.setAlpha(1f);
        BubbleTextView.TEXT_ALPHA_PROPERTY.set(icon, 1f);
    }

    private void setContentClipping(boolean enabled) {
        ((ViewGroup) mCardView).setClipChildren(enabled);
        mCardView.setClipToOutline(enabled);
        mGridView.setClipChildren(enabled);
        mGridView.setClipToPadding(enabled);
    }

    public void hide(boolean animate) {
        if (mPendingOpen != null) {
            mPendingOpen.removeListener();
            mPendingOpen = null;
        }
        if (!isOpen()) return;
        hideKeyboard();
        if (!animate || mProgress == 0f) {
            if (mAnimator != null) {
                mAnimator.cancel();
                mAnimator = null;
            }
            finishClose();
            return;
        }
        if (mClosing) return;
        mClosing = true;
        mGridView.stopScroll();
        if (mProgress == 1f) {
            // The user may have scrolled: recalculate the morph for the currently visible icons.
            if (mSource != null) mSource.getPreviewContent().setVisibility(VISIBLE);
            prepareIconMotions();
            if (mSource != null) mSource.getPreviewContent().setVisibility(INVISIBLE);
        }
        setContentClipping(false);
        long duration = Math.max(100, Math.round(250 * mProgress));
        if (mDismissListener != null) mDismissListener.onDismissStarted(duration);
        animateProgress(0f, duration);
    }

    private void finishClose() {
        mProgress = 0f;
        setVisibility(GONE);
        for (IconMotion motion : mIconMotions) resetIcon(motion.icon);
        mIconMotions.clear();
        if (mSource != null) {
            mSource.getPreviewContent().setVisibility(VISIBLE);
            mSource = null;
        }
        mClosing = false;
        if (mDismissListener != null) {
            mDismissListener.onExpansionProgress(0f);
            mDismissListener.onDismissFinished();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(
                Color.argb(
                        Math.round(mProgress * (Utilities.isDarkTheme(getContext()) ? 120 : 75)),
                        0,
                        0,
                        0));
        super.onDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        hide(false);
        super.onDetachedFromWindow();
    }

    public void setOnExpandedSheetDismissListener(
            OnExpandedSheetDismissListener listener) {
        mDismissListener = listener;
    }

    public boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    public boolean isAnimating() {
        return mAnimator != null || mPendingOpen != null;
    }

    private static class IconMotion {
        BubbleTextView icon;
        float dx, dy, scale, stagger;
        boolean preview;
    }

    private class ExpandedGridAdapter extends RecyclerView.Adapter<ExpandedGridAdapter.ViewHolder> {
        private final List<AppInfo> mApps = new ArrayList<>();
        private final List<AppInfo> mAllApps = new ArrayList<>();

        void setApps(List<AppInfo> apps) {
            mAllApps.clear();
            mAllApps.addAll(apps);
            filter("");
        }

        int getOriginalIndex(int position) {
            return position >= 0 && position < mApps.size()
                    ? mAllApps.indexOf(mApps.get(position))
                    : -1;
        }

        void filter(String query) {
            String normalized = query.trim().toLowerCase(Locale.getDefault());
            mApps.clear();
            for (AppInfo app : mAllApps) {
                if (normalized.isEmpty()
                        || (app.title != null
                                && app.title
                                        .toString()
                                        .toLowerCase(Locale.getDefault())
                                        .contains(normalized))) {
                    mApps.add(app);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            BubbleTextView icon =
                    (BubbleTextView)
                            LayoutInflater.from(parent.getContext())
                                    .inflate(R.layout.app_library_icon, parent, false);
            icon.setLayoutParams(
                    new RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, cellHeight()));
            return new ViewHolder(icon);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            BubbleTextView icon = (BubbleTextView) holder.itemView;
            resetIcon(icon);
            icon.reset();
            icon.applyFromApplicationInfo(mApps.get(position));
            icon.setTextColor(CaddyStyle.textColor(getContext()));
            icon.getLayoutParams().height = cellHeight();
            icon.setOnClickListener(
                    v -> {
                        // Keep the clicked icon attached and visible while launch bounds are
                        // captured.
                        mActivityContext.getItemOnClickListener().onClick(v);
                        hide(false);
                    });
            icon.setOnLongClickListener(
                    v ->
                            mActivityContext instanceof Launcher
                                    && ((Launcher) mActivityContext).getPopupControllerForAppIcons()
                                            != null
                                    && ((Launcher) mActivityContext)
                                                    .getPopupControllerForAppIcons()
                                                    .show(v)
                                            != null);
        }

        @Override
        public int getItemCount() {
            return mApps.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(BubbleTextView icon) {
                super(icon);
            }
        }
    }
}
