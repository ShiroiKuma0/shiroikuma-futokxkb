package org.futo.inputmethod.latin.uix.settings.pages

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.Tip
import java.util.Locale

// kxkb: pins the locale used for THIS app's own screens (Settings, keyboard chrome) independently of
// the phone's system language, via the AndroidX per-app-language API. "System default" clears the
// override (follows the phone again). The list of offered languages is the set the app is actually
// translated into — emitted at build time into R.array.kxkb_app_locales from the translation res dirs
// (see the generateAppLocales task in build.gradle); if that's somehow empty we fall back to the
// locales present in the resource table. Keyboard *typing* languages remain under Languages & Models.

// kxkb: we persist the chosen app-UI locale ourselves so it survives process death. On API 33+ the
// framework already stores & re-applies the per-app locale, but on API < 33 AppCompat's auto-storage
// only re-reads it when an AppCompatActivity is created — and our Settings UI is a plain
// ComponentActivity, so nothing repopulates it on a cold start and it silently reverts to the system
// language. We therefore mirror the tag into our own pref and re-apply it from the Application on
// startup (see restoreAppLocale, called from CrashLoggingApplication.onCreate). Empty = system default.
private const val PREF_APP_LOCALE = "kxkb_app_locale"

// Set the app-UI locale AND remember it. Call from the picker.
fun setAppLocale(context: Context, tag: String) {
    PreferenceUtils.getDefaultSharedPreferences(context).edit { putString(PREF_APP_LOCALE, tag) }
    AppCompatDelegate.setApplicationLocales(
        if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
    )
}

// Re-apply the remembered app-UI locale at app startup. No-op if the framework already restored one
// (API 33+) or if none was ever chosen. Safe to call early; guarded against locked/direct-boot storage.
fun restoreAppLocale(context: Context) {
    try {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        val tag = PreferenceUtils.getDefaultSharedPreferences(context)
            .getString(PREF_APP_LOCALE, "") ?: ""
        if (tag.isNotEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun localeMatches(current: String, tag: String): Boolean {
    if (current.equals(tag, ignoreCase = true)) return true
    val c = Locale.forLanguageTag(current)
    val t = Locale.forLanguageTag(tag)
    return c.language.equals(t.language, ignoreCase = true)
            && (c.country.isEmpty() || t.country.isEmpty() || c.country.equals(t.country, ignoreCase = true))
}

@Composable
private fun LocaleRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    SettingItem(title = title, subtitle = subtitle, onClick = onClick) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.app_language_selected),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AppLanguageScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val applied = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (applied.isEmpty) "" else (applied.toLanguageTags().substringBefore(','))

    // Languages the app is translated into (build-generated); fall back to the resource table.
    val arrayTags = resources.getStringArray(R.array.kxkb_app_locales).filter { it.isNotBlank() }
    val rawTags = if (arrayTags.isNotEmpty()) {
        arrayTags
    } else {
        resources.assets.locales.toList().filter { it.isNotBlank() }
    }
    val locales = rawTags
        .map { tag -> tag to Locale.forLanguageTag(tag) }
        .distinctBy { (_, l) -> l.toLanguageTag() }
        .sortedBy { (_, l) -> l.getDisplayName(l).lowercase(l) }

    fun apply(tag: String) {
        setAppLocale(context, tag)
        // On API < 33 the framework won't recreate a plain ComponentActivity for the locale change,
        // so trigger it ourselves. On 33+ the framework persists and recreates automatically.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            (context as? Activity)?.recreate()
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.app_language_title), showBack = true, navController)
        Tip(stringResource(R.string.app_language_tip))

        LocaleRow(
            title = stringResource(R.string.app_language_system_default),
            subtitle = null,
            selected = currentTag.isEmpty(),
            onClick = { apply("") }
        )

        locales.forEach { (tag, locale) ->
            val autonym = locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
            val english = locale.getDisplayName(Locale.ENGLISH)
            LocaleRow(
                title = autonym,
                subtitle = if (english.equals(autonym, ignoreCase = true)) null else english,
                selected = currentTag.isNotEmpty() && localeMatches(currentTag, tag),
                onClick = { apply(tag) }
            )
        }
    }
}
