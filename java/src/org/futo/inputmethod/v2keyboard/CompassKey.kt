package org.futo.inputmethod.v2keyboard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.futo.inputmethod.keyboard.internal.KeyboardParams
import org.futo.inputmethod.latin.common.Constants

// kxkb: "compass" key — a tap (primary) plus up to eight directional "slide" targets, reusing FUTO's
// existing flick engine. It is sugar over `flick`:
//
//   - a compact `slide:` string, one codepoint per slot, in the order
//       up, down, left, right, up-left, up-right, down-left, down-right
//     where a space skips that slot and a short string leaves trailing slots empty; plus
//   - optional per-direction overrides (up/down/left/right/upLeft/upRight/downLeft/downRight),
//     each accepting ANY key (a bare char, a `macro`, later a `chord`); overrides win over the
//     compact string for their direction.
//
// Unlike FlickKey (which casts each direction `as? BaseKey` and silently drops anything else),
// compass builds the flick map itself and accepts any key per direction, so `macro` slots (and
// later `chord` slots) work. The primary's data is reused verbatim and a ComputedFlickData is
// attached, exactly as FlickKey does, so rendering / at-rest labels / the slide gesture are
// identical.

private val COMPASS_SLOT_ORDER = listOf(
    Direction.North,     // up
    Direction.South,     // down
    Direction.West,      // left
    Direction.East,      // right
    Direction.NorthWest, // up-left
    Direction.NorthEast, // up-right
    Direction.SouthWest, // down-left
    Direction.SouthEast  // down-right
)

@Serializable
@SerialName("compass")
data class CompassKey(
    val primary: Key,

    val slide: String? = null,

    val up: Key? = null,
    val down: Key? = null,
    val left: Key? = null,
    val right: Key? = null,
    val upLeft: Key? = null,
    val upRight: Key? = null,
    val downLeft: Key? = null,
    val downRight: Key? = null,

    val attributes: KeyAttributes? = null,
    val label: String? = null,
    val icon: String? = null,
) : AbstractKey {
    private val extraAttrs = attributes?.let { listOf(it) } ?: emptyList()

    // A compact-string slot becomes a literal single-codepoint key: keyspec-shortcut expansion is
    // disabled (so `$`, `^`, `{`, etc. stay literal rather than becoming currency/etc.) and no auto
    // morekeys are added (irrelevant on a flick target anyway).
    private fun literalKey(codePoint: Int): Key = BaseKey(
        spec = String(Character.toChars(codePoint)),
        attributes = KeyAttributes(
            useKeySpecShortcut = false,
            moreKeyMode = MoreKeyMode.OnlyExplicit
        )
    )

    private fun resolveSlots(): Map<Direction, Key> {
        val slots = LinkedHashMap<Direction, Key>()
        slide?.let { s ->
            val cps = s.codePoints().toArray()
            for (i in cps.indices) {
                if (i >= COMPASS_SLOT_ORDER.size) break
                if (cps[i] == ' '.code) continue
                slots[COMPASS_SLOT_ORDER[i]] = literalKey(cps[i])
            }
        }
        // Named overrides win over the compact string.
        up?.let        { slots[Direction.North]     = it }
        down?.let      { slots[Direction.South]     = it }
        left?.let      { slots[Direction.West]      = it }
        right?.let     { slots[Direction.East]      = it }
        upLeft?.let    { slots[Direction.NorthWest] = it }
        upRight?.let   { slots[Direction.NorthEast] = it }
        downLeft?.let  { slots[Direction.SouthWest] = it }
        downRight?.let { slots[Direction.SouthEast] = it }
        return slots
    }

    private fun slotData(
        key: Key,
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = when (key) {
        // BaseKey directions inherit the compass key's attributes, matching FlickKey.
        is BaseKey -> key.computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)
        else -> key.computeData(params, row, keyboard, coordinate)
    }

    override fun countsToKeyCoordinate(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard
    ): Boolean = false

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = primary.computeData(params, row, keyboard, coordinate)?.copy(
        moreKeys = emptyList(),
        longPressEnabled = false,
        flick = ComputedFlickData(
            directions = buildMap {
                resolveSlots().forEach { (dir, key) ->
                    slotData(key, params, row, keyboard, coordinate)?.let { put(dir, it) }
                }
            },
            label = label,
            icon = icon
        ),
        showPopup = true
    )
}

// kxkb: "macro" key — types a literal string, optionally showing a shorter label on the key face.
// Usable anywhere a key is valid, including a compass slide slot. Bypasses the keyspec parser
// entirely (so the text may contain |, \, a leading !, commas, etc. with no escaping), emitting via
// CODE_OUTPUT_TEXT exactly like FUTO's own multi-character keys.
@Serializable
@SerialName("macro")
data class MacroKey(
    val text: String,
    val label: String? = null,
    val attributes: KeyAttributes = KeyAttributes(),
) : AbstractKey {
    override fun countsToKeyCoordinate(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard
    ): Boolean = false

    override fun computeData(
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData {
        val attrs = attributes.getEffectiveAttributes(row, keyboard)
        val moreKeyMode = attrs.moreKeyMode!!
        return ComputedKeyData(
            label = label ?: text,
            code = Constants.CODE_OUTPUT_TEXT,
            outputText = text,
            width = attrs.width!!,
            icon = "",
            style = attrs.style!!,
            anchored = attrs.anchored!!,
            showPopup = attrs.showPopup!!,
            moreKeys = listOf(),
            longPressEnabled = false,
            repeatable = false,
            moreKeyFlags = 0,
            countsToKeyCoordinate = moreKeyMode.autoNumFromCoord && moreKeyMode.autoSymFromCoord,
            hint = "",
            labelFlags = attrs.labelFlags?.getValue() ?: 0,
        )
    }
}
