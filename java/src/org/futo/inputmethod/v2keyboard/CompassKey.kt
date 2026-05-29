package org.futo.inputmethod.v2keyboard

import com.charleskorn.kaml.YamlInput
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.futo.inputmethod.keyboard.KeyboardId
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

// kxkb: a compass direction can be a full key OR a compact shorthand for the common rich slots:
//   { mod: ctrl }         -> a chord on the PRIMARY char  (ctrl/alt/shift/super -> C-/M-/S-/s-)
//   { mod: ctrl, on: x }  -> a chord on an explicit base char
//   { chord: "C-x C-s" }  -> a chord key
//   { macro: "etc. " }    -> a macro key
// All take an optional `label`. A scalar (`up: O`), a list, or a `type:`-tagged map decodes as a
// normal key. `{ mod: … }` needs the primary char (only CompassKey knows it), so it defers to a
// ModSlot resolved at computeData time; chord/macro desugar immediately. The serializer peeks at the
// YAML node, mirroring the `Key` typealias / ClassOrScalarsSerializer pattern.

sealed interface CompassSlot
data class KeySlot(val key: Key) : CompassSlot
data class ModSlot(val mod: String, val on: String? = null, val label: String? = null) : CompassSlot

typealias Slot = @Serializable(with = CompassSlotSerializer::class) CompassSlot

@Serializable private data class ModShorthand(val mod: String, val on: String? = null, val label: String? = null)
@Serializable private data class ChordShorthand(val chord: String, val label: String? = null)
@Serializable private data class MacroShorthand(val macro: String, val label: String? = null)

object CompassSlotSerializer : KSerializer<CompassSlot> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("org.futo.inputmethod.v2keyboard.CompassSlot", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): CompassSlot {
        val valueDecoder = decoder.beginStructure(descriptor) as YamlInput
        val node = valueDecoder.node
        if (node is YamlMap) {
            val keys = node.entries.keys.map { it.content }.toSet()
            if (!keys.contains("type")) {
                when {
                    keys.contains("mod") -> {
                        val s = valueDecoder.yaml.decodeFromYamlNode(ModShorthand.serializer(), node)
                        return ModSlot(s.mod, s.on, s.label)
                    }
                    keys.contains("chord") -> {
                        val s = valueDecoder.yaml.decodeFromYamlNode(ChordShorthand.serializer(), node)
                        return KeySlot(ChordKey(keys = s.chord, label = s.label))
                    }
                    keys.contains("macro") -> {
                        val s = valueDecoder.yaml.decodeFromYamlNode(MacroShorthand.serializer(), node)
                        return KeySlot(MacroKey(text = s.macro, label = s.label))
                    }
                }
            }
        }
        // Scalar, list, or a `type:`-tagged map: decode as a normal key.
        return KeySlot(valueDecoder.yaml.decodeFromYamlNode(KeyPathSerializer, node))
    }

    override fun serialize(encoder: Encoder, value: CompassSlot): Unit =
        throw SerializationException("CompassSlot serialization is not supported")
}

