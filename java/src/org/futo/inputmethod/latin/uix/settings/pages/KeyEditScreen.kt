package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.Route
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.v2keyboard.AbstractKey
import org.futo.inputmethod.v2keyboard.ActionKey
import org.futo.inputmethod.v2keyboard.BaseKey
import org.futo.inputmethod.v2keyboard.CaseSelector
import org.futo.inputmethod.v2keyboard.ChordKey
import org.futo.inputmethod.v2keyboard.ClusterKey
import org.futo.inputmethod.v2keyboard.ColumnKey
import org.futo.inputmethod.v2keyboard.CompassKey
import org.futo.inputmethod.v2keyboard.ContextualKey
import org.futo.inputmethod.v2keyboard.CycleKey
import org.futo.inputmethod.v2keyboard.EnterKey
import org.futo.inputmethod.v2keyboard.FlickKey
import org.futo.inputmethod.v2keyboard.GapKey
import org.futo.inputmethod.v2keyboard.KeyAttributes
import org.futo.inputmethod.v2keyboard.KeySlot
import org.futo.inputmethod.v2keyboard.KeyVisualStyle
import org.futo.inputmethod.v2keyboard.KeyWidth
import org.futo.inputmethod.v2keyboard.MacroKey
import org.futo.inputmethod.v2keyboard.ModSlot
import org.futo.inputmethod.v2keyboard.MoreKeyMode
import org.futo.inputmethod.v2keyboard.OptionalZWNJKey
import org.futo.inputmethod.v2keyboard.Taps
import kotlin.math.roundToInt

// kxkb: visual layout editor — per-key edit sub-screen. Reads the key at an EditPath from
// KeyboardEditorSession, edits its fields/attributes/type, and writes back. Nested keys (case
// branches, compass primary, KeySlot directions) navigate to deeper KeyEdit screens. Phase 1.

private val mono = TextStyle(fontFamily = FontFamily.Monospace)

// ---- small form widgets ----

@Composable
private fun EditTextRow(label: String, initial: String, seedKey: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    var v by remember(seedKey) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp, 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = v,
            onValueChange = { v = it; onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = mono,
            singleLine = singleLine
        )
    }
}

