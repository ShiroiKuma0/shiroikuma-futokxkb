package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.CollapsibleSection
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
fun SpecialKeysScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.special_keys_title), showBack = true, navController)

        Tip("These go in your custom-layout YAML (dev layouts). Each section says what the key does and gives the exact snippet to drop into a key slot.")

        CollapsibleSection("Escape (Esc)") {
            Para("Sends a real Escape keystroke (KEYCODE_ESCAPE) — useful in terminals, Emacs, vim, and the like. It types no character; it is a control key.")
            Yaml("- { type: base, spec: \"Esc|!code/key_escape\" }")
        }

        CollapsibleSection("Control (Ctrl)") {
            Para("A one-shot Control modifier: tap it, then the next key is sent as Ctrl+<key> (tap Ctrl, then c → Ctrl+C). It does not latch — it only affects the single next key.")
            Yaml("- { type: base, spec: \"Ctrl|!code/key_ctrl\" }")
        }

        CollapsibleSection("4D keys (key sliding)") {
            Para("A key with up to eight directional characters. With \"Key sliding\" on (the action-bar icon, or the Typing settings toggle), a short slide off the key in any of eight directions types that direction's character; a plain tap types the primary.")
            Para("While key sliding is on, the eight characters are drawn small on the key face in a 3×3 grid. Their positions are tunable per fold-state under Live sizing → \"4D label\" sliders.")
            Para("Directions: up, down, left, right, upLeft, upRight, downLeft, downRight. Omit any you don't want — empty slots draw nothing.")
            Yaml("- { type: flick, primary: o, left: e, up: O, right: ó,\n    down: Ō, upLeft: Ó, upRight: ö, downRight: ō }")
            Para("Note: key sliding and swipe/glide typing are mutually exclusive — turning one on turns the other off.")
        }
    }
}