@Serializable
@SerialName("compass")
data class CompassKey(
    val primary: Key,

    val slide: String? = null,

    val up: Slot? = null,
    val down: Slot? = null,
    val left: Slot? = null,
    val right: Slot? = null,
    val upLeft: Slot? = null,
    val upRight: Slot? = null,
    val downLeft: Slot? = null,
    val downRight: Slot? = null,

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

    private fun resolveSlots(primaryChar: String?): Map<Direction, Key> {
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
        fun place(dir: Direction, slot: Slot?) {
            if (slot == null) return
            slotToKey(slot, primaryChar)?.let { slots[dir] = it }
        }
        place(Direction.North,     up)
        place(Direction.South,     down)
        place(Direction.West,      left)
        place(Direction.East,      right)
        place(Direction.NorthWest, upLeft)
        place(Direction.NorthEast, upRight)
        place(Direction.SouthWest, downLeft)
        place(Direction.SouthEast, downRight)
        return slots
    }

    // Convert a slot to a concrete key. A `{ mod: … }` shorthand becomes a chord on its explicit
    // `on:` char, or — the common case — on the compass primary's own character.
    private fun slotToKey(slot: CompassSlot, primaryChar: String?): Key? = when (slot) {
        is KeySlot -> slot.key
        is ModSlot -> {
            val base = slot.on ?: primaryChar
            if (base.isNullOrEmpty()) null
            else ChordKey(keys = modPrefix(slot.mod) + "-" + base, label = slot.label)
        }
    }

    private fun modPrefix(mod: String): String = when (mod.trim().lowercase()) {
        "ctrl", "control", "c"               -> "C"
        "alt", "meta", "opt", "option", "m"  -> "M"
        "shift"                              -> "S"
        "super", "win", "cmd", "gui", "s"    -> "s"
        else                                 -> "C"
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
    ): ComputedKeyData? {
        val primaryData = primary.computeData(params, row, keyboard, coordinate) ?: return null
        // The primary's character, for `{ mod: … }` shorthands (chord on the primary).
        val primaryChar = if (primaryData.code > 0) String(Character.toChars(primaryData.code))
                          else primaryData.label.ifEmpty { null }
        return primaryData.copy(
            moreKeys = emptyList(),
            longPressEnabled = false,
            flick = ComputedFlickData(
                directions = buildMap {
                    resolveSlots(primaryChar).forEach { (dir, key) ->
                        slotData(key, params, row, keyboard, coordinate)?.let { put(dir, it) }
                    }
                },
                label = label,
                icon = icon
            ),
            showPopup = true,
            // kxkb: the compass primary is computed without the compass's own attributes, so apply
            // the per-key visual overrides here.
            color = attributes?.color ?: primaryData.color,
            fontScale = attributes?.fontScale ?: primaryData.fontScale,
            hintScale = attributes?.hintScale ?: primaryData.hintScale,
            backgroundColor = attributes?.backgroundColor ?: primaryData.backgroundColor,
            borderColor = attributes?.borderColor ?: primaryData.borderColor,
            labelOffsetX = attributes?.labelOffsetX ?: primaryData.labelOffsetX,
            labelOffsetY = attributes?.labelOffsetY ?: primaryData.labelOffsetY,
            flickTopOffset = attributes?.flickTopOffset ?: primaryData.flickTopOffset,
            flickBottomOffset = attributes?.flickBottomOffset ?: primaryData.flickBottomOffset,
            flickLeftOffset = attributes?.flickLeftOffset ?: primaryData.flickLeftOffset,
            flickRightOffset = attributes?.flickRightOffset ?: primaryData.flickRightOffset
        )
    }
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
            color = attrs.color, fontScale = attrs.fontScale, hintScale = attrs.hintScale,
            backgroundColor = attrs.backgroundColor, borderColor = attrs.borderColor,
            labelOffsetX = attrs.labelOffsetX, labelOffsetY = attrs.labelOffsetY,
            flickTopOffset = attrs.flickTopOffset, flickBottomOffset = attrs.flickBottomOffset,
            flickLeftOffset = attrs.flickLeftOffset, flickRightOffset = attrs.flickRightOffset,
        )
    }
}

// kxkb: "chord" key — emits a modifier+keystroke chord (or a space-separated sequence of them) as
// real hardware key events, e.g. `keys: "C-x C-s"`. Notation is Emacs-ish: each step is
// MOD-...-BASE where MOD is C (Ctrl), M (Alt/Meta), S (Shift) or s (Super); BASE is a single char
// or a named key (TAB, RET/ENTER, SPC/SPACE, ESC, DEL, UP/DOWN/LEFT/RIGHT, HOME, END, PGUP/PGDN,
// F1..F12). Usable standalone or inside a compass slide slot. The spec is carried to InputLogic via
// outputText with a leading U+0000 sentinel (impossible in real text); InputLogic.onTextInput
// intercepts that and performs the chord (see performChord). Like macro, it bypasses the keyspec
// parser, so the spec is taken verbatim.
@Serializable
@SerialName("chord")
data class ChordKey(
    val keys: String,
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
            label = label ?: keys,
            code = Constants.CODE_OUTPUT_TEXT,
            outputText = "\u0000" + keys,
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
            color = attrs.color, fontScale = attrs.fontScale, hintScale = attrs.hintScale,
            backgroundColor = attrs.backgroundColor, borderColor = attrs.borderColor,
            labelOffsetX = attrs.labelOffsetX, labelOffsetY = attrs.labelOffsetY,
            flickTopOffset = attrs.flickTopOffset, flickBottomOffset = attrs.flickBottomOffset,
            flickLeftOffset = attrs.flickLeftOffset, flickRightOffset = attrs.flickRightOffset,
        )
    }
}

