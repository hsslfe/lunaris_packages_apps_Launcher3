package com.android.launcher3.applibrary.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class AppLibraryAlphabetIndexer extends View {

    public interface OnSectionSelectedListener {
        void onSectionSelected(String section);
    }

    private static final int INVALID_POINTER_ID = -1;
    private static final String[] ALPHABET = {
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
            "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "#"
    };

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private OnSectionSelectedListener mListener;
    private final int mTouchSlop;
    private TextView mLetterPreview;
    private int mLastSelectedIndex = -1;
    private int mActivePointerId = INVALID_POINTER_ID;
    private float mDownX;
    private float mDownY;
    private boolean mDragging;
    private boolean mRejected;

    public AppLibraryAlphabetIndexer(Context context) {
        this(context, null);
    }

    public AppLibraryAlphabetIndexer(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppLibraryAlphabetIndexer(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        init();
    }

    private void init() {
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        float textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 10f, getResources().getDisplayMetrics());
        mPaint.setTextSize(textSize);
        mPaint.setColor(Color.WHITE);
    }

    public void setOnSectionSelectedListener(OnSectionSelectedListener listener) {
        mListener = listener;
    }

    public void setLetterPreview(TextView preview) {
        cancelDrag();
        mLetterPreview = preview;
        if (preview != null) preview.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int height = getHeight();
        int width = getWidth();
        if (height <= 0 || width <= 0) {
            return;
        }

        int count = ALPHABET.length;
        float itemHeight = (float) height / count;
        float xPos = width / 2f;

        Paint.FontMetrics fontMetrics = mPaint.getFontMetrics();
        float fontHeight = fontMetrics.descent - fontMetrics.ascent;
        float baseLineOffset = (itemHeight - fontHeight) / 2f - fontMetrics.ascent;

        for (int i = 0; i < count; i++) {
            float yPos = i * itemHeight + baseLineOffset;
            mPaint.setAlpha(mDragging && i != mLastSelectedIndex ? 170 : 255);
            canvas.drawText(ALPHABET[i], xPos, yPos, mPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelDrag();
                if (!isEnabled() || getHeight() <= 0) return false;
                mActivePointerId = event.getPointerId(0);
                mDownX = event.getX();
                mDownY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == INVALID_POINTER_ID || mRejected) {
                    return true;
                }
                int pointer = event.findPointerIndex(mActivePointerId);
                if (pointer < 0) {
                    cancelDrag();
                    return true;
                }
                float x = event.getX(pointer);
                float y = event.getY(pointer);
                if (!mDragging) {
                    float dx = Math.abs(x - mDownX);
                    float dy = Math.abs(y - mDownY);
                    // Never let a horizontal edge/back swipe become a letter selection.
                    if (dx > mTouchSlop && dx >= dy) {
                        mRejected = true;
                        return true;
                    }
                    if (dy <= mTouchSlop || dy <= dx * 1.5f) return true;
                    mDragging = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                selectAt(y);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // A second finger must not turn a pending touch into fast scrolling.
                cancelDrag();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                return true;
            default:
                return true;
        }
    }

    private void selectAt(float y) {
        if (getHeight() <= 0) return;
        int index = Math.max(0, Math.min(ALPHABET.length - 1,
                (int) (y * ALPHABET.length / getHeight())));
        if (index == mLastSelectedIndex) return;
        mLastSelectedIndex = index;
        updatePreview(index);
        invalidate();
        if (mListener != null) mListener.onSectionSelected(ALPHABET[index]);
    }

    private void updatePreview(int index) {
        if (mLetterPreview == null || !(getParent() instanceof View parent)) return;
        boolean showing = mLetterPreview.getVisibility() == View.VISIBLE;
        float center = getTop() + (index + 0.5f) * getHeight() / ALPHABET.length;
        float y = center - mLetterPreview.getHeight() / 2f;
        mLetterPreview.setTranslationY(Math.max(0f, Math.min(y,
                parent.getHeight() - mLetterPreview.getHeight())));
        mLetterPreview.setText(ALPHABET[index]);
        if (!showing) {
            mLetterPreview.animate().cancel();
            mLetterPreview.setVisibility(View.VISIBLE);
            boolean animate = isAttachedToWindow() && ValueAnimator.areAnimatorsEnabled();
            mLetterPreview.setAlpha(animate ? 0f : 1f);
            mLetterPreview.setScaleX(animate ? 0.85f : 1f);
            mLetterPreview.setScaleY(animate ? 0.85f : 1f);
            if (animate) {
                mLetterPreview.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(100).start();
            }
        }
    }

    public void cancelDrag() {
        boolean wasDragging = mDragging;
        mDragging = false;
        mRejected = false;
        mActivePointerId = INVALID_POINTER_ID;
        mLastSelectedIndex = -1;
        if (wasDragging && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (mLetterPreview != null) {
            mLetterPreview.animate().cancel();
            mLetterPreview.setVisibility(View.INVISIBLE);
            mLetterPreview.setAlpha(1f);
            mLetterPreview.setScaleX(1f);
            mLetterPreview.setScaleY(1f);
        }
        invalidate();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != View.VISIBLE) cancelDrag();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelDrag();
        super.onDetachedFromWindow();
    }
}
