package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.keyboard.internal.MoreKeySpec
import kotlin.math.sqrt

data class KeyCoordinate(
    val regularRow: Int,
    val regularColumn: Int,
    val element: KeyboardLayoutElement,
    val measurement: KeyCoordinateMeasurement
)

data class KeyCoordinateMeasurement(
    val totalRows: Int,
    val numColumnsByRow: List<Int>
)

@Serializable
sealed interface AbstractKey {
    fun countsToKeyCoordinate(params: KeyboardParams, row: Row, keyboard: Keyboard): Boolean
    fun computeData(params: KeyboardParams, row: Row, keyboard: Keyboard, coordinate: KeyCoordinate): ComputedKeyData?
}

@Serializable
enum class Direction {
    West,
    NorthWest,
    North,
    NorthEast,
    East,
    SouthEast,
    South,
    SouthWest
}

fun Direction.toVector(): Pair<Double, Double> = when(this) {
    Direction.West -> 1.0 to 0.0
    Direction.NorthWest -> 0.70710 to 0.70710
    Direction.North -> 0.0 to 1.0
    Direction.NorthEast -> -0.70710 to 0.70710
    Direction.East -> -1.0 to 0.0
    Direction.SouthEast -> -0.70710 to -0.70710
    Direction.South -> 0.0 to -1.0
    Direction.SouthWest -> 0.70710 to -0.70710
}

fun computeDirectionsFromDeltaPos(
    dx: Double,
    dy: Double,
    threshold: Double
): List<Direction> {
    val length = sqrt(dx * dx + dy * dy)
    if(length < threshold || length == 0.0) return emptyList()

    val dirX = -dx / length
    val dirY = -dy / length

    val scored = Direction.entries.map {
        val vec = it.toVector()
        val dotProduct = vec.first * dirX + vec.second * dirY

        dotProduct to it
    }.sortedBy { // descending order, highest scores first
        -it.first
    }

    return scored.filter { it.first > 0.0 }.map { it.second }
}

data class ComputedFlickData(
    // It is illegal for child ComputedKeyData to also contain flick data
    val directions: Map<Direction, ComputedKeyData>,
    val label: String? = null,
    val icon: String? = null
)

// kxkb: one "main" glyph of a `cluster` key (the predictive multi-key). The mains share one wide
// key predictively: a tap commits the centre but ALL of the band's letters become candidates for
// that tap position, disambiguated by the language model. `column`/`columnCount` let ProximityInfo
// tile each main's sub-rect across the key width (non-letter mains are positioned but not injected,
// since the decoder only mixes letters). See ClusterKey + ProximityInfo.createNativeProximityInfo.
data class ClusterMain(
    val codePoint: Int,
    val column: Int,
    val columnCount: Int
)

data class ComputedKeyData(
    val label: String,
    val code: Int,
    val outputText: String?,
    val width: KeyWidth,
    val icon: String,
    val style: KeyVisualStyle,
    val anchored: Boolean,
    val showPopup: Boolean,
    val moreKeys: List<MoreKeySpec>,
    val longPressEnabled: Boolean,
    val repeatable: Boolean,
    val moreKeyFlags: Int,
    val countsToKeyCoordinate: Boolean,
    val hint: String,
    val labelFlags: Int,
    val flick: ComputedFlickData? = null,
    val fastLongPress: Boolean = false,
    val rowSpan: Int = 1,
    val swipeLetter: String? = null,
    // kxkb: set only by `cluster` keys — the band of main glyphs, carried to ProximityInfo so a tap
    // injects all of them as predictive candidates. Null on every other key type.
    val clusterMains: List<ClusterMain>? = null,
)
