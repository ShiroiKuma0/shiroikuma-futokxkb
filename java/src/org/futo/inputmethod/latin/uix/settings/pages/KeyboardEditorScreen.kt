package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.keyboard.Key
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardView
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.uix.DynamicThemeProvider
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.Route
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.findActivity
import org.futo.inputmethod.v2keyboard.KeyWidth
import org.futo.inputmethod.v2keyboard.emitKeyboardYaml

// kxkb: visual layout editor — main screen. Pick a custom layout, see a live preview, tap a key to
// edit it (dedicated sub-screen), then Apply (writes the custom layout + switches to it so the live
// keyboard refreshes) or Export the YAML. Phase 1; see [[keyboard-editor-plan]].

@Composable
internal fun EditorPreview(keyboard: Keyboard, onTapKey: (Key) -> Unit) {
    val density = LocalDensity.current
    // The keyboard is built at the container width, so it renders 1:1 (no scale). Size the view to its
    // native px so a tap offset (px) maps to keyboard coordinates by identity.
    val wDp = with(density) { keyboard.mId.mWidth.toDp() }
    val hDp = with(density) { keyboard.mOccupiedHeight.toDp() }
    val context = LocalContext.current
    val ctx = remember { ContextThemeWrapper(context, R.style.KeyboardTheme_LXX_Light) }

    Box(modifier = Modifier.size(wDp, hDp)) {
        key(DynamicThemeProvider.obtainFromContext(ctx)) {
            AndroidView(
                factory = { KeyboardView(ctx, null).apply { setKeyboard(keyboard) } },
                update = { it.setKeyboard(keyboard) },
                modifier = Modifier.size(wDp, hDp)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(keyboard) {
                    detectTapGestures { ofs ->
                        val kx = ofs.x.toInt()
                        val ky = ofs.y.toInt()
                        // Hit-test against VISUAL bounds (not key.hitBox, which is inflated by the gap
                        // and overlaps neighbouring rows — that caused taps to select the row above).
                        keyboard.sortedKeys.firstOrNull { k ->
                            kx >= k.x && kx < k.x + k.width && ky >= k.y && ky < k.y + k.height
                        }?.let(onTapKey)
                    }
                }
        )
    }
}

private fun applyLayout(context: Context, idx: Int) {
    val kb = KeyboardEditorSession.working ?: return
    val yaml = emitKeyboardYaml(kb)
    val list = getCustomLayouts(context).toMutableList()
    if (idx !in list.indices) return
    list[idx] = CustomLayout(language = KeyboardEditorSession.sourceLanguage, layoutYaml = yaml)
    updateCustomLayoutsAndSyncSubtypes(context, list)
    // Re-activate this custom layout so the live keyboard reloads with the edits.
    Subtypes.switchToSubtypeString(
        context,
        Subtypes.subtypeToString(Subtypes.makeSubtype(KeyboardEditorSession.sourceLanguage, "custom$idx"))
    )
}

@Composable
fun KeyboardEditorScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    var customLayouts by remember { mutableStateOf(getCustomLayouts(context)) }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    var showApplyAsNew by remember { mutableStateOf(false) }

    // "Apply as a new layout": save the current working model under a new name/language as a NEW
    // custom layout (same path as "Create new layout"), then open that copy in the editor.
    fun applyAsNew(name: String, language: String) {
        val kb = KeyboardEditorSession.working ?: return
        val yaml = emitKeyboardYaml(kb.copy(name = name))
        val newCl = CustomLayout(language = language, layoutYaml = yaml)
        val list = getCustomLayouts(context) + newCl
        updateCustomLayoutsAndSyncSubtypes(context, list)
        customLayouts = list
        KeyboardEditorSession.load(list.size - 1, newCl)
    }

    // Subscribe to working-model changes (recompose + rebuild preview on every edit).
    val working = KeyboardEditorSession.working
    val dirty = KeyboardEditorSession.dirty
    val sourceIdx = KeyboardEditorSession.sourceIdx
    val page = KeyboardEditorSession.page

    var preview by remember { mutableStateOf<Keyboard?>(null) }
    var previewWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(working, previewWidthPx, page) {
        preview = if (working != null && previewWidthPx > 0)
            withContext(Dispatchers.Default) { KeyboardEditorSession.buildPreview(context, previewWidthPx, page) }
        else null
    }

    ScrollableList {
        ScreenTitle("Keyboard editor", showBack = true, navController)

        // kxkb: quick exit straight back to the keyboard (this page is usually opened from the
        // space-swipe menu while typing), matching the Keyboard UI / Custom Layouts screens.
        OutlinedButton(
            onClick = { context.findActivity()?.finish() },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) { Text("Close Keyboard editor") }

        if (customLayouts.isEmpty()) {
            Text(
                "No custom layouts to edit. Import one via Developer → Custom layouts first.",
                modifier = Modifier.padding(16.dp)
            )
            return@ScrollableList
        }

        Text("Layout to edit", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp))
        customLayouts.forEachIndexed { i, cl ->
            Button(
                onClick = { KeyboardEditorSession.load(i, cl) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 2.dp),
                colors = if (i == sourceIdx) ButtonDefaults.buttonColors()
                         else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("[$i] ${cl.name}", style = mono)
            }
        }

        KeyboardEditorSession.loadError?.let {
            Text("Parse error: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        if (working != null) {
            Spacer(Modifier.height(8.dp))

            // Page selector: base + each alt page (alt0 = sym, alt1 = altGr). Editing applies to the
            // selected page; the preview, Rows section and tap-to-edit all follow it.
            val pages = listOf(-1) + (0 until KeyboardEditorSession.altPageCount())
            if (pages.size > 1) {
                Row(Modifier.fillMaxWidth().padding(14.dp, 4.dp)) {
                    pages.forEach { p ->
                        val label = when (p) { -1 -> "Base"; 0 -> "alt0 (sym)"; 1 -> "alt1 (altGr)"; else -> "alt$p" }
                        Button(
                            onClick = { KeyboardEditorSession.selectPage(p) },
                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                            colors = if (p == page) ButtonDefaults.buttonColors()
                                     else ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            Text(
                "Tap a key to edit it" + if (dirty) "  •  unsaved changes" else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp, 4.dp)
            )

            // kxkb: a second close button right above the keyboard, so it's reachable once you've
            // scrolled down past the layout list to the preview.
            OutlinedButton(
                onClick = { context.findActivity()?.finish() },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) { Text("Close Keyboard editor") }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wPx = constraints.maxWidth
                LaunchedEffect(wPx) { previewWidthPx = wPx }
                preview?.let { kb ->
                    EditorPreview(kb) { tapped ->
                        val path = KeyboardEditorSession.pathForRuntimeKey(page, tapped.row, tapped.column)
                        if (path == null) {
                            Toast.makeText(context, "That key is auto-generated (not editable yet)", Toast.LENGTH_SHORT).show()
                        } else {
                            navController.navigate(Route.KeyEdit(path.encode()))
                        }
                    }
                } ?: Text("(building preview…)", modifier = Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Rows of the selected page: reorder / delete / add rows, and append a key to a row.
            // (Tap a key in the preview to move/insert/delete within a row, or to edit it.)
            val pageRowList = KeyboardEditorSession.pageRows(page)
            Spacer(Modifier.height(12.dp))
            Text("Rows", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
            pageRowList.forEachIndexed { i, r ->
                val type = when { r.isNumberRow -> "number"; r.isBottomRow -> "bottom"; else -> "letters" }
                Row(Modifier.fillMaxWidth().padding(16.dp, 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("[$i] $type ·${r.keys.size}", style = mono, modifier = Modifier.weight(1f))
                    TextButton(onClick = { KeyboardEditorSession.moveRow(page, i, -1) }) { Text("▲") }
                    TextButton(onClick = { KeyboardEditorSession.moveRow(page, i, +1) }) { Text("▼") }
                    TextButton(onClick = {
                        val n = KeyboardEditorSession.pageRows(page).getOrNull(i)?.keys?.size ?: 0
                        KeyboardEditorSession.insertKey(page, i, n, org.futo.inputmethod.v2keyboard.BaseKey(spec = "a"))
                        navController.navigate(Route.KeyEdit(EditPath(i, n, page = page).encode()))
                    }) { Text("+key") }
                    TextButton(onClick = { KeyboardEditorSession.removeRow(page, i) }) { Text("✕") }
                }
            }
            OutlinedButton(
                onClick = { KeyboardEditorSession.addRow(page, pageRowList.indexOfLast { it.isLetterRow }) },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 2.dp)
            ) { Text("Add row") }

            // Alt pages: reorder (◀/▶) and delete whole pages, plus append a page copied from another
            // layout. Max 3 (alt0/alt1/alt2 — the engine's switchable alt layouts).
            val altCount = KeyboardEditorSession.altPageCount()
            val atCap = altCount >= KeyboardEditorSession.MAX_ALT_PAGES
            Spacer(Modifier.height(12.dp))
            Text("Alt pages (max ${KeyboardEditorSession.MAX_ALT_PAGES})", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
            for (p in 0 until altCount) {
                Row(Modifier.fillMaxWidth().padding(16.dp, 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("alt$p ·${KeyboardEditorSession.pageRows(p).size} rows", style = mono, modifier = Modifier.weight(1f))
                    TextButton(onClick = { KeyboardEditorSession.moveAltPage(p, -1) }) { Text("◀") }
                    TextButton(onClick = { KeyboardEditorSession.moveAltPage(p, +1) }) { Text("▶") }
                    TextButton(onClick = { KeyboardEditorSession.deleteAltPage(p) }) { Text("✕") }
                }
            }
            if (altCount == 0) Text("(none)", style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 1.dp))

            // Append a single alt page copied from another layout — one row per (layout, alt page),
            // with Preview (renders that page) and Append.
            val otherLayouts = remember(customLayouts, sourceIdx) {
                customLayouts.withIndex().filter { it.index != sourceIdx }
                    .map { (idx, cl) -> Triple(idx, cl, KeyboardEditorSession.altPageCountOf(cl)) }
            }
            if (otherLayouts.isNotEmpty()) {
                Text(
                    "Append a page from another layout" + if (atCap) " — at max, delete one first" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (atCap) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp, 8.dp, 16.dp, 2.dp)
                )
                otherLayouts.forEach { (idx, cl, n) ->
                    if (n == 0) {
                        Text("${cl.name} — (no alt pages)", style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(16.dp, 1.dp))
                    } else {
                        for (sp in 0 until n) {
                            Row(Modifier.fillMaxWidth().padding(16.dp, 1.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${cl.name} · alt$sp", style = mono, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                TextButton(onClick = { navController.navigate(Route.AltPreview(idx, sp)) }) { Text("Preview") }
                                TextButton(enabled = !atCap, onClick = {
                                    val ok = KeyboardEditorSession.appendAltPageFrom(cl, sp)
                                    Toast.makeText(context, if (ok) "Appended alt$sp from ${cl.name}" else "Couldn't append", Toast.LENGTH_SHORT).show()
                                }) { Text("Append") }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                Button(
                    onClick = { applyLayout(context, sourceIdx); KeyboardEditorSession.markApplied() },
                    enabled = dirty,
                    modifier = Modifier.weight(1f)
                ) { Text("Apply") }
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = { navController.navigate("keyboardeditorexport") },
                    modifier = Modifier.weight(1f)
                ) { Text("Export YAML") }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
                OutlinedButton(
                    onClick = { showApplyAsNew = true },
                    modifier = Modifier.weight(1f)
                ) { Text("Apply as a new layout") }
                Spacer(Modifier.size(12.dp))
                OutlinedButton(
                    onClick = { customLayouts.getOrNull(sourceIdx)?.let { KeyboardEditorSession.load(sourceIdx, it) } },
                    enabled = dirty,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Revert changes") }
            }

            if (showApplyAsNew) {
                var nm by remember { mutableStateOf(KeyboardEditorSession.working?.name ?: "") }
                var lg by remember { mutableStateOf(KeyboardEditorSession.sourceLanguage) }
                AlertDialog(
                    onDismissRequest = { showApplyAsNew = false },
                    title = { Text("Apply as a new layout") },
                    text = {
                        Column {
                            Text("Language", style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(value = lg, onValueChange = { lg = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text("Layout name", style = MaterialTheme.typography.labelMedium)
                            OutlinedTextField(value = nm, onValueChange = { nm = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            applyAsNew(nm.ifBlank { "Layout" }, lg.ifBlank { "en_US" })
                            showApplyAsNew = false
                        }) { Text("Create & edit") }
                    },
                    dismissButton = { TextButton(onClick = { showApplyAsNew = false }) { Text("Cancel") } }
                )
            }

            // Custom key widths: define what the Custom1–4 width tokens mean for this layout (as a %
            // of the keyboard width). A key's width picker then shows these resolved percentages.
            Spacer(Modifier.height(12.dp))
            Text("Custom key widths (% of keyboard)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp, 4.dp))
            listOf(KeyWidth.Custom1, KeyWidth.Custom2, KeyWidth.Custom3, KeyWidth.Custom4).forEach { w ->
                val current = working.overrideWidths[w]?.let { (it * 100f) }
                var text by remember(sourceIdx, w) { mutableStateOf(current?.let { "%.3f".format(java.util.Locale.ROOT, it).trimEnd('0').trimEnd('.') } ?: "") }
                Row(Modifier.fillMaxWidth().padding(16.dp, 2.dp)) {
                    Text(w.name, style = mono, modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            val pct = it.trim().toFloatOrNull()
                            KeyboardEditorSession.setOverrideWidth(w, pct?.let { p -> p / 100f })
                        },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1.5f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
