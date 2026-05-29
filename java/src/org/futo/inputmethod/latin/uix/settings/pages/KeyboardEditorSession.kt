package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.graphics.Rect
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutElement
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutKind
import org.futo.inputmethod.keyboard.internal.KeyboardLayoutPage
import org.futo.inputmethod.v2keyboard.AbstractKey
import org.futo.inputmethod.v2keyboard.BaseKey
import org.futo.inputmethod.v2keyboard.CaseSelector
import org.futo.inputmethod.v2keyboard.ChordKey
import org.futo.inputmethod.v2keyboard.ClusterKey
import org.futo.inputmethod.v2keyboard.ColumnKey
import org.futo.inputmethod.v2keyboard.CompassKey
import org.futo.inputmethod.v2keyboard.CompassSlot
import org.futo.inputmethod.v2keyboard.CycleKey
import org.futo.inputmethod.v2keyboard.FlickKey
import org.futo.inputmethod.v2keyboard.GapKey
import org.futo.inputmethod.v2keyboard.KeySlot
import org.futo.inputmethod.v2keyboard.KeyWidth
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2
import org.futo.inputmethod.v2keyboard.KeyboardLayoutSetV2Params
import org.futo.inputmethod.v2keyboard.MacroKey
import org.futo.inputmethod.v2keyboard.ModSlot
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.Row
import org.futo.inputmethod.v2keyboard.emitKeyboardYaml
import org.futo.inputmethod.v2keyboard.parseKeyboardYamlString
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.futo.inputmethod.keyboard.Keyboard as RuntimeKeyboard
import org.futo.inputmethod.v2keyboard.Keyboard as V2Keyboard

// kxkb: visual layout editor — Phase 1. Shared in-memory working model + path-addressed key editing.
// The working model is the parsed [V2Keyboard]; edits produce new immutable copies. A session lives
// across the editor's navigation screens (main editor + per-key edit sub-screens) and is discarded on
// exit unless Applied. See [[keyboard-editor-plan]].

/**
 * Address of an editable key within the working model's BASE rows (altPages are Phase 2).
 * [row]/[col] index `working.rows[row].keys[col]`; [fields] then descends into composite keys
 * (a CaseSelector branch, a Compass/Flick `primary`, or a direction slot that is a KeySlot).
 */
@Serializable
data class EditPath(val row: Int, val col: Int, val fields: List<String> = emptyList()) {
    fun child(field: String) = copy(fields = fields + field)
    fun encode(): String = Json.Default.encodeToString(serializer(), this)
    companion object {
        fun decode(s: String): EditPath = Json.Default.decodeFromString(serializer(), s)
    }
}

object KeyboardEditorSession {
    var sourceIdx by mutableStateOf(-1)
        private set
    var sourceLanguage by mutableStateOf("en_US")
        private set
    var working by mutableStateOf<V2Keyboard?>(null)
        private set
    var dirty by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    /** Load a custom layout into the session for editing. */
    fun load(idx: Int, layout: CustomLayout) {
        sourceIdx = idx
        sourceLanguage = layout.language
        dirty = false
        loadError = null
        working = try {
            parseKeyboardYamlString(layout.layoutYaml)
        } catch (e: Exception) {
            loadError = e.message ?: e::class.simpleName
            null
        }
    }

    fun clear() {
        sourceIdx = -1; working = null; dirty = false; loadError = null
    }

    /** Mark the working model as saved (called after a successful Apply). */
    fun markApplied() { dirty = false }

    /** Set/clear a Custom key-width fraction (0..1) at the keyboard level (overrideWidths). */
    fun setOverrideWidth(width: KeyWidth, fraction: Float?) {
        val kb = working ?: return
        val m = kb.overrideWidths.toMutableMap()
        if (fraction == null) m.remove(width) else m[width] = fraction
        working = kb.copy(overrideWidths = m)
        dirty = true
    }

    // ---- Phase 2: structural editing (base rows) ----

