package com.android.launcher3.quickspace.views;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.quickspace.QuickCalendarController;
import com.android.launcher3.quickspace.QuickEventsController;
import com.android.launcher3.quickspace.QuickspaceController;
import com.android.launcher3.quickspace.receivers.QuickSpaceActionReceiver;
import com.android.launcher3.smartspace.QuickspaceBatteryController;

/** Voltage's full-date clock with weather alongside it and optional detail chips. */
public final class VoltagePagedView extends ScrollView {
    private TextClock mDate;
    private PagedTextClock mClock;
    private View mWeather, mDivider, mChips;
    private ImageView mWeatherIcon;
    private TextView mTemperature, mCalendarRow, mEventRow, mBatteryRow;
    private View mCalendarChip, mMediaChip, mNoteChip, mBatteryChip, mSelectedChip;
    private QuickspaceController mController;
    private QuickCalendarController mCalendar;
    private QuickspaceBatteryController mBattery;
    private ClipDrawable mBatteryFill;
    private boolean mActive;
    private int mSizeClass = -1;

    public VoltagePagedView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDate = findViewById(R.id.paged_date);
        mClock = findViewById(R.id.paged_clock);
        mWeather = findViewById(R.id.paged_weather);
        mDivider = findViewById(R.id.paged_divider);
        mWeatherIcon = findViewById(R.id.paged_weather_icon);
        mTemperature = findViewById(R.id.paged_temperature);
        mCalendarRow = findViewById(R.id.paged_calendar);
        mEventRow = findViewById(R.id.paged_event);
        mBatteryRow = findViewById(R.id.paged_battery);
        mChips = findViewById(R.id.paged_chips);
        mCalendarChip = findViewById(R.id.paged_calendar_chip);
        mMediaChip = findViewById(R.id.paged_media_chip);
        mNoteChip = findViewById(R.id.paged_note_chip);
        mBatteryChip = findViewById(R.id.paged_battery_chip);
        updateDateFormat();
        for (View chip : new View[] {mCalendarChip, mMediaChip, mNoteChip, mBatteryChip}) {
            chip.setOnClickListener(v -> {
                mSelectedChip = mSelectedChip == v ? null : v;
                updateDetails();
            });
        }
        OnClickListener alarms = v -> launch(new Intent(AlarmClock.ACTION_SHOW_ALARMS), v);
        mClock.setOnClickListener(alarms);
        mDate.setOnClickListener(alarms);
        mWeather.setOnClickListener(QuickSpaceActionReceiver.getWeatherAction());
        mCalendarRow.setOnClickListener(v -> {
            QuickCalendarController.CalendarEvent event = mCalendar.getEvent();
            if (event != null) launch(new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.getId())), v);
        });
        mBatteryFill = new ClipDrawable(new ColorDrawable(0x33ffffff),
                Gravity.START, ClipDrawable.HORIZONTAL);
        mBatteryRow.setBackground(new LayerDrawable(new Drawable[] {
                mBatteryRow.getBackground(), mBatteryFill}));
        mBatteryRow.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
            }
        });
        mBatteryRow.setClipToOutline(true);
        mBatteryRow.setOnClickListener(v -> mBattery.cycleOrOpen(v));
        mBatteryRow.setOnLongClickListener(v -> {
            mBattery.openPagedSettings(v);
            return true;
        });
    }

    private void launch(Intent intent, View view) {
        Launcher.getLauncher(getContext()).startActivitySafely(view, intent, null);
    }

    private void updateDateFormat() {
        String format = DateFormat.getBestDateTimePattern(
                getResources().getConfiguration().getLocales().get(0), "EEEEMMMMd");
        mDate.setFormat12Hour(format);
        mDate.setFormat24Hour(format);
    }

    @Override
    protected void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateDateFormat();
        mSizeClass = -1;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float density = getResources().getDisplayMetrics().density;
        float height = MeasureSpec.getSize(heightSpec)
                / (density * Math.max(1f, getResources().getConfiguration().fontScale));
        int sizeClass = height < 110 ? 0 : height < 200 ? 1 : 2;
        if (mSizeClass != sizeClass) {
            mSizeClass = sizeClass;
            View content = getChildAt(0);
            content.setPaddingRelative(content.getPaddingStart(),
                    Math.round((sizeClass == 2 ? 24 : 0) * density),
                    content.getPaddingEnd(), 0);
            mClock.setTextSize(sizeClass == 0 ? 32 : sizeClass == 1 ? 52 : 80);
            mDate.setTextSize(sizeClass == 0 ? 15 : sizeClass == 1 ? 21 : 28);
            mTemperature.setTextSize(sizeClass == 0 ? 14 : sizeClass == 1 ? 18 : 24);
            int iconSize = Math.round(density * (sizeClass == 0 ? 20 : sizeClass == 1 ? 28 : 40));
            mWeatherIcon.getLayoutParams().width = iconSize;
            mWeatherIcon.getLayoutParams().height = iconSize;
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    public void bind(QuickspaceController controller) {
        mController = controller;
        mClock.refreshAccent();
        String temperature = controller.getPagedWeatherTemperature();
        boolean weather = !TextUtils.isEmpty(temperature);
        visible(mWeather, weather);
        visible(mDivider, weather);
        if (weather) {
            mTemperature.setText(temperature);
            Drawable icon = controller.getWeatherIcon();
            mWeatherIcon.setImageDrawable(icon);
            visible(mWeatherIcon, icon != null);
        }
        updateDetails();
    }

    public void setActive(boolean active) {
        if (mActive == active) return;
        mActive = active;
        if (active) {
            if (mCalendar == null) {
                mCalendar = new QuickCalendarController(getContext(), this::updateDetails);
                mBattery = new QuickspaceBatteryController(getContext(), this::updateDetails);
            }
            mCalendar.onResume();
            mBattery.onResume();
        } else {
            mCalendar.onPause();
            mBattery.onPause();
        }
        updateDetails();
    }

    private void updateDetails() {
        if (mController == null) return;
        QuickEventsController events = mController.getEventController();
        boolean music = events != null && events.isNowPlaying()
                && LauncherPrefs.SHOW_QUICKSPACE_NOWPLAYING.get(getContext());
        boolean note = !music && events != null && events.isQuickEvent()
                && LauncherPrefs.SHOW_QUICKSPACE_PSONALITY.get(getContext());
        String eventText = null;
        if (music) {
            String title = events.getTitle();
            String artist = events.getActionTitle();
            eventText = TextUtils.isEmpty(artist) ? title : TextUtils.isEmpty(title) ? artist
                    : title + " — " + artist;
        } else if (note) {
            eventText = events.getActionTitle();
        }
        QuickCalendarController.CalendarEvent event = mCalendar == null ? null : mCalendar.getEvent();
        String battery = mBattery == null ? null : mBattery.getPagedText();
        boolean minimal = LauncherPrefs.QUICKSPACE_PAGED_MINIMAL.get(getContext());
        boolean hasEvent = !TextUtils.isEmpty(eventText);
        boolean hasBattery = !TextUtils.isEmpty(battery);
        visible(mChips, minimal && (event != null || hasEvent || hasBattery));
        bindChip(mCalendarChip, minimal && event != null);
        bindChip(mMediaChip, minimal && music && hasEvent);
        bindChip(mNoteChip, minimal && note && hasEvent);
        bindChip(mBatteryChip, minimal && hasBattery);
        visible(mCalendarRow, event != null && (!minimal || mSelectedChip == mCalendarChip));
        if (event != null && mCalendarRow.getVisibility() == VISIBLE) {
            int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH
                    | (event.isAllDay() ? DateUtils.FORMAT_UTC : DateUtils.FORMAT_SHOW_TIME);
            mCalendarRow.setText(DateUtils.formatDateTime(getContext(), event.getStartTime(), flags)
                    + " · " + event.getTitle());
        }
        visible(mEventRow, hasEvent && (!minimal || mSelectedChip == (music ? mMediaChip : mNoteChip)));
        if (mEventRow.getVisibility() == VISIBLE) {
            mEventRow.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    music ? R.drawable.ic_music_note_24dp : R.drawable.ic_quickspace_minimal_psa, 0, 0, 0);
            mEventRow.setText(eventText);
            mEventRow.setOnClickListener(events.getAction());
        }
        visible(mBatteryRow, hasBattery && (!minimal || mSelectedChip == mBatteryChip));
        if (hasBattery) {
            mBatteryRow.setText(battery);
            mBatteryFill.setLevel(Math.max(0, mBattery.getPagedPhoneLevel()) * 100);
        }
    }

    private void bindChip(View chip, boolean show) {
        visible(chip, show);
        if (!show && mSelectedChip == chip) mSelectedChip = null;
        chip.setSelected(chip == mSelectedChip);
        chip.setAlpha(chip == mSelectedChip || mSelectedChip == null ? 1f : .7f);
    }

    private static void visible(View view, boolean show) {
        view.setVisibility(show ? VISIBLE : GONE);
    }

    @Override
    protected void onDetachedFromWindow() {
        setActive(false);
        super.onDetachedFromWindow();
    }

    public void destroy() {
        mController = null;
        setActive(false);
        if (mCalendar != null) mCalendar.onDestroy();
    }
}
