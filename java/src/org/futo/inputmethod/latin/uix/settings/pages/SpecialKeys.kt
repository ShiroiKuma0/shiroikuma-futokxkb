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
            Para("While key sliding is on, the slide targets are drawn small on the key face in a 3×3 grid. Their positions are tunable per fold-state under Live sizing → \"4D label\" sliders.")
            Para("Note: key sliding and swipe/glide typing are mutually exclusive — turning one on turns the other off. (The verbose `type: flick` still works as a lower-level escape hatch.)")
        }

        CollapsibleSection("Macro (literal text)") {
            Para("Types a literal string. Optionally shows a shorter label on the key face; without one, the text itself is the label. Works as a standalone key or inside a compass slide slot.")
            Yaml("- { type: macro, text: \"etc. \", label: etc }")
            Para("The text can contain |, \\, commas, or a leading ! with no escaping needed.")
        }

        CloseToKeyboardButton()
    }
}