// kxkb: "cycle" key — multitap. Repeated taps within the multitap window (a setting) cycle through
// `taps`, each tap deleting the previously-committed entry and committing the next (wrapping at the
// end). The window lapsing, the cursor moving, or any other key ends the cycle so the next tap
// starts fresh. `taps` is either a string (one codepoint per tap) or a list (for multi-character
// entries). Carried to InputLogic via outputText behind a U+0001 sentinel, entries joined by U+0001
// (both impossible in real text); InputLogic.onTextInput intercepts it and runs performCycle.
@Serializable(with = TapsSerializer::class)
data class Taps(val items: List<String>)

object TapsSerializer : KSerializer<Taps> {
    @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("org.futo.inputmethod.v2keyboard.CycleTaps", SerialKind.CONTEXTUAL)

    override fun deserialize(decoder: Decoder): Taps {
        val valueDecoder = decoder.beginStructure(descriptor) as YamlInput
        return when (val node = valueDecoder.node) {
            // String: one tap per codepoint.
            is YamlScalar -> Taps(node.content.codePoints().toArray().map { String(Character.toChars(it)) })
            // List: one tap per entry (may be multi-character).
            is YamlList -> Taps(node.items.map { valueDecoder.yaml.decodeFromYamlNode(String.serializer(), it) })
            else -> throw SerializationException("`taps` must be a string or a list of strings")
        }
    }

    override fun serialize(encoder: Encoder, value: Taps): Unit =
        throw SerializationException("Taps serialization is not supported")
}

@Serializable
@SerialName("cycle")
data class CycleKey(
    val taps: Taps,
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
        // U+0001 marker + entries joined by U+0001 (see InputLogic.performCycle).
        val payload = "\u0001" + taps.items.joinToString("\u0001")
        return ComputedKeyData(
            label = label ?: taps.items.joinToString(""),
            code = Constants.CODE_OUTPUT_TEXT,
            outputText = payload,
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
            color = attrs.color, fontScale = attrs.fontScale, hintScale = attrs.hintScale,
            backgroundColor = attrs.backgroundColor, borderColor = attrs.borderColor,
            labelOffsetX = attrs.labelOffsetX, labelOffsetY = attrs.labelOffsetY,
            flickTopOffset = attrs.flickTopOffset, flickBottomOffset = attrs.flickBottomOffset,
            flickLeftOffset = attrs.flickLeftOffset, flickRightOffset = attrs.flickRightOffset,
        )
    }
}

