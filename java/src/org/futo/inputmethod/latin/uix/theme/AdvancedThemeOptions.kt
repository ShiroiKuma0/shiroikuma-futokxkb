package org.futo.inputmethod.latin.uix.theme

import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap

data class KeyBackground(
    val foregroundColor: Int?,
    val padding: Rect,
    val background: Drawable
)

data class KeyIcon(
    val drawable: Drawable
)

data class AdvancedThemeOptions(
    val backgroundShader: String? = null,
    val backgroundImage: ImageBitmap? = null,
    val backgroundImageVisibleArea: Rect? = null,
    val thumbnailImage: ImageBitmap? = null,
    val thumbnailScale: Float = 1.0f,
    val keyRoundness: Float = 1.0f,
    val keyBorders: Boolean? = null,
    val keyBackgrounds: KeyedBitmaps<KeyBackground>? = null,
    val keyIcons: KeyedBitmaps<KeyIcon>? = null,
    val font: Typeface? = null,
    val themeName: String? = null,
    val themeAuthor: String? = null,
    // >0 enables a per-key border stroke (in dp) drawn in the theme's outline color.
    val keyStrokeWidthDp: Float = 0f,
    // >0 overrides key label font weight (100..1000; 400 = regular, 700 = bold). 0 = leave default.
    val keyLabelWeight: Int = 0,
    // Multiplier applied to key letter/label text size (1.0 = unchanged).
    val keyLetterScale: Float = 1.0f,
)