    private inline fun mutateRows(block: (MutableList<Row>) -> Unit) {
        val kb = working ?: return
        val rows = kb.rows.toMutableList()
        block(rows)
        working = kb.copy(rows = rows)
        dirty = true
    }

    /** Insert [key] at [col] in [row] (col is clamped; col == size appends). */
    fun insertKey(row: Int, col: Int, key: AbstractKey) = mutateRows { rows ->
        val r = rows.getOrNull(row) ?: return@mutateRows
        val ks = r.keys.toMutableList()
        ks.add(col.coerceIn(0, ks.size), key)
        rows[row] = r.withKeys(ks)
    }

    /** Remove the key at [col] in [row]. No-op if it is the row's only key (delete the row instead). */
    fun removeKey(row: Int, col: Int) = mutateRows { rows ->
        val r = rows.getOrNull(row) ?: return@mutateRows
        val ks = r.keys.toMutableList()
        if (col !in ks.indices || ks.size <= 1) return@mutateRows
        ks.removeAt(col)
        rows[row] = r.withKeys(ks)
    }

    /** Swap the key at [col] with its neighbour [delta] away (±1). Returns the key's new column. */
    fun moveKey(row: Int, col: Int, delta: Int): Int {
        val r = working?.rows?.getOrNull(row) ?: return col
        val target = col + delta
        if (col !in r.keys.indices || target !in r.keys.indices) return col
        mutateRows { rows ->
            val ks = rows[row].keys.toMutableList()
            val t = ks[col]; ks[col] = ks[target]; ks[target] = t
            rows[row] = rows[row].withKeys(ks)
        }
        return target
    }

    /** Insert a new single-key letters row after [afterRow]. */
    fun addRow(afterRow: Int) = mutateRows { rows ->
        rows.add((afterRow + 1).coerceIn(0, rows.size), Row(letters = listOf(BaseKey("a"))))
    }

    /** Remove [row]. No-op if it is the only row, or the only letters row. */
    fun removeRow(row: Int) = mutateRows { rows ->
        if (row !in rows.indices || rows.size <= 1) return@mutateRows
        if (rows[row].isLetterRow && rows.count { it.isLetterRow } <= 1) return@mutateRows
        rows.removeAt(row)
    }

    /** Swap [row] with its neighbour [delta] away (±1). */
    fun moveRow(row: Int, delta: Int) = mutateRows { rows ->
        val target = row + delta
        if (row !in rows.indices || target !in rows.indices) return@mutateRows
        val t = rows[row]; rows[row] = rows[target]; rows[target] = t
    }

    /** Replace the key at [path], rebuilding the working model. */
    fun replaceKey(path: EditPath, newKey: AbstractKey) {
        val kb = working ?: return
        val rows = kb.rows.toMutableList()
        val row = rows.getOrNull(path.row) ?: return
        val topKey = row.keys.getOrNull(path.col) ?: return
        val updatedTop = replaceDescend(topKey, path.fields, 0, newKey)
        rows[path.row] = row.withKeyAt(path.col, updatedTop)
        working = kb.copy(rows = rows)
        dirty = true
    }

    /** The key currently at [path], or null if the path no longer resolves. */
    fun keyAt(path: EditPath): AbstractKey? {
        val kb = working ?: return null
        var key = kb.rows.getOrNull(path.row)?.keys?.getOrNull(path.col) ?: return null
        for (f in path.fields) key = childKey(key, f) ?: return null
        return key
    }

