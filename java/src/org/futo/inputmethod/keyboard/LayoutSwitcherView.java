/*
 * kxkb: LayoutSwitcherView — a compact, swipe-driven panel for fast switching, opened mid-swipe off
 * the spacebar (see PointerTracker.openLayoutSwitcherPanel). It implements {@link MoreKeysPanel} so the
 * existing PointerTracker gesture path drives it for free: it receives onMoveEvent as the finger
 * travels, highlights the cell under the finger, and on onUpEvent activates the highlighted cell —
 * all in one motion.
 *
 * Phases: (1) layouts column; (2) + languages column + recency; (3) + a header cell atop each column
 * and a left settings-shortcuts column. The grid is now 3 columns (left→right): settings shortcuts,
 * other languages, current-language layouts; each column has a header on top.
 *
 * Each cell carries a target string: a subtype string (→ Subtypes.switchToSubtypeString, observed by
 * LatinIME), or a command — "!nav/<route>" opens that SettingsActivity navDest, "!ime" opens the
 * system input-method picker.
 *
 * Coordinate model: the view is sized to exactly cover the MainKeyboardView and positioned at its
 * window origin inside the drawing-preview placer, so the keyboard-local coordinates PointerTracker
 * forwards map 1:1 to this view's local space — translateX/Y are the identity. Columns are anchored
 * near the swipe so reaches stay short (compact, not full width), and fitted to the keyboard width.
 */
package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import org.futo.inputmethod.latin.Subtypes;
import org.futo.inputmethod.latin.uix.settings.SettingsActivity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

public final class LayoutSwitcherView extends View implements MoreKeysPanel {
    private static final String CMD_NAV_PREFIX = "!nav/";
    private static final String CMD_IME_PICKER = "!ime";

    private static final class Cell {
        final String label;
        final String target;
        final boolean isHeader;
        final Rect rect = new Rect();
        Cell(final String label, final String target, final boolean isHeader) {
            this.label = label;
            this.target = target;
            this.isHeader = isHeader;
        }
    }

    private Controller mController = EMPTY_CONTROLLER;

