package org.futo.inputmethod.latin.uix.settings.pages

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.v2keyboard.emitKeyboardYaml

// kxkb: layout editor — Export screen. Shows the emitted YAML and lets the user copy it or save it to
// a file (system file picker, prefilled with the layout name). Reads the working model from the
// shared KeyboardEditorSession.

@Composable
fun KeyboardEditorExportScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val working = KeyboardEditorSession.working
    val mono = editorLabelStyle()

    val yaml = remember(working) { if (working != null) emitKeyboardYaml(working) else "" }
    val suggestedName = remember(working) {
        // Keep Unicode (e.g. Japanese) in the name; only replace path-unsafe / control characters.
        ((working?.name ?: "layout").trim().ifEmpty { "layout" }
            .replace(Regex("[/\\\\\\u0000-\\u001f]"), "_")) + ".yaml"
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-yaml")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(yaml.toByteArray()) }
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ScrollableList {
        ScreenTitle("Export YAML", showBack = true, navController)

        if (working == null) {
            Text("No layout loaded.", modifier = Modifier.padding(16.dp))
            return@ScrollableList
        }

        Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(yaml))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) { Text("Copy") }
            Spacer(Modifier.size(12.dp))
            OutlinedButton(
                onClick = { saveLauncher.launch(suggestedName) },
                modifier = Modifier.weight(1f)
            ) { Text("Save to file…") }
        }

        Text(suggestedName, style = mono, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 0.dp))

        Spacer(Modifier.height(8.dp))
        Text(
            yaml,
            style = mono,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(8.dp)
        )
        Spacer(Modifier.height(32.dp))
    }
}
