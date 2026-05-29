package org.futo.inputmethod.latin.uix.settings.pages

import android.widget.Toast
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList

// kxkb: preview a single alt page of ANOTHER custom layout, with a button to append it to the layout
// currently open in the editor. Reached from the editor's "Append a page from another layout" list.

@Composable
fun AltPagePreviewScreen(navController: NavHostController = rememberNavController(), srcIdx: Int, page: Int) {
    val context = LocalContext.current
    val cl = remember(srcIdx) { getCustomLayouts(context).getOrNull(srcIdx) }
    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)

    var preview by remember { mutableStateOf<Keyboard?>(null) }
    var widthPx by remember { mutableStateOf(0) }
    LaunchedEffect(widthPx, srcIdx, page) {
        preview = if (cl != null && widthPx > 0)
            withContext(Dispatchers.Default) { KeyboardEditorSession.buildAltPagePreview(context, widthPx, cl, page) }
        else null
    }

    ScrollableList {
        ScreenTitle("Preview alt page", showBack = true, navController)
        if (cl == null) {
            Text("Layout not found.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            return@ScrollableList
        }

        Text("${cl.name} · alt$page", style = mono, modifier = Modifier.padding(16.dp, 4.dp))

        val atCap = KeyboardEditorSession.altPageCount() >= KeyboardEditorSession.MAX_ALT_PAGES
        Button(
            enabled = !atCap,
            onClick = {
                val ok = KeyboardEditorSession.appendAltPageFrom(cl, page)
                Toast.makeText(
                    context,
                    if (ok) "Appended alt$page from ${cl.name}" else "Couldn't append (at max alt pages?)",
                    Toast.LENGTH_SHORT
                ).show()
                navController.popBackStack()
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp)
        ) { Text(if (atCap) "At max alt pages — delete one first" else "Append to current layout") }

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wPx = constraints.maxWidth
            LaunchedEffect(wPx) { widthPx = wPx }
            preview?.let { EditorPreview(it) { } }
                ?: Text("(building preview…)", style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}
