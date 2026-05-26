/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.keyboard.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.Nullable;

import org.futo.inputmethod.keyboard.Key;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.uix.DynamicThemeProvider;
import org.futo.inputmethod.v2keyboard.ClusterMain;
import org.futo.inputmethod.v2keyboard.Direction;
import org.futo.inputmethod.v2keyboard.KeyDataKt;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kotlin.Pair;

/**
 * The pop up key preview view.
 */
public class KeyPreviewView extends androidx.appcompat.widget.AppCompatTextView {
    public static final int POSITION_MIDDLE = 0;
    public static final int POSITION_LEFT = 1;
    public static final int POSITION_RIGHT = 2;

    private final Rect mBackgroundPadding = new Rect();
    private static final HashSet<String> sNoScaleXTextSet = new HashSet<>();

    private final DynamicThemeProvider mDrawableProvider;

    public KeyPreviewView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyPreviewView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);

        mDrawableProvider = DynamicThemeProvider.obtainFromContext(context);
    }

    @Nullable
    private Drawable mIcon;

    private Key currKey;

    // kxkb: when true, this preview is an enlarged long-press "study popup" and draws the key's full
    // content (cluster glyph band, or macro/chord title + output) in addition to the compass layout.
    private boolean mStudyMode = false;
    public void setStudyMode(final boolean studyMode) {
        mStudyMode = studyMode;
    }
    public void setPreviewVisual(final Key key, final KeyboardIconsSet iconsSet,
                                 final KeyDrawParams drawParams, int foregroundColor) {
        // What we show as preview should match what we show on a key top in onDraw().
        final String iconId = key.getIconId();
        if (!Objects.equals(iconId, KeyboardIconsSet.ICON_UNDEFINED) && key.getFlickDirection() == null) {
            setCompoundDrawables(null, null, null, key.getPreviewIcon(iconsSet));
            mIcon = key.getPreviewIcon(iconsSet);
            currKey = key;
            setText(null);
            return;
        }

        mIcon = null;
        setCompoundDrawables(null, null, null, null);
        setTextColor(foregroundColor);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, key.selectTextSize(drawParams));
        setTypeface(mDrawableProvider.selectKeyTypeface(key.selectPreviewTypeface(drawParams)));
        // TODO Should take care of temporaryShiftLabel here.
        setTextAndScaleX(key.getWidth(), key.getPreviewLabel());

        currKey = key;
    }

    private boolean drawFlickKeys(final Canvas canvas) {
        if(currKey == null) return false;

        Map<Direction, Key> flickKeys = currKey.getFlickKeys();
        if(flickKeys == null || flickKeys.isEmpty()) return false;

        if(currKey.getFlickDirection() != null) return false;

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        int dim = Math.min(width, height);

        Paint paint = new Paint();
        paint.setTypeface(getTypeface());
        paint.setColor(getCurrentTextColor());
        paint.setTextSize(dim * 0.265f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);

        final float yp = 2.7f; // TODO
        final float offsMul = 0.33f;

        int cx = width / 2;
        int cy = (int)(height / 2 + paint.getTextSize() / yp);

        for(Direction dir : flickKeys.keySet()) {
            Key value = flickKeys.get(dir);
            Pair<Double, Double> vec = KeyDataKt.toVector(dir);

            int x = (int)(cx - (vec.getFirst() * width * offsMul));
            int y = (int)(cy - (vec.getSecond() * height * offsMul));
            canvas.drawText(value.getPreviewLabel(), x, y, paint);
        }

        paint.setTextSize(dim * 0.485f);

        if(mIcon != null) {
            /*int iconWidth = mIcon.getIntrinsicWidth();
            if(iconWidth > width) iconWidth = width;
            iconWidth = iconWidth * 8 / 10;

            mIcon.setBounds(
                    cx - iconWidth / 2,
                    height / 2 - iconWidth / 2,
                    cx + iconWidth / 2,
                    height / 2 + iconWidth / 2
            );
            mIcon.draw(canvas);*/
        } else {
            canvas.drawText(
                    currKey.getPreviewLabel(),
                    cx,
                    (int) (height / 2 + paint.getTextSize() / yp),
                    paint
            );
        }

        return true;
    }


    private Drawable mBackground = null;
    @Override
    public void setBackground(Drawable background) {
        mBackground = background;
        background.getPadding(mBackgroundPadding);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        mBackground.setBounds(0, 0, getWidth(), getHeight());
        mBackground.draw(canvas);

        if (mStudyMode) {
            // Study popup: cluster band / macro-chord text take priority (a cluster key may also carry
            // flick slots for its side mains, which we don't want drawFlickKeys to render instead).
            if (drawStudyExtras(canvas)) return;
            if (drawFlickKeys(canvas)) return;
            super.onDraw(canvas);
            return;
        }
        if(!drawFlickKeys(canvas)) {
            super.onDraw(canvas);
        }
    }

    // kxkb: study-popup content for the non-compass complex keys. Compass keys are already handled by
    // drawFlickKeys above; here we cover cluster (the predictive main glyphs) and macro / chord (the
    // label as a title with the typed output below). Returns false for anything else so the caller
    // falls back to the normal single-label preview.
    private boolean drawStudyExtras(final Canvas canvas) {
        if (currKey == null) return false;

        final int width = canvas.getWidth();
        final int height = canvas.getHeight();
        final int dim = Math.min(width, height);
        final int cx = width / 2;

        // Cluster: lay the main glyphs out evenly across the width on the centre line.
        final List<ClusterMain> mains = currKey.getClusterMains();
        if (mains != null && !mains.isEmpty()) {
            final Paint paint = new Paint();
            paint.setTypeface(getTypeface());
            paint.setColor(getCurrentTextColor());
            paint.setTextSize(dim * 0.30f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setAntiAlias(true);
            final int n = mains.size();
            if (mains.get(0).getVertical()) {
                // kxkb column: stack the mains down the preview centre line.
                for (int i = 0; i < n; i++) {
                    final float y = height * (i + 0.5f) / n + paint.getTextSize() / 2.7f;
                    final String glyph = new String(Character.toChars(mains.get(i).getCodePoint()));
                    canvas.drawText(glyph, cx, y, paint);
                }
                return true;
            }
            final float baseline = height / 2.0f + paint.getTextSize() / 2.7f;
            for (int i = 0; i < n; i++) {
                final float x = width * (i + 0.5f) / n;
                final String glyph = new String(Character.toChars(mains.get(i).getCodePoint()));
                canvas.drawText(glyph, x, baseline, paint);
            }
            return true;
        }

        // Macro / chord: the label as a title, the (cleaned) output text below.
        final String out = currKey.getOutputText();
        if (out != null) {
            final String body = cleanStudyOutput(out);

            final Paint titlePaint = new Paint();
            titlePaint.setTypeface(getTypeface());
            titlePaint.setColor(getCurrentTextColor());
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setAntiAlias(true);
            titlePaint.setTextSize(dim * 0.18f);

            final Paint bodyPaint = new Paint(titlePaint);
            bodyPaint.setTextSize(dim * 0.26f);
            final float bodyW = bodyPaint.measureText(body);
            final float maxW = width * 0.9f;
            if (bodyW > maxW && bodyW > 0.0f) {
                bodyPaint.setTextSize(bodyPaint.getTextSize() * maxW / bodyW);
            }

            final String title = currKey.getLabel();
            if (title != null && !title.isEmpty() && !title.equals(body)) {
                canvas.drawText(title, cx, height * 0.40f, titlePaint);
                canvas.drawText(body, cx, height * 0.70f, bodyPaint);
            } else {
                canvas.drawText(body, cx, height / 2.0f + bodyPaint.getTextSize() / 2.7f, bodyPaint);
            }
            return true;
        }

        return false;
    }

    // kxkb: chord output is "\u0000" + "C-x C-s"; multitap/cycle joins entries with \u0001. Strip the
    // control sentinels so the study popup shows a human-readable string.
    private static String cleanStudyOutput(final String s) {
        return s.replace("\u0000", "").replace('\u0001', ' ').trim();
    }

    private void setTextAndScaleX(int maxWidth, final String text) {
        setTextScaleX(1.0f);
        setText(text);
        if (sNoScaleXTextSet.contains(text)) {
            return;
        }

        final float width = getTextWidth(text, getPaint());
        if (width <= maxWidth) {
            sNoScaleXTextSet.add(text);
            return;
        }
        setTextScaleX(maxWidth / width);
    }

    public static void clearTextCache() {
        sNoScaleXTextSet.clear();
    }

    private static float getTextWidth(final String text, final TextPaint paint) {
        if (TextUtils.isEmpty(text)) {
            return 0.0f;
        }
        final int len = text.length();
        final float[] widths = new float[len];
        final int count = paint.getTextWidths(text, 0, len, widths);
        float width = 0;
        for (int i = 0; i < count; i++) {
            width += widths[i];
        }
        return width;
    }

    // Background state set
    private static final int[][][] KEY_PREVIEW_BACKGROUND_STATE_TABLE = {
        { // POSITION_MIDDLE
            {},
            { R.attr.state_has_morekeys }
        },
        { // POSITION_LEFT
            { R.attr.state_left_edge },
            { R.attr.state_left_edge, R.attr.state_has_morekeys }
        },
        { // POSITION_RIGHT
            { R.attr.state_right_edge },
            { R.attr.state_right_edge, R.attr.state_has_morekeys }
        }
    };
    private static final int STATE_NORMAL = 0;
    private static final int STATE_HAS_MOREKEYS = 1;

    public void setPreviewBackground(final boolean hasMoreKeys, final int position) {
        //final Drawable background = getBackground();
        //if (background == null) {
        //    return;
        //}
        //final int hasMoreKeysState = hasMoreKeys ? STATE_HAS_MOREKEYS : STATE_NORMAL;
        //background.setState(KEY_PREVIEW_BACKGROUND_STATE_TABLE[position][hasMoreKeysState]);
    }
}
