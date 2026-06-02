package org.futo.inputmethod.latin.uix.actions

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

// kxkb quick-access action-bar icons (the row above the suggestion bar).

// Reactive read of PREF_KEY_SLIDING for the action bar. We can't use useSharedPrefsBool here because
// that hook requires a SharedPrefsCacheProvider in the composition (present in SettingsActivity but
// NOT in the keyboard's action bar — using it there throws "Shared prefs cache was not provided!").
// Instead read the SharedPreferences directly and recompose via a change listener.
@Composable
private fun keySlidingEnabledState(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceUtils.getDefaultSharedPreferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_KEY_SLIDING, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || key == Settings.PREF_KEY_SLIDING) {
                enabled = p.getBoolean(Settings.PREF_KEY_SLIDING, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

// Flip "key sliding" (4D directional flicks) on/off from the action bar. Writing PREF_KEY_SLIDING
// triggers the IME's settings reload, which re-applies the mutual exclusion live (PointerTracker's
// flick gate + the glide gate in SettingsValues): turning key sliding on forces swipe/glide off,
// turning it off restores swipe. So this single icon switches between the two slide behaviours.
val KeySlidingToggleAction = Action(
    icon = R.drawable.swipe_icon,
    name = R.string.action_key_sliding_toggle,
    simplePressImpl = { manager, _ ->
        val prefs = PreferenceUtils.getDefaultSharedPreferences(manager.getContext())
        val current = prefs.getBoolean(Settings.PREF_KEY_SLIDING, false)
        prefs.edit { putBoolean(Settings.PREF_KEY_SLIDING, !current) }
    },
    windowImpl = null,
    // Filled background when SWIPE is active (i.e. key sliding off); plain when key sliding is on.
    activeStateProvider = { !keySlidingEnabledState() },
)

// Reactive read of PREF_TERMINAL_PREDICTION for the action bar (same pattern as keySlidingEnabledState).
@Composable
private fun terminalPredictionEnabledState(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceUtils.getDefaultSharedPreferences(context) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(Settings.PREF_TERMINAL_PREDICTION, false)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == null || key == Settings.PREF_TERMINAL_PREDICTION) {
                enabled = p.getBoolean(Settings.PREF_TERMINAL_PREDICTION, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

// kxkb: toggle "terminal prediction" on/off from the action bar. Raw-input fields (TYPE_NULL terminals
// like Termux) normally disable composing/suggestions and send raw key events; turning this on forces
// composing + prediction + commit there, so candidates work and a space-committed word is sent to the
// remote over SSH. Off by default. Writing PREF_TERMINAL_PREDICTION triggers the IME's settings reload
// (Settings rebuilds SettingsValues, where needsToLookupSuggestions()/shouldInsertSpacesAutomatically()
// honour the flag), so it applies live. Highlighted (filled) while on, like the swipe icon.
val TerminalPredictionToggleAction = Action(
    icon = R.drawable.text_prediction,
    name = R.string.action_terminal_prediction_toggle,
    simplePressImpl = { manager, _ ->
        val prefs = PreferenceUtils.getDefaultSharedPreferences(manager.getContext())
        val current = prefs.getBoolean(Settings.PREF_TERMINAL_PREDICTION, false)
        prefs.edit { putBoolean(Settings.PREF_TERMINAL_PREDICTION, !current) }
    },
    windowImpl = null,
    activeStateProvider = { terminalPredictionEnabledState() },
)

// Open the kxkb "Live sizing" settings page (per-geometry sizes, gaps, colours) directly, deep-linked
// via the SettingsActivity "navDest" extra to the existing "kxkbSizing" route.
val LiveResizeAction = Action(
    icon = R.drawable.aspect_ratio,
    name = R.string.action_live_resize,
    simplePressImpl = { manager, _ ->
        val intent = Intent()
        intent.setClass(manager.getContext(), SettingsActivity::class.java)
        intent.putExtra("navDest", "kxkbSizing")
        // SINGLE_TOP + CLEAR_TOP (not RESET_TASK_IF_NEEDED): with the default launchMode this forces
        // SettingsActivity through onCreate, where the "navDest" extra is read. RESET_TASK_IF_NEEDED
        // could reuse an existing instance without re-running onCreate, landing on the root page.
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        manager.getContext().startActivity(intent)
    },
    windowImpl = null,
)

// kxkb: hide the keyboard — the old Multiling "[HIDE]". Simple-press (no window) so it can sit on a
// key / flick target via `!code/action_hide_keyboard` (icon `!icon/action_hide_keyboard`, auto-
// registered from this action's drawable). Calls InputMethodService.requestHideSelf via the manager.
// Not placed in any action-bar category — it's only meant for keys.
val HideKeyboardAction = Action(
    icon = R.drawable.chevron_down,
    name = R.string.action_hide_keyboard_title,
    simplePressImpl = { manager, _ ->
        manager.requestHideSelf()
    },
    windowImpl = null,
)

// kxkb: switch to the next enabled layout WITHIN the current language only (cycling, wrapping), with
// no language change. No-op if the active language has just one enabled layout. Used as the tap of
// the bottom-row cog (whose long-press moreKeys are kept), and available as a standalone action key.
val NextLanguageLayoutAction = Action(
    icon = R.drawable.keyboard,
    name = R.string.action_next_language_layout_title,
    simplePressImpl = { manager, _ ->
        org.futo.inputmethod.latin.Subtypes.switchToNextLayoutInLanguage(manager.getContext())
    },
    windowImpl = null,
)

// Open the kxkb "Special keys" reference page (Esc / Ctrl / 4D) directly, deep-linked via the
// SettingsActivity "navDest" extra to the "specialKeys" route. Same flags as LiveResizeAction so a
// reused SettingsActivity still navigates there (handled by SettingsActivity.onNewIntent).
val SpecialKeysAction = Action(
    icon = R.drawable.book,
    name = R.string.action_special_keys,
    simplePressImpl = { manager, _ ->
        val intent = Intent()
        intent.setClass(manager.getContext(), SettingsActivity::class.java)
        intent.putExtra("navDest", "specialKeys")
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        manager.getContext().startActivity(intent)
    },
    windowImpl = null,
)

// kxkb: open the Personal Dictionary editor directly from the action bar. This is the "Edit Personal
// Dictionary" page (Settings → Predictive Text), where you add custom words — including Japanese kanji
// entered with their hiragana reading + part-of-speech, which Mozc then offers as a conversion
// candidate. Deep-linked via the SettingsActivity "navDest" extra to the "pdict" route; same flags as
// the other launchers so a reused SettingsActivity still navigates there (handled by onNewIntent).
val PersonalDictionaryAction = Action(
    icon = R.drawable.icon_spellcheck,
    name = R.string.action_personal_dictionary,
    simplePressImpl = { manager, _ ->
        val intent = Intent()
        intent.setClass(manager.getContext(), SettingsActivity::class.java)
        intent.putExtra("navDest", "pdict")
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        manager.getContext().startActivity(intent)
    },
    windowImpl = null,
)

// Open the Developer-settings "Custom layouts" editor directly, deep-linked via the SettingsActivity
// "navDest" extra to the "devlayouteditor" route. Same flags as the other launchers so a reused
// SettingsActivity still navigates there (handled by SettingsActivity.onNewIntent).
val CustomLayoutsAction = Action(
    icon = R.drawable.code,
    name = R.string.action_custom_layouts,
    simplePressImpl = { manager, _ ->
        val intent = Intent()
        intent.setClass(manager.getContext(), SettingsActivity::class.java)
        intent.putExtra("navDest", "devlayouteditor")
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
                    or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        manager.getContext().startActivity(intent)
    },
    windowImpl = null,
)
