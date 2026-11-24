package com.android.launcher3.quickspace.views;

import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.widget.TextClock;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.util.Themes;

/** Voltage's first-digit accent, including localized clock digits. */
public class PagedTextClock extends TextClock {
    private boolean mAccented;
    private int mAccentColor;

    public PagedTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
        mAccentColor = Themes.getAttrColor(context, android.R.attr.colorAccent);
        refreshAccent();
    }

    public void refreshAccent() {
        boolean enabled = LauncherPrefs.QUICKSPACE_PAGED_ACCENT.get(getContext());
        if (mAccented == enabled) return;
        mAccented = enabled;
        setText(getText().toString());
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (mAccented && text != null) {
            for (int i = 0; i < text.length();) {
                int digit = Character.codePointAt(text, i);
                int end = i + Character.charCount(digit);
                if (Character.isDigit(digit)) {
                    SpannableString colored = new SpannableString(text);
                    colored.setSpan(new ForegroundColorSpan(mAccentColor), i, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    super.setText(colored, BufferType.SPANNABLE);
                    return;
                }
                i = end;
            }
        }
        super.setText(text, type);
    }
}
