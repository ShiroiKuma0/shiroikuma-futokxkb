package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.CollapsibleSection
import org.futo.inputmethod.latin.uix.settings.findActivity
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSliderSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.theme.Typography

// kxkb Special Keys reference. An info page describing each special key kind the layouts support and
// the exact YAML to drop into a key slot. Grows as new kinds are added (chord/macro/action, the
// compact 4d syntax, etc.). Reached from Keyboard settings and from the action-bar Special Keys icon.

@Composable
private fun Para(text: String) {
    Text(
        text,
        modifier = Modifier.padding(16.dp, 4.dp),
        style = Typography.Body.RegularMl
    )
}

@Composable
private fun Yaml(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 4.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(10.dp),
            fontFamily = FontFamily.Monospace,
            style = Typography.Body.RegularMl
        )
    }
}

@Composable
private fun CloseToKeyboardButton() {
    val context = LocalContext.current
    // Finish the settings activity to return straight to the keyboard, matching the Live sizing page.
    OutlinedButton(
        onClick = { context.findActivity()?.finish() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(stringResource(R.string.kxkb_sizing_close_to_keyboard))
    }
}

@Composable
fun SpecialKeysScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.special_keys_title), showBack = true, navController)

        CloseToKeyboardButton()

        Tip("These go in your custom-layout YAML (dev layouts). Each section says what the key does and gives the exact snippet to drop into a key slot.")

        CollapsibleSection("Escape (Esc)") {
            Para("Sends a real Escape keystroke (KEYCODE_ESCAPE) — useful in terminals, Emacs, vim, and the like. It types no character; it is a control key.")
            Yaml("- { type: base, spec: \"Esc|!code/key_escape\" }")
        }

        CollapsibleSection("Control (Ctrl)") {
            Para("A one-shot Control modifier: tap it, then the next key is sent as Ctrl+<key> (tap Ctrl, then c → Ctrl+C). It does not latch — it only affects the single next key.")
            Yaml("- { type: base, spec: \"Ctrl|!code/key_ctrl\" }")
        }

        CollapsibleSection("Compass keys (key sliding)") {
            Para("A key with a tap (primary) plus up to eight directional slide targets. With \"Key sliding\" on (the action-bar icon, or the Typing settings toggle), a short slide off the key in any of eight directions types that direction's target; a plain tap types the primary.")
            Para("Compact form — one codepoint per slot, in order up, down, left, right, up-left, up-right, down-left, down-right. A space skips that slot; a short string leaves the rest empty:")
            Yaml("- { type: compass, primary: o, slide: \"Oó Ō ÓöÅ\" }")
            Para("Explicit form — override any direction with a full key (a bare char, a macro, etc.). Overrides win over the compact string, and you can mix the two:")
            Yaml("- type: compass\n  primary: e\n  up: E\n  right: á\n  down: { type: macro, text: \"etc. \", label: etc }")
            Para("Slot shorthands — a direction can be a compact rich slot (no type: needed): { mod: ctrl } is a chord on the primary (mod = ctrl/alt/shift/super), { chord: \"C-x C-s\" } a chord, { macro: \"etc. \" } a macro. Each takes an optional label; { mod: ctrl, on: x } targets an explicit base instead of the primary.")
            Yaml("- type: compass\n  primary: o\n  up: O\n  down: { mod: ctrl }\n  left: { mod: alt }\n  right: ó")
            Para("While key sliding is on, the slide targets are drawn small on the key face in a 3×3 grid. Their positions are tunable per fold-state under Keyboard UI → \"4D label\" sliders.")
            Para("Note: key sliding and swipe/glide typing are mutually exclusive — turning one on turns the other off. (The verbose `type: flick` still works as a lower-level escape hatch.)")
        }

        CollapsibleSection("Macro (literal text)") {
            Para("Types a literal string. Optionally shows a shorter label on the key face; without one, the text itself is the label. Works as a standalone key or inside a compass slide slot.")
            Yaml("- { type: macro, text: \"etc. \", label: etc }")
            Para("The text can contain |, \\, commas, or a leading ! with no escaping needed.")
        }

        CollapsibleSection("Chord (modifier keystrokes)") {
            Para("Emits a modifier+key chord — or a space-separated sequence of them — as real hardware key events (for terminals, Emacs, vim, etc.). Emacs-style notation: each step is MOD-…-BASE.")
            Yaml("- { type: chord, keys: \"C-x C-s\", label: save }")
            Para("Modifiers: C = Ctrl, M = Alt/Meta, S = Shift, s = Super. BASE is a single char (C-x, C--) or a named key: TAB, RET/ENTER, SPC/SPACE, ESC, DEL, UP/DOWN/LEFT/RIGHT, HOME, END, PGUP/PGDN, F1–F12. Shift must be explicit (S-TAB).")
            Para("Works inside a compass slide slot too — e.g. slide down on the o-key to send Ctrl+O:")
            Yaml("- type: compass\n  primary: o\n  down: { type: chord, keys: \"C-o\" }\n  left: { type: chord, keys: \"M-o\" }")
        }

        CollapsibleSection("Cycle (multitap)") {
            Para("Repeated taps of the key cycle through a set, each tap replacing the previous one (classic phone multitap). The window below lapsing, the cursor moving, or any other key ends the cycle so the next tap starts fresh.")
            Para("String form — one tap per codepoint:")
            Yaml("- { type: cycle, taps: \"àâãä\" }")
            Para("List form — for multi-character entries (optionally with a label):")
            Yaml("- { type: cycle, taps: [\".\", \"...\", \"…\"], label: \"…\" }")
            SettingSliderSharedPrefsInt(
                title = "Multitap window",
                key = "kxkb_multitap_timeout",
                default = 800,
                range = 300.0f..1500.0f,
                transform = { it.toInt() },
                indicator = { "$it ms" },
                steps = 11
            )
        }

        CollapsibleSection("Cluster (predictive multi-key)") {
            Para("A wide key carrying a band of main glyphs that share the key predictively (the old Multiling \"3+2\"). A plain tap commits the CENTRE main, but the tap position is considered against ALL the band's letters — the prediction/suggestion engine picks the right one from word context, so you don't have to aim. Type roughly; the candidates appear in the suggestion strip.")
            Para("Centre form — `main` is the band, left→right; the centre commits on tap:")
            Yaml("- { type: cluster, main: \"aev\" }")
            Para("To type the LEFT or RIGHT main precisely (overriding prediction), slide left or right — so key sliding should be on. The six remaining directions are \"extra\" slide targets (up-left/up/up-right, down-left/down/down-right), each any key or a { mod }/{ chord }/{ macro } shorthand, exactly like a compass:")
            Yaml("- type: cluster\n  main: \"aev\"\n  up: \"4\"\n  down: { macro: \"…\" }")
            Para("Prediction is letter-only (the decoder mixes a–z): non-letter mains like ( ; : or digits still commit (centre-tap or precise slide) but never become predictive candidates. left/right are reserved for the side mains and aren't author-settable.")
            Para("Rendering: the mains draw at the primary key size on the centre line — the centre at the key centre, the left/right characters pushed out by their own \"Cluster: left/right character distance\" sliders (default 0.333 = evenly tiled); the extras draw small above and below, by the top/bottom and left/right \"4D label\" sliders — all under Keyboard UI per fold-state. The cluster distance sliders are display-only (they don't move the prediction tap-zones). Needs a prediction model + autocorrect/suggestions for the disambiguation; with prediction off it falls back to centre-tap + precise slides.")
        }

        CloseToKeyboardButton()
    }
}
