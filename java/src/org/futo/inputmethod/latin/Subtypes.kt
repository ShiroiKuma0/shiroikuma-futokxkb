package org.futo.inputmethod.latin

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import org.futo.inputmethod.v2keyboard.LayoutManager
import java.util.Locale
import kotlin.math.sign

fun localeFromString(s: String): Locale =
    Locale.forLanguageTag(s.replace("__#", "-").replace("_", "-"))

fun String.toLocale() = localeFromString(this)

fun Locale.stripExtensionsIfNeeded(): Locale {
    val newLocale = if(Build.VERSION.SDK_INT >= 26) {
        this.stripExtensions().stripExtensions() // TODO: Sometimes this requires two calls??
    } else {
        this
    }

    return newLocale
}

val SubtypesSetting = SettingsKey(
    stringSetPreferencesKey("subtypes"),
    setOf()
)

val ActiveSubtype = SettingsKey(
    stringPreferencesKey("activeSubtype"),
    ""
)

val MultilingualBucketSetting = SettingsKey(
    stringSetPreferencesKey("multilingual_bucket"),
    emptySet()
)

// kxkb: most-recently-active subtypes, newline-joined, most-recent first (subtype strings can't
// contain a newline). Updated on every ActiveSubtype change; backs the swipe-space switcher's
// "last-used layout per language" and recency ordering.
val LayoutRecency = SettingsKey(
    stringPreferencesKey("kxkb_layout_recency"),
    ""
)

// kxkb: optional left-column shortcuts for the swipe-space switcher. Each opens a settings
// destination ("!nav/<route>") and is individually toggled on/off in the Keyboard UI screen
// (KxkbSizingScreen). The on/off state is a SharedPreferences boolean per id (same store the panel
// reads), so the toggle UI and the panel agree without a DataStore round-trip.
data class SwitcherShortcut(
    val id: String,
    val label: String,
    val target: String,
    val defaultOn: Boolean
)

// Order matters: this column renders bottom-up (index 0 sits nearest the finger; the last entry sits
// just under the "Keyboard UI" column header). So "All settings"/"Special keys" are first (low, easy
// to reach) and "Keyboard editor" is last (directly under the Keyboard UI header). "Themes" removed.
val LayoutSwitcherShortcutCatalog = listOf(
    SwitcherShortcut("settings", "All settings", "!nav/home", true),
    SwitcherShortcut("special_keys", "Special keys", "!nav/specialKeys", true),
    SwitcherShortcut("add_layout", "Add layout", "!nav/addLanguage", true),
    SwitcherShortcut("custom_layouts", "Custom layouts", "!nav/devlayouteditor", false),
    SwitcherShortcut("resize", "Resize keyboard", "!nav/resize", false),
    SwitcherShortcut("languages", "Languages", "!nav/languages", false),
    SwitcherShortcut("keyboard_editor", "Keyboard editor", "!nav/keyboardeditor", true)
)

fun layoutSwitcherShortcutPrefKey(id: String) = "kxkb_switcher_shortcut_$id"

object Subtypes {
    // Removes extensions from existing existing subtypes which are not meant to be there
    private fun removeExtensionsIfNecessary(context: Context) {
        val currentSubtypes = context.getSettingBlocking(SubtypesSetting)
        if(currentSubtypes.isEmpty()) return

        val newSubtypes = currentSubtypes.map {
            val subtype = convertToSubtype(it)
            if(subtype.locale.contains("_#u-")) {
                subtypeToString(InputMethodSubtypeBuilder()
                    .setSubtypeLocale(subtype.locale.split("_#u-")[0])
                    .setSubtypeExtraValue(subtype.extraValue)
                    .setLanguageTag(subtype.languageTag)
                    .build())
            } else {
                it
            }
        }.toSet()

        if(newSubtypes != currentSubtypes) {
            Log.w("Subtypes", "Removed extensions: $currentSubtypes - $newSubtypes")
            context.setSettingBlocking(SubtypesSetting.key, newSubtypes)
        }
    }

