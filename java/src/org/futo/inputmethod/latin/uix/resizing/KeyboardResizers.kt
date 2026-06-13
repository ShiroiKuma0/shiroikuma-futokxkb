package org.futo.inputmethod.latin.uix.resizing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.uix.keyboardBottomPadding
import org.futo.inputmethod.latin.uix.navBarHeight
import org.futo.inputmethod.latin.uix.safeKeyboardPadding
import org.futo.inputmethod.v2keyboard.ComputedKeyboardSize
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.OneHandedKeyboardSize
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SavedKeyboardSizingSettings
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize

open class KeyboardResizeHelper(
    val viewSize: IntSize,
    val computedKeyboardSize: ComputedKeyboardSize,
    val density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    val delta: DragDelta
) {
    val minimumKeyboardWidth = 48.dp * 3
    val maximumKeyboardWidth = with(density) { viewSize.width.toDp() }

    val minimumKeyboardHeight = 32.dp * 3
    val maximumKeyboardHeight = with(density) { (viewSize.height * 2.0f / 3.0f).toDp() }.coerceAtLeast(128.dp * 3)

    val maximumSidePadding = 64.dp
    val maximumBottomPadding = 72.dp

    var result = true

    var editedSettings = initialSettings

    fun editBottomPaddingAndHeightAddition() {
        var heightCorrection = 0.0f
        val bottomPadding = with(density) {
            var newBottomPadding = when (editedSettings.currentMode) {
                KeyboardMode.Regular -> editedSettings.paddingDp.bottom
                KeyboardMode.Split -> editedSettings.splitPaddingDp.bottom
                KeyboardMode.OneHanded -> editedSettings.oneHandedRectDp.bottom
                KeyboardMode.Floating -> 0.dp
            } - delta.bottom.toDp()

            if (newBottomPadding !in 0.dp..maximumBottomPadding) {
                // Correct for height difference if it's being dragged up/down
                val correction = if (newBottomPadding < 0.dp) {
                    newBottomPadding.toPx().coerceAtLeast(-delta.top)
                } else {
                    (newBottomPadding - maximumBottomPadding).toPx()
                        .coerceAtMost(-delta.top)
                }
                heightCorrection += correction

                newBottomPadding = newBottomPadding.coerceIn(0.dp..maximumBottomPadding)
                result = false
            }

            newBottomPadding
        }

        val heightAdditionDiffDp = with(density) {
            var heightDiff = (-delta.top - heightCorrection).toDp()
            val currHeight =
                (computedKeyboardSize.height - computedKeyboardSize.padding.bottom - computedKeyboardSize.padding.top).toDp()
            if (currHeight + heightDiff < minimumKeyboardHeight) {
                heightDiff += minimumKeyboardHeight - (currHeight + heightDiff)
                result = false
            } else if (currHeight + heightDiff > maximumKeyboardHeight) {
                heightDiff += maximumKeyboardHeight - (currHeight + heightDiff)
                result = false
            }

            heightDiff
        }

        editedSettings = when(editedSettings.currentMode) {
            KeyboardMode.Regular -> editedSettings.copy(
                heightAdditionDp = editedSettings.heightAdditionDp + heightAdditionDiffDp.value,
                paddingDp = editedSettings.paddingDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.Split -> editedSettings.copy(
                splitHeightAdditionDp = editedSettings.splitHeightAdditionDp + heightAdditionDiffDp.value,
                splitPaddingDp = editedSettings.splitPaddingDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.OneHanded -> editedSettings.copy(
                oneHandedHeightAdditionDp = editedSettings.oneHandedHeightAdditionDp + heightAdditionDiffDp.value,
                oneHandedRectDp = editedSettings.oneHandedRectDp.copy(bottom = bottomPadding)
            )
            KeyboardMode.Floating -> editedSettings // unused by Floating
        }
    }

    fun applySymmetricalPaddingForRegular(sideDelta: Float) = with(density) {
        var newSidePadding =
            when (editedSettings.currentMode) {
                KeyboardMode.Regular -> editedSettings.paddingDp.left
                KeyboardMode.Split -> editedSettings.splitPaddingDp.left
                KeyboardMode.OneHanded -> editedSettings.oneHandedRectDp.left
                KeyboardMode.Floating -> 0.dp
            } + sideDelta.toDp()

        if (newSidePadding !in 0.dp..maximumSidePadding) {
            newSidePadding = newSidePadding.coerceIn(0.dp..maximumSidePadding)
            result = false
        }

        editedSettings = when (editedSettings.currentMode) {
            KeyboardMode.Regular -> editedSettings.copy(
                paddingDp = editedSettings.paddingDp.copy(
                    left = newSidePadding,
                    right = newSidePadding
                )
            )
            KeyboardMode.Split -> editedSettings.copy(
                splitPaddingDp = editedSettings.splitPaddingDp.copy(
                    left = newSidePadding,
                    right = newSidePadding
                )
            )
            KeyboardMode.OneHanded -> editedSettings // unused by OneHanded
            KeyboardMode.Floating -> editedSettings // unused by Floating
        }
    }
}

class OneHandedKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: OneHandedKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {

    // These have to be flipped in right handed mode, because the setting values are relative to
    // left-handed mode.

    val deltaLeft = if(computedKeyboardSize.direction == OneHandedDirection.Left) {
        delta.left
    } else {
        -delta.right
    }

    val deltaRight = if(computedKeyboardSize.direction == OneHandedDirection.Left) {
        delta.right
    } else {
        -delta.left
    }


    fun moveSideToSide() = with(density) {
        var rightCorrection = 0.dp
        var newLeft = editedSettings.oneHandedRectDp.left + deltaLeft.toDp()
        if(newLeft < 0.dp) {
            // prevent shrinking when being dragged into the wall
            if(deltaRight < 0.0f) {
                rightCorrection -= newLeft
            }
            newLeft = 0.dp

            result = false
        }

        val newRight = editedSettings.oneHandedRectDp.right + deltaRight.toDp() + rightCorrection

        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                left = newLeft,
                right = newRight
            )
        )
    }

    fun limitToCorrectSide() = with(density) {
        var newLeft = editedSettings.oneHandedRectDp.left
        var newRight = editedSettings.oneHandedRectDp.right

        val newCenter = (newLeft + newRight) / 2.0f
        val limit = (viewSize.width.toDp()) / 2.0f
        if(newCenter > limit) {
            val diff = newCenter - limit
            newLeft -= diff
            newRight -= diff
            result = false
        }

        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                left = newLeft,
                right = newRight
            )
        )
    }

    fun limitMinimumWidth() = with(density) {
        editedSettings = editedSettings.copy(
            oneHandedRectDp = editedSettings.oneHandedRectDp.copy(
                right = editedSettings.oneHandedRectDp.right.coerceAtLeast(
                    editedSettings.oneHandedRectDp.left + minimumKeyboardWidth
                )
            )
        )
    }
}

class FloatingKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: ComputedKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {
    // Matching the necessary coordinate space
    var deltaX = delta.left
    var deltaY = -delta.bottom
    var deltaWidth = delta.right - delta.left
    var deltaHeight = delta.bottom - delta.top

    fun applyOriginOffsetWithLimits() = with(density) {
        var newX = editedSettings.floatingBottomOriginDp.first.dp + deltaX.toDp()
        var newY = editedSettings.floatingBottomOriginDp.second.dp + deltaY.toDp()

        val maxX = (viewSize.width.toDp() - editedSettings.floatingWidthDp.dp).coerceAtLeast(0.dp)
        val maxY = (viewSize.height.toDp() - editedSettings.floatingHeightDp.dp).coerceAtLeast(0.dp)

        val xRange = 0.dp .. maxX
        val yRange = 0.dp .. maxY

        if(newX !in xRange) {
            newX = newX.coerceIn(xRange)
            result = false
        }

        if(newY !in yRange) {
            newY = newY.coerceIn(yRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            floatingBottomOriginDp = Pair(newX.value, newY.value)
        )
    }

    fun applyDeltaSizeWithLimits() = with(density) {
        var newWidth = editedSettings.floatingWidthDp.dp + deltaWidth.toDp()
        var newHeight = editedSettings.floatingHeightDp.dp + deltaHeight.toDp()

        val widthRange = minimumKeyboardWidth .. maximumKeyboardWidth
        val heightRange = minimumKeyboardHeight .. maximumKeyboardHeight

        if(newWidth !in widthRange) {
            deltaX = 0.0f
            newWidth = newWidth.coerceIn(widthRange)
            result = false
        }

        if(newHeight !in heightRange) {
            deltaY = 0.0f
            newHeight = newHeight.coerceIn(heightRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            floatingWidthDp = newWidth.value,
            floatingHeightDp = newHeight.value
        )
    }
}

class SplitKeyboardResizeHelper(
    viewSize: IntSize,
    computedKeyboardSize: SplitKeyboardSize,
    density: Density,
    initialSettings: SavedKeyboardSizingSettings,
    delta: DragDelta
) : KeyboardResizeHelper(viewSize, computedKeyboardSize, density, initialSettings, delta) {
    fun editSplitLayoutWidth(delta: Float) {
        val oldSplitWidth = (computedKeyboardSize as SplitKeyboardSize).splitLayoutWidth
        val newSplitWidth = oldSplitWidth + 2*delta

        var newFraction = editedSettings.splitWidthFraction * (newSplitWidth / oldSplitWidth)

        val fractionRange = 0.1f .. 0.9f
        if(newFraction !in fractionRange) {
            newFraction = newFraction.coerceIn(fractionRange)
            result = false
        }

        editedSettings = editedSettings.copy(
            splitWidthFraction = newFraction
        )
    }
}

// kxkb: which seamless-resize zone an EXTRA finger landed in. Top-left (height) is the primary finger
// driven by the "…" button, so it isn't a secondary zone here; centre = split, bottom-right = padding.
private fun secondaryResizeTarget(pos: Offset, w: Float, h: Float): CurrentDraggingTarget? = when {
    pos.x in (w * 0.35f)..(w * 0.65f) && pos.y in (h * 0.30f)..(h * 0.70f) -> CurrentDraggingTarget.Center
    pos.x > w * 0.55f && pos.y > h * 0.50f -> CurrentDraggingTarget.Bottom
    else -> null
}

class KeyboardResizers(val latinIME: LatinIME) {
    private val resizing = mutableStateOf(false)

    private fun finishResizer() {
        resizing.value = false
    }

    @Composable
    private fun BoxScope.FloatingKeyboardResizer(size: FloatingKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = FloatingKeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value as? FloatingKeyboardSize ?: size,
                    this,
                    settings,
                    delta
                )

                helper.applyDeltaSizeWithLimits()
                helper.applyOriginOffsetWithLimits()

                result = result && helper.result

                helper.editedSettings
            }

            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.RegularKeyboardResizer(size: RegularKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = KeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value ?: size,
                    this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.applySymmetricalPaddingForRegular(delta.left - delta.right)

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.OneHandedResizer(size: OneHandedKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        ResizerRect({ delta ->
            var result = true

            latinIME.sizingCalculator.editSavedSettings { settings ->
                val helper = OneHandedKeyboardResizeHelper(
                    IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                    latinIME.size.value as? OneHandedKeyboardSize ?: size,
                    this, settings, delta
                )

                helper.editBottomPaddingAndHeightAddition()
                helper.moveSideToSide()
                helper.limitToCorrectSide()
                helper.limitMinimumWidth()

                result = result && helper.result

                helper.editedSettings
            }
            result
        }, true, {
            finishResizer()
        }, {
            latinIME.sizingCalculator.resetCurrentMode()
        }, shape)
    }

    @Composable
    private fun BoxScope.SplitKeyboardResizer(size: SplitKeyboardSize, shape: RoundedCornerShape) = with(LocalDensity.current) {
        println("Active size: ${size.width} ${size.splitLayoutWidth} ${size.padding}")
        Box(
            modifier = Modifier.matchParentSize()
                .absolutePadding(right = (size.width - size.splitLayoutWidth * 0.55f - size.padding.right - size.padding.left).toDp().coerceAtLeast(0.dp))
        ) {
            ResizerRect({ delta ->
                var result = true

                latinIME.sizingCalculator.editSavedSettings { settings ->
                    val helper = SplitKeyboardResizeHelper(
                        IntSize(latinIME.getViewWidth(), latinIME.getViewHeight()),
                        latinIME.size.value as? SplitKeyboardSize ?: size,
                        this@with, settings, delta
                    )

                    helper.editBottomPaddingAndHeightAddition()
                    helper.applySymmetricalPaddingForRegular(delta.left)
                    helper.editSplitLayoutWidth(delta.right - delta.left)

                    result = result && helper.result

                    helper.editedSettings
                }

                result
            }, true, {
                finishResizer()
            }, {
                latinIME.sizingCalculator.resetCurrentMode()
            }, shape)
        }
    }



    @Composable
    fun Resizer(boxScope: BoxScope, size: ComputedKeyboardSize, shape: RoundedCornerShape = RoundedCornerShape(4.dp)) = with(boxScope) {
        if (!resizing.value) return

        val modifier = Modifier.matchParentSize().let { mod ->
            if (size is FloatingKeyboardSize) mod
            else mod
                .safeKeyboardPadding()
                .keyboardBottomPadding(size)
                .absolutePadding(bottom = navBarHeight())
        }

        Box(modifier) {
            when (size) {
                is OneHandedKeyboardSize -> OneHandedResizer(size, shape)
                is RegularKeyboardSize -> RegularKeyboardResizer(size, shape)
                is SplitKeyboardSize -> SplitKeyboardResizer(size, shape)
                is FloatingKeyboardSize -> FloatingKeyboardResizer(size, shape)
            }
        }
    }

    fun displayResizer() {
        resizing.value = true
    }

    fun hideResizer() {
        if(resizing.value) finishResizer()
    }

    // --- kxkb: SEAMLESS live resize (long-press the "…" button and KEEP sliding) -------------------
    // The "…" button owns the long-pressing finger and drives the keyboard directly (seamlessPrimaryMove)
    // — no separate overlay grabs it, so the same finger flows from long-press into the slide. While
    // active, KxkbSeamlessOverlay shows blue zones and lets EXTRA fingers grab bottom-right (padding)
    // and centre (split). Releasing the primary finger ends the session (endSeamlessResize). Live
    // throughout; the docking dead-zone applies via applyKxkbDrag/calculate().
    private val seamlessActive = mutableStateOf(false)
    // Accumulated movement of the PRIMARY ("…") finger since activation, so the top-left dot can
    // follow it (the overlay doesn't otherwise see that finger).
    private val primaryDelta = mutableStateOf(Offset.Zero)

    fun beginSeamlessResize() {
        resizing.value = false   // hide FUTO's menu resizer if it's open
        primaryDelta.value = Offset.Zero
        seamlessActive.value = true
    }

    fun endSeamlessResize() {
        seamlessActive.value = false
    }

    fun isSeamlessActive(): Boolean = seamlessActive.value

    // Primary finger (the "…" long-press): vertical slide = height, horizontal slide = width.
    fun seamlessPrimaryMove(delta: Offset) {
        primaryDelta.value += delta
        applySeamless(CurrentDraggingTarget.Top, delta)
    }

    // Apply a finger's slide for its zone. Top-left = height + width; bottom-right = padding + width;
    // centre = split. Width uses the symmetric widthFraction, signed so dragging a corner OUTWARD
    // (top-left → left, bottom-right → right) widens it.
    private fun applySeamless(zone: CurrentDraggingTarget, delta: Offset) {
        when (zone) {
            CurrentDraggingTarget.Top -> {
                applyKxkbDrag(CurrentDraggingTarget.Top, delta)   // height (delta.y)
                applyKxkbDrag(CurrentDraggingTarget.Left, delta)  // width (delta.x; left = wider)
            }
            CurrentDraggingTarget.Bottom -> {
                applyKxkbDrag(CurrentDraggingTarget.Bottom, delta) // padding (delta.y)
                applyKxkbDrag(CurrentDraggingTarget.Right, delta)  // width (delta.x; right = wider)
            }
            else -> applyKxkbDrag(CurrentDraggingTarget.Center, delta) // split (delta.x)
        }
    }

    @Composable
    fun KxkbSeamlessOverlay(boxScope: BoxScope, kbSize: ComputedKeyboardSize) = with(boxScope) {
        if (!seamlessActive.value) return
        // Floating mode has its own drag affordances.
        if (kbSize is FloatingKeyboardSize) return

        // current position of each grabbed secondary finger (for the lit highlight); also the set of
        // grabbed targets so the zones light up while held.
        val grabbed = remember { mutableStateMapOf<CurrentDraggingTarget, Offset>() }
        val haptic = LocalHapticFeedback.current

        Box(Modifier.matchParentSize()
            .safeKeyboardPadding()
            .keyboardBottomPadding(kbSize)
            .absolutePadding(bottom = navBarHeight())
            // A faint dim marks resize mode AND blocks typing while resizing; consume every touch.
            .background(Color.Black.copy(alpha = 0.15f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    val assigns = HashMap<PointerId, CurrentDraggingTarget>()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            when {
                                change.changedToDown() -> {
                                    val t = secondaryResizeTarget(change.position, size.width.toFloat(), size.height.toFloat())
                                    if (t != null && !assigns.containsValue(t)) {
                                        assigns[change.id] = t
                                        grabbed[t] = change.position
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    change.consume()   // block typing under the resize overlay
                                }
                                change.changedToUp() -> {
                                    assigns.remove(change.id)?.let { grabbed.remove(it) }
                                    change.consume()
                                }
                                change.pressed -> {
                                    assigns[change.id]?.let { t ->
                                        val d = change.positionChange()
                                        if (d != Offset.Zero) {
                                            applySeamless(t, d)
                                            grabbed[t] = change.position
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            val dot = Color(0xFF2962FF).copy(alpha = 0.85f)
            val radius = with(LocalDensity.current) { 28.dp.toPx() }
            Canvas(Modifier.matchParentSize()) {
                val w = this.size.width
                val h = this.size.height
                // Top-left follows the "…" finger; bottom-right sits deep in the corner; centre = split.
                // A grabbed secondary follows its finger. All three the same colour.
                val tl = Offset(w * 0.08f, h * 0.10f) + primaryDelta.value
                val br = grabbed[CurrentDraggingTarget.Bottom] ?: Offset(w * 0.92f, h * 0.90f)
                val ce = grabbed[CurrentDraggingTarget.Center] ?: Offset(w * 0.5f, h * 0.5f)
                drawCircle(dot, radius, tl)
                drawCircle(dot, radius, br)
                drawCircle(dot, radius, ce)
            }
        }
    }

    /**
     * Map one incremental drag of a hot-point onto our own per-geometry sizing settings (the same
     * fields the Settings → Keyboard UI sliders drive), so the live keyboard resizes/splits exactly
     * the way that page does. Returns false when the drag was clamped at a limit (drives the red
     * handle highlight). Reads the live `size`/view width fresh each call so it tracks the keyboard
     * as it reflows mid-drag.
     */
    private fun applyKxkbDrag(target: CurrentDraggingTarget, amount: Offset): Boolean {
        val density = latinIME.resources.displayMetrics.density.coerceAtLeast(0.5f)
        val size = latinIME.size.value ?: return true
        val viewWidth = latinIME.getViewWidth().toFloat().coerceAtLeast(1.0f)
        var ok = true

        latinIME.sizingCalculator.editSavedSettings { s ->
            when (target) {
                // Top edge → overall keyboard height (heightMultiplier). Drag up (amount.y < 0) = taller.
                CurrentDraggingTarget.Top -> {
                    val corePx = (size.height - size.padding.bottom).toFloat().coerceAtLeast(1.0f)
                    val newMult = s.heightMultiplier * (corePx + (-amount.y)) / corePx
                    val clamped = newMult.coerceIn(0.5f, 2.5f)
                    if (clamped != newMult) ok = false
                    s.copy(heightMultiplier = clamped)
                }

                // Bottom edge → lift off the bottom edge (bottomLiftDp). Drag up (amount.y < 0) = more lift.
                CurrentDraggingTarget.Bottom -> {
                    val newLift = s.bottomLiftDp + (-amount.y) / density
                    val clamped = newLift.coerceIn(0.0f, 640.0f)
                    if (clamped != newLift) ok = false
                    // NB: the dock dead-zone lives in calculate() (the render/insets decision), NOT
                    // here — snapping each incremental delta would pin the value and block the drag
                    // from ever accumulating past the threshold.
                    s.copy(bottomLiftDp = clamped)
                }

                // Left / right edge → symmetric keyboard width (widthFraction). Inward drag narrows.
                CurrentDraggingTarget.Left, CurrentDraggingTarget.Right -> {
                    val frac = s.widthFraction.coerceIn(0.3f, 1.0f)
                    val fullWidthPx = (size.width / frac).coerceAtLeast(1.0f)
                    val inwardPx = if (target == CurrentDraggingTarget.Left) amount.x else -amount.x
                    val newFrac = s.widthFraction + (-2.0f * inwardPx / fullWidthPx)
                    val clamped = newFrac.coerceIn(0.5f, 1.0f)
                    if (clamped != newFrac) ok = false
                    // NB: the dock dead-zone lives in calculate() (the render/insets decision), NOT
                    // here — snapping each incremental delta would pin the value at 1.0 and block the
                    // drag from ever accumulating past the threshold (the "can't narrow" bug).
                    s.copy(widthFraction = clamped)
                }

                // Center → progressive split via horizontal drag. Rightward opens the centre gap (more
                // split); from a non-split keyboard the first rightward drag switches into Split. Leftward
                // closes the gap and, past the minimum, switches back to Regular (un-splits).
                CurrentDraggingTarget.Center -> {
                    val fracStep = amount.x / viewWidth
                    when (s.currentMode) {
                        KeyboardMode.Regular ->
                            if (amount.x > 0.0f) {
                                s.copy(
                                    currentMode = KeyboardMode.Split,
                                    prefersSplit = true,
                                    splitWidthFraction = (1.0f - fracStep).coerceIn(0.4f, 1.0f)
                                )
                            } else s

                        KeyboardMode.Split -> {
                            val newFrac = s.splitWidthFraction - fracStep
                            if (newFrac >= 1.0f && amount.x < 0.0f) {
                                s.copy(
                                    currentMode = KeyboardMode.Regular,
                                    prefersSplit = false,
                                    splitWidthFraction = 1.0f
                                )
                            } else {
                                s.copy(splitWidthFraction = newFrac.coerceIn(0.4f, 1.0f))
                            }
                        }

                        // One-handed / floating: centre drag is a no-op (no split concept).
                        else -> s
                    }
                }
            }
        }

        return ok
    }
}