// kxkb: "cluster" key — the predictive multi-key (the old Multiling "3+2"). It carries a band of N
// "main" glyphs that share one wide key PREDICTIVELY: a plain tap commits the centre main, but the
// real touch position is decomposed across ALL the band's letters by the spatial model, so the
// language model can pick any of them from word context (see ProximityInfo.createNativeProximityInfo
// + the native decomposeTapPosition path). The leftmost/rightmost mains are also reachable PRECISELY
// by a slide left/right; the six remaining directions (up-left/up/up-right, down-left/down/down-right)
// are "extra" slide targets, each any key or { mod }/{ chord }/{ macro } shorthand — exactly like a
// compass. So a cluster IS a compass whose middle row is the predictive main-band:
//
//     up-left      up        up-right       (extras, secondary size, slide)
//     main[0] ·· main[centre] ·· main[N-1]  (left/right slide-precise, centre = tap; all predicted)
//     down-left    down      down-right     (extras, secondary size, slide)
//
//   { type: cluster, main: "aev", up: "4", down: { macro: "…" } }
//
// Unlike chord/macro/cycle this does NOT intercept onTextInput — the tap is an ordinary centre-main
// keypress; the predictive magic is entirely the ProximityInfo sub-rect injection keyed off the
// `clusterMains` tag on the ComputedKeyData. left/right are reserved (the side mains); only the six
// diagonal/vertical slots are author-settable. Letter-only prediction (the decoder mixes a–z only).
@Serializable
@SerialName("cluster")
data class ClusterKey(
    val main: String,

    val up: Slot? = null,
    val down: Slot? = null,
    val upLeft: Slot? = null,
    val upRight: Slot? = null,
    val downLeft: Slot? = null,
    val downRight: Slot? = null,

    val attributes: KeyAttributes? = null,
    val label: String? = null,
    val icon: String? = null,
) : AbstractKey {
    private val extraAttrs = attributes?.let { listOf(it) } ?: emptyList()

    // A main/side key is literal (no keyspec-shortcut expansion, no auto morekeys) so "(", "$", etc.
    // stay themselves, matching compass's compact-string slots. Returns BaseKey (not Key) so the
    // centre main can call computeDataWithExtraAttrs to inherit the cluster's width/style.
    private fun literalKey(codePoint: Int): BaseKey = BaseKey(
        spec = String(Character.toChars(codePoint)),
        attributes = KeyAttributes(
            useKeySpecShortcut = false,
            moreKeyMode = MoreKeyMode.OnlyExplicit
        )
    )

    // Identical slot resolution to CompassKey: { mod } becomes a chord on its `on:` char or the
    // band's centre char; chord/macro have already desugared in the serializer.
    private fun slotToKey(slot: CompassSlot, primaryChar: String?): Key? = when (slot) {
        is KeySlot -> slot.key
        is ModSlot -> {
            val base = slot.on ?: primaryChar
            if (base.isNullOrEmpty()) null
            else ChordKey(keys = modPrefix(slot.mod) + "-" + base, label = slot.label)
        }
    }

    private fun modPrefix(mod: String): String = when (mod.trim().lowercase()) {
        "ctrl", "control", "c"               -> "C"
        "alt", "meta", "opt", "option", "m"  -> "M"
        "shift"                              -> "S"
        "super", "win", "cmd", "gui", "s"    -> "s"
        else                                 -> "C"
    }

    private fun slotData(
        key: Key,
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = when (key) {
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
    ): ComputedKeyData? {
        // kxkb: auto-capitalise the whole band (display + prediction) when the layout is shifted, so a
        // cluster/column needn't be wrapped in a `case` just to upper-case. `shiftable: false` opts out.
        val shifted = (attributes ?: KeyAttributes()).getEffectiveAttributes(row, keyboard).shiftable == true && when (params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> true
            else -> false
        }
        val effMain = if (shifted) main.uppercase(params.mId.locale) else main
        val cps = effMain.codePoints().toArray()
        if (cps.isEmpty()) return null
        val n = cps.size
        val centerIdx = n / 2   // N=3 -> 1 (centre); N=2 -> 1; N=1 -> 0

        // The centre main is the tap-commit primary, built as a literal BaseKey carrying the
        // cluster's own attributes (width/style/etc.).
        val primaryData = literalKey(cps[centerIdx])
            .computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)
        val primaryChar = if (primaryData.code > 0) String(Character.toChars(primaryData.code))
                          else primaryData.label.ifEmpty { null }

        // Flick map: West = first main, East = last main (precise side access), plus the six extras.
        val flick = LinkedHashMap<Direction, ComputedKeyData>()
        if (n >= 2) {
            slotData(literalKey(cps[0]),     params, row, keyboard, coordinate)?.let { flick[Direction.West] = it }
            slotData(literalKey(cps[n - 1]), params, row, keyboard, coordinate)?.let { flick[Direction.East] = it }
        }
        fun place(dir: Direction, slot: Slot?) {
            if (slot == null) return
            slotToKey(slot, primaryChar)?.let { key ->
                slotData(key, params, row, keyboard, coordinate)?.let { flick[dir] = it }
            }
        }
        place(Direction.North,     up)
        place(Direction.South,     down)
        place(Direction.NorthWest, upLeft)
        place(Direction.NorthEast, upRight)
        place(Direction.SouthWest, downLeft)
        place(Direction.SouthEast, downRight)

        // The band, tagged for the proximity model (predictive multi-key).
        val mains = cps.mapIndexed { i, cp -> ClusterMain(cp, i, n) }

        return primaryData.copy(
            label = effMain,
            moreKeys = emptyList(),
            longPressEnabled = false,
            flick = if (flick.isEmpty()) null
                    else ComputedFlickData(directions = flick, label = label, icon = icon),
            showPopup = true,
            clusterMains = mains
        )
    }
}