    fun addDefaultSubtypesIfNecessary(context: Context) {
        if(!context.isDirectBootUnlocked) return

        val currentSubtypes = context.getSettingBlocking(SubtypesSetting)
        if(currentSubtypes.isNotEmpty()) {
            removeExtensionsIfNecessary(context)
            return
        }

        val locales = context.resources.configuration.locales
        if(locales.size() == 0) return

        var numAdded = 0
        for(i in 0 until locales.size()) {
            val locale = locales.get(i).stripExtensionsIfNeeded()
            val layout = findClosestLocaleLayouts(context, locale).firstOrNull() ?: continue

            addLanguage(context, locale, layout)
            numAdded += 1
        }

        if(numAdded == 0) {
            // We need to have something...
            addLanguage(context, Locale.forLanguageTag("zz"), "qwerty")
        }

        context.setSettingBlocking(ActiveSubtype.key, context.getSettingBlocking(SubtypesSetting).firstOrNull() ?: return)
    }

    fun findClosestLocaleLayouts(context: Context, locale: Locale): List<String> {
        val supportedLocales = LayoutManager.getLayoutMapping(context)

        val perfectMatch = supportedLocales.keys.find { it.language == locale.language && it.country == locale.country }
        val languageMatch = supportedLocales.keys.find { it.language == locale.language }

        val match = perfectMatch ?: languageMatch

        return match?.let { supportedLocales[it] } ?: listOf()
    }

    fun convertToSubtype(string: String): InputMethodSubtype {
        val splits = string.split(":")
        val locale = splits[0]

        val extraValue = splits.getOrNull(1) ?: ""
        val languageTag = splits.getOrNull(2) ?: ""

        return InputMethodSubtypeBuilder()
            .setSubtypeLocale(locale)
            .setSubtypeExtraValue(extraValue)
            .setLanguageTag(languageTag)
            .build()
    }

    fun getActiveSubtype(context: Context): InputMethodSubtype {
        val activeSubtype = context.getSettingBlocking(ActiveSubtype).ifEmpty {
            context.getSettingBlocking(SubtypesSetting).firstOrNull() ?: "en_US:"
        }

        return convertToSubtype(activeSubtype)
    }

    fun hasMultipleEnabledSubtypes(context: Context): Boolean {
        return context.getSettingBlocking(SubtypesSetting).size > 1
    }

    fun subtypeToString(subtype: InputMethodSubtype): String {
        return subtype.locale + ":" + (subtype.extraValue ?: "") + ":" + subtype.languageTag
    }

    fun removeLanguage(context: Context, entry: InputMethodSubtype) {
        val value = subtypeToString(entry)
        val currentSetting = context.getSettingBlocking(SubtypesSetting)

        context.setSettingBlocking(SubtypesSetting.key, currentSetting.filter { it != value && it != value.replace("::", ":") }.toSet())

        if(context.getSettingBlocking(ActiveSubtype) == value) {
            context.setSettingBlocking(ActiveSubtype.key, currentSetting.find {
                it != value
            } ?: "")
        }
    }

    // TODO: Set extra value MultilingualTyping=en;lt;etc
    fun makeSubtype(locale: String, layout: String): InputMethodSubtype =
        InputMethodSubtypeBuilder()
            .setSubtypeLocale(locale)
            .setSubtypeExtraValue("KeyboardLayoutSet=$layout")
            .build()

    fun addLanguage(context: Context, language: Locale, layout: String) {
        val value = subtypeToString(makeSubtype(
            language.stripExtensionsIfNeeded().toString(), layout
        ))

        val currentSetting = context.getSettingBlocking(SubtypesSetting)

        context.setSettingBlocking(SubtypesSetting.key, currentSetting + setOf(value))
    }

    fun getName(inputMethodSubtype: InputMethodSubtype): String {
        val locale = getLocale(inputMethodSubtype)
        return getLocaleDisplayName(locale, locale)
    }

