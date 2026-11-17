package com.android.launcher3.applibrary.view;

import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.test.InstrumentationRegistry;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.launcher3.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the keyboard submission contract independently of result layout timing. */
@SmallTest
@UiThreadTest
@RunWith(AndroidJUnit4.class)
public class AppLibrarySearchBarTest {
    private AppLibrarySearchBar mBar;
    private EditText mInput;
    private AppLibrarySearchBar.OnSearchListener mListener;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        mBar = new AppLibrarySearchBar(context);
        mInput = new EditText(context);
        mInput.setId(R.id.app_library_search_input);
        mBar.addView(mInput);
        mBar.onFinishInflate();
        mListener = mock(AppLibrarySearchBar.OnSearchListener.class);
        mBar.setOnSearchListener(mListener);
        mBar.setSearching(true);
        mInput.setText("camera");
        clearInvocations(mListener);
    }

    @Test
    public void searchAction_submitsOnce() {
        mInput.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
        verify(mListener).onSearchSubmit();
    }

    @Test
    public void hardwareEnter_submitsOnKeyUpOnly() {
        mInput.onKeyDown(
                KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        verify(mListener, never()).onSearchSubmit();
        mInput.onKeyUp(
                KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        verify(mListener).onSearchSubmit();
    }

    @Test
    public void goAndDoneActions_submit() {
        mInput.onEditorAction(EditorInfo.IME_ACTION_GO);
        verify(mListener).onSearchSubmit();
        clearInvocations(mListener);
        mInput.onEditorAction(EditorInfo.IME_ACTION_DONE);
        verify(mListener).onSearchSubmit();
    }

    @Test
    public void nextAction_doesNotSubmit() {
        mInput.onEditorAction(EditorInfo.IME_ACTION_NEXT);
        verify(mListener, never()).onSearchSubmit();
    }

    @Test
    public void inactiveSearch_doesNotSubmit_andResetKeepsHandler() {
        mBar.resetSearch();
        mInput.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
        verify(mListener, never()).onSearchSubmit();
        mBar.setSearching(true);
        mInput.setText("camera");
        mInput.onEditorAction(EditorInfo.IME_ACTION_SEARCH);
        verify(mListener).onSearchSubmit();
    }
}
