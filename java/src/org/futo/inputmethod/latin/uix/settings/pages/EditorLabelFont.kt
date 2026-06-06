package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStoreItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue

// kxkb: configurable font for the layout-name LABELS shown throughout the visual keyboard editor —
// the "Layout to edit" list, the Rows / alt-page rows, the append-from-another-layout list, and the
// Export / alt-page-preview screens. Four knobs (family, size, weight, optional colour) so layout
// names in any script render legibly. Defaults reproduce the old hardcoded look exactly (small
// Monospace, normal weight, theme colour), so nothing changes until a knob is moved.
// Settings live under Settings → Keyboard UI → "Keyboard editor font" (KeyboardEditorFontScreen);
// every editor screen reads the resolved style via [editorLabelStyle].

val EditorLabelFontFamily = SettingsKey(stringPreferencesKey("kxkb_editor_label_font_family"), "mono")
val EditorLabelFontSize   = SettingsKey(floatPreferencesKey("kxkb_editor_label_font_size"), 12f)
val EditorLabelFontWeight = SettingsKey(intPreferencesKey("kxkb_editor_label_font_weight"), 400)
val EditorLabelColorOn    = SettingsKey(booleanPreferencesKey("kxkb_editor_label_color_on"), false)
val EditorLabelColor      = SettingsKey(intPreferencesKey("kxkb_editor_label_color"), 0xFFFFFFFF.toInt())

// internal family keys ↔ Compose generic families (the stored value is the stable key, not a name)
private val EDITOR_FONT_FAMILIES = listOf("mono", "default", "sans", "serif")
private fun editorFontFamily(key: String): FontFamily = when (key) {
    "default" -> FontFamily.Default
    "sans"    -> FontFamily.SansSerif
    "serif"   -> FontFamily.Serif
    else      -> FontFamily.Monospace
}
private fun editorFontFamilyName(key: String): String = when (key) {
    "default" -> "Default"
    "sans"    -> "Sans-serif"
    "serif"   -> "Serif"
    else      -> "Monospace"
}

private val EDITOR_FONT_WEIGHTS = listOf(300, 400, 500, 600, 700, 800)
private fun editorFontWeightName(w: Int): String = when (w) {
    300 -> "Light (300)"
    400 -> "Normal (400)"
    500 -> "Medium (500)"
    600 -> "Semi-bold (600)"
    700 -> "Bold (700)"
    800 -> "Extra-bold (800)"
    else -> w.toString()
}

/**
 * The resolved label style. Built from `bodySmall` (so line-height / letter-spacing carry over) with
 * the four configured knobs overlaid. Colour is `Unspecified` unless the override is on, so by
 * default the label inherits its container's content colour (Button content colour, etc.) exactly as
 * the old hardcoded `mono` did. Where a call site passes an explicit `color =` to `Text`, that wins
 * over this style's colour (intended: muted hint texts stay muted even with the override on).
 */
@Composable
fun editorLabelStyle(): TextStyle {
    val family   = useDataStoreValue(EditorLabelFontFamily)
    val size     = useDataStoreValue(EditorLabelFontSize)
    val weight   = useDataStoreValue(EditorLabelFontWeight)
    val colorOn  = useDataStoreValue(EditorLabelColorOn)
    val colorInt = useDataStoreValue(EditorLabelColor)
    return MaterialTheme.typography.bodySmall.copy(
        fontFamily = editorFontFamily(family),
        fontSize   = size.sp,
        fontWeight = FontWeight(weight.coerceIn(1, 1000)),
        color      = if (colorOn) Color(colorInt) else Color.Unspecified
    )
}

@Preview(showBackground = true)
@Composable
fun KeyboardEditorFontScreen(navController: NavHostController = rememberNavController()) {
    val familyItem  = useDataStore(EditorLabelFontFamily)
    val sizeItem    = useDataStore(EditorLabelFontSize)
    val weightItem  = useDataStore(EditorLabelFontWeight)
    val colorOnItem = useDataStore(EditorLabelColorOn)
    val colorItem   = useDataStore(EditorLabelColor)

    ScrollableList {
        ScreenTitle("Keyboard editor font", showBack = true, navController)

        Text(
            "Font of the layout-name labels throughout the Keyboard editor — the layout list, the " +
            "Rows and alt-page lists, and the Export / preview screens. Defaults reproduce the " +
            "original small Monospace look.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp)
        )

        // Live preview — a sample label rendered exactly as it appears in the editor's layout list.
        Button(onClick = { }, modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
            Text("[0] Sample layout name", style = editorLabelStyle())
        }

        DropDownPickerSettingItem(
            label = "Font family",
            options = EDITOR_FONT_FAMILIES,
            selection = familyItem.value,
            onSet = { familyItem.setValue(it) },
            getDisplayName = { editorFontFamilyName(it) }
        )

        SettingSliderForDataStoreItem(
            title = "Font size",
            item = sizeItem,
            default = EditorLabelFontSize.default,
            range = 8f..28f,
            transform = { it },
            indicator = { "%.0f sp".format(it) }
        )

        DropDownPickerSettingItem(
            label = "Font weight",
            options = EDITOR_FONT_WEIGHTS,
            selection = weightItem.value,
            onSet = { weightItem.setValue(it) },
            getDisplayName = { editorFontWeightName(it) }
        )

        SettingToggleDataStoreItem(
            title = "Override label colour",
            dataStoreItem = colorOnItem,
            subtitle = "Use a custom colour instead of the theme default"
        )
        if (colorOnItem.value) {
            ColorSetting(
                title = "Label colour",
                current = colorItem.value,
                inherited = MaterialTheme.colorScheme.onSurface.toArgb(),
                onChange = { colorItem.setValue(it) }
            )
        }

        OutlinedButton(
            onClick = {
                familyItem.setValue(EditorLabelFontFamily.default)
                sizeItem.setValue(EditorLabelFontSize.default)
                weightItem.setValue(EditorLabelFontWeight.default)
                colorOnItem.setValue(EditorLabelColorOn.default)
                colorItem.setValue(EditorLabelColor.default)
            },
            modifier = Modifier.padding(16.dp, 8.dp)
        ) {
            Text("Reset to defaults")
        }
    }
}
