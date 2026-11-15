package com.android.launcher3.applibrary.view;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.CaddyStyle;
import com.android.launcher3.applibrary.model.AppCategory;
import com.android.launcher3.applibrary.model.AppCategoryGroup;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;

/** App Library's category data and interactions, with a full-width recent/suggested-app row. */
public class AppLibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_RECENT = 0;
    private static final int TYPE_CATEGORY = 1;
    private final ActivityContext mActivityContext;
    private final List<AppCategoryGroup> mGroups = new ArrayList<>();
    private final List<AppInfo> mRecentApps = new ArrayList<>();
    private AppCategoryCardView.OnCategoryExpandListener mExpandListener;

    public AppLibraryAdapter(ActivityContext activityContext) {
        mActivityContext = activityContext;
    }

    public void setOnCategoryExpandListener(AppCategoryCardView.OnCategoryExpandListener listener) {
        mExpandListener = listener;
    }

    public void setGroups(List<AppCategoryGroup> groups) {
        mGroups.clear();
        if (groups != null) {
            for (AppCategoryGroup group : groups) {
                // Suggestions are displayed as a row, rather than a duplicate category card.
                if (group.getCategory() != AppCategory.SUGGESTIONS) mGroups.add(group);
            }
        }
        notifyDataSetChanged();
    }

    public void setRecentApps(List<AppInfo> apps) {
        mRecentApps.clear();
        mRecentApps.addAll(apps.subList(0, Math.min(4, apps.size())));
        notifyDataSetChanged();
    }

    public boolean isRecentRow(int position) {
        return !mRecentApps.isEmpty() && position == 0;
    }

    @Override
    public int getItemViewType(int position) {
        return isRecentRow(position) ? TYPE_RECENT : TYPE_CATEGORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_RECENT) {
            LinearLayout row =
                    new LinearLayout(parent.getContext()) {
                        @Override
                        protected void onMeasure(int widthSpec, int heightSpec) {
                            int height =
                                    Math.max(
                                            CaddyStyle.dp(getContext(), 96),
                                            Math.min(
                                                    CaddyStyle.dp(getContext(), 128),
                                                    Math.round(
                                                            MeasureSpec.getSize(widthSpec)
                                                                    * .29f)));
                            if (LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(getContext())) {
                                height +=
                                        Math.round(
                                                getResources().getDisplayMetrics().scaledDensity
                                                        * 18);
                            }
                            super.onMeasure(
                                    widthSpec,
                                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                        }
                    };
            row.setGravity(Gravity.CENTER);
            int padding = CaddyStyle.dp(parent.getContext(), 8);
            row.setPadding(padding, padding, padding, padding);
            RecyclerView.LayoutParams params =
                    new RecyclerView.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(padding / 2, padding / 2, padding / 2, padding);
            row.setLayoutParams(params);
            row.setContentDescription(
                    parent.getContext().getString(R.string.app_library_recent_apps));
            return new RecentViewHolder(row);
        }
        AppCategoryCardView view =
                (AppCategoryCardView)
                        LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.app_library_category_card, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof RecentViewHolder) {
            LinearLayout row = (LinearLayout) holder.itemView;
            row.setBackground(CaddyStyle.background(row.getContext(), false));
            row.removeAllViews();
            for (AppInfo app : mRecentApps) {
                BubbleTextView icon =
                        (BubbleTextView)
                                LayoutInflater.from(row.getContext())
                                        .inflate(R.layout.app_library_icon, row, false);
                icon.applyFromApplicationInfo(app);
                icon.setTextColor(CaddyStyle.textColor(row.getContext()));
                icon.setOnClickListener(mActivityContext.getItemOnClickListener());
                icon.setOnLongClickListener(
                        v ->
                                mActivityContext instanceof Launcher
                                        && ((Launcher) mActivityContext)
                                                        .getPopupControllerForAppIcons()
                                                != null
                                        && ((Launcher) mActivityContext)
                                                        .getPopupControllerForAppIcons()
                                                        .show(v)
                                                != null);
                row.addView(
                        icon,
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
        } else {
            AppCategoryGroup group = mGroups.get(position - (mRecentApps.isEmpty() ? 0 : 1));
            AppCategoryCardView card = (AppCategoryCardView) holder.itemView;
            card.setBackground(CaddyStyle.background(card.getContext(), false));
            card.setOnCategoryExpandListener(mExpandListener);
            card.bindGroup(group, mActivityContext);
        }
    }

    @Override
    public int getItemCount() {
        return mGroups.size() + (mRecentApps.isEmpty() ? 0 : 1);
    }

    private static class CategoryViewHolder extends RecyclerView.ViewHolder {
        CategoryViewHolder(AppCategoryCardView card) {
            super(card);
        }
    }

    private static class RecentViewHolder extends RecyclerView.ViewHolder {
        RecentViewHolder(LinearLayout row) {
            super(row);
        }
    }
}
