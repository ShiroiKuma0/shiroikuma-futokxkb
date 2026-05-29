package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.keyboard.Keyboard as RuntimeKeyboard
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

// A CaseSelector (e.g. the shift key) has no attributes of its own — width/style live on its per-state
// branches. So for editing we surface a UNIFIED attributes editor: read the `normal` branch's
// attributes and write the edited set to every attribute-bearing branch at once.
private fun applyAttrsIfPossible(k: AbstractKey, a: KeyAttributes): AbstractKey =
    if (keyAttributes(k) != null) withAttributes(k, a) else k

private fun caseWithUnifiedAttributes(cs: CaseSelector, a: KeyAttributes): CaseSelector = cs.copy(
    normal = applyAttrsIfPossible(cs.normal, a),
    shifted = applyAttrsIfPossible(cs.shifted, a),
    shiftedManually = applyAttrsIfPossible(cs.shiftedManually, a),
    shiftLocked = applyAttrsIfPossible(cs.shiftLocked, a),
    symbols = applyAttrsIfPossible(cs.symbols, a),
    symbolsShifted = applyAttrsIfPossible(cs.symbolsShifted, a)
)

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

// A size override as a PERCENTAGE of the theme size (the stored value is a multiplier, 1.0 = 100%).
// Off (Switch) = null = use the theme/geometry size. On = a slider 20%–300%.
@Composable
private fun SizePercentRow(label: String, value: Float?, onChange: (Float?) -> Unit) {
    Column(Modifier.padding(16.dp, 4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                label + (value?.let { " — ${(it * 100).roundToInt()}%" } ?: " — theme"),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            androidx.compose.material3.Switch(
                checked = value != null,
                onCheckedChange = { on -> onChange(if (on) (value ?: 1f) else null) }
            )
        }
        if (value != null) {
            androidx.compose.material3.Slider(
                value = (value * 100f).coerceIn(20f, 300f),
                onValueChange = { onChange(it / 100f) },
                valueRange = 20f..300f
            )
        }
    }
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
    val context = LocalContext.current
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

        val pageLabel = if (path.page < 0) "base" else "alt${path.page}"
        val breadcrumb = "$pageLabel · row ${path.row}, col ${path.col}" + path.fields.joinToString("") { " › $it" }
        Text(breadcrumb, style = mono, modifier = Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)

        PickerRow("Type", TYPE_OPTIONS, keyTypeName(key), { it }) { t ->
            if (t != keyTypeName(key)) put(convertTo(t, key))
        }
        HorizontalDivider(Modifier.padding(16.dp, 8.dp))

        // Position in row (only for a top-level row key, not a nested case branch / slot).
        if (path.fields.isEmpty()) {
            val rowKeyCount = KeyboardEditorSession.pageRows(path.page).getOrNull(path.row)?.keys?.size ?: 0
            Text("Position in row", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
            Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
                OutlinedButton(
                    onClick = { KeyboardEditorSession.moveKey(path.page, path.row, path.col, -1); navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) { Text("◀ Move") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { KeyboardEditorSession.moveKey(path.page, path.row, path.col, +1); navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) { Text("Move ▶") }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
                OutlinedButton(
                    onClick = {
                        KeyboardEditorSession.insertKey(path.page, path.row, path.col, BaseKey(spec = "a"))
                        navController.navigate(Route.KeyEdit(EditPath(path.row, path.col, page = path.page).encode()))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Insert ◀") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = {
                        KeyboardEditorSession.insertKey(path.page, path.row, path.col + 1, BaseKey(spec = "a"))
                        navController.navigate(Route.KeyEdit(EditPath(path.row, path.col + 1, page = path.page).encode()))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Insert ▶") }
            }
            OutlinedButton(
                onClick = {
                    KeyboardEditorSession.duplicateKey(path.page, path.row, path.col)
                    navController.navigate(Route.KeyEdit(EditPath(path.row, path.col + 1, page = path.page).encode()))
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 2.dp)
            ) { Text("Duplicate key") }
            OutlinedButton(
                onClick = { KeyboardEditorSession.removeKey(path.page, path.row, path.col); navController.popBackStack() },
                enabled = rowKeyCount > 1,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete key") }
            HorizontalDivider(Modifier.padding(16.dp, 8.dp))
        }

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

        // Attributes. A CaseSelector has none of its own, so edit a unified set applied to all its
        // shift-state branches (read from `normal`); every other key edits its own attributes.
        val attrsForEdit: Pair<KeyAttributes, (KeyAttributes) -> AbstractKey>? = when (key) {
            is CaseSelector -> keyAttributes(key.normal)?.let { it to { a: KeyAttributes -> caseWithUnifiedAttributes(key, a) } }
            else -> keyAttributes(key)?.let { it to { a: KeyAttributes -> withAttributes(key, a) } }
        }
        attrsForEdit?.let { (attrs, build) ->
            HorizontalDivider(Modifier.padding(16.dp, 8.dp))
            Text("Appearance (live)" + if (key is CaseSelector) " — applied to all shift states" else "",
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))

            // Single-key live preview at REAL size: build the full page keyboard (at the device
            // keyboard width), find this key, and crop to it — so its true width:height proportions
            // show 1:1. Rebuilds when the key changes, so colour/size edits show live.
            var pageKb by remember { mutableStateOf<RuntimeKeyboard?>(null) }
            LaunchedEffect(key, path.page) {
                pageKb = withContext(Dispatchers.Default) {
                    KeyboardEditorSession.buildPreview(context, context.resources.displayMetrics.widthPixels, path.page)
                }
            }
            val tgtKey = pageKb?.sortedKeys?.firstOrNull { it.row == path.row && it.column == path.col }
            if (tgtKey != null) {
                Box(Modifier.fillMaxWidth().padding(16.dp, 8.dp), contentAlignment = Alignment.Center) {
                    SingleKeyCrop(pageKb!!, tgtKey)
                }
            }

            ColorSetting("Text colour", attrs.color, 0xFFFFFFFF.toInt()) { put(build(attrs.copy(color = it))) }
            if (attrs.color != null) {
                OutlinedButton(
                    onClick = { put(build(attrs.copy(color = null))) },
                    modifier = Modifier.padding(16.dp, 2.dp)
                ) { Text("Use theme colour") }
            }
            SizePercentRow("Font size", attrs.fontScale) { put(build(attrs.copy(fontScale = it))) }
            SizePercentRow("Secondary (hint) size", attrs.hintScale) { put(build(attrs.copy(hintScale = it))) }

            ColorSetting("Background colour", attrs.backgroundColor, 0xFF888888.toInt()) { put(build(attrs.copy(backgroundColor = it))) }
            if (attrs.backgroundColor != null) {
                OutlinedButton(onClick = { put(build(attrs.copy(backgroundColor = null))) }, modifier = Modifier.padding(16.dp, 2.dp)) { Text("Use theme background") }
            }
            ColorSetting("Border colour", attrs.borderColor, 0xFF000000.toInt()) { put(build(attrs.copy(borderColor = it))) }
            if (attrs.borderColor != null) {
                OutlinedButton(onClick = { put(build(attrs.copy(borderColor = null))) }, modifier = Modifier.padding(16.dp, 2.dp)) { Text("Use theme border") }
            }

            HorizontalDivider(Modifier.padding(16.dp, 8.dp))
            Text(
                if (key is CaseSelector) "Attributes (applied to all shift states)"
                else "Attributes (blank/inherit = use row/keyboard default)",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(16.dp, 4.dp)
            )
            AttributesEditor(seed, attrs, working.overrideWidths) { newAttrs -> put(build(newAttrs)) }
        }

        Spacer(Modifier.height(32.dp))
    }
}
