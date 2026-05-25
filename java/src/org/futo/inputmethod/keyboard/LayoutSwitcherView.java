/*
 * kxkb: LayoutSwitcherView — a compact, swipe-driven panel for switching between the layouts of the
 * current language. It implements {@link MoreKeysPanel} so the existing PointerTracker gesture path
 * drives it for free: it is opened mid-swipe off the spacebar (see PointerTracker.openLayoutSwitcherPanel),
 * receives onMoveEvent as the finger travels, highlights the cell under the finger, and on onUpEvent
 * switches to the highlighted layout — all in one motion.
 *
 * This is phase 1 (the "thin slice"): a single vertical column of the current language's layouts,
 * anchored near the swipe so the reach is short. Languages column, header row and settings shortcuts
 * come in later phases.
 *
 * Coordinate model: the view is sized to exactly cover the MainKeyboardView and positioned at its
 * window origin inside the drawing-preview placer, so the keyboard-local coordinates PointerTracker
 * forwards map 1:1 to this view's local space — translateX/Y are therefore the identity.
 */
package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.futo.inputmethod.latin.Subtypes;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public final class LayoutSwitcherView extends View implements MoreKeysPanel {
    private Controller mController = EMPTY_CONTROLLER;

    private final List<String> mSubtypes = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();
    private String mActiveSubtype = "";

    private int mKeyboardColor = Color.BLACK;
    private int mCellColor = Color.DKGRAY;
    private int mTextColor = Color.WHITE;

    private int mKbWidth = 0;
    private int mKbHeight = 0;
    private int mWinX = 0;
    private int mWinY = 0;

    private int mPanelLeft = 0;
    private int mPanelTop = 0;
    private int mPanelWidth = 0;
    private int mCellHeight = 0;
    private Rect[] mCellRects = new Rect[0];
    private int mHighlighted = -1;

    private final Paint mFillPaint = new Paint();
    private final Paint mBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public LayoutSwitcherView(final Context context) {
        super(context);
        mBorderPaint.setStyle(Paint.Style.STROKE);
        mBorderPaint.setStrokeWidth(2.0f);
        mTextPaint.setTextAlign(Paint.Align.CENTER);
    }

    /**
     * @param layouts        ordered list of (subtypeString, displayName) for the current language.
     * @param activeSubtype  the currently active subtype string (marked in the list).
     */
    public void setContents(@Nonnull final List<kotlin.Pair<String, String>> layouts,
            final String activeSubtype, final int keyboardColor, final int cellColor,
            final int textColor, final int kbWidth, final int kbHeight) {
        mSubtypes.clear();
        mLabels.clear();
        for (final kotlin.Pair<String, String> p : layouts) {
            mSubtypes.add(p.getFirst());
            mLabels.add(p.getSecond());
        }
        mActiveSubtype = activeSubtype == null ? "" : activeSubtype;
        mKeyboardColor = keyboardColor;
        mCellColor = cellColor;
        mTextColor = textColor;
        mKbWidth = kbWidth;
        mKbHeight = kbHeight;
        mHighlighted = -1;
    }

    private void layoutCells(final int anchorX, final int anchorY) {
        final int n = mLabels.size();
        mCellHeight = Math.max(1, Math.round(mKbHeight / 6.0f));
        mTextPaint.setTextSize(mCellHeight * 0.42f);

        float maxText = 0;
        for (final String label : mLabels) {
            maxText = Math.max(maxText, mTextPaint.measureText(label));
        }
        final int padding = mCellHeight; // generous horizontal padding
        int w = Math.round(maxText) + padding;
        w = Math.max(w, mKbWidth / 4);
        w = Math.min(w, Math.round(mKbWidth * 0.7f));
        mPanelWidth = w;

        final int totalH = mCellHeight * n;
        int left = anchorX - mPanelWidth / 2;
        left = Math.max(0, Math.min(left, mKbWidth - mPanelWidth));
        final int gap = mCellHeight / 2;
        int bottom = Math.max(totalH, anchorY - gap);
        bottom = Math.min(bottom, mKbHeight);
        final int top = Math.max(0, bottom - totalH);
        mPanelLeft = left;
        mPanelTop = top;

        mCellRects = new Rect[n];
        for (int i = 0; i < n; i++) {
            final int cy = top + i * mCellHeight;
            mCellRects[i] = new Rect(left, cy, left + mPanelWidth, cy + mCellHeight);
        }
    }

    private int cellAt(final int x, final int y) {
        for (int i = 0; i < mCellRects.length; i++) {
            if (mCellRects[i].contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        // Dim the keyboard behind the panel so it reads as an overlay (theme-agnostic).
        canvas.drawColor(0xB0000000);

        final Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        final float textOffset = -(fm.ascent + fm.descent) / 2.0f;

        for (int i = 0; i < mCellRects.length; i++) {
            final Rect r = mCellRects[i];
            final boolean hi = i == mHighlighted;
            mFillPaint.setColor(hi ? mTextColor : mCellColor);
            canvas.drawRect(r, mFillPaint);
            mBorderPaint.setColor(mTextColor);
            canvas.drawRect(r, mBorderPaint);

            mTextPaint.setColor(hi ? mKeyboardColor : mTextColor);
            String label = mLabels.get(i);
            if (mSubtypes.get(i).equals(mActiveSubtype)) {
                label = "\u2022 " + label; // bullet marks the current layout
            }
            canvas.drawText(label, r.centerX(), r.centerY() + textOffset, mTextPaint);
        }
    }

    // --- MoreKeysPanel ---

    @Override
    public void showMoreKeysPanel(final View parentView, final Controller controller,
            final int pointX, final int pointY, final KeyboardActionListener listener,
            final int[] touchOrigin, final boolean strongCaptive) {
        mController = controller;
        mKbWidth = parentView.getWidth();
        mKbHeight = parentView.getHeight();
        final int[] loc = new int[2];
        parentView.getLocationInWindow(loc);
        mWinX = loc[0];
        mWinY = loc[1];
        layoutCells(pointX, pointY);
        mHighlighted = cellAt(pointX, pointY);
        controller.onShowMoreKeysPanel(this);
    }

    @Override
    public void onDownEvent(final int x, final int y, final int pointerId, final long eventTime) {
        mHighlighted = cellAt(x, y);
        invalidate();
    }

    @Override
    public void onMoveEvent(final int x, final int y, final int pointerId, final long eventTime) {
        final int newHi = cellAt(x, y);
        if (newHi != mHighlighted) {
            mHighlighted = newHi;
            invalidate();
        }
    }

    @Override
    public void onUpEvent(final int x, final int y, final int pointerId, final long eventTime) {
        final int sel = cellAt(x, y);
        if (sel >= 0 && sel < mSubtypes.size()) {
            final String target = mSubtypes.get(sel);
            if (!target.equals(mActiveSubtype)) {
                // Writing ActiveSubtype is observed by LatinIME, which performs the switch.
                Subtypes.INSTANCE.switchToSubtypeString(getContext(), target);
            }
        }
    }

    @Override
    public void dismissMoreKeysPanel() {
        if (isShowingInParent()) {
            mController.onDismissMoreKeysPanel();
        }
    }

    @Override
    public int translateX(final int x) {
        return x;
    }

    @Override
    public int translateY(final int y) {
        return y;
    }

    @Override
    public void showInParent(final ViewGroup parentView) {
        removeFromParent();
        parentView.addView(this, new ViewGroup.LayoutParams(mKbWidth, mKbHeight));
        setTranslationX(mWinX);
        setTranslationY(mWinY);
    }

    @Override
    public void removeFromParent() {
        final ViewGroup parent = (ViewGroup) getParent();
        if (parent != null) {
            parent.removeView(this);
        }
    }

    @Override
    public boolean isShowingInParent() {
        return getParent() != null;
    }
}
