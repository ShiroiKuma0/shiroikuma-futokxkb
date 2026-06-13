package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStoreItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue

// kxkb: configurable font for the layout-name LABELS shown throughout the visual keyboard editor AND
// the two layout-selection lists ("Custom layouts" + "Keyboard editor"), which share one grouped look
// via [LayoutSelectionList]. Knobs: family, size, weight, optional colour, plus the inter-item padding
// between layouts in those lists. The font default is a comfortable 16 sp (the lists were too small);
// every editor screen reads the resolved style via [editorLabelStyle].
// Settings live under Settings → Keyboard UI → "Layout list font" (KeyboardEditorFontScreen).

val EditorLabelFontFamily = SettingsKey(stringPreferencesKey("kxkb_editor_label_font_family"), "mono")
val EditorLabelFontSize   = SettingsKey(floatPreferencesKey("kxkb_editor_label_font_size"), 16f)
val EditorLabelFontWeight = SettingsKey(intPreferencesKey("kxkb_editor_label_font_weight"), 400)
val EditorLabelColorOn    = SettingsKey(booleanPreferencesKey("kxkb_editor_label_color_on"), false)
val EditorLabelColor      = SettingsKey(intPreferencesKey("kxkb_editor_label_color"), 0xFFFFFFFF.toInt())
// Vertical padding (dp) between layout rows in the selection lists. Default 2 (tight).
val EditorLabelItemPadding = SettingsKey(floatPreferencesKey("kxkb_editor_label_item_padding"), 2f)

// kxkb: the rounded box/pill drawn around each layout name in the selection lists — all settable.
// All same width (LayoutPillWidth). Background visible by default; border off by default (colour
// alpha 0). Inner padding kept small.
val LayoutPillWidth       = SettingsKey(floatPreferencesKey("kxkb_layout_pill_width"), 280f)
val LayoutPillRadius      = SettingsKey(floatPreferencesKey("kxkb_layout_pill_radius"), 10f)
val LayoutPillPadH        = SettingsKey(floatPreferencesKey("kxkb_layout_pill_pad_h"), 14f)
val LayoutPillPadV        = SettingsKey(floatPreferencesKey("kxkb_layout_pill_pad_v"), 4f)
val LayoutPillBgColor     = SettingsKey(intPreferencesKey("kxkb_layout_pill_bg"), 0x40808080)
val LayoutPillBorderColor = SettingsKey(intPreferencesKey("kxkb_layout_pill_border"), 0x00000000)
val LayoutPillBorderWidth = SettingsKey(floatPreferencesKey("kxkb_layout_pill_border_w"), 1.5f)
// Left indent (dp) of each box, on top of the list's 16 dp margin. Default 64 = the previous
// "massive" indent (boxes land ~80 dp from the edge, where the names sat before).
val LayoutPillIndent      = SettingsKey(floatPreferencesKey("kxkb_layout_pill_indent"), 64f)

// internal family keys ↔ Compose generic families (the stored value is the stable key, not a name).
// "keyboard" = the active keyboard theme's custom font (a Typeface), resolved in [editorLabelStyle].
private val EDITOR_FONT_FAMILIES = listOf("mono", "default", "sans", "serif", "keyboard")
private fun editorFontFamily(key: String): FontFamily = when (key) {
    "default" -> FontFamily.Default
    "sans"    -> FontFamily.SansSerif
    "serif"   -> FontFamily.Serif
    else      -> FontFamily.Monospace
}
private fun editorFontFamilyName(key: String): String = when (key) {
    "default"  -> "Default"
    "sans"     -> "Sans-serif"
    "serif"    -> "Serif"
    "keyboard" -> "Keyboard font (theme)"
    else       -> "Monospace"
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
    // "keyboard" → the active theme's custom font (same Typeface the keyboard uses, via
    // withCustomFont); falls back to the default family if the theme carries no custom font.
    val keyboardTypeface = LocalKeyboardScheme.current.extended.advancedThemeOptions.font
    val resolvedFamily = if (family == "keyboard") {
        keyboardTypeface?.let { FontFamily(it) } ?: FontFamily.Default
    } else {
        editorFontFamily(family)
    }
    return MaterialTheme.typography.bodySmall.copy(
        fontFamily = resolvedFamily,
        fontSize   = size.sp,
        fontWeight = FontWeight(weight.coerceIn(1, 1000)),
        color      = if (colorOn) Color(colorInt) else Color.Unspecified
    )
}

// The displayed section header for a layout's locale: "GNU" for the zxx ("no linguistic content")
// locale our GNU layouts use, otherwise the language autonym (English, Čeština, 日本語, Русский …).
private fun sectionName(language: String): String {
    if (language.equals("zxx", ignoreCase = true) || language.startsWith("zxx_") || language.startsWith("zxx-")) return "GNU"
    return try { SubtypeLocaleUtils.getSubtypeLanguageDisplayName(language) }
           catch (_: Exception) { language }
}

