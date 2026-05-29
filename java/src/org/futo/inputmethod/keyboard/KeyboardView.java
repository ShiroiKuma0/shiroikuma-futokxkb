/*
 * Copyright (C) 2010 The Android Open Source Project
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

package org.futo.inputmethod.keyboard;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import org.futo.inputmethod.keyboard.internal.KeyDrawParams;
import org.futo.inputmethod.keyboard.internal.KeyVisualAttributes;
import org.futo.inputmethod.latin.uix.DynamicThemeProvider;
import org.futo.inputmethod.latin.R;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.uix.theme.KeyDrawingConfiguration;
import org.futo.inputmethod.latin.uix.VisualStyleDescriptor;
import org.futo.inputmethod.v2keyboard.KeyVisualStyle;
import org.futo.inputmethod.latin.utils.TypefaceUtils;
import org.futo.inputmethod.v2keyboard.Direction;
import org.futo.inputmethod.v2keyboard.KeyDataKt;
import org.futo.inputmethod.v2keyboard.ClusterMain;
import kotlin.Pair;
import java.util.Map;
import java.util.List;

import java.util.HashSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A view that renders a virtual {@link Keyboard}.
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_functionalKeyBackground
 * @attr ref R.styleable#KeyboardView_spacebarBackground
 * @attr ref R.styleable#KeyboardView_spacebarIconWidthRatio
 * @attr ref R.styleable#Keyboard_Key_keyLabelFlags
 * @attr ref R.styleable#KeyboardView_keyHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetter
 * @attr ref R.styleable#KeyboardView_keyPopupHintLetterPadding
 * @attr ref R.styleable#KeyboardView_keyShiftedLetterHintPadding
 * @attr ref R.styleable#KeyboardView_keyTextShadowRadius
 * @attr ref R.styleable#KeyboardView_verticalCorrection
 * @attr ref R.styleable#Keyboard_Key_keyTypeface
 * @attr ref R.styleable#Keyboard_Key_keyLetterSize
 * @attr ref R.styleable#Keyboard_Key_keyLabelSize
 * @attr ref R.styleable#Keyboard_Key_keyLargeLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyLargeLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterRatio
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelRatio
 * @attr ref R.styleable#Keyboard_Key_keyLabelOffCenterRatio
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelOffCenterRatio
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextRatio
 * @attr ref R.styleable#Keyboard_Key_keyTextColor
 * @attr ref R.styleable#Keyboard_Key_keyTextColorDisabled
 * @attr ref R.styleable#Keyboard_Key_keyTextShadowColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLetterColor
 * @attr ref R.styleable#Keyboard_Key_keyHintLabelColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintInactivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyShiftedLetterHintActivatedColor
 * @attr ref R.styleable#Keyboard_Key_keyPreviewTextColor
 */
public class KeyboardView extends View {
    // XML attributes
    private final KeyVisualAttributes mKeyVisualAttributes;
    // Default keyLabelFlags from {@link KeyboardTheme}.
    // Currently only "alignHintLabelToBottom" is supported.
    private final int mDefaultKeyLabelFlags;
    private final float mKeyHintLetterPadding;
    private final String mKeyPopupHintLetter;
    private final float mKeyPopupHintLetterPadding;
    private final float mKeyShiftedLetterHintPadding;
    private final float mKeyTextShadowRadius;
    private final float mVerticalCorrection;
    private final Drawable mKeyboardBackground;
    protected final DynamicThemeProvider mDrawableProvider;
    private final float mSpacebarIconWidthRatio;
    private final Rect mKeyBackgroundPadding = new Rect();
    private static final float KET_TEXT_SHADOW_RADIUS_DISABLED = -1.0f;

    // The maximum key label width in the proportion to the key width.
    private static final float MAX_LABEL_RATIO = 0.90f;

    // Main keyboard
    // TODO: Consider having a base keyboard object to make this @Nonnull
    @Nullable
    private Keyboard mKeyboard;
    // Highlight state for the one-shot Ctrl modifier: when armed, the Ctrl key is drawn
    // with the bright StickyOn style (same visual language as shift-lock).
    private boolean mCtrlActive = false;
    @Nonnull
    private final KeyDrawParams mKeyDrawParams = new KeyDrawParams();

    // Drawing
    /** True if all keys should be drawn */
    private boolean mInvalidateAllKeys;
    /** The keys that should be drawn */
    private final HashSet<Key> mInvalidatedKeys = new HashSet<>();
    /** The working rectangle for clipping */
    private final Rect mClipRect = new Rect();
    /** The keyboard bitmap buffer for faster updates */
    private Bitmap mOffscreenBuffer;
    /** The canvas for the above mutable keyboard bitmap */
    @Nonnull
    private final Canvas mOffscreenCanvas = new Canvas();
    @Nonnull
    private final Paint mPaint = new Paint();
    private final Paint.FontMetrics mFontMetrics = new Paint.FontMetrics();