    // Columns of (target, label) entries, left-to-right, plus one optional header (target,label) per
    // column. Flattened into mCells with rects on layout (entries bottom-up, header on top).
    private final List<List<kotlin.Pair<String, String>>> mColumns = new ArrayList<>();
    private final List<kotlin.Pair<String, String>> mHeaders = new ArrayList<>();
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
     * @param columns       per-column lists of (target, label) entries, left-to-right.
     * @param headers       per-column header (target, label); element may be null for no header.
     * @param activeSubtype the currently active subtype string (marked with a bullet).
     */
    public void setColumns(@Nonnull final List<List<kotlin.Pair<String, String>>> columns,
            @Nonnull final List<kotlin.Pair<String, String>> headers,
            final String activeSubtype, final int keyboardColor, final int cellColor,
            final int textColor, final int kbWidth, final int kbHeight) {
        mColumns.clear();
        mColumns.addAll(columns);
        mHeaders.clear();
        mHeaders.addAll(headers);
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
        final int nCols = mColumns.size();
        if (nCols == 0) {
            return;
        }
        mCellHeight = Math.max(1, Math.round(mKbHeight / 6.0f));
        mTextPaint.setTextSize(mCellHeight * 0.40f);
        final int gap = Math.max(1, mCellHeight / 4);

        // Fit all columns within the keyboard width: equal share, capped to content width.
        final int avail = mKbWidth - gap * Math.max(0, nCols - 1);
        final int maxCol = Math.max(1, avail / nCols);
        final int pad = mCellHeight / 2;
        final int[] colWidth = new int[nCols];
        int totalWidth = 0;
        int maxEntries = 0;
        boolean anyHeader = false;
        for (int c = 0; c < nCols; c++) {
            final List<kotlin.Pair<String, String>> col = mColumns.get(c);
            float maxText = 0;
            for (final kotlin.Pair<String, String> p : col) {
                maxText = Math.max(maxText, mTextPaint.measureText(p.getSecond()));
            }
            final kotlin.Pair<String, String> header = c < mHeaders.size() ? mHeaders.get(c) : null;
            if (header != null) {
                maxText = Math.max(maxText, mTextPaint.measureText(header.getSecond()));
                anyHeader = true;
            }
            colWidth[c] = Math.min(maxCol, Math.round(maxText) + pad);
            totalWidth += colWidth[c];
            maxEntries = Math.max(maxEntries, col.size());
        }
        totalWidth += gap * Math.max(0, nCols - 1);

        // Entries fill from the bottom (most-recent nearest the finger); the header sits on a single
        // top row shared across all columns, so headers line up even when columns differ in length.
        final int headerRow = maxEntries;
        final int totalRows = maxEntries + (anyHeader ? 1 : 0);

        int blockLeft = anchorX - totalWidth / 2;
        blockLeft = Math.max(0, Math.min(blockLeft, mKbWidth - totalWidth));

        final int blockHeight = totalRows * mCellHeight;
        int baseline = Math.max(blockHeight, anchorY - gap); // bottom edge of the columns
        baseline = Math.min(baseline, mKbHeight);

        int x = blockLeft;
        for (int c = 0; c < nCols; c++) {
            final List<kotlin.Pair<String, String>> col = mColumns.get(c);
            for (int i = 0; i < col.size(); i++) { // entries: row 0 == bottom (nearest finger)
                final kotlin.Pair<String, String> p = col.get(i);
                addCell(p.getSecond(), p.getFirst(), false, x, baseline, i, colWidth[c]);
            }
            final kotlin.Pair<String, String> header = c < mHeaders.size() ? mHeaders.get(c) : null;
            if (header != null) { // header on the shared top row
                addCell(header.getSecond(), header.getFirst(), true, x, baseline, headerRow, colWidth[c]);
            }
            x += colWidth[c] + gap;
        }
    }

    private void addCell(final String label, final String target, final boolean isHeader,
            final int x, final int baseline, final int rowFromBottom, final int width) {
        final Cell cell = new Cell(label, target, isHeader);
        final int bottom = baseline - rowFromBottom * mCellHeight;
        cell.rect.set(x, bottom - mCellHeight, x + width, bottom);
        mCells.add(cell);
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
            // Header cells use the (darker) keyboard colour to read as a section top; entries use the
            // key colour; the highlighted cell inverts to the text colour.
            mFillPaint.setColor(hi ? mTextColor : (cell.isHeader ? mKeyboardColor : mCellColor));
            canvas.drawRect(cell.rect, mFillPaint);
            mBorderPaint.setColor(mTextColor);
            canvas.drawRect(cell.rect, mBorderPaint);

            mTextPaint.setColor(hi ? mKeyboardColor : mTextColor);
            String label = cell.label;
            if (!cell.isHeader && cell.target.equals(mActiveSubtype)) {
                label = "\u2022 " + label; // bullet marks the current layout
            }
            canvas.save();
            canvas.clipRect(cell.rect);
            canvas.drawText(label, cell.rect.centerX(), cell.rect.centerY() + textOffset, mTextPaint);
            canvas.restore();
        }
    }

    private void activate(final String target) {
        if (target.startsWith(CMD_NAV_PREFIX)) {
            final String route = target.substring(CMD_NAV_PREFIX.length());
            final Intent intent = new Intent();
            intent.setClass(getContext(), SettingsActivity.class);
            if (!route.isEmpty()) {
                intent.putExtra("navDest", route);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getContext().startActivity(intent);
        } else if (target.equals(CMD_IME_PICKER)) {
            final InputMethodManager imm =
                    (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        } else if (!target.isEmpty() && !target.equals(mActiveSubtype)) {
            Subtypes.INSTANCE.switchToSubtypeString(getContext(), target);
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
            activate(mCells.get(sel).target);
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
