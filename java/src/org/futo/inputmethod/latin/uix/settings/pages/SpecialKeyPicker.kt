package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.AllActionsMap

// kxkb: pickers for the layout editor — choose a "special key" (an action or a layer/control key,
// writing a full `!icon/…|!code/…` spec) or an internal `!icon/<name>` icon for a key, instead of
// hand-typing the spec. Glyphs are rendered from the same drawables the keyboard uses.

// Internal `!icon/<name>` → preview drawable (mirrors BasicThemeProvider's registrations). `space_key`
// is omitted — it has no drawable (it renders the language name). Tinted to onSurface when shown, so it
// reads on the dialog regardless of the theme's on-key colour.
private val NAMED_ICON_DRAWABLE: Map<String, Int> = mapOf(
    "shift_key" to R.drawable.kxkb_shift,
    "shift_key_shifted" to R.drawable.kxkb_shift,
    "delete_key" to R.drawable.kxkb_delete,
    "settings_key" to R.drawable.kxkb_settings,
    "space_key_for_number_layout" to R.drawable.space,
    "enter_key" to R.drawable.kxkb_enter,
    "go_key" to R.drawable.sym_keyboard_go_lxx_light,
    "search_key" to R.drawable.sym_keyboard_search_lxx_light,
    "send_key" to R.drawable.sym_keyboard_send_lxx_light,
    "next_key" to R.drawable.sym_keyboard_next_lxx_light,
    "done_key" to R.drawable.sym_keyboard_done_lxx_light,
    "previous_key" to R.drawable.sym_keyboard_previous_lxx_light,
    "tab_key" to R.drawable.kxkb_tab,
    "zwnj_key" to R.drawable.sym_keyboard_zwnj_lxx_dark,
    "zwj_key" to R.drawable.sym_keyboard_zwj_lxx_dark,
    "emoji_action_key" to R.drawable.smile,
    "emoji_normal_key" to R.drawable.smile,
    "numpad" to R.drawable.numpad,
    "japanese_key" to R.drawable.japanesekey,
)

// The preview drawable for any valid `!icon/<name>` — a named key icon, or `action_<id>` → that
// action's own icon. null = no glyph (caller falls back to a text label).
internal fun drawableForIconName(name: String): Int? =
    NAMED_ICON_DRAWABLE[name]
        ?: if (name.startsWith("action_")) AllActionsMap[name.removePrefix("action_")]?.icon else null

// A key spec is `visual|code` (in either order, `|`-separated): the `!code/…` part is the output, the
// other part is the visual (a `!icon/…` or a plain label). Split keeps them independent so a picker can
// replace one without clobbering the other.
internal fun splitSpec(spec: String): Pair<String, String?> {
    val parts = spec.split("|")
    val code = parts.firstOrNull { it.startsWith("!code/") }
    val visual = parts.firstOrNull { !it.startsWith("!code/") } ?: ""
    return visual to code
}

internal fun composeSpec(visual: String, code: String?): String =
    if (!code.isNullOrEmpty()) "$visual|$code" else visual

// Curated `!code/key_*` layer/control keys with a natural icon (else a readable label, auto-filled as
// the visual). Only the ones useful for layout authoring — the obscure internal codes are left out.
private data class KeyCodeEntry(val title: String, val iconName: String?, val label: String, val code: String)

