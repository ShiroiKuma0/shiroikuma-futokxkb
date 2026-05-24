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