// Phonetic ("by how it sounds") sort key for a language section: the romanized autonym, so the
// sections order by sound rather than by script code points — Japanese 日本語 → "nihongo" (N),
// Russian Русский → "russkij" (R), Czech Čeština → "cestina" (C), English → "english", GNU → "gnu".
// Latin-script names fall back to their diacritic-stripped form; a non-Latin script needs a map
// entry here (without one it would keep its own letters and sort after the Latin sections).
private val PHONETIC_SORT_KEYS = mapOf(
    "ja"  to "nihongo",
    "ru"  to "russkij",
    "zxx" to "gnu",
)
private fun phoneticSortKey(language: String, displayName: String): String {
    val subtag = language.substringBefore('_').substringBefore('-').lowercase()
    PHONETIC_SORT_KEYS[subtag]?.let { return it }
    return java.text.Normalizer.normalize(displayName, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
}

/**
 * Shared, unified layout-selection list used by BOTH "Custom layouts" (DevLayoutEditor) and the
 * "Keyboard editor" (KeyboardEditorScreen). Layouts are grouped under **underlined language section
 * headers** (the language display name, e.g. "English", "Czech", "GNU"), the groups ordered
 * alphabetically and the layouts within each group ordered alphabetically by name. No bracketed
 * index or per-row subtitle — just the layout name, in [editorLabelStyle] at the configured
 * inter-item padding. Each row keeps its ORIGINAL index (the slot callers address) regardless of the
 * display order. [selectedIndex] (the editor's active layout) draws filled; the rest outlined.
 *
 * The name (parsed YAML) and the language display name are resolved once per list change.
 */
@Composable
fun LayoutSelectionList(
    layouts: List<CustomLayout>,
    selectedIndex: Int? = null,
    onClick: (index: Int, layout: CustomLayout) -> Unit
) {
    val style = editorLabelStyle()
    val itemPadding = useDataStoreValue(EditorLabelItemPadding)

    // (originalIndex, layout, parsedName), grouped by language display name (alphabetical), each
    // group sorted alphabetically by name.
    val groups = remember(layouts) {
        layouts.mapIndexed { idx, cl ->
            Triple(idx, cl, try { cl.name } catch (_: Exception) { cl.language })
        }.groupBy { (_, cl, _) -> sectionName(cl.language) }
         .map { (display, list) ->
             // Layouts within a section: alphabetical by name.
             display to list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.third })
         }
         // Sections: phonetic order (by the romanized autonym), so e.g. 日本語 sorts under N.
         .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { (display, list) ->
             phoneticSortKey(list.first().second.language, display)
         })
    }

    groups.forEach { (language, entries) ->
        Text(
            language,
            style = style.copy(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 2.dp)
        )
        entries.forEach { (index, layout, name) ->
            LayoutRow(name, index == selectedIndex, itemPadding, style) { onClick(index, layout) }
        }
    }
}

/**
 * One layout row: a fixed-width rounded box/pill (all settable — width, roundedness, background,
 * border, inner padding) around the left-aligned name. Built from a bare Box + clickable (NOT a
 * Material Button/Surface, which enforce a 40–48 dp minimum touch-target height that would make rows
 * tall regardless of padding), so [topPadding] (the "Padding between layouts" setting) is the only
 * gap between rows. The selected row (the editor's active layout) is a solid primary pill.
 */
