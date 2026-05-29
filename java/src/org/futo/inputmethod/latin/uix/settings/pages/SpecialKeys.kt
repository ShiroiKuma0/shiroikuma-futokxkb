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

        CollapsibleSection("Column (vertical predictive multi-key)") {
            Para("The vertical sibling of a cluster. The `main` glyphs stack TOP-TO-BOTTOM as the predictive band; everything else behaves exactly like a cluster — a plain tap commits the MIDDLE main, and the tap is weighed against all the band's letters so prediction picks the right one from word context.")
            Para("Centre form — `main` is the band, top→bottom; the middle commits on tap:")
            Yaml("- { type: column, main: \"aev\" }")
            Para("To type the TOP or BOTTOM main precisely, slide up or down (mirroring cluster's left/right) — so key sliding should be on. The optional 3 + 3 small side keys are the left column (upLeft / left / downLeft) and right column (upRight / right / downRight), each any key or a { mod }/{ chord }/{ macro } shorthand, reached by sliding toward them and drawn small in their corners:")
            Yaml("- type: column\n  main: \"aev\"\n  left: \"(\"\n  right: \")\"\n  upLeft: \"1\"\n  downRight: { macro: \"…\" }")
            Para("Prediction is letter-only, same as cluster (non-letter mains commit but never predict). up/down are reserved for the top/bottom band ends and aren't author-settable. The band glyphs draw stacked and slightly smaller so three fit; the side keys draw at the small 4D-label size in their corners.")
        }

        CollapsibleSection("Case (per-shift-state key)") {
            Para("A `case` swaps the WHOLE key out depending on the keyboard's current shift state — it is not one key with variants, it is a chooser that picks a different key per state. Each branch can be ANY key type (base, compass, cluster, macro, a gap…), so the key can change glyph, type, and behaviour as you shift.")
            Para("The branches, in priority order: `normal` (the only required one — the fallback for any unlisted state), `shifted` (any shifted state), then the more specific `shiftedManually` (one-shot ⇧), `shiftLocked` (caps-lock), `symbols` (the ?123 page) and `symbolsShifted` (its shifted page). A state falls back to `shifted`, then to `normal`, when its own branch is absent.")
            Yaml("- type: case\n  normal: a\n  shifted: A")
            Para("What it is GOOD for — differences that go beyond upper-casing one letter: a different glyph per state (normal `,` → shifted `;`), a different key TYPE per state (a plain letter that becomes a compass or macro when shifted), or a key present in only some states (a gap on the symbols page). Caps-lock vs one-shot can also differ — e.g. show a lock glyph only under `shiftLocked`.")
            Yaml("- type: case\n  normal: { type: compass, primary: e, up: 3 }\n  shifted: E\n  shiftLocked: { type: macro, text: \"E\", label: \"E (locked)\" }")
            Para("What you NO LONGER need it for — clusters and columns now upper-case their whole predictive band automatically when the layout is shifted (the band glyphs AND the prediction follow the shift), so you don't have to wrap one in a `case` just to capitalise it. Set `attributes: { shiftable: false }` on the cluster/column to opt a band out of auto-capitalisation. Plain base keys and compass keys have always auto-capitalised this way; `case` remains for when the states must differ in more than letter case.")
        }

        CollapsibleSection("Spanning rows (tall keys)") {
            Para("Any key can be made several rows tall with the heightRows attribute — it then occupies its own row plus the next (heightRows − 1) rows. Useful for flanking a multi-row block (e.g. column keys beside a 3×3 navigation grid).")
            Yaml("- { type: column, main: \"aev\", attributes: { heightRows: 3 } }")
            Para("There is no column grid: the engine lays out one row at a time, so a tall key does NOT automatically push the rows below it aside. In each row the tall key covers, reserve its cell(s) with a `gap` key of matching width, and keep the widths uniform so the columns line up underneath. A row with a tall key and the rows it spans should still each describe the full width (tall key / gaps + the other keys). Width spanning is separate — use the Custom width tokens for a key that is several columns wide.")
        }

        CollapsibleSection("Hide keyboard") {
            Para("An action key that dismisses the keyboard (the old Multiling [HIDE]). It is the Hide-keyboard action bound to a key — its glyph is a downward chevron and a press closes the keyboard. Works as a standalone key or inside a compass/cluster slide slot.")
            Yaml("- { type: base, spec: \"!icon/action_hide_keyboard|!code/action_hide_keyboard\" }")
            Para("As a slide target — e.g. a down-flick to hide (key sliding must be on):")
            Yaml("- type: compass\n  primary: \"*\"\n  down: \"!icon/action_hide_keyboard|!code/action_hide_keyboard\"")
            Para("Any action can ride a key this way: !code/action_<id> runs it and !icon/action_<id> draws its glyph. (An icon-only slide target shows on the key face but not in the long-press preview.)")
        }

        CollapsibleSection("Next layout (same language)") {
            Para("An action key that switches to the NEXT layout within the current language only — it cycles through your enabled layouts for that language (wrapping at the end) and never changes language. It's a no-op if the active language has just one layout. Tapping it repeatedly rotates through e.g. your GNU layouts without touching your other languages.")
            Yaml("- { type: base, spec: \"!icon/action_next_language_layout|!code/action_next_language_layout\" }")
            Para("Tip: put it on a key whose long-press you still want for something else — give the key its own moreKeys, so tap cycles layouts while long-press opens that popup (e.g. the bottom-row cog: tap = next layout, hold = its symbol list):")
            Yaml("- { type: base, spec: \"!icon/settings_key|!code/action_next_language_layout\",\n    moreKeys: [\"%\", \"\\\"\", \":\", \"'\", \"@\"] }")
        }

        CollapsibleSection("Alt pages (extra layers) & return to letters") {
            Para("If your layout defines an altPages: block (up to four extra full pages, alt0–alt3), these keys switch the WHOLE keyboard to one of them. The page stays shown until you go back — a sticky layer, not a momentary one.")
            Para("Switch to a page — fires the alt-page code (the ${'$'}alt0 / ${'$'}alt1 / ${'$'}alt2 template keys do the same):")
            Yaml("- { type: base, spec: \"sym|!code/key_to_alt_0_layout\" }")
            Para("Return to your normal layout — two ways. Typing any ordinary character returns automatically (alt pages auto-revert after one keystroke). Or put a return-to-letters key on the page:")
            Yaml("- { type: base, spec: \"ABC|!code/key_to_alpha_0_layout\" }")
            Para("key_to_alpha_0_layout jumps straight to the base alphabet from ANY page, so the identical key works on every alt page (the label — \"ABC\" here — is just text; use whatever you like). Don't use the ${'$'}alphabet template for this: it toggles to the symbols layer, not your letters.")
        }

        CloseToKeyboardButton()
    }
}