@Composable
private fun <T> PickerRow(label: String, options: List<T>, selected: T, render: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp, 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(render(selected), style = mono)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(render(opt), style = mono) }, onClick = { onSelect(opt); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun TriBoolRow(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    PickerRow(label, listOf<Boolean?>(null, true, false), value, { it?.toString() ?: "(inherit)" }, onChange)
}

// ---- attributes ----

// Returns the editable attributes for a key — and for the types whose `attributes` is nullable
// (compass/cluster/column/flick) substitutes an empty KeyAttributes() when null, so the editor still
// shows the full attribute set (width, etc.) for them. CaseSelector has no attributes.
private fun keyAttributes(k: AbstractKey): KeyAttributes? = when (k) {
    is BaseKey -> k.attributes
    is MacroKey -> k.attributes
    is ChordKey -> k.attributes
    is CycleKey -> k.attributes
    is GapKey -> k.attributes
    is CompassKey -> k.attributes ?: KeyAttributes()
    is ClusterKey -> k.attributes ?: KeyAttributes()
    is ColumnKey -> k.attributes ?: KeyAttributes()
    is FlickKey -> k.attributes ?: KeyAttributes()
    is EnterKey -> k.attributes
    is ActionKey -> k.attributes
    is ContextualKey -> k.attributes
    is OptionalZWNJKey -> k.attributes
    else -> null
}

private fun withAttributes(k: AbstractKey, a: KeyAttributes): AbstractKey = when (k) {
    is BaseKey -> k.copy(attributes = a)
    is MacroKey -> k.copy(attributes = a)
    is ChordKey -> k.copy(attributes = a)
    is CycleKey -> k.copy(attributes = a)
    is GapKey -> k.copy(attributes = a)
    is CompassKey -> k.copy(attributes = a)
    is ClusterKey -> k.copy(attributes = a)
    is ColumnKey -> k.copy(attributes = a)
    is FlickKey -> k.copy(attributes = a)
    is EnterKey -> k.copy(attributes = a)
    is ActionKey -> k.copy(attributes = a)
    is ContextualKey -> k.copy(attributes = a)
    is OptionalZWNJKey -> k.copy(attributes = a)
    else -> k
}

// A meaningful width label rather than the bare token: Regular/Functional/Grow read plainly, and a
// Custom token shows its resolved width (from the layout's overrideWidths). Custom values are edited
// in the "Custom key widths" section on the main editor screen.
private fun widthLabel(w: KeyWidth?, ow: Map<KeyWidth, Float>): String = when (w) {
    null -> "(inherit)"
    KeyWidth.Regular -> "Regular (1 key)"
    KeyWidth.FunctionalKey -> "Functional"
    KeyWidth.Grow -> "Grow (fills the row)"
    else -> ow[w]?.let { "${w.name} — ${(it * 100).roundToInt()}% of keyboard" } ?: "${w.name} — (set its % below)"
}

@Composable
private fun AttributesEditor(seedKey: String, attrs: KeyAttributes, overrideWidths: Map<KeyWidth, Float>, onChange: (KeyAttributes) -> Unit) {
    PickerRow("width", listOf<KeyWidth?>(null) + KeyWidth.entries, attrs.width, { widthLabel(it, overrideWidths) }) { onChange(attrs.copy(width = it)) }
    PickerRow("style", listOf<KeyVisualStyle?>(null) + KeyVisualStyle.entries, attrs.style, { it?.name ?: "(inherit)" }) { onChange(attrs.copy(style = it)) }
    PickerRow("moreKeyMode", listOf<MoreKeyMode?>(null) + MoreKeyMode.entries, attrs.moreKeyMode, { it?.name ?: "(inherit)" }) { onChange(attrs.copy(moreKeyMode = it)) }
    EditTextRow("heightRows (blank = inherit)", attrs.heightRows?.toString() ?: "", seedKey + "#hr") { onChange(attrs.copy(heightRows = it.trim().toIntOrNull())) }
    TriBoolRow("showPopup", attrs.showPopup) { onChange(attrs.copy(showPopup = it)) }
    TriBoolRow("longPressEnabled", attrs.longPressEnabled) { onChange(attrs.copy(longPressEnabled = it)) }
    TriBoolRow("repeatableEnabled", attrs.repeatableEnabled) { onChange(attrs.copy(repeatableEnabled = it)) }
    TriBoolRow("anchored", attrs.anchored) { onChange(attrs.copy(anchored = it)) }
    TriBoolRow("useKeySpecShortcut", attrs.useKeySpecShortcut) { onChange(attrs.copy(useKeySpecShortcut = it)) }
    TriBoolRow("shiftable", attrs.shiftable) { onChange(attrs.copy(shiftable = it)) }
    TriBoolRow("fastMoreKeys", attrs.fastMoreKeys) { onChange(attrs.copy(fastMoreKeys = it)) }
}

// ---- change type ----

private val TYPE_OPTIONS = listOf("base", "compass", "cluster", "column", "macro", "chord", "cycle", "case", "gap")

private fun seedSpec(k: AbstractKey?): String = when (k) {
    is BaseKey -> k.spec
    is ClusterKey -> k.main
    is ColumnKey -> k.main
    is MacroKey -> k.text
    is CompassKey -> (k.primary as? BaseKey)?.spec ?: ""
    else -> ""
}

private fun convertTo(type: String, current: AbstractKey?): AbstractKey {
    val s = seedSpec(current)
    return when (type) {
        "base" -> BaseKey(spec = s)
        "compass" -> CompassKey(primary = BaseKey(spec = s.ifEmpty { "a" }))
        "cluster" -> ClusterKey(main = s.ifEmpty { "abc" })
        "column" -> ColumnKey(main = s.ifEmpty { "abc" })
        "macro" -> MacroKey(text = s)
        "chord" -> ChordKey(keys = "C-a")
        "cycle" -> CycleKey(taps = Taps(if (s.isNotEmpty()) s.map { it.toString() } else listOf("a", "b")))
        "case" -> CaseSelector(normal = (current ?: BaseKey(spec = s)))
        "gap" -> GapKey()
        else -> current ?: BaseKey(spec = "")
    }
}

// ---- child / slot rows ----

@Composable
private fun ChildKeyRow(label: String, child: AbstractKey?, onEdit: () -> Unit, onAdd: (() -> Unit)? = null, onClear: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(keySummary(child), style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (child != null) {
            OutlinedButton(onClick = onEdit) { Text("Edit") }
            onClear?.let { Spacer(Modifier.size(6.dp)); OutlinedButton(onClick = it) { Text("✕") } }
        } else {
            onAdd?.let { OutlinedButton(onClick = it) { Text("Add") } }
        }
    }
}

@Composable
private fun DirectionRow(
    dir: String,
    parent: AbstractKey,
    path: EditPath,
    nav: NavHostController
) {
    val slot = rawSlot(parent, dir)
    Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(dir, style = MaterialTheme.typography.labelMedium)
            Text(slotSummary(slot), style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (slot) {
            is KeySlot -> {
                OutlinedButton(onClick = { nav.navigate(Route.KeyEdit(path.child(dir).encode())) }) { Text("Edit") }
                Spacer(Modifier.size(6.dp))
                OutlinedButton(onClick = { KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, null)) }) { Text("✕") }
            }
            is ModSlot -> {
                OutlinedButton(onClick = { KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, null)) }) { Text("✕") }
            }
            null -> {
                OutlinedButton(onClick = {
                    KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, KeySlot(BaseKey(spec = ""))))
                    nav.navigate(Route.KeyEdit(path.child(dir).encode()))
                }) { Text("Add key") }
            }
        }
    }
    // Inline ModSlot editor (mod / on / label)
    if (slot is ModSlot) {
        EditTextRow("  $dir · mod (ctrl/alt/shift/super)", slot.mod, path.encode() + dir + "mod") {
            KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, slot.copy(mod = it)))
        }
        EditTextRow("  $dir · on (base char, blank = primary)", slot.on ?: "", path.encode() + dir + "on") {
            KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, slot.copy(on = it.ifEmpty { null })))
        }
        EditTextRow("  $dir · label", slot.label ?: "", path.encode() + dir + "lbl") {
            KeyboardEditorSession.replaceKey(path, replaceSlot(parent, dir, slot.copy(label = it.ifEmpty { null })))
        }
    }
}

