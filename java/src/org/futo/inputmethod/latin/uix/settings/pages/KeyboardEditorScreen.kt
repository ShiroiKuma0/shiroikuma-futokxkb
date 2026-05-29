package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
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
    val customLayouts = remember { getCustomLayouts(context) }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    // Subscribe to working-model changes (recompose + rebuild preview on every edit).
    val working = KeyboardEditorSession.working
    val dirty = KeyboardEditorSession.dirty
    val sourceIdx = KeyboardEditorSession.sourceIdx

    var preview by remember { mutableStateOf<Keyboard?>(null) }
    var previewWidthPx by remember { mutableStateOf(0) }
    LaunchedEffect(working, previewWidthPx) {
        preview = if (working != null && previewWidthPx > 0)
            withContext(Dispatchers.Default) { KeyboardEditorSession.buildPreview(context, previewWidthPx) }
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
                        val path = KeyboardEditorSession.pathForRuntimeKey(tapped.row, tapped.column)
                        if (path == null) {
                            Toast.makeText(context, "That key is auto-generated (not editable yet)", Toast.LENGTH_SHORT).show()
                        } else {
                            navController.navigate(Route.KeyEdit(path.encode()))
                        }
                    }
                } ?: Text("(building preview…)", modifier = Modifier.padding(16.dp, 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            OutlinedButton(
                onClick = { customLayouts.getOrNull(sourceIdx)?.let { KeyboardEditorSession.load(sourceIdx, it) } },
                enabled = dirty,
                modifier = Modifier.fillMaxWidth().padding(16.dp, 2.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Revert changes") }

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
