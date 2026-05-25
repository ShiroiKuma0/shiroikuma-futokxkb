/*
 * kxkb: LayoutSwitcherView — a compact, swipe-driven panel for fast switching, opened mid-swipe off
 * the spacebar (see PointerTracker.openLayoutSwitcherPanel). It implements {@link MoreKeysPanel} so the
 * existing PointerTracker gesture path drives it for free: it receives onMoveEvent as the finger
 * travels, highlights the cell under the finger, and on onUpEvent switches to the highlighted target —
 * all in one motion.
 *
 * Phases: (1) the layouts column; (2) + the languages column (this file now renders N columns side by
 * side — languages then layouts, with the left settings-shortcuts column coming in phase 3). Every cell
 * carries a target subtype string; selecting it writes ActiveSubtype (LatinIME observes + switches).
 *
 * Coordinate model: the view is sized to exactly cover the MainKeyboardView and positioned at its
 * window origin inside the drawing-preview placer, so the keyboard-local coordinates PointerTracker
 * forwards map 1:1 to this view's local space — translateX/Y are the identity. Columns are anchored
 * near the swipe so reaches stay short (compact, not full width).
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
    private static final class Cell {
        final String label;
        final String subtype;
        final Rect rect = new Rect();
        Cell(final String label, final String subtype) {
            this.label = label;
            this.subtype = subtype;
        }
    }

    private Controller mController = EMPTY_CONTROLLER;

    // Columns of (label, subtype) input, left-to-right; flattened into mCells with rects on layout.
    private final List<List<kotlin.Pair<String, String>>> mColumns = new ArrayList<>();
    private final List<Cell> mCells = new ArrayList<>();
    private String mActiveSubtype = "";

    private int mKeyboardColor = Color.BLACK;
    private int mCellColor = Color.DKGRAY;
    private int mTextColor = Color.WHITE;

    private int mKbWidth = 0;
    private int mKbHeight = 0;
    private int mWinX = 0;
    private int mWinY = 0;

    private int mCellHeight = 0;
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
     * @param columns       columns of (subtypeString, displayLabel), left-to-right.
     * @param activeSubtype the currently active subtype string (marked with a bullet).
     */
    public void setColumns(@Nonnull final List<List<kotlin.Pair<String, String>>> columns,
            final String activeSubtype, final int keyboardColor, final int cellColor,
            final int textColor, final int kbWidth, final int kbHeight) {
        mColumns.clear();
        mColumns.addAll(columns);
        mActiveSubtype = activeSubtype == null ? "" : activeSubtype;
        mKeyboardColor = keyboardColor;
        mCellColor = cellColor;
        mTextColor = textColor;
        mKbWidth = kbWidth;
        mKbHeight = kbHeight;
        mHighlighted = -1;
    }

    private void layoutColumns(final int anchorX, final int anchorY) {
        mCells.clear();
        mCellHeight = Math.max(1, Math.round(mKbHeight / 6.0f));
        mTextPaint.setTextSize(mCellHeight * 0.42f);
        final int pad = mCellHeight;
        final int gap = Math.max(1, mCellHeight / 3);
        final int minCol = Math.max(1, mKbWidth / 5);
        final int maxCol = Math.round(mKbWidth * 0.45f);

        // Per-column width and the tallest column (for vertical anchoring).
        final int nCols = mColumns.size();
        final int[] colWidth = new int[nCols];
        int totalWidth = 0;
        int maxRows = 0;
        for (int c = 0; c < nCols; c++) {
            final List<kotlin.Pair<String, String>> col = mColumns.get(c);
            float maxText = 0;
            for (final kotlin.Pair<String, String> p : col) {
                maxText = Math.max(maxText, mTextPaint.measureText(p.getSecond()));
            }
            int w = Math.round(maxText) + pad;
            w = Math.max(minCol, Math.min(maxCol, w));
            colWidth[c] = w;
            totalWidth += w;
            maxRows = Math.max(maxRows, col.size());
        }
        totalWidth += gap * Math.max(0, nCols - 1);

        int blockLeft = anchorX - totalWidth / 2;
        blockLeft = Math.max(0, Math.min(blockLeft, mKbWidth - totalWidth));

        final int blockHeight = maxRows * mCellHeight;
        int baseline = Math.max(blockHeight, anchorY - gap); // bottom edge of the columns
        baseline = Math.min(baseline, mKbHeight);

        int x = blockLeft;
        for (int c = 0; c < nCols; c++) {
            final List<kotlin.Pair<String, String>> col = mColumns.get(c);
            for (int i = 0; i < col.size(); i++) {
                final kotlin.Pair<String, String> p = col.get(i);
                final Cell cell = new Cell(p.getSecond(), p.getFirst());
                final int bottom = baseline - i * mCellHeight; // i==0 is the bottom (nearest finger)
                cell.rect.set(x, bottom - mCellHeight, x + colWidth[c], bottom);
                mCells.add(cell);
            }
            x += colWidth[c] + gap;
        }
    }

    private int cellAt(final int x, final int y) {
        for (int i = 0; i < mCells.size(); i++) {
            if (mCells.get(i).rect.contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        canvas.drawColor(0xB0000000); // dim the keyboard behind the panel (theme-agnostic)

        final Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        final float textOffset = -(fm.ascent + fm.descent) / 2.0f;

        for (int i = 0; i < mCells.size(); i++) {
            final Cell cell = mCells.get(i);
            final boolean hi = i == mHighlighted;
            mFillPaint.setColor(hi ? mTextColor : mCellColor);
            canvas.drawRect(cell.rect, mFillPaint);
            mBorderPaint.setColor(mTextColor);
            canvas.drawRect(cell.rect, mBorderPaint);

            mTextPaint.setColor(hi ? mKeyboardColor : mTextColor);
            String label = cell.label;
            if (cell.subtype.equals(mActiveSubtype)) {
                label = "\u2022 " + label; // bullet marks the current layout
            }
            canvas.drawText(label, cell.rect.centerX(), cell.rect.centerY() + textOffset, mTextPaint);
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
        layoutColumns(pointX, pointY);
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
        if (sel >= 0 && sel < mCells.size()) {
            final String target = mCells.get(sel).subtype;
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