@Composable
private fun LayoutRow(
    name: String,
    selected: Boolean,
    topPadding: Float,
    style: TextStyle,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val pillWidth   = useDataStoreValue(LayoutPillWidth)
    val radius      = useDataStoreValue(LayoutPillRadius)
    val padH        = useDataStoreValue(LayoutPillPadH)
    val padV        = useDataStoreValue(LayoutPillPadV)
    val bgColor     = useDataStoreValue(LayoutPillBgColor)
    val borderColor = useDataStoreValue(LayoutPillBorderColor)
    val borderWidth = useDataStoreValue(LayoutPillBorderWidth)
    val indent      = useDataStoreValue(LayoutPillIndent)

    val shape = RoundedCornerShape(radius.dp)
    // Selected (the editor's active layout) → solid primary pill with onPrimary text. Unselected →
    // the settable background, with the user's colour override (if any) else primary text.
    val fillColor = if (selected) scheme.primary else Color(bgColor)
    val textColor = when {
        selected -> scheme.onPrimary
        style.color != Color.Unspecified -> style.color
        else -> scheme.primary
    }
    val drawBorder = !selected && borderWidth > 0f && Color(borderColor).alpha > 0f

    // Outer Box holds the indent; the inner pill is the fixed-width (same for all), rounded,
    // bordered box that is the actual tap target. A bare Box + clickable (NOT Material
    // Button/Surface) so there is no 40–48 dp minimum height — the pill is exactly text + padding.
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = topPadding.dp)) {
        Box(
            modifier = Modifier
                .padding(start = indent.dp)
                .width(pillWidth.dp)
                .clip(shape)
                .background(fillColor, shape)
                .then(if (drawBorder) Modifier.border(borderWidth.dp, Color(borderColor), shape) else Modifier)
                .clickable(onClick = onClick)
                .padding(horizontal = padH.dp, vertical = padV.dp)
        ) {
            Text(name, style = style, color = textColor)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun KeyboardEditorFontScreen(navController: NavHostController = rememberNavController()) {
    val familyItem  = useDataStore(EditorLabelFontFamily)
    val sizeItem    = useDataStore(EditorLabelFontSize)
    val weightItem  = useDataStore(EditorLabelFontWeight)
    val colorOnItem = useDataStore(EditorLabelColorOn)
    val colorItem   = useDataStore(EditorLabelColor)
    val paddingItem = useDataStore(EditorLabelItemPadding)
    val pillWidthItem   = useDataStore(LayoutPillWidth)
    val pillRadiusItem  = useDataStore(LayoutPillRadius)
    val pillPadHItem    = useDataStore(LayoutPillPadH)
    val pillPadVItem    = useDataStore(LayoutPillPadV)
    val pillBgItem      = useDataStore(LayoutPillBgColor)
    val pillBorderItem  = useDataStore(LayoutPillBorderColor)
    val pillBorderWItem = useDataStore(LayoutPillBorderWidth)
    val pillIndentItem  = useDataStore(LayoutPillIndent)

    ScrollableList {
        ScreenTitle("Layout list font", showBack = true, navController)

        Text(
            "Font, colour, and row spacing of the layout-selection lists shared by \"Custom layouts\" " +
            "and the \"Keyboard editor\" (layouts grouped under underlined language headers) — and of " +
            "the layout-name labels elsewhere in the editor (Rows, alt pages, Export / preview).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp, 4.dp)
        )

        // Live preview — the grouped list look (underlined language header + name rows, the first
        // "selected" / filled) at the configured font, colour and inter-item padding.
        val previewStyle = editorLabelStyle()
        val previewPad = useDataStoreValue(EditorLabelItemPadding)
        Text(
            "English",
            style = previewStyle.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp, 16.dp, 2.dp)
        )
        LayoutRow("QWERTY", selected = true, topPadding = previewPad, style = previewStyle) { }
        LayoutRow("Dvorak", selected = false, topPadding = previewPad, style = previewStyle) { }

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

        SettingSliderForDataStoreItem(
            title = "Padding between layouts",
            item = paddingItem,
            default = EditorLabelItemPadding.default,
            range = 0f..16f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
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

        // The rounded box/pill around each layout name — all settable. Same width for every layout.
        Text(
            "Layout box",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(16.dp, 14.dp, 16.dp, 0.dp)
        )
        SettingSliderForDataStoreItem(
            title = "Indent",
            item = pillIndentItem,
            default = LayoutPillIndent.default,
            range = 0f..240f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
        )
        SettingSliderForDataStoreItem(
            title = "Box width",
            item = pillWidthItem,
            default = LayoutPillWidth.default,
            range = 80f..600f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
        )
        SettingSliderForDataStoreItem(
            title = "Roundedness",
            item = pillRadiusItem,
            default = LayoutPillRadius.default,
            range = 0f..40f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
        )
        SettingSliderForDataStoreItem(
            title = "Inner padding (horizontal)",
            item = pillPadHItem,
            default = LayoutPillPadH.default,
            range = 0f..40f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
        )
        SettingSliderForDataStoreItem(
            title = "Inner padding (vertical)",
            item = pillPadVItem,
            default = LayoutPillPadV.default,
            range = 0f..24f,
            transform = { it },
            indicator = { "%.0f dp".format(it) }
        )
        ColorSetting(
            title = "Box background",
            current = pillBgItem.value,
            inherited = pillBgItem.value,
            onChange = { pillBgItem.setValue(it) }
        )
        ColorSetting(
            title = "Box border colour",
            current = pillBorderItem.value,
            inherited = pillBorderItem.value,
            onChange = { pillBorderItem.setValue(it) }
        )
        SettingSliderForDataStoreItem(
            title = "Border width",
            item = pillBorderWItem,
            default = LayoutPillBorderWidth.default,
            range = 0f..8f,
            transform = { it },
            indicator = { "%.1f dp".format(it) }
        )

        OutlinedButton(
            onClick = {
                familyItem.setValue(EditorLabelFontFamily.default)
                sizeItem.setValue(EditorLabelFontSize.default)
                weightItem.setValue(EditorLabelFontWeight.default)
                colorOnItem.setValue(EditorLabelColorOn.default)
                colorItem.setValue(EditorLabelColor.default)
                paddingItem.setValue(EditorLabelItemPadding.default)
                pillWidthItem.setValue(LayoutPillWidth.default)
                pillRadiusItem.setValue(LayoutPillRadius.default)
                pillPadHItem.setValue(LayoutPillPadH.default)
                pillPadVItem.setValue(LayoutPillPadV.default)
                pillBgItem.setValue(LayoutPillBgColor.default)
                pillBorderItem.setValue(LayoutPillBorderColor.default)
                pillBorderWItem.setValue(LayoutPillBorderWidth.default)
                pillIndentItem.setValue(LayoutPillIndent.default)
            },
            modifier = Modifier.padding(16.dp, 8.dp)
        ) {
            Text("Reset to defaults")
        }
    }
}