// kxkb: a `column` key — the vertical sibling of `cluster`. The `main` chars stack TOP-TO-BOTTOM as a
// predictive band (sub-rects split the key height; letters feed the decoder exactly like cluster), the
// centre main is the tap-commit primary, and North/South flicks give precise access to the top/bottom
// main (mirroring cluster's West/East). The six author slots become the optional 3+3 small side keys,
// reached by flick and drawn as at-rest flick labels in a left column (upLeft/left/downLeft) and a
// right column (upRight/right/downRight). Behaviour is otherwise identical to cluster.
//
//   { type: column, main: "abc", left: "(", right: ")", upLeft: "1", downRight: { macro: "…" } }
//
@Serializable
@SerialName("column")
data class ColumnKey(
    val main: String,

    val left: Slot? = null,
    val right: Slot? = null,
    val upLeft: Slot? = null,
    val upRight: Slot? = null,
    val downLeft: Slot? = null,
    val downRight: Slot? = null,

    val attributes: KeyAttributes? = null,
    val label: String? = null,
    val icon: String? = null,
) : AbstractKey {
    private val extraAttrs = attributes?.let { listOf(it) } ?: emptyList()

    private fun literalKey(codePoint: Int): BaseKey = BaseKey(
        spec = String(Character.toChars(codePoint)),
        attributes = KeyAttributes(
            useKeySpecShortcut = false,
            moreKeyMode = MoreKeyMode.OnlyExplicit
        )
    )

    private fun slotToKey(slot: CompassSlot, primaryChar: String?): Key? = when (slot) {
        is KeySlot -> slot.key
        is ModSlot -> {
            val base = slot.on ?: primaryChar
            if (base.isNullOrEmpty()) null
            else ChordKey(keys = modPrefix(slot.mod) + "-" + base, label = slot.label)
        }
    }

    private fun modPrefix(mod: String): String = when (mod.trim().lowercase()) {
        "ctrl", "control", "c"               -> "C"
        "alt", "meta", "opt", "option", "m"  -> "M"
        "shift"                              -> "S"
        "super", "win", "cmd", "gui", "s"    -> "s"
        else                                 -> "C"
    }

    private fun slotData(
        key: Key,
        params: KeyboardParams,
        row: Row,
        keyboard: Keyboard,
        coordinate: KeyCoordinate
    ): ComputedKeyData? = when (key) {
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
    ): ComputedKeyData? {
        // kxkb: auto-capitalise the whole band (display + prediction) when the layout is shifted, so a
        // cluster/column needn't be wrapped in a `case` just to upper-case. `shiftable: false` opts out.
        val shifted = (attributes ?: KeyAttributes()).getEffectiveAttributes(row, keyboard).shiftable == true && when (params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED,
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED,
            KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> true
            else -> false
        }
        val effMain = if (shifted) main.uppercase(params.mId.locale) else main
        val cps = effMain.codePoints().toArray()
        if (cps.isEmpty()) return null
        val n = cps.size
        val centerIdx = n / 2

        val primaryData = literalKey(cps[centerIdx])
            .computeDataWithExtraAttrs(params, row, keyboard, coordinate, extraAttrs)
        val primaryChar = if (primaryData.code > 0) String(Character.toChars(primaryData.code))
                          else primaryData.label.ifEmpty { null }

        // Flick map: North = first (top) main, South = last (bottom) main — the band ends, mirroring
        // cluster's West/East. The six author slots fill the left and right side columns.
        val flick = LinkedHashMap<Direction, ComputedKeyData>()
        if (n >= 2) {
            slotData(literalKey(cps[0]),     params, row, keyboard, coordinate)?.let { flick[Direction.North] = it }
            slotData(literalKey(cps[n - 1]), params, row, keyboard, coordinate)?.let { flick[Direction.South] = it }
        }
        fun place(dir: Direction, slot: Slot?) {
            if (slot == null) return
            slotToKey(slot, primaryChar)?.let { key ->
                slotData(key, params, row, keyboard, coordinate)?.let { flick[dir] = it }
            }
        }
        place(Direction.West,      left)
        place(Direction.East,      right)
        place(Direction.NorthWest, upLeft)
        place(Direction.NorthEast, upRight)
        place(Direction.SouthWest, downLeft)
        place(Direction.SouthEast, downRight)

        // The band, tagged vertical for the proximity model + vertical glyph layout.
        val mains = cps.mapIndexed { i, cp -> ClusterMain(cp, i, n, vertical = true) }

        return primaryData.copy(
            label = effMain,
            moreKeys = emptyList(),
            longPressEnabled = false,
            flick = if (flick.isEmpty()) null
                    else ComputedFlickData(directions = flick, label = label, icon = icon),
            showPopup = true,
            clusterMains = mains
        )
    }
}
