package com.android.launcher3.applibrary.view;

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

public class AppLibraryAdapter extends RecyclerView.Adapter<AppLibraryAdapter.ViewHolder> {

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
            mGroups.addAll(groups);
        }
        notifyDataSetChanged();
    }

    public void setRecentApps(List<AppInfo> apps) {
        boolean hadRecentApps = !mRecentApps.isEmpty();
        mRecentApps.clear();
        mRecentApps.addAll(apps);
        if (hadRecentApps && !mRecentApps.isEmpty()) {
            notifyItemChanged(0);
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
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (isRecentAppsPosition(position)) {
            LinearLayout row = (LinearLayout) holder.itemView;
            int height =
                    LauncherPrefs.APP_LIBRARY_SHOW_LABELS.get(row.getContext())
                            ? mActivityContext
                                    .getDeviceProfile()
                                    .getAllAppsProfile()
                                    .getCellHeightPx()
                            : mActivityContext
                                            .getDeviceProfile()
                                            .getAllAppsProfile()
                                            .getIconSizePx()
                                    + Utilities.dpToPx(12, row.getContext());
            while (row.getChildCount() > mRecentApps.size()) {
                row.removeViewAt(row.getChildCount() - 1);
            }
            for (int i = 0; i < mRecentApps.size(); i++) {
                BubbleTextView icon;
                if (i < row.getChildCount()) {
                    icon = (BubbleTextView) row.getChildAt(i);
                } else {
                    icon =
                            (BubbleTextView)
                                    mActivityContext
                                            .getLayoutInflater()
                                            .inflate(R.layout.app_library_icon, row, false);
                    row.addView(icon);
                }
                icon.applyFromApplicationInfo(mRecentApps.get(i));
                icon.setOnClickListener(mActivityContext.getItemOnClickListener());
                icon.setLayoutParams(new LinearLayout.LayoutParams(0, height, 1));
            }
        } else {
            AppCategoryGroup group = mGroups.get(position - (mRecentApps.isEmpty() ? 0 : 1));
            AppCategoryCardView card = (AppCategoryCardView) holder.itemView;
            card.setOnCategoryExpandListener(mExpandListener);
            card.bindGroup(group, mActivityContext);
        }
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