    fun getNameForLocale(locale: String): String {
        return getName(InputMethodSubtypeBuilder().setSubtypeLocale(locale).build())
    }

    fun getLocale(locale: String): Locale {
        return localeFromString(locale).stripExtensionsIfNeeded()
    }

    fun getLocale(inputMethodSubtype: InputMethodSubtype): Locale {
        return getLocale(inputMethodSubtype.locale)
    }

    fun getLayoutName(context: Context, layout: String): String {
        return LayoutManager.getLayoutOrNull(context, layout)?.name ?: layout
    }

    fun getLocaleDisplayName(locale: Locale, nameLocale: Locale): String {
        val definedName = LayoutManager.getExceptionalNameForLocale(locale, nameLocale)
        if(definedName != null) return definedName

        val localeString = locale.toString()
        if(SubtypeLocaleUtils.isExceptionalLocale(localeString)) {
            return SubtypeLocaleUtils.getSubtypeLocaleDisplayNameInternal(localeString, nameLocale)
        } else {
            return locale.getDisplayName(nameLocale)
        }
    }

    fun layoutsMappedByLanguage(layouts: Set<String>): Map<String, List<InputMethodSubtype>> {
        val subtypes = layouts.map {
            convertToSubtype(it)
        }

        return HashMap<String, MutableList<InputMethodSubtype>>().apply {
            subtypes.forEach {
                val list = this.getOrPut(it.locale) { mutableListOf() }
                list.add(it)
            }
        }
    }

    fun getDirectBootInitialLayouts(context: Context): Set<String> {
        // BFU (direct boot): expose only the kxkb layout for unlock-password entry.
        return setOf("en_US:KeyboardLayoutSet=kxkb")
    }

    fun switchToNextLanguage(
        context: Context,
        direction: Int
    ): Boolean {
        if(direction == 0) return true

        val enabledSubtypes = context.getSettingBlocking(SubtypesSetting).toList()
        val currentSubtype = context.getSettingBlocking(ActiveSubtype)

        if(enabledSubtypes.isEmpty()) return false

        if(enabledSubtypes.size == 1 && currentSubtype == enabledSubtypes.first()) {
            return false
        }

        val index = enabledSubtypes.indexOf(currentSubtype)
        val nextIndex = if(index == -1) {
            0
        } else {
            (index + direction.sign).mod(enabledSubtypes.size)
        }

        context.setSettingBlocking(ActiveSubtype.key, enabledSubtypes[nextIndex])
        return true
    }

    // kxkb: the active subtype string (raw), for the swipe-space layout switcher.
    fun getActiveSubtypeString(context: Context): String =
        context.getSettingBlocking(ActiveSubtype).ifEmpty {
            context.getSettingBlocking(SubtypesSetting).firstOrNull() ?: ""
        }

    private const val RECENCY_CAP = 16
    private fun langOf(subtypeString: String): String =
        getLocale(subtypeString.split(":").firstOrNull() ?: "").language
    private fun layoutOf(subtypeString: String): String =
        subtypeString.split(":").getOrNull(1)?.substringAfter("KeyboardLayoutSet=", "") ?: ""

    // kxkb: most-recent-first list of subtype strings (see LayoutRecency).
    fun getLayoutRecency(context: Context): List<String> =
        context.getSettingBlocking(LayoutRecency).split("\n").filter { it.isNotEmpty() }

    // kxkb: record a switch. Suspend so it can be called from the ActiveSubtype observer coroutine
    // without runBlocking nesting. Prepends (dedup), caps the list.
    suspend fun pushLayoutRecency(context: Context, subtypeString: String) {
        if (subtypeString.isEmpty()) return
        val cur = context.getSetting(LayoutRecency).split("\n").filter { it.isNotEmpty() }
        if (cur.firstOrNull() == subtypeString) return
        val updated = (listOf(subtypeString) + cur.filter { it != subtypeString }).take(RECENCY_CAP)
        context.setSetting(LayoutRecency.key, updated.joinToString("\n"))
    }

