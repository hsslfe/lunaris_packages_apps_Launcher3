package com.android.launcher3.applibrary.view;

import static com.android.app.animation.Interpolators.EMPHASIZED_DECELERATE;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AppLibraryAdapter extends RecyclerView.Adapter<AppLibraryAdapter.ViewHolder> {

    private static final Object RECENT_APPS_PAYLOAD = new Object();
    private static final long RECENT_ICON_DURATION_MS = 200;

    private final ActivityContext mActivityContext;
    private final List<AppCategoryGroup> mGroups = new ArrayList<>();
    private final List<AppInfo> mRecentApps = new ArrayList<>();
    private AppCategoryCardView.OnCategoryExpandListener mExpandListener;
    private boolean mRecentAnimationsEnabled;

    public AppLibraryAdapter(ActivityContext activityContext) {
        mActivityContext = activityContext;
    }

    public void setOnCategoryExpandListener(AppCategoryCardView.OnCategoryExpandListener listener) {
        mExpandListener = listener;
    }

    public void setGroups(List<AppCategoryGroup> groups) {
        mGroups.clear();
        if (groups != null) {
            mGroups.addAll(groups);
        }
        notifyDataSetChanged();
    }

    public void setRecentAnimationsEnabled(boolean enabled) {
        mRecentAnimationsEnabled = enabled;
    }

    public void setRecentApps(List<AppInfo> apps) {
        if (mRecentApps.equals(apps)) return;
        boolean hadRecentApps = !mRecentApps.isEmpty();
        boolean sameSize = mRecentApps.size() == apps.size();
        mRecentApps.clear();
        mRecentApps.addAll(apps);
        if (hadRecentApps && !mRecentApps.isEmpty()) {
            if (mRecentAnimationsEnabled && sameSize) {
                notifyItemChanged(0, RECENT_APPS_PAYLOAD);
            } else {
                notifyItemChanged(0);
            }
        } else if (hadRecentApps) {
            notifyItemRemoved(0);
        } else if (!mRecentApps.isEmpty()) {
            notifyItemInserted(0);
        }
    }

    boolean isRecentAppsPosition(int position) {
        return position == 0 && !mRecentApps.isEmpty();
    }

    @Override
    public int getItemViewType(int position) {
        return isRecentAppsPosition(position) ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(
                inflater.inflate(
                        viewType == 1
                                ? R.layout.app_library_recent_apps
                                : R.layout.app_library_category_card,
                        parent,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position,
            @NonNull List<Object> payloads) {
        if (isRecentAppsPosition(position) && !payloads.isEmpty()
                && payloads.stream().allMatch(payload -> payload == RECENT_APPS_PAYLOAD)) {
            bindRecentRow((LinearLayout) holder.itemView, mRecentAnimationsEnabled);
        } else {
            onBindViewHolder(holder, position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (isRecentAppsPosition(position)) {
            bindRecentRow((LinearLayout) holder.itemView, false);
        } else {
            AppCategoryGroup group = mGroups.get(position - (mRecentApps.isEmpty() ? 0 : 1));
            AppCategoryCardView card = (AppCategoryCardView) holder.itemView;
            card.setOnCategoryExpandListener(mExpandListener);
            card.bindGroup(group, mActivityContext);
        }
    }

    private void bindRecentRow(LinearLayout row, boolean animate) {
        animate = animate && row.isShown() && row.isLaidOut()
                && row.getWindowVisibility() == View.VISIBLE
                && row.getChildCount() == mRecentApps.size();
        // Capture current visual positions before cancelling any interrupted icon motion.
        int count = row.getChildCount();
        AppInfo[] oldApps = animate ? new AppInfo[count] : null;
        float[] oldX = animate ? new float[count] : null;
        float[] oldAlpha = animate ? new float[count] : null;
        for (int i = 0; i < count; i++) {
            View child = row.getChildAt(i);
            if (animate) {
                if (child.getTag() instanceof AppInfo app) oldApps[i] = app;
                oldX[i] = child.getX();
                oldAlpha[i] = child.getAlpha();
            }
            finishIconAnimation(child);
        }

        int height =
                LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(row.getContext())
                        ? mActivityContext.getDeviceProfile().getAllAppsProfile().getCellHeightPx()
                        : mActivityContext.getDeviceProfile().getAllAppsProfile().getIconSizePx()
                                + Utilities.dpToPx(12, row.getContext());
        while (row.getChildCount() > mRecentApps.size()) {
            ((AppLibraryIconView) row.getChildAt(row.getChildCount() - 1)).clearAppIcon();
            row.removeViewAt(row.getChildCount() - 1);
        }
        for (int i = 0; i < mRecentApps.size(); i++) {
            BubbleTextView icon;
            if (i < row.getChildCount()) {
                icon = (BubbleTextView) row.getChildAt(i);
            } else {
                icon = (BubbleTextView) mActivityContext.getLayoutInflater()
                        .inflate(R.layout.app_library_icon, row, false);
                row.addView(icon);
            }
            AppInfo app = mRecentApps.get(i);
            if (!animate || icon.getTag() != app) {
                icon.reset();
                icon.applyFromApplicationInfo(app);
                icon.setOnClickListener(mActivityContext.getItemOnClickListener());
            }
            if (!animate) {
                icon.setLayoutParams(new LinearLayout.LayoutParams(0, height, 1));
                continue;
            }

            int oldPosition = -1;
            for (int j = 0; j < count; j++) {
                if (sameApp(oldApps[j], app)) {
                    oldPosition = j;
                    break;
                }
            }
            if (oldPosition >= 0) {
                icon.setTranslationX(oldX[oldPosition] - icon.getLeft());
                icon.setAlpha(oldAlpha[oldPosition]);
            } else {
                icon.setAlpha(0f);
                icon.setTranslationX(Utilities.dpToPx(8, row.getContext())
                        * (row.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1));
            }
            if (icon.getTranslationX() != 0f || icon.getAlpha() != 1f) {
                icon.animate().translationX(0f).alpha(1f)
                        .setDuration(RECENT_ICON_DURATION_MS)
                        .setInterpolator(EMPHASIZED_DECELERATE).start();
            }
        }
    }

    private static boolean sameApp(AppInfo first, AppInfo second) {
        return first != null && Objects.equals(first.componentName, second.componentName)
                && Objects.equals(first.user, second.user);
    }

    private static void finishIconAnimation(View icon) {
        icon.animate().cancel();
        icon.setTranslationX(0f);
        icon.setAlpha(1f);
    }

    private static void finishRowAnimations(LinearLayout row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            finishIconAnimation(row.getChildAt(i));
        }
    }

    public void finishRecentAnimations(RecyclerView recyclerView) {
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            if (child.getId() == R.id.app_library_recent_apps && child instanceof LinearLayout row) {
                finishRowAnimations(row);
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ViewHolder holder) {
        if (holder.itemView instanceof LinearLayout row) finishRowAnimations(row);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        if (holder.itemView instanceof AppCategoryCardView card) {
            card.clearAppIcons();
        } else if (holder.itemView instanceof LinearLayout row) {
            finishRowAnimations(row);
            for (int i = 0; i < row.getChildCount(); i++) {
                ((AppLibraryIconView) row.getChildAt(i)).clearAppIcon();
            }
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return mGroups.size() + (mRecentApps.isEmpty() ? 0 : 1);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(View view) {
            super(view);
        }
    }
}
