package org.futo.inputmethod.latin.uix.actions

import android.content.Intent
import androidx.core.content.edit
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.PreferenceUtils
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

// kxkb quick-access action-bar icons (the row above the suggestion bar).

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