    // kxkb: enabled subtypes sharing the active subtype's language, ordered active-first then by
    // recency then enabled order, capped at 4, returned as (subtypeString, layoutDisplayName) pairs.
    // Backs the swipe-space switcher's layouts column.
    fun getCurrentLanguageLayouts(context: Context): List<Pair<String, String>> {
        val enabled = context.getSettingBlocking(SubtypesSetting).toList()
        if (enabled.isEmpty()) return emptyList()
        val active = getActiveSubtypeString(context)
        if (active.isEmpty()) return emptyList()
        val activeLang = langOf(active)
        val sameLang = enabled.filter { langOf(it) == activeLang }
        val recency = getLayoutRecency(context)
        val ordered = LinkedHashSet<String>()
        if (active in sameLang) ordered.add(active)
        recency.forEach { if (it in sameLang) ordered.add(it) }
        sameLang.forEach { ordered.add(it) }
        return ordered.take(4).map { it to getLayoutName(context, layoutOf(it)) }
    }

    // kxkb: distinct OTHER languages among enabled subtypes (not the active language), ordered by
    // recency then enabled order, capped at 4. Each entry targets that language's last-used layout
    // (recency, else first enabled for it). Returns (targetSubtypeString, nativeLanguageName) pairs.
    // Backs the swipe-space switcher's languages column.
    fun getOtherLanguageEntries(context: Context): List<Pair<String, String>> {
        val enabled = context.getSettingBlocking(SubtypesSetting).toList()
        if (enabled.isEmpty()) return emptyList()
        val active = getActiveSubtypeString(context)
        val activeLang = if (active.isEmpty()) "" else langOf(active)
        val recency = getLayoutRecency(context)

        val langs = LinkedHashSet<String>()
        recency.forEach { if (it in enabled && langOf(it) != activeLang) langs.add(langOf(it)) }
        enabled.forEach { if (langOf(it) != activeLang) langs.add(langOf(it)) }

        return langs.take(4).mapNotNull { lang ->
            val target = recency.firstOrNull { it in enabled && langOf(it) == lang }
                ?: enabled.firstOrNull { langOf(it) == lang } ?: return@mapNotNull null
            val locale = getLocale(target.split(":").first())
            target to getLocaleDisplayName(locale, locale)
        }
    }

    // kxkb: switch to a specific (language, layout) subtype. Writing ActiveSubtype is observed by
    // LatinIME, which performs the actual keyboard switch (and records recency).
    fun switchToSubtypeString(context: Context, subtypeString: String) {
        context.setSettingBlocking(ActiveSubtype.key, subtypeString)
    }

    // kxkb: cycle to the NEXT enabled layout sharing the active subtype's language (in enabled order,
    // wrapping), without changing language. No-op if that language has fewer than two enabled layouts.
    // Backs the "Next layout (same language)" action (e.g. the bottom-row cog's tap).
    fun switchToNextLayoutInLanguage(context: Context) {
        val enabled = context.getSettingBlocking(SubtypesSetting).toList()
        if (enabled.isEmpty()) return
        val active = getActiveSubtypeString(context)
        if (active.isEmpty()) return
        val sameLang = enabled.filter { langOf(it) == langOf(active) }
        if (sameLang.size < 2) return
        val idx = sameLang.indexOf(active)
        val next = sameLang[(idx + 1) % sameLang.size]
        if (next != active) switchToSubtypeString(context, next)
    }

    // kxkb: the enabled left-column shortcuts, in catalog order, as (target, label) pairs.
    fun getLayoutSwitcherShortcuts(context: Context): List<Pair<String, String>> {
        val prefs = PreferenceUtils.getDefaultSharedPreferences(context)
        return LayoutSwitcherShortcutCatalog
            .filter { prefs.getBoolean(layoutSwitcherShortcutPrefKey(it.id), it.defaultOn) }
            .map { it.target to it.label }
    }