// ---- screen ----

@Composable
fun KeyEditScreen(navController: NavHostController = rememberNavController(), pathStr: String) {
    val path = remember(pathStr) { EditPath.decode(pathStr) }
    // subscribe to working-model changes
    val working = KeyboardEditorSession.working
    val key = KeyboardEditorSession.keyAt(path)
    val seed = pathStr

    fun put(newKey: AbstractKey) = KeyboardEditorSession.replaceKey(path, newKey)

    ScrollableList {
        ScreenTitle("Edit key", showBack = true, navController)

        if (working == null || key == null) {
            Text("This key is no longer available.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            return@ScrollableList
        }

        val breadcrumb = "row ${path.row}, col ${path.col}" + path.fields.joinToString("") { " › $it" }
        Text(breadcrumb, style = mono, modifier = Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

        PickerRow("Type", TYPE_OPTIONS, keyTypeName(key), { it }) { t ->
            if (t != keyTypeName(key)) put(convertTo(t, key))
        }
        HorizontalDivider(Modifier.padding(16.dp, 8.dp))

        // Type-specific fields
        when (key) {
            is BaseKey -> {
                EditTextRow("spec  (label|!code/… or !icon/…|…)", key.spec, seed + "spec") { put(key.copy(spec = it)) }
                EditTextRow("hint (blank = none)", key.hint ?: "", seed + "hint") { put(key.copy(hint = it.ifEmpty { null })) }
                EditTextRow("code override (int, blank = none)", key.code?.toString() ?: "", seed + "code") { put(key.copy(code = it.trim().toIntOrNull())) }
                EditTextRow("moreKeys (one per line)", key.moreKeys.joinToString("\n"), seed + "mk", singleLine = false) {
                    put(key.copy(moreKeys = it.split("\n").map { s -> s.trim() }.filter { s -> s.isNotEmpty() }))
                }
            }
            is MacroKey -> {
                EditTextRow("text (typed literally)", key.text, seed + "text", singleLine = false) { put(key.copy(text = it)) }
                EditTextRow("label (blank = text)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
            }
            is ChordKey -> {
                EditTextRow("keys  (e.g. C-x C-s)", key.keys, seed + "keys") { put(key.copy(keys = it)) }
                EditTextRow("label (blank = keys)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
            }
            is CycleKey -> {
                EditTextRow("taps (one per line)", key.taps.items.joinToString("\n"), seed + "taps", singleLine = false) {
                    put(key.copy(taps = Taps(it.split("\n").map { s -> s }.filter { s -> s.isNotEmpty() })))
                }
                EditTextRow("label (blank = joined taps)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
            }
            is ClusterKey -> {
                EditTextRow("main band (e.g. abc)", key.main, seed + "main") { put(key.copy(main = it)) }
                EditTextRow("label (blank = main)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
                EditTextRow("icon (blank = none)", key.icon ?: "", seed + "icon") { put(key.copy(icon = it.ifEmpty { null })) }
                HorizontalDivider(Modifier.padding(16.dp, 8.dp))
                Text("Slide directions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
                CLUSTER_DIRS.forEach { DirectionRow(it, key, path, navController) }
            }
            is ColumnKey -> {
                EditTextRow("main band (stacks vertically)", key.main, seed + "main") { put(key.copy(main = it)) }
                EditTextRow("label (blank = main)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
                EditTextRow("icon (blank = none)", key.icon ?: "", seed + "icon") { put(key.copy(icon = it.ifEmpty { null })) }
                HorizontalDivider(Modifier.padding(16.dp, 8.dp))
                Text("Side directions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
                COLUMN_DIRS.forEach { DirectionRow(it, key, path, navController) }
            }
            is CompassKey -> {
                EditTextRow("slide (compact string, optional)", key.slide ?: "", seed + "slide") { put(key.copy(slide = it.ifEmpty { null })) }
                EditTextRow("label (blank = primary)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
                EditTextRow("icon (blank = none)", key.icon ?: "", seed + "icon") { put(key.copy(icon = it.ifEmpty { null })) }
                HorizontalDivider(Modifier.padding(16.dp, 8.dp))
                ChildKeyRow("primary (tap)", key.primary, onEdit = { navController.navigate(Route.KeyEdit(path.child("primary").encode())) })
                Text("Slide directions", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
                COMPASS_DIRS.forEach { DirectionRow(it, key, path, navController) }
            }
            is CaseSelector -> {
                Text("Shift states (each is its own key)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
                CASE_FIELDS.forEach { field ->
                    ChildKeyRow("$field", childKey(key, field), onEdit = { navController.navigate(Route.KeyEdit(path.child(field).encode())) })
                }
            }
            is FlickKey -> {
                EditTextRow("label (blank = primary)", key.label ?: "", seed + "lbl") { put(key.copy(label = it.ifEmpty { null })) }
                ChildKeyRow("primary", key.primary, onEdit = { navController.navigate(Route.KeyEdit(path.child("primary").encode())) })
                FLICK_DIRS.forEach { dir ->
                    val child = childKey(key, dir)
                    ChildKeyRow(dir, child,
                        onEdit = { navController.navigate(Route.KeyEdit(path.child(dir).encode())) },
                        onAdd = {
                            put(replaceChildKey(key, dir, BaseKey(spec = "")))
                            navController.navigate(Route.KeyEdit(path.child(dir).encode()))
                        })
                }
            }
            is GapKey -> {
                Text("An empty gap (reserves a cell).", modifier = Modifier.padding(16.dp, 4.dp))
            }
            else -> {
                Text("Type '${keyTypeName(key)}' has no extra fields here.", modifier = Modifier.padding(16.dp, 4.dp))
            }
        }

        // Attributes (not applicable to CaseSelector)
        keyAttributes(key)?.let { attrs ->
            HorizontalDivider(Modifier.padding(16.dp, 8.dp))
            Text("Attributes (blank/inherit = use row/keyboard default)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
            AttributesEditor(seed, attrs, working.overrideWidths) { newAttrs -> put(withAttributes(key, newAttrs)) }
        }

        Spacer(Modifier.height(32.dp))
    }
}