    // ---- build a runtime keyboard from the working model (for the preview) ----
    // Built at the EXACT [widthPx] we render at, so the preview draws 1:1 (scale = 1) and a tap maps
    // to a key by identity — no scale offset.
    @OptIn(ExperimentalEncodingApi::class)
    fun buildPreview(context: Context, widthPx: Int): RuntimeKeyboard? {
        val kb = working ?: return null
        if (widthPx <= 0) return null
        val emitted = try { emitKeyboardYaml(kb) } catch (e: Exception) { return null }
        val loc = Locale.forLanguageTag(sourceLanguage.replace("_", "-"))
        val density = context.resources.displayMetrics.density
        val cl = CustomLayout(language = sourceLanguage, layoutYaml = emitted)
        val editorInfo = EditorInfo().apply {
            privateImeOptions = "org.futo.inputmethod.latin.ForceCustomLayoutYamlB64=" +
                Base64.encode(Json.Default.encodeToString(CustomLayout.serializer(), cl).toByteArray())
                    .replace("=", "_")
        }
        return try {
            KeyboardLayoutSetV2(
                context,
                KeyboardLayoutSetV2Params(
                    computedSize = RegularKeyboardSize(
                        width = widthPx,
                        height = (390.0 * density).toInt(),
                        padding = Rect()
                    ),
                    gap = 4.0f,
                    keyboardLayoutSet = "custompreview",
                    locale = loc,
                    editorInfo = editorInfo,
                    numberRow = false,
                    arrowRow = false,
                    alternativePeriodKey = false,
                    bottomActionKey = null,
                    useLocalNumbers = true,
                    numberRowMode = 0
                )
            ).getKeyboard(KeyboardLayoutElement(KeyboardLayoutKind.Alphabet0, KeyboardLayoutPage.Base))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Map a tapped runtime key (its `row`/`column`) back to a base-page [EditPath].
     *
     * The runtime keyboard is built with the number row disabled (these layouts set
     * `numberRowMode: AlwaysDisabled`), so the layout engine does NOT lay out the auto-added number
     * row — the top AUTHORED row is `key.row = 0`. So runtime row/column index the authored
     * `kb.rows` directly. (Using `getEffectiveRows(0)` here was wrong: it prepends a number row at
     * index 0, shifting every mapping up by one — the top row read as the phantom number row.)
     * A row/col beyond the authored rows (e.g. an auto-added bottom row, or auto shift/delete keys
     * appended to a row) has no authored counterpart → null (not editable yet).
     */
    fun pathForRuntimeKey(runtimeRow: Int, runtimeCol: Int): EditPath? {
        val kb = working ?: return null
        val row = kb.rows.getOrNull(runtimeRow) ?: return null
        if (row.keys.getOrNull(runtimeCol) == null) return null
        return EditPath(runtimeRow, runtimeCol)
    }
}

// ---- row helper ----

private fun Row.withKeyAt(col: Int, newKey: AbstractKey): Row = when {
    numbers != null -> copy(numbers = numbers!!.toMutableList().also { it[col] = newKey })
    bottom != null  -> copy(bottom  = bottom!!.toMutableList().also  { it[col] = newKey })
    else            -> copy(letters = letters!!.toMutableList().also { it[col] = newKey })
}

// Replace the whole key list, keeping the row's kind (numbers / letters / bottom).
private fun Row.withKeys(newKeys: List<AbstractKey>): Row = when {
    numbers != null -> copy(numbers = newKeys)
    bottom != null  -> copy(bottom  = newKeys)
    else            -> copy(letters = newKeys)
}

// ---- composite-key field navigation ----

val CASE_FIELDS = listOf("normal", "shifted", "shiftedManually", "shiftLocked", "symbols", "symbolsShifted")
val COMPASS_DIRS = listOf("up", "down", "left", "right", "upLeft", "upRight", "downLeft", "downRight")
val CLUSTER_DIRS = listOf("up", "down", "upLeft", "upRight", "downLeft", "downRight")
val COLUMN_DIRS  = listOf("left", "right", "upLeft", "upRight", "downLeft", "downRight")
val FLICK_DIRS   = listOf("up", "down", "left", "right", "upLeft", "upRight", "downLeft", "downRight")

/** The child KEY reachable at [field] (a case branch, a compass/flick primary, or a KeySlot's key). */
fun childKey(parent: AbstractKey, field: String): AbstractKey? = when (parent) {
    is CaseSelector -> when (field) {
        "normal" -> parent.normal
        "shifted" -> parent.shifted
        "shiftedManually" -> parent.shiftedManually
        "shiftLocked" -> parent.shiftLocked
        "symbols" -> parent.symbols
        "symbolsShifted" -> parent.symbolsShifted
        else -> null
    }
    is CompassKey -> if (field == "primary") parent.primary else (compassSlot(parent, field) as? KeySlot)?.key
    is ClusterKey -> (clusterSlot(parent, field) as? KeySlot)?.key
    is ColumnKey  -> (columnSlot(parent, field) as? KeySlot)?.key
    is FlickKey   -> if (field == "primary") parent.primary else flickDir(parent, field)
    else -> null
}

/** The raw slot at a compass/cluster/column direction (may be a ModSlot, which is not a key). */
fun rawSlot(parent: AbstractKey, field: String): CompassSlot? = when (parent) {
    is CompassKey -> compassSlot(parent, field)
    is ClusterKey -> clusterSlot(parent, field)
    is ColumnKey  -> columnSlot(parent, field)
    else -> null
}

private fun compassSlot(k: CompassKey, f: String): CompassSlot? = when (f) {
    "up" -> k.up; "down" -> k.down; "left" -> k.left; "right" -> k.right
    "upLeft" -> k.upLeft; "upRight" -> k.upRight; "downLeft" -> k.downLeft; "downRight" -> k.downRight
    else -> null
}
private fun clusterSlot(k: ClusterKey, f: String): CompassSlot? = when (f) {
    "up" -> k.up; "down" -> k.down
    "upLeft" -> k.upLeft; "upRight" -> k.upRight; "downLeft" -> k.downLeft; "downRight" -> k.downRight
    else -> null
}
private fun columnSlot(k: ColumnKey, f: String): CompassSlot? = when (f) {
    "left" -> k.left; "right" -> k.right
    "upLeft" -> k.upLeft; "upRight" -> k.upRight; "downLeft" -> k.downLeft; "downRight" -> k.downRight
    else -> null
}
private fun flickDir(k: FlickKey, f: String): AbstractKey? = when (f) {
    "up" -> k.up; "down" -> k.down; "left" -> k.left; "right" -> k.right
    "upLeft" -> k.upLeft; "upRight" -> k.upRight; "downLeft" -> k.downLeft; "downRight" -> k.downRight
    else -> null
}

// ---- replacement ----

private fun replaceDescend(parent: AbstractKey, fields: List<String>, i: Int, newKey: AbstractKey): AbstractKey {
    if (i >= fields.size) return newKey
    val field = fields[i]
    val child = childKey(parent, field) ?: return parent
    val updatedChild = replaceDescend(child, fields, i + 1, newKey)
    return replaceChildKey(parent, field, updatedChild)
}

/** Return a copy of [parent] with the KEY child at [field] replaced (slots are wrapped in KeySlot). */
fun replaceChildKey(parent: AbstractKey, field: String, newChild: AbstractKey): AbstractKey = when (parent) {
    is CaseSelector -> when (field) {
        "normal" -> parent.copy(normal = newChild)
        "shifted" -> parent.copy(shifted = newChild)
        "shiftedManually" -> parent.copy(shiftedManually = newChild)
        "shiftLocked" -> parent.copy(shiftLocked = newChild)
        "symbols" -> parent.copy(symbols = newChild)
        "symbolsShifted" -> parent.copy(symbolsShifted = newChild)
        else -> parent
    }
    is CompassKey -> if (field == "primary") parent.copy(primary = newChild)
                     else replaceCompassSlot(parent, field, KeySlot(newChild))
    is ClusterKey -> replaceClusterSlot(parent, field, KeySlot(newChild))
    is ColumnKey  -> replaceColumnSlot(parent, field, KeySlot(newChild))
    is FlickKey   -> if (field == "primary") parent.copy(primary = newChild) else replaceFlickDir(parent, field, newChild)
    else -> parent
}

/** Set/clear the raw slot at a compass/cluster/column direction (for inline ModSlot/empty editing). */
fun replaceSlot(parent: AbstractKey, field: String, slot: CompassSlot?): AbstractKey = when (parent) {
    is CompassKey -> replaceCompassSlot(parent, field, slot)
    is ClusterKey -> replaceClusterSlot(parent, field, slot)
    is ColumnKey  -> replaceColumnSlot(parent, field, slot)
    else -> parent
}

private fun replaceCompassSlot(k: CompassKey, f: String, s: CompassSlot?): CompassKey = when (f) {
    "up" -> k.copy(up = s); "down" -> k.copy(down = s); "left" -> k.copy(left = s); "right" -> k.copy(right = s)
    "upLeft" -> k.copy(upLeft = s); "upRight" -> k.copy(upRight = s)
    "downLeft" -> k.copy(downLeft = s); "downRight" -> k.copy(downRight = s)
    else -> k
}
private fun replaceClusterSlot(k: ClusterKey, f: String, s: CompassSlot?): ClusterKey = when (f) {
    "up" -> k.copy(up = s); "down" -> k.copy(down = s)
    "upLeft" -> k.copy(upLeft = s); "upRight" -> k.copy(upRight = s)
    "downLeft" -> k.copy(downLeft = s); "downRight" -> k.copy(downRight = s)
    else -> k
}
private fun replaceColumnSlot(k: ColumnKey, f: String, s: CompassSlot?): ColumnKey = when (f) {
    "left" -> k.copy(left = s); "right" -> k.copy(right = s)
    "upLeft" -> k.copy(upLeft = s); "upRight" -> k.copy(upRight = s)
    "downLeft" -> k.copy(downLeft = s); "downRight" -> k.copy(downRight = s)
    else -> k
}
private fun replaceFlickDir(k: FlickKey, f: String, v: AbstractKey?): FlickKey = when (f) {
    "up" -> k.copy(up = v); "down" -> k.copy(down = v); "left" -> k.copy(left = v); "right" -> k.copy(right = v)
    "upLeft" -> k.copy(upLeft = v); "upRight" -> k.copy(upRight = v)
    "downLeft" -> k.copy(downLeft = v); "downRight" -> k.copy(downRight = v)
    else -> k
}

// ---- type identity + a short human description for list rows ----

fun keyTypeName(key: AbstractKey?): String = when (key) {
    null -> "(empty)"
    is BaseKey -> "base"
    is CaseSelector -> "case"
    is CompassKey -> "compass"
    is ClusterKey -> "cluster"
    is ColumnKey -> "column"
    is MacroKey -> "macro"
    is ChordKey -> "chord"
    is CycleKey -> "cycle"
    is FlickKey -> "flick"
    is GapKey -> "gap"
    else -> key::class.simpleName ?: "?"
}

fun keySummary(key: AbstractKey?): String = when (key) {
    null -> "(empty)"
    is BaseKey -> key.spec
    is CaseSelector -> "case: ${keySummary(key.normal)}"
    is CompassKey -> "compass: ${keySummary(key.primary)}"
    is ClusterKey -> "cluster: ${key.main}"
    is ColumnKey -> "column: ${key.main}"
    is MacroKey -> "macro: ${key.label ?: key.text}"
    is ChordKey -> "chord: ${key.label ?: key.keys}"
    is CycleKey -> "cycle: ${key.taps.items.joinToString("")}"
    is FlickKey -> "flick: ${keySummary(key.primary)}"
    is GapKey -> "gap"
    else -> keyTypeName(key)
}

fun slotSummary(slot: CompassSlot?): String = when (slot) {
    null -> "(empty)"
    is KeySlot -> keySummary(slot.key)
    is ModSlot -> "mod ${slot.mod}${slot.on?.let { "+$it" } ?: ""}${slot.label?.let { " '$it'" } ?: ""}"
}