    public KeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public KeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);

        final TypedArray keyAttr = context.obtainStyledAttributes(attrs,
                R.styleable.Keyboard_Key, defStyle, R.style.KeyboardView);

        mDrawableProvider = DynamicThemeProvider.obtainFromContext(context);

        boolean isMoreKeys = keyAttr.getBoolean(R.styleable.Keyboard_Key_isMoreKey, false);
        boolean isMoreKeysAction = keyAttr.getBoolean(R.styleable.Keyboard_Key_isAction, false);

        mKeyboardBackground = isMoreKeys ?  mDrawableProvider.getMoreKeysKeyboardBackground() :
                        mDrawableProvider.getKeyboardBackground();
        setBackground(null);
        setBackgroundColor(0);

        mSpacebarIconWidthRatio = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_spacebarIconWidthRatio, 1.0f);
        mKeyHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyHintLetterPadding, 0.0f);
        mKeyPopupHintLetter = keyboardViewAttr.getString(
                R.styleable.KeyboardView_keyPopupHintLetter);
        mKeyPopupHintLetterPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyPopupHintLetterPadding, 0.0f);
        mKeyShiftedLetterHintPadding = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_keyShiftedLetterHintPadding, 0.0f);
        mKeyTextShadowRadius = keyboardViewAttr.getFloat(
                R.styleable.KeyboardView_keyTextShadowRadius, KET_TEXT_SHADOW_RADIUS_DISABLED);
        mVerticalCorrection = keyboardViewAttr.getDimension(
                R.styleable.KeyboardView_verticalCorrection, 0.0f);
        keyboardViewAttr.recycle();

        mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0);
        mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr, mDrawableProvider);

        if((isMoreKeys || isMoreKeysAction) && mKeyVisualAttributes != null) {
            mKeyVisualAttributes.mTextColor = mDrawableProvider.getMoreKeysTextColor();
        }

        keyAttr.recycle();

        mPaint.setAntiAlias(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDarkAllowed(false);
        }
    }

    @Nullable
    public KeyVisualAttributes getKeyVisualAttribute() {
        return mKeyVisualAttributes;
    }

    private static void blendAlpha(@Nonnull final Paint paint, final int alpha) {
        final int color = paint.getColor();
        paint.setARGB((paint.getAlpha() * alpha) / Constants.Color.ALPHA_OPAQUE,
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    public void setKeyboard(@Nonnull final Keyboard keyboard) {
        mKeyboard = keyboard;
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        final int keyWidth = keyboard.mMostCommonKeyWidth;

        mKeyDrawParams.updateParams(keyWidth, Math.min(keyWidth, keyHeight), mKeyVisualAttributes);
        mKeyDrawParams.updateParams(keyWidth, Math.min(keyWidth, keyHeight), keyboard.mKeyVisualAttributes);
        invalidateAllKeys();
        requestLayout();
    }

    /**
     * Returns the current keyboard being displayed by this view.
     * @return the currently attached keyboard
     * @see #setKeyboard(Keyboard)
     */
    @Nullable
    public Keyboard getKeyboard() {
        return mKeyboard;
    }

    protected float getVerticalCorrection() {
        return mVerticalCorrection;
    }

    @Nonnull
    protected KeyDrawParams getKeyDrawParams() {
        return mKeyDrawParams;
    }

    protected void updateKeyDrawParams(final int keyHeight) {
        final int keyWidth = mKeyboard.mMostCommonKeyWidth;
        mKeyDrawParams.updateParams(keyWidth, Math.min(keyWidth, keyHeight), mKeyVisualAttributes);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
        final int height = keyboard.mOccupiedHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        mKeyboardBackground.setBounds(0, 0, getWidth(), getHeight());
        mKeyboardBackground.draw(canvas);

        if (canvas.isHardwareAccelerated()) {
            onDrawKeyboard(canvas);
            return;
        }

        final boolean bufferNeedsUpdates = mInvalidateAllKeys || !mInvalidatedKeys.isEmpty();
        if (bufferNeedsUpdates || mOffscreenBuffer == null) {
            if (maybeAllocateOffscreenBuffer()) {
                mInvalidateAllKeys = true;
                // TODO: Stop using the offscreen canvas even when in software rendering
                mOffscreenCanvas.setBitmap(mOffscreenBuffer);
            }
            onDrawKeyboard(mOffscreenCanvas);
        }
        canvas.drawBitmap(mOffscreenBuffer, 0.0f, 0.0f, null);
    }

    private boolean maybeAllocateOffscreenBuffer() {
        final int width = getWidth();
        final int height = getHeight();
        if (width == 0 || height == 0) {
            return false;
        }
        if (mOffscreenBuffer != null && mOffscreenBuffer.getWidth() == width
                && mOffscreenBuffer.getHeight() == height) {
            return false;
        }
        freeOffscreenBuffer();
        mOffscreenBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        return true;
    }

    private void freeOffscreenBuffer() {
        setLayerType(LAYER_TYPE_NONE, null);

        mOffscreenCanvas.setBitmap(null);
        mOffscreenCanvas.setMatrix(null);
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    private void onDrawKeyboard(@Nonnull final Canvas canvas) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }

        final Paint paint = mPaint;
        final Drawable background = getBackground();
        // Calculate clip region and set.
        final boolean drawAllKeys = mInvalidateAllKeys || mInvalidatedKeys.isEmpty();
        final boolean isHardwareAccelerated = canvas.isHardwareAccelerated();
        // TODO: Confirm if it's really required to draw all keys when hardware acceleration is on.
        if (drawAllKeys || isHardwareAccelerated) {
            if (!isHardwareAccelerated && background != null) {
                // Need to draw keyboard background on {@link #mOffscreenBuffer}.
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
                background.draw(canvas);
            }
            // Draw all keys.
            for (final Key key : keyboard.getSortedKeys()) {
                onDrawKey(key, canvas, paint);
            }
        } else {
            for (final Key key : mInvalidatedKeys) {
                if (!keyboard.hasKey(key)) {
                    continue;
                }
                if (background != null) {
                    // Need to redraw key's background on {@link #mOffscreenBuffer}.
                    final int x = key.getX() + getPaddingLeft();
                    final int y = key.getY() + getPaddingTop();
                    mClipRect.set(x, y, x + key.getWidth(), y + key.getHeight());
                    canvas.save();
                    canvas.clipRect(mClipRect);
                    canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR);
                    background.draw(canvas);
                    canvas.restore();
                }
                onDrawKey(key, canvas, paint);
            }
        }

        mInvalidatedKeys.clear();
        mInvalidateAllKeys = false;
    }

    private void onDrawKey(@Nonnull final Key key, @Nonnull final Canvas canvas,
            @Nonnull final Paint paint) {
        final int keyDrawX = key.getDrawX() + getPaddingLeft();
        final int keyDrawY = key.getY() + getPaddingTop();
        canvas.translate(keyDrawX, keyDrawY);

        final KeyVisualAttributes attr = key.getVisualAttributes();
        final KeyDrawParams params = mKeyDrawParams.mayCloneAndUpdateParams(key.getWidth(),
                Math.min(key.getHeight(), key.getWidth()), attr);
        params.mAnimAlpha = Constants.Color.ALPHA_OPAQUE;

        KeyDrawingConfiguration kdc = mDrawableProvider.selectKeyDrawingConfiguration(mKeyboard, params, key);
        if (mCtrlActive && key.getCode() == Constants.CODE_CTRL) {
            // Armed one-shot Ctrl modifier: repaint with the bright StickyOn style so it's
            // clear the next key press will be sent as Ctrl+<key>.
            final VisualStyleDescriptor sticky =
                    mDrawableProvider.getKeyStyleDescriptor(KeyVisualStyle.StickyOn);
            final Drawable stickyBg = sticky.getBackgroundDrawable();
            kdc = new KeyDrawingConfiguration(
                    stickyBg != null ? stickyBg : kdc.getBackground(),
                    kdc.getBackgroundPadding(),
                    kdc.getIcon(),
                    kdc.getHintIcon(),
                    kdc.getLabel(),
                    kdc.getHintLabel(),
                    sticky.getForegroundColor(),
                    kdc.getHintColor(),
                    kdc.getTextSize(),
                    kdc.getHintSize());
        }
        // kxkb: caps lock on → recolour the Shift glyph (icon/label foreground) with the configured
        // colour, leaving the key background alone. The icon is tinted with getTextColor() and labels
        // are painted with it, so overriding textColor turns the shift shape itself blue. Caps lock
        // switches the keyboard to a SHIFT_LOCKED element (a full reload), so detecting it at draw
        // time is enough — no extra invalidation needed.
        if (key.getCode() == Constants.CODE_SHIFT && mKeyboard != null
                && (mKeyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED
                    || mKeyboard.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)) {
            kdc = new KeyDrawingConfiguration(
                    kdc.getBackground(),
                    kdc.getBackgroundPadding(),
                    kdc.getIcon(),
                    kdc.getHintIcon(),
                    kdc.getLabel(),
                    kdc.getHintLabel(),
                    sCapsLockColor,
                    kdc.getHintColor(),
                    kdc.getTextSize(),
                    kdc.getHintSize());
        }
        // kxkb: per-key visual overrides (Phase 3) — text colour, primary font size and hint size.
        // colorOverride tints the label + icon (both honour textColor); the scales multiply the
        // computed sizes. Background/border colour overrides are a later stage.
        if (key.getColorOverride() != null || key.getFontScaleOverride() != null || key.getHintScaleOverride() != null) {
            final int textColor = key.getColorOverride() != null ? key.getColorOverride() : kdc.getTextColor();
            final float textSize = key.getFontScaleOverride() != null ? kdc.getTextSize() * key.getFontScaleOverride() : kdc.getTextSize();
            final float hintSize = key.getHintScaleOverride() != null ? kdc.getHintSize() * key.getHintScaleOverride() : kdc.getHintSize();
            kdc = new KeyDrawingConfiguration(
                    kdc.getBackground(), kdc.getBackgroundPadding(), kdc.getIcon(), kdc.getHintIcon(),
                    kdc.getLabel(), kdc.getHintLabel(), textColor, kdc.getHintColor(), textSize, hintSize);
        }
        // kxkb: per-key background / border colour (Phase 3 Stage 2). Build a rounded-rect matching
        // the theme's corner radius (read from the existing GradientDrawable) and stroke width; the
        // half the user didn't override falls back to the key's existing fill / the theme border.
        if (key.getBackgroundColorOverride() != null || key.getBorderColorOverride() != null) {
            final Drawable base = kdc.getBackground();
            float radius = 0f;
            int existingFill = sKeyBgColor;
            if (base instanceof GradientDrawable) {
                radius = ((GradientDrawable) base).getCornerRadius();
                final android.content.res.ColorStateList csl = ((GradientDrawable) base).getColor();
                if (csl != null) existingFill = csl.getDefaultColor();
            }
            final GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.RECTANGLE);
            gd.setCornerRadius(radius);
            gd.setColor(key.getBackgroundColorOverride() != null ? key.getBackgroundColorOverride() : existingFill);
            int strokeWidth = sKeyStrokeWidthPx;
            final int strokeColor = key.getBorderColorOverride() != null ? key.getBorderColorOverride() : sKeyBorderColor;
            if (key.getBorderColorOverride() != null && strokeWidth <= 0) {
                strokeWidth = Math.round(2f * getResources().getDisplayMetrics().density);
            }
            if (strokeWidth > 0) gd.setStroke(strokeWidth, strokeColor);
            kdc = new KeyDrawingConfiguration(
                    gd, kdc.getBackgroundPadding(), kdc.getIcon(), kdc.getHintIcon(),
                    kdc.getLabel(), kdc.getHintLabel(), kdc.getTextColor(), kdc.getHintColor(),
                    kdc.getTextSize(), kdc.getHintSize());
        }
        final Drawable background = kdc.getBackground();
        if (background != null) {
            onDrawKeyBackground(key, canvas, background);
        }
        onDrawKeyTopVisuals(key, canvas, paint, params, kdc);

        canvas.translate(-keyDrawX, -keyDrawY);
    }

    // Draw key background.
    protected void onDrawKeyBackground(@Nonnull final Key key, @Nonnull final Canvas canvas,
            @Nonnull final Drawable background) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final int bgWidth, bgHeight, bgX, bgY;
        if (key.needsToKeepBackgroundAspectRatio(mDefaultKeyLabelFlags)
                // HACK: To disable expanding normal/functional key background.
                && !key.getHasCustomActionLabel()) {
            bgWidth = Math.min(keyWidth, keyHeight);
            bgHeight = Math.min(keyWidth, keyHeight);
            bgX = (keyWidth - bgWidth) / 2;
            bgY = (keyHeight - bgHeight) / 2;
        } else {
            final Rect padding = new Rect();
            background.getPadding(padding);

            bgWidth = keyWidth + padding.left + padding.right;
            bgHeight = keyHeight + padding.top + padding.bottom;
            bgX = -padding.left;
            bgY = -padding.top;
        }
        final Rect bounds = background.getBounds();
        if (bgWidth != bounds.right || bgHeight != bounds.bottom) {
            background.setBounds(0, 0, bgWidth, bgHeight);
        }
        canvas.translate(bgX, bgY);
        background.draw(canvas);
        canvas.translate(-bgX, -bgY);
    }

    // Draw key top visuals.
    protected void onDrawKeyTopVisuals(@Nonnull final Key key, @Nonnull final Canvas canvas,
            @Nonnull final Paint paint, @Nonnull final KeyDrawParams params, @Nonnull final KeyDrawingConfiguration kdc) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();
        final float centerX = keyWidth * 0.5f;
        final float centerY = keyHeight * 0.5f;

        // kxkb 4D: when key sliding is on, a flick key shows its eight directional labels on its
        // face (drawn at the end of this method). The single corner hint and the "..." popup hint
        // are suppressed on those keys so they don't collide with the corner directionals.
        final Map<Direction, Key> atRestFlickKeys =
                PointerTracker.isKeySlidingEnabled() ? key.getFlickKeys() : null;
        final boolean drawFlickFace = atRestFlickKeys != null && !atRestFlickKeys.isEmpty();

        // kxkb cluster: a predictive multi-key draws its band of main glyphs itself (primary size,
        // on the centre line, positioned by the left/right sliders), so the single centred label is
        // suppressed; the side mains live in the West/East flick slots and are drawn here too, so the
        // at-rest flick pass skips them. The band shows whether or not sliding is on (it's the face).
        final List<ClusterMain> clusterMains = key.getClusterMains();
        final boolean isCluster = clusterMains != null && !clusterMains.isEmpty();

        // Draw key label.
        final Drawable icon = kdc.getIcon();
        final Drawable hintIcon = kdc.getHintIcon();
        float labelX = centerX;
        float labelBaseline = centerY;
        final String label = kdc.getLabel();
        final Rect bgPadding = kdc.getBackgroundPadding();

        float keyHintPaddingX = mKeyHintLetterPadding;
        float keyHintPaddingY = mKeyHintLetterPadding;
        if((bgPadding.left | bgPadding.right | bgPadding.top | bgPadding.bottom) != 0) {
            keyHintPaddingX = bgPadding.right;
            keyHintPaddingY = bgPadding.top;
        }

        if (label != null && icon == null && !isCluster) {
            paint.setTypeface(mDrawableProvider.selectKeyTypeface(key.selectTypeface(params)));
            paint.setTextSize(kdc.getTextSize() * mDrawableProvider.getKeyLetterScale());
            final float labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint);
            final float labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint);

            // Vertical label text alignment.
            labelBaseline = centerY + labelCharHeight / 2.0f;

            // Horizontal label text alignment
            if (key.isAlignLabelOffCenter()) {
                // The label is placed off center of the key. Used mainly on "phone number" layout.
                labelX = centerX + params.mLabelOffCenterRatio * labelCharWidth;
                paint.setTextAlign(Align.LEFT);
            } else {
                labelX = centerX;
                paint.setTextAlign(Align.CENTER);
            }
            if (key.getNeedsAutoXScale()) {
                final float ratio = Math.min(1.0f, (keyWidth * MAX_LABEL_RATIO) /
                        TypefaceUtils.getStringWidth(label, paint));
                if (key.getNeedsAutoScale()) {
                    final float autoSize = paint.getTextSize() * ratio;
                    paint.setTextSize(autoSize);
                } else {
                    paint.setTextScaleX(ratio);
                }
            }

            if (key.isEnabled()) {
                paint.setColor(kdc.getTextColor());
                // Set a drop shadow for the text if the shadow radius is positive value.
                if (mKeyTextShadowRadius > 0.0f) {
                    paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor);
                } else {
                    paint.clearShadowLayer();
                }
            } else {
                // Make label invisible
                paint.setColor(Color.TRANSPARENT);
                paint.clearShadowLayer();
            }
            blendAlpha(paint, params.mAnimAlpha);
            canvas.drawText(label, 0, label.length(), labelX, labelBaseline, paint);
            // Turn off drop shadow and reset x-scale.
            paint.clearShadowLayer();
            paint.setTextScaleX(1.0f);
        }

        // Draw hint label.
        final String hintLabel = kdc.getHintLabel();
        if (hintLabel != null && !drawFlickFace) {
            paint.setTextSize(kdc.getHintSize() * mDrawableProvider.getKeyHintScale());
            paint.setColor(kdc.getHintColor());

            // Bold explicit hints
            paint.setTypeface(key.selectHintTypeface(mDrawableProvider, params));

            blendAlpha(paint, params.mAnimAlpha);
            final float labelCharHeight = TypefaceUtils.getReferenceCharHeight(paint);
            final float labelCharWidth = TypefaceUtils.getReferenceCharWidth(paint);
            final float hintX, hintBaseline;
            if (key.getHasHintLabel()) {
                // The hint label is placed just right of the key label. Used mainly on
                // "phone number" layout.
                hintX = labelX + params.mHintLabelOffCenterRatio * labelCharWidth;
                if (key.isAlignHintLabelToBottom(mDefaultKeyLabelFlags)) {
                    hintBaseline = labelBaseline;
                } else {
                    hintBaseline = centerY + labelCharHeight / 2.0f;
                }
                paint.setTextAlign(Align.CENTER);
            } else if (key.getHasShiftedLetterHint()) {
                // The hint label is placed at top-right corner of the key. Used mainly on tablet.
                hintX = keyWidth - mKeyShiftedLetterHintPadding - labelCharWidth / 2.0f;
                paint.getFontMetrics(mFontMetrics);
                hintBaseline = -mFontMetrics.top;
                paint.setTextAlign(Align.CENTER);
            } else { // key.hasHintLetter()
                // The hint letter is placed at top-right corner of the key. Used mainly on phone.
                final float hintDigitWidth = TypefaceUtils.getReferenceDigitWidth(paint);
                final float hintLabelWidth = TypefaceUtils.getStringWidth(hintLabel, paint);
                hintX = keyWidth - keyHintPaddingX - Math.max(hintDigitWidth, hintLabelWidth) / 2.0f;
                hintBaseline = -paint.ascent() + keyHintPaddingY;
                paint.setTextAlign(Align.CENTER);
            }
            final float adjustmentY = params.mHintLabelVerticalAdjustment * labelCharHeight;
            canvas.drawText(
                    hintLabel, 0, hintLabel.length(), hintX, hintBaseline + adjustmentY, paint);
        } else if(hintIcon != null && !drawFlickFace) {
            final float size = kdc.getHintSize() * mDrawableProvider.getKeyHintScale();

            int iconWidth = (int)size;
            int iconHeight = (int)size;

            int hintX = keyWidth - iconWidth - (int)keyHintPaddingX;
            int hintY = (int)keyHintPaddingY;

            hintIcon.setTint(kdc.getHintColor());
            drawIcon(canvas, hintIcon, hintX, hintY, iconWidth, iconHeight);
        }

        // Draw key icon.
        if (icon != null) {
            // Size key icons as a direct fraction of the smaller key dimension (square, centered),
            // so glyph keys read as large as the letters. Bounded by the key so it never overflows.
            final float size = Math.min(keyWidth, keyHeight) * 1.0f;

            int iconWidth;
            if (key.getCode() == Constants.CODE_SPACE && icon instanceof NinePatchDrawable) {
                iconWidth = (int)(keyWidth * mSpacebarIconWidthRatio);
            } else {
                iconWidth = Math.min(icon.getIntrinsicWidth(), keyWidth);
            }
            int iconHeight = icon.getIntrinsicHeight();

            if(iconWidth > size) {
                //iconHeight = (int)((float)iconHeight / (float)iconWidth * (float)size);
                iconWidth = (int)size;
            }

            iconHeight = iconWidth;

            final int iconY;
            if (key.isAlignIconToBottom()) {
                iconY = keyHeight - iconHeight;
            } else {
                iconY = (keyHeight - iconHeight) / 2; // Align vertically center.
            }
            final int iconX = (keyWidth - iconWidth) / 2; // Align horizontally center.

            icon.setTint(kdc.getTextColor());
            drawIcon(canvas, icon, iconX, iconY, iconWidth, iconHeight);
        }

        if (key.getHasPopupHint() && !key.getMoreKeys().isEmpty() && !drawFlickFace) {
            drawKeyPopupHint(key, canvas, paint, params);
        }

        if (drawFlickFace) {
            drawAtRestFlickLabels(key, canvas, paint, params, kdc, keyWidth, keyHeight, centerX, centerY);
        }

        // kxkb cluster: draw the predictive main band (primary size). Always drawn — it is the key
        // face, independent of the sliding toggle (a tap predicts the band whether sliding is on or
        // not; sliding only governs the precise side-main / extra slides).
        if (isCluster) {
            drawClusterMains(key, clusterMains, canvas, paint, params, kdc, keyWidth, keyHeight,
                    centerX, centerY);
        }
    }

    // kxkb 4D: at-rest directional-label positions, as fractions of the key dimension from center,
    // pushed from the current geometry's blob by LatinIME.withPerKindLook (top/bottom and left/right
    // independent). Defaults match SavedKeyboardSizingSettings so the look is right before first push.
    private static float sFlickTopOff = 0.30f;
    private static float sFlickBotOff = 0.40f;
    private static float sFlickLeftOff = 0.34f;
    private static float sFlickRightOff = 0.34f;
    public static void setFlickLabelOffsets(final float top, final float bottom,
            final float left, final float right) {
        sFlickTopOff = top;
        sFlickBotOff = bottom;
        sFlickLeftOff = left;
        sFlickRightOff = right;
    }

    // kxkb cluster: how far the left / right outer mains sit from the centre main, as a fraction of
    // the key width per column-step. Pushed per-geometry by LatinIME.withPerKindLook. Default 0.333 =
    // the band evenly tiled across the key (matches SavedKeyboardSizingSettings). DISPLAY ONLY — the
    // proximity sub-rects (prediction) are unaffected.
    private static float sClusterLeftOff = 0.333f;
    private static float sClusterRightOff = 0.333f;
    // kxkb column: vertical band glyphs are placed one-per-slice down the key and sized to the slice
    // (capped at the normal letter size). Display only; not user-tunable.
    private static final float CLUSTER_VERTICAL_FILL = 0.62f;
    public static void setClusterMainOffsets(final float left, final float right) {
        sClusterLeftOff = left;
        sClusterRightOff = right;
    }

    // kxkb: colour the Shift glyph takes when caps-lock is on, so it's visually obvious. Pushed
    // per-(language·layout·geometry) from LatinIME.withPerKindLook; defaults to pure blue.
    private static int sCapsLockColor = 0xFF0000FF;
    public static void setCapsLockColor(final int color) {
        sCapsLockColor = color;
    }

    // kxkb: current theme's key background / border colour + border width (px), pushed per-geometry
    // from LatinIME.withPerKindLook. Used as the base when a key sets only a per-key background OR
    // only a border colour, so the other half matches the theme.
    private static int sKeyBgColor = 0xFFCCCCCC;
    private static int sKeyBorderColor = 0xFF888888;
    private static int sKeyStrokeWidthPx = 0;
    public static void setKeyDrawDefaults(final int bgColor, final int borderColor, final int strokeWidthPx) {
        sKeyBgColor = bgColor;
        sKeyBorderColor = borderColor;
        sKeyStrokeWidthPx = strokeWidthPx;
    }

    // kxkb 4D: draw a flick key's eight directional labels on its face, mirroring the hold-popup
    // (KeyPreviewView.drawFlickKeys) but snapped to a Multiling-style 3x3 grid. The primary stays
    // centered (drawn above); the directionals sit at column/row offsets given by the per-geometry
    // sliders, sized by the secondary-font (hint-size) knob. Empty directions draw nothing.
    private void drawAtRestFlickLabels(@Nonnull final Key key, @Nonnull final Canvas canvas,
            @Nonnull final Paint paint, @Nonnull final KeyDrawParams params,
            @Nonnull final KeyDrawingConfiguration kdc, final int keyWidth, final int keyHeight,
            final float centerX, final float centerY) {
        final Map<Direction, Key> flickKeys = key.getFlickKeys();
        if (flickKeys == null || flickKeys.isEmpty()) {
            return;
        }

        paint.setTextSize(kdc.getHintSize() * mDrawableProvider.getKeyHintScale());
        paint.setColor(kdc.getHintColor());
        paint.setTypeface(key.selectHintTypeface(mDrawableProvider, params));
        paint.setTextAlign(Align.CENTER);
        blendAlpha(paint, params.mAnimAlpha);

        // 3x3 grid placement (Multiling-style): snap each direction to the sign of its vector, then
        // place columns/rows by the per-geometry offsets (left/right and top/bottom independent), so
        // the top three share a horizontal line, the bottom three another, diagonals in the corners.
        final float charHeight = TypefaceUtils.getReferenceCharHeight(paint);
        final float cx = centerX;
        final float cy = centerY + charHeight / 2.0f;

        // kxkb cluster: the band-end slots ARE the side mains, drawn in the band at primary size, so
        // skip them here. A horizontal cluster's ends are West/East; a vertical `column` key's are
        // North/South. The remaining slots are drawn as the small side keys.
        final java.util.List<ClusterMain> cMains = key.getClusterMains();
        final boolean isCluster = cMains != null && !cMains.isEmpty();
        final boolean isVerticalCluster = isCluster && cMains.get(0).getVertical();

        for (final Map.Entry<Direction, Key> entry : flickKeys.entrySet()) {
            final Key target = entry.getValue();
            if (target == null) {
                continue;
            }
            if (isCluster) {
                final Direction d = entry.getKey();
                if (isVerticalCluster) {
                    if (d == Direction.North || d == Direction.South) continue;
                } else if (d == Direction.West || d == Direction.East) {
                    continue;
                }
            }
            final Pair<Double, Double> vec = KeyDataKt.toVector(entry.getKey());
            final double sx = Math.signum(vec.getFirst());   // +1 = left column, -1 = right column
            final double sy = Math.signum(vec.getSecond());  // +1 = top row, -1 = bottom row
            final float x = sx > 0 ? cx - sFlickLeftOff * keyWidth
                    : (sx < 0 ? cx + sFlickRightOff * keyWidth : cx);
            final float y = sy > 0 ? cy - sFlickTopOff * keyHeight
                    : (sy < 0 ? cy + sFlickBotOff * keyHeight : cy);

            final String label = target.getPreviewLabel();
            if (label != null && !label.isEmpty()) {
                canvas.drawText(label, x, y, paint);
                continue;
            }
            // kxkb: an icon-only flick target (e.g. !icon/action_hide_keyboard) has an empty preview
            // label, so draw its icon at the hint size instead — tinted with the hint colour and
            // centred on the same spot the text would occupy (y is the text baseline; the glyph
            // centre sits ~charHeight/2 above it).
            final Drawable flickIcon = (mKeyboard == null)
                    ? null : target.getPreviewIcon(mKeyboard.mIconsSet);
            if (flickIcon != null) {
                final int size = Math.round(charHeight);
                final int left = Math.round(x - size / 2.0f);
                final int top = Math.round(y - charHeight / 2.0f - size / 2.0f);
                flickIcon.setTint(kdc.getHintColor());
                flickIcon.setAlpha(key.isEnabled() ? params.mAnimAlpha : 0);
                drawIcon(canvas, flickIcon, left, top, size, size);
            }
        }
        paint.setTextScaleX(1.0f);
    }

    // kxkb cluster: draw the band of N main glyphs at the primary letter size, on the centre line.
    // The centre main sits at the key centre (= the tap-commit glyph); the others step outward by the
    // same left/right sliders the extras use, so a 3-main band reads as left | centre | right. All
    // mains are predictive candidates regardless of where they're drawn — this is purely the face.
    private void drawClusterMains(@Nonnull final Key key, @Nonnull final List<ClusterMain> mains,
            @Nonnull final Canvas canvas, @Nonnull final Paint paint,
            @Nonnull final KeyDrawParams params, @Nonnull final KeyDrawingConfiguration kdc,
            final int keyWidth, final int keyHeight, final float centerX, final float centerY) {
        if (mains.isEmpty()) {
            return;
        }
        paint.setTypeface(mDrawableProvider.selectKeyTypeface(key.selectTypeface(params)));
        paint.setTextSize(kdc.getTextSize() * mDrawableProvider.getKeyLetterScale());
        paint.setTextAlign(Align.CENTER);
        if (key.isEnabled()) {
            paint.setColor(kdc.getTextColor());
            if (mKeyTextShadowRadius > 0.0f) {
                paint.setShadowLayer(mKeyTextShadowRadius, 0.0f, 0.0f, params.mTextShadowColor);
            } else {
                paint.clearShadowLayer();
            }
        } else {
            paint.setColor(Color.TRANSPARENT);
            paint.clearShadowLayer();
        }
        blendAlpha(paint, params.mAnimAlpha);

        final float charHeight = TypefaceUtils.getReferenceCharHeight(paint);
        final float baseline = centerY + charHeight / 2.0f;
        // Centre main sits at the key centre (= the tap-commit glyph); the outer mains step outward
        // from it by the per-side cluster offsets (fraction of key width per column-step), so the user
        // can pull the left / right characters in or out independently. Default 0.333 = evenly tiled
        // (1/6 · 1/2 · 5/6 for three). These offsets are DISPLAY ONLY — the prediction sub-rects in
        // ProximityInfo stay uniform thirds, so moving the glyphs never changes typing.
        final int n = mains.size();
        final int centerIdx = n / 2;
        if (mains.get(0).getVertical()) {
            // kxkb column: centre each main in its 1/n slice of the key height and size it to the slice
            // (capped at the normal letter size), so the stack stays tidy and proportionate whatever
            // the row height. Display only — prediction sub-rects stay uniform.
            final float slice = (float) keyHeight / n;
            final float primarySize = kdc.getTextSize() * mDrawableProvider.getKeyLetterScale();
            paint.setTextSize(Math.min(slice * CLUSTER_VERTICAL_FILL, primarySize));
            final float vCharHeight = TypefaceUtils.getReferenceCharHeight(paint);
            final float top = centerY - keyHeight / 2.0f;
            for (int i = 0; i < n; i++) {
                final float gc = top + (i + 0.5f) * slice;
                final String glyph = new String(Character.toChars(mains.get(i).getCodePoint()));
                canvas.drawText(glyph, 0, glyph.length(), centerX, gc + vCharHeight / 2.0f, paint);
            }
        } else {
            for (int i = 0; i < n; i++) {
                final float x;
                if (i == centerIdx) {
                    x = centerX;
                } else if (i < centerIdx) {
                    x = centerX - sClusterLeftOff * keyWidth * (centerIdx - i);
                } else {
                    x = centerX + sClusterRightOff * keyWidth * (i - centerIdx);
                }
                final String glyph = new String(Character.toChars(mains.get(i).getCodePoint()));
                canvas.drawText(glyph, 0, glyph.length(), x, baseline, paint);
            }
        }
        paint.clearShadowLayer();
        paint.setTextScaleX(1.0f);
    }

    // Draw popup hint "..." at the bottom right corner of the key.
    protected void drawKeyPopupHint(@Nonnull final Key key, @Nonnull final Canvas canvas,
            @Nonnull final Paint paint, @Nonnull final KeyDrawParams params) {
        if (TextUtils.isEmpty(mKeyPopupHintLetter)) {
            return;
        }
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();

        paint.setTypeface(params.mTypeface);
        paint.setTextSize(params.mHintLetterSize);
        paint.setColor(params.mHintLabelColor);
        paint.setTextAlign(Align.CENTER);
        final float hintX = keyWidth - mKeyHintLetterPadding
                - TypefaceUtils.getReferenceCharWidth(paint) / 2.0f;
        final float hintY = keyHeight - mKeyPopupHintLetterPadding;
        canvas.drawText(mKeyPopupHintLetter, hintX, hintY, paint);
    }

    protected static void drawIcon(@Nonnull final Canvas canvas,@Nonnull final Drawable icon,
            final int x, final int y, final int width, final int height) {
        canvas.translate(x, y);
        icon.setBounds(0, 0, width, height);
        icon.draw(canvas);
        canvas.translate(-x, -y);
    }

    public Paint newLabelPaint(@Nullable final Key key) {
        final Paint paint = new Paint();
        paint.setAntiAlias(true);
        if (key == null) {
            paint.setTypeface(mKeyDrawParams.mTypeface);
            paint.setTextSize(mKeyDrawParams.mLabelSize);
        } else {
            paint.setColor(key.selectTextColor(mDrawableProvider, mKeyDrawParams));
            paint.setTypeface(mDrawableProvider.selectKeyTypeface(key.selectTypeface(mKeyDrawParams)));
            paint.setTextSize(key.selectTextSize(mKeyDrawParams));
        }
        return paint;
    }

    /**
     * Requests a redraw of the entire keyboard. Calling {@link #invalidate} is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     * @see #invalidateKey(Key)
     */
    public void invalidateAllKeys() {
        mInvalidatedKeys.clear();
        mInvalidateAllKeys = true;
        invalidate();
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param key key in the attached {@link Keyboard}.
     * @see #invalidateAllKeys
     */
    /** Arm/disarm the Ctrl-modifier highlight and repaint just the Ctrl key(s). */
    public void setCtrlActive(final boolean active) {
        if (mCtrlActive == active) {
            return;
        }
        mCtrlActive = active;
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        for (final Key key : keyboard.getSortedKeys()) {
            if (key.getCode() == Constants.CODE_CTRL) {
                invalidateKey(key);
            }
        }
    }

    public void invalidateKey(@Nullable final Key key) {
        if (mInvalidateAllKeys || key == null) {
            return;
        }
        mInvalidatedKeys.add(key);
        final int x = key.getX() + getPaddingLeft();
        final int y = key.getY() + getPaddingTop();
        invalidate(x, y, x + key.getWidth(), y + key.getHeight());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        freeOffscreenBuffer();
    }

    public void deallocateMemory() {
        freeOffscreenBuffer();
    }
}