    fun getMultilingualBucket(context: Context, locale: Locale): List<Locale> {
        val set = context.getSetting(MultilingualBucketSetting).map {
            getLocale(it)
        }
        if(!set.contains(locale)) {
            return emptyList()
        } else {
            return set.filter { it != locale }
        }
    }
}


@Composable
@Preview
fun LanguageSwitcherDialog(
    onDismiss: () -> Unit = { },
    switchToIme: (InputMethodInfo) -> Unit = { }
) {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    val subtypeSet = if(inspection) {
        setOf("en_US:", "pt_PT:KeyboardLayoutSet=portugues:", "lt:", "fr:KeyboardLayoutSet=bepo:")
    } else {
        useDataStoreValue(SubtypesSetting)
    }

    val subtypes = remember(subtypeSet) {
        Subtypes.layoutsMappedByLanguage(subtypeSet)
    }

    val keys = remember(subtypes) { subtypes.keys.toList().sorted() }

    val activeSubtype = if(inspection) {
        "pt_PT:KeyboardLayoutSet=portugues:"
    } else {
        useDataStoreValue(ActiveSubtype)
    }

    val activeIMEs = if(LocalInspectionMode.current) {
        listOf(
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Joe's Keyboard", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Example Keyboard", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", "Company's Very Special Keyboard Application", ""),
            InputMethodInfo("com.example.Keyboard", ".Keyboard", null, ""),
        )
    } else {
        remember {
            RichInputMethodManager.init(context)
            RichInputMethodManager.getInstance().enabledInputMethodList.filter {
                it.packageName != context.packageName
            }
        }
    }

    Surface(shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.background) {
        Column {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.select_language),
                textAlign = TextAlign.Center,
                style = Typography.Heading.MediumMl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(0.dp, 16.dp)
            )

            LazyColumn(modifier = Modifier.weight(1.0f)) {
                items(keys) { locale ->
                    subtypes[locale]!!.forEach { subtype ->
                        val layoutSetName = subtype.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: ""

                        val layout = if(inspection) { layoutSetName } else { Subtypes.getLayoutName(context, layoutSetName) }
                        val title = if(inspection) { subtype.locale } else { Subtypes.getName(subtype) }

                        val selected = activeSubtype == Subtypes.subtypeToString(subtype)

                        val item = @Composable {
                            NavigationItem(
                                title = title,
                                subtitle = layout.ifBlank { null },
                                style = NavigationItemStyle.MiscNoArrow,
                                navigate = {
                                    context.setSettingBlocking(ActiveSubtype.key, Subtypes.subtypeToString(subtype))
                                    onDismiss()
                                },
                                icon = if(selected) painterResource(R.drawable.check_circle) else painterResource(R.drawable.circle)
                            )
                        }

                        if (selected) {
                            Surface(color = MaterialTheme.colorScheme.primary) {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
                                    item()
                                }
                            }
                        } else {
                            item()
                        }
                    }
                }

                item {
                    if(activeIMEs.isNotEmpty() && keys.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                    }
                }

                items(activeIMEs) { ime ->
                    val title = try {
                        ime.loadLabel(context.packageManager)?.toString()
                    } catch(_: Exception) {
                        null
                    } ?: ime.id

                    NavigationItem(
                        title = title,
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            switchToIme(ime)
                        },
                        compact = true,
                        icon = painterResource(R.drawable.circle)
                    )
                }
            }

            Row(modifier = Modifier.height(64.dp)) {
                Spacer(modifier = Modifier.weight(1.0f))
                TextButton(onClick = {
                    val inputMethodManager =
                        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.showInputMethodPicker()

                    onDismiss()
                }) {
                    Text(stringResource(R.string.keyboard_switch_keyboard))
                }
                TextButton(onClick = {
                    val intent = Intent()
                    intent.setClass(context, SettingsActivity::class.java)
                    intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    intent.putExtra("navDest", "languages")
                    context.startActivity(intent)

                    onDismiss()
                }) {
                    Text(stringResource(R.string.keyboard_language_settings))
                }
                Spacer(modifier = Modifier.width(32.dp))
            }
        }
    }
}