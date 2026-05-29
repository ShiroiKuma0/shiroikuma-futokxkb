package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.engine.general.DEFAULT_TOPBAR_ENTRIES
import org.futo.inputmethod.engine.general.TopBarEntriesSetting
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.useDataStore

// kxkb: editor for the global "topBar" list (static candidates shown when nothing is typed). A
// layout's own `topBar:` overrides this. See engine/general/TopBar.kt for the entry syntax.
@Preview(showBackground = true)
@Composable
fun TopBarEditorScreen(navController: NavHostController = rememberNavController()) {
    val item = useDataStore(TopBarEntriesSetting)
    var text by remember { mutableStateOf(item.value) }

    ScrollableList {
        ScreenTitle("Suggestion bar candidates", showBack = true, navController)

        Text(
            "Shown in the suggestion bar when nothing is typed — one entry per line:\n" +
            "•  plain text / emoji — inserted as-is  (❤, :@), +)\n" +
            "•  A…B (contains …) — inserts \"AB\" with the caret between  ( (…) → () )\n" +
            "•  [Paste] — pastes the clipboard\n" +
            "•  {{<pattern> — current date/time  ({{yyyy-MM-dd → today)\n\n" +
            "A layout that defines its own topBar overrides this global list.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp, 4.dp)
        )

        OutlinedTextField(
            value = text,
            onValueChange = { text = it; item.setValue(it) },
            label = { Text("One entry per line") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 8.dp)
                .heightIn(min = 240.dp)
        )

        Button(
            onClick = {
                text = DEFAULT_TOPBAR_ENTRIES
                item.setValue(DEFAULT_TOPBAR_ENTRIES)
            },
            modifier = Modifier.padding(16.dp, 8.dp)
        ) {
            Text("Reset to default")
        }
    }
}
