package com.android.launcher3.applibrary.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class AppLibraryAlphabetIndexerTest {
    private AppLibraryAlphabetIndexer mIndexer;
    private TextView mPreview;
    private final List<String> mSelected = new ArrayList<>();
    private long mDownTime;
    private int mSlop;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        mSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        FrameLayout parent = new FrameLayout(context);
        mIndexer = new AppLibraryAlphabetIndexer(context);
        mPreview = new TextView(context);
        parent.addView(mIndexer, new FrameLayout.LayoutParams(40, 540));
        parent.addView(mPreview, new FrameLayout.LayoutParams(100, 100));
        parent.layout(0, 0, 300, 600);
        mIndexer.layout(260, 0, 300, 540);
        mPreview.layout(0, 0, 100, 100);
        mIndexer.setLetterPreview(mPreview);
        mIndexer.setOnSectionSelectedListener(mSelected::add);
    }

    private void send(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) mDownTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(mDownTime, SystemClock.uptimeMillis(),
                action, x, y, 0);
        try {
            assertTrue(mIndexer.onTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    @Test
    public void tapAndSmallJitter_doNotSelect() {
        send(MotionEvent.ACTION_DOWN, 20, 100);
        send(MotionEvent.ACTION_MOVE, 20, 100 + mSlop / 2f);
        send(MotionEvent.ACTION_UP, 20, 100);
        assertTrue(mSelected.isEmpty());
        assertEquals(View.INVISIBLE, mPreview.getVisibility());
    }

    @Test
    public void horizontalBackGesture_neverSelectsEvenIfItLaterMovesVertically() {
        send(MotionEvent.ACTION_DOWN, 20, 100);
        send(MotionEvent.ACTION_MOVE, 20 - mSlop * 2f, 101);
        send(MotionEvent.ACTION_MOVE, 20 - mSlop * 2f, 400);
        send(MotionEvent.ACTION_CANCEL, 20, 400);
        assertTrue(mSelected.isEmpty());
        assertEquals(View.INVISIBLE, mPreview.getVisibility());
    }

    @Test
    public void verticalDrag_updatesLetterAndDismissesOnRelease() {
        send(MotionEvent.ACTION_DOWN, 20, 0);
        assertTrue(mSelected.isEmpty());
        send(MotionEvent.ACTION_MOVE, 20, 200);
        assertEquals(List.of("K"), mSelected);
        assertEquals("K", mPreview.getText().toString());
        assertEquals(View.VISIBLE, mPreview.getVisibility());
        send(MotionEvent.ACTION_MOVE, 20, 201);
        assertEquals(1, mSelected.size());
        send(MotionEvent.ACTION_MOVE, 20, 400);
        assertEquals(List.of("K", "U"), mSelected);
        assertEquals("U", mPreview.getText().toString());
        send(MotionEvent.ACTION_UP, 20, 400);
        assertEquals(View.INVISIBLE, mPreview.getVisibility());
    }

    @Test
    public void cancelAndHide_clearPreviewAndRequireNewDrag() {
        send(MotionEvent.ACTION_DOWN, 20, 0);
        send(MotionEvent.ACTION_MOVE, 20, 200);
        assertFalse(mSelected.isEmpty());
        send(MotionEvent.ACTION_CANCEL, 20, 200);
        assertEquals(View.INVISIBLE, mPreview.getVisibility());
        send(MotionEvent.ACTION_DOWN, 20, 0);
        send(MotionEvent.ACTION_MOVE, 20, 200);
        mIndexer.setVisibility(View.GONE);
        assertEquals(View.INVISIBLE, mPreview.getVisibility());
    }
}
