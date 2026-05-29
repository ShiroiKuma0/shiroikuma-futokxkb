package org.futo.inputmethod.engine.general

import androidx.datastore.preferences.core.stringPreferencesKey
import org.futo.inputmethod.latin.uix.SettingsKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// kxkb: Multiling-style "topBar" — static candidates shown in the suggestion bar when nothing is
// being composed (FUTO otherwise shows nothing until you type). Useful for quick emoji/snippets,
// bracket pairs, paste, and date/time stamps. A layout's own `topBar:` list (if non-empty) overrides
// this global default; the global list is editable in Settings. One entry per line. Entry forms:
//   plain text / emoji   -> inserted literally          (+, :@), ❤, ...)
//   A…B (contains U+2026) -> inserts "AB" with the caret placed between A and B   ((…)->() , “…” , […])
//   [Paste]              -> pastes the clipboard
//   {{<pattern>          -> the current date/time via SimpleDateFormat   ({{yyyy-MM-dd -> 2026-05-29)
val DEFAULT_TOPBAR_ENTRIES: String = listOf(
    "+", "-", "*", "#", "“…”", "\"…\"", "(…)", "[Paste]", ":@)", "[…]",
    "{{yyyy-MM-dd ", "☺", "❤", "♡ ", "{…}", "{{yyyy-MM-dd_HH-mm-ss"
).joinToString("\n")

val TopBarEntriesSetting = SettingsKey(
    stringPreferencesKey("kxkb_topbar_entries"),
    DEFAULT_TOPBAR_ENTRIES
)

enum class TopBarType { TEXT, PAIR, PASTE, DATE }

data class TopBarEntry(
    val label: String,        // what the suggestion bar shows
    val type: TopBarType,
    val insert: String,       // TEXT/PAIR: the text to commit
    val cursorBack: Int,      // PAIR: UTF-16 units to move the caret left after committing
    val datePattern: String?  // DATE: the SimpleDateFormat pattern (re-evaluated at tap time)
)

fun parseTopBarEntry(raw: String): TopBarEntry? {
    if (raw.isEmpty()) return null
    return when {
        raw.startsWith("{{") -> {
            val pattern = raw.substring(2)
            val preview = try {
                SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
            } catch (e: Exception) { raw }
            TopBarEntry(preview, TopBarType.DATE, "", 0, pattern)
        }
        raw.equals("[Paste]", ignoreCase = true) ->
            TopBarEntry("Paste", TopBarType.PASTE, "", 0, null)
        raw.contains('…') -> {
            val i = raw.indexOf('…')
            val before = raw.substring(0, i)
            val after = raw.substring(i + 1)
            TopBarEntry(raw, TopBarType.PAIR, before + after, after.length, null)
        }
        else -> TopBarEntry(raw, TopBarType.TEXT, raw, 0, null)
    }
}

// A layout's own topBar (when set) wins; otherwise fall back to the global list. One entry per line.
fun resolveTopBarEntries(layoutTopBar: List<String>?, globalRaw: String): List<TopBarEntry> {
    val raws = if (!layoutTopBar.isNullOrEmpty()) layoutTopBar else globalRaw.split("\n")
    return raws.filter { it.isNotEmpty() }.mapNotNull { parseTopBarEntry(it) }
}

// The text a DATE entry inserts, re-evaluated now (the label preview may be slightly stale).
fun TopBarEntry.dateText(): String =
    try { SimpleDateFormat(datePattern ?: "", Locale.getDefault()).format(Date()) } catch (e: Exception) { label }