private val LAYER_KEYS = listOf(
    KeyCodeEntry("Tab", "tab_key", "Tab", "key_tab"),
    KeyCodeEntry("Enter", "enter_key", "Enter", "key_enter"),
    KeyCodeEntry("Space", null, "Space", "key_space"),
    KeyCodeEntry("Delete", "delete_key", "Del", "key_delete"),
    KeyCodeEntry("Shift", "shift_key", "Shift", "key_shift"),
    KeyCodeEntry("Caps lock", "shift_key", "Caps", "key_capslock"),
    KeyCodeEntry("Escape", null, "Esc", "key_escape"),
    KeyCodeEntry("Ctrl", null, "Ctrl", "key_ctrl"),
    KeyCodeEntry("Emoji", "emoji_normal_key", "☺", "key_emoji"),
    KeyCodeEntry("Language switch", "action_switch_language", "🌐", "key_language_switch"),
    KeyCodeEntry("Symbols ⇄ letters", null, "?123", "key_switch_alpha_symbol"),
    KeyCodeEntry("Numbers layer", null, "123", "key_to_number_layout"),
    KeyCodeEntry("Alt page 0 (sym)", null, "sym", "key_to_alt_0_layout"),
    KeyCodeEntry("Alt page 1 (altGr)", null, "alt1", "key_to_alt_1_layout"),
    KeyCodeEntry("Alt page 2", null, "alt2", "key_to_alt_2_layout"),
    KeyCodeEntry("Return to letters", null, "ABC", "key_to_alpha_0_layout"),
)

@Composable
private fun Glyph(iconName: String?, fallback: String) {
    val res = iconName?.let { drawableForIconName(it) }
    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
        if (res != null) {
            Icon(
                painterResource(res), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp)
            )
        } else {
            Text(
                fallback, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PickRow(iconName: String?, fallback: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Glyph(iconName, fallback)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = editorLabelStyle())
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp)
    )
}

@Composable
private fun PickerShell(title: String, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            // High-contrast look matching the theme: black background, yellow border.
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(16.dp, 8.dp)
                )
                LazyColumn(Modifier.heightIn(max = 460.dp), content = content)
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(8.dp)) { Text("Cancel") }
            }
        }
    }
}

// Pick an action or a layer/control key → writes the WHOLE spec (`!icon/…|!code/…`, or a readable
// label + `!code/…` when there's no icon).
@Composable
fun SpecialKeyPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val actions = remember {
        AllActionsMap.entries
            .filter { it.key != "mem_dbg" && it.key != "bugs" }
            .map { (id, a) -> Triple(id, "action_$id", ctx.getString(a.name)) }
            .sortedBy { it.third.lowercase() }
    }
    PickerShell("Insert special key", onDismiss) {
        item { SectionHeader("Actions") }
        items(actions) { (id, iconName, name) ->
            PickRow(iconName, name.take(2), name, "!code/action_$id") {
                onPick(composeSpec("!icon/$iconName", "!code/action_$id")); onDismiss()
            }
        }
        item { SectionHeader("Layer / control keys") }
        items(LAYER_KEYS) { e ->
            val visual = e.iconName?.let { "!icon/$it" } ?: e.label
            PickRow(e.iconName, e.label, e.title, "!code/${e.code}") {
                onPick(composeSpec(visual, "!code/${e.code}")); onDismiss()
            }
        }
    }
}

// Pick an internal `!icon/<name>` → replaces only the VISUAL half of the current spec, keeping any
// `!code/…`.
@Composable
fun IconPickerDialog(currentSpec: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val code = remember(currentSpec) { splitSpec(currentSpec).second }
    val keyIcons = remember { NAMED_ICON_DRAWABLE.keys.sorted() }
    val actionIcons = remember {
        AllActionsMap.entries
            .filter { it.key != "mem_dbg" && it.key != "bugs" }
            .map { (id, a) -> "action_$id" to ctx.getString(a.name) }
            .sortedBy { it.second.lowercase() }
    }
    PickerShell("Choose icon", onDismiss) {
        item { SectionHeader("Key icons") }
        items(keyIcons) { name ->
            PickRow(name, name.take(2), name, "!icon/$name") {
                onPick(composeSpec("!icon/$name", code)); onDismiss()
            }
        }
        item { SectionHeader("Action icons") }
        items(actionIcons) { (name, label) ->
            PickRow(name, label.take(2), label, "!icon/$name") {
                onPick(composeSpec("!icon/$name", code)); onDismiss()
            }
        }
    }
}
