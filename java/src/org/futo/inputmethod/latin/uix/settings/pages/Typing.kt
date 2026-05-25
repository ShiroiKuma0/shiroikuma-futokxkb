package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import android.view.WindowManager
import org.futo.inputmethod.latin.uix.settings.findActivity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformImeOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputSession
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import org.futo.inputmethod.accessibility.AccessibilityUtils
import org.futo.inputmethod.engine.IMESettingsMenu
import org.futo.inputmethod.latin.HideKeyboardWhenHardKeyboardConnected
import org.futo.inputmethod.latin.LayoutSwitcherShortcutCatalog
import org.futo.inputmethod.latin.layoutSwitcherShortcutPrefKey
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.settings.LongPressKey
import org.futo.inputmethod.latin.settings.LongPressKeyLayoutSetting
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.settings.Settings.PREF_KEYPRESS_SOUND_VOLUME
import org.futo.inputmethod.latin.settings.Settings.PREF_VIBRATION_DURATION_SETTINGS
import org.futo.inputmethod.latin.settings.description
import org.futo.inputmethod.latin.settings.name
import org.futo.inputmethod.latin.settings.toEncodedString
import org.futo.inputmethod.latin.settings.toLongPressKeyLayoutItems
import org.futo.inputmethod.latin.uix.AndroidTextInput
import org.futo.inputmethod.latin.uix.BasicThemeProvider
import org.futo.inputmethod.latin.uix.KeyHintsSetting
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.SHOW_EMOJI_SUGGESTIONS
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.setSettingBlocking
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.DataStoreItem
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.LocalSharedPrefsCache
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.PrimarySettingToggleDataStoreItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingRadio
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingSliderForDataStoreItem
import org.futo.inputmethod.latin.uix.settings.SettingSliderSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.SettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.settings.SyncDataStoreToPreferencesFloat
import org.futo.inputmethod.latin.uix.settings.SyncDataStoreToPreferencesInt
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.render
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsBool
import org.futo.inputmethod.latin.uix.settings.useSharedPrefsInt
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.KeyboardSettings
import org.futo.inputmethod.v2keyboard.KeyboardSizeSettingKind
import org.futo.inputmethod.v2keyboard.LastUsedSizeStateSetting
import org.futo.inputmethod.v2keyboard.SavedKeyboardSizingSettings
import org.futo.inputmethod.v2keyboard.getDefaultSettingForKind
import org.futo.inputmethod.v2keyboard.perComboSizingKey
import org.futo.inputmethod.latin.ActiveSubtype
import org.futo.inputmethod.latin.Subtypes
import org.futo.inputmethod.latin.utils.SubtypeLocaleUtils
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.sign

val vibrationDurationSetting = SettingsKey(
    intPreferencesKey("vibration_duration"),
    -1
)

val keySoundVolumeSetting = SettingsKey(
    floatPreferencesKey("key_sound_volume"),
    0.0f
)

val ActionBarDisplayedSetting = SettingsKey(
    booleanPreferencesKey("enable_action_bar"),
    true
)

val InlineAutofillSetting = SettingsKey(
    booleanPreferencesKey("inline_autofill"),
    true
)

val ResizeMenuLite = UserSettingsMenu(
    title = R.string.size_settings_title,
    navPath = "resize", registerNavPath = false,
    settings = listOf(
        userSettingNavigationItem(
            title = R.string.size_settings_reset,
            subtitle = R.string.size_settings_reset_subtitle,
            style = NavigationItemStyle.Misc,
            icon = R.drawable.close,
            navigate = { nav ->
                KeyboardSettings.values.forEach {
                    nav.context.setSettingBlocking(it.key, it.default)
                }
            }
        )
    )
)

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
fun ResizeScreen(navController: NavHostController = rememberNavController()) {
    val textInputService = LocalTextInputService.current
    val session = remember { mutableStateOf<TextInputSession?>(null) }

    DisposableEffect(Unit) {
        session.value = textInputService?.startInput(
            TextFieldValue(""),
            imeOptions = ImeOptions.Default.copy(
                platformImeOptions = PlatformImeOptions(
                    privateImeOptions = "org.futo.inputmethod.latin.ResizeMode=1"
                )
            ),
            onEditCommand = { },
            onImeActionPerformed = { }
        )

        onDispose {
            textInputService?.stopInput(session.value ?: return@onDispose)
        }
    }

    Box {
        ScrollableList {
            ScreenTitle(stringResource(R.string.size_settings_title), showBack = true, navController)

            PaymentSurface(
                isPrimary = false,
            ) {
                PaymentSurfaceHeading(title = stringResource(R.string.settings_tip))

                Text(
                    buildAnnotatedString {
                        append(stringResource(R.string.size_settings_keyboard_modes_tip))
                        append(" ")
                        appendInlineContent("icon")
                        appendLine()
                        append(stringResource(R.string.size_settings_keyboard_modes_portrait_landscape_tip))
                        appendLine()
                        append(stringResource(R.string.size_settings_resize_tip))
                    },
                    style = Typography.Body.MediumMl,
                    color = LocalContentColor.current,
                    inlineContent = mapOf(
                        "icon" to InlineTextContent(
                            Placeholder(
                                width = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                                height = with(LocalDensity.current) { 24.dp.toPx().toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ){
                            Icon(painterResource(R.drawable.keyboard_gear), contentDescription = null)
                        }
                    ))
            }

            Spacer(Modifier.height(8.dp))
            ResizeMenuLite.render(showTitle = false)

            AndroidTextInput(allowPredictions = false, customOptions = setOf("org.futo.inputmethod.latin.ResizeMode"), autoshow = false)
        }
    }
}

// Resets only the kxkb live sizing knobs (gaps, row heights, suggestion bar) for the geometry
// the docked keyboard is currently showing, leaving the stock resize fields (padding, height)
// intact. Recomputes the bucket from LastUsedSizeStateSetting so it needs no captured state.
val KxkbSizingResetMenu = UserSettingsMenu(
    title = R.string.kxkb_sizing_title,
    navPath = "kxkbSizingReset", registerNavPath = false,
    settings = listOf(
        userSettingNavigationItem(
            title = R.string.kxkb_sizing_reset,
            subtitle = R.string.kxkb_sizing_reset_subtitle,
            style = NavigationItemStyle.Misc,
            icon = R.drawable.close,
            navigate = { nav ->
                val kind = try {
                    KeyboardSizeSettingKind.valueOf(nav.context.getSettingBlocking(LastUsedSizeStateSetting))
                } catch (e: Exception) {
                    KeyboardSizeSettingKind.Portrait
                }
                val key = KeyboardSettings[kind]!!
                val cur = SavedKeyboardSizingSettings.fromJsonString(nav.context.getSettingBlocking(key))
                    ?: getDefaultSettingForKind(kind, nav.context)
                val defaults = getDefaultSettingForKind(kind, nav.context)
                nav.context.setSettingBlocking(
                    key.key,
                    cur.copy(
                        heightMultiplier = defaults.heightMultiplier,
                        horizontalGapAddDp = 0f,
                        verticalGapAddDp = 0f,
                        topRowHeightFactor = 1f,
                        bottomRowHeightFactor = 1f,
                        suggestionBarHeightFactor = 1f,
                        fontSizeMultiplier = -1f,
                        keyRoundness = -1f,
                        hintSizeMultiplier = -1f,
                        splitWidthFraction = defaults.splitWidthFraction,
                        fontColor = null,
                        secondaryFontColor = null,
                        keyBackgroundColor = null,
                        functionalKeyBackgroundColor = null,
                        keyBorderColor = null,
                        keyboardBackgroundColor = null,
                        suggestionBarColor = null,
                        suggestionTextColor = null
                    ).toJsonString()
                )
            }
        )
    )
)

// One Alpha/Red/Green/Blue channel of a per-geometry colour, rendered with the same slider widget
// as the size knobs. Reads its byte out of `effective` (the colour currently shown) and writes the
// whole packed ARGB back: if this colour is still inheriting (current == null) the other three
// channels are seeded from `inherited`, so the first drag promotes the inherited colour to an
// explicit override rather than starting from black.
@Composable
private fun ColorChannelSlider(
    label: String,
    effective: Int,
    current: Int?,
    inherited: Int,
    shift: Int,
    onChange: (Int) -> Unit
) {
    SettingSliderForDataStoreItem(
        title = label,
        item = DataStoreItem(((effective ushr shift) and 0xFF).toFloat()) { v ->
            val b = v.toInt().coerceIn(0, 255)
            val base = current ?: inherited
            onChange((base and (0xFF shl shift).inv()) or (b shl shift))
        },
        default = ((inherited ushr shift) and 0xFF).toFloat(),
        range = 0f..255f,
        transform = { it },
        indicator = { it.toInt().toString() }
    )
}

// One per-geometry colour override: a tappable header (title + live swatch) that expands to the four
// channel sliders. `current` is the stored ARGB (null = inherit the theme); `inherited` is the
// theme's effective colour, used for the swatch and as the seed on first edit. Channel edits write
// the full ARGB through onChange, which drives LatinIME.withPerKindLook so the docked keyboard and
// the suggestion bar recolour live.
@Composable
private fun ColorSetting(
    title: String,
    current: Int?,
    inherited: Int,
    onChange: (Int) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }
    val effective = current ?: inherited

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded.value = !expanded.value }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = CenterVertically
    ) {
        Text(
            title,
            style = Typography.Body.MediumMl,
            color = LocalContentColor.current,
            modifier = Modifier.weight(1f)
        )
        if (current == null) {
            Text(
                stringResource(R.string.kxkb_color_inherited),
                style = Typography.SmallMl,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        Box(
            Modifier
                .size(48.dp, 28.dp)
                .border(1.dp, LocalContentColor.current.copy(alpha = 0.4f))
                .background(Color(effective))
        )
    }

    if (expanded.value) {
        ColorChannelSlider(stringResource(R.string.kxkb_color_alpha), effective, current, inherited, 24, onChange)
        ColorChannelSlider(stringResource(R.string.kxkb_color_red), effective, current, inherited, 16, onChange)
        ColorChannelSlider(stringResource(R.string.kxkb_color_green), effective, current, inherited, 8, onChange)
        ColorChannelSlider(stringResource(R.string.kxkb_color_blue), effective, current, inherited, 0, onChange)
    }
}

// kxkb live sizing-knob screen (Phase 1): docks the live keyboard like ResizeScreen and exposes
// the five sizing sliders. Edits the per-geometry blob the docked keyboard is currently showing
// (auto-tracked via LastUsedSizeStateSetting), so dragging a slider updates the real keyboard live.
@OptIn(ExperimentalLayoutApi::class)
// kxkb: Jami-style section header — large accent title + a full-width accent rule beneath it.
@Composable
private fun KxkbSection(title: String) {
    Column(Modifier.fillMaxWidth().padding(16.dp, 24.dp, 16.dp, 4.dp)) {
        Text(title, style = Typography.Heading.MediumMl, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
    }
}

// kxkb: Jami-style subgroup — accent title with a SHORT underline matching the text width
// (IntrinsicSize.Min sizes the column to its single-line text), then its controls indented one step
// deeper, so section (0) > subgroup (1) > controls (2) reads as a cascade.
@Composable
private fun KxkbSubgroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(24.dp, 16.dp, 16.dp, 2.dp).width(IntrinsicSize.Min)) {
        Text(
            title,
            style = Typography.Body.MediumMl,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.primary)
    }
    Column(Modifier.fillMaxWidth().padding(start = 32.dp)) { content() }
}

@Composable
fun KxkbSizingScreen(navController: NavHostController = rememberNavController()) {
    val textInputService = LocalTextInputService.current
    val session = remember { mutableStateOf<TextInputSession?>(null) }

    DisposableEffect(Unit) {
        // Dock the live keyboard WITHOUT ResizeMode: that private option makes inputStarted()
        // auto-open the stock KeyboardModeAction window (the Standard/One Hand/Split/Float toggle
        // row + drag-resizer), which is tall and pushes this screen's sliders off the top. We just
        // want a plain docked keyboard whose layout reflects the sliders; suggestions/action bar
        // stay on so the suggestion-bar-height slider has something visible to resize.
        session.value = textInputService?.startInput(
            TextFieldValue(""),
            imeOptions = ImeOptions.Default,
            onEditCommand = { },
            onImeActionPerformed = { }
        )

        onDispose {
            textInputService?.stopInput(session.value ?: return@onDispose)
        }
    }

    val context = LocalContext.current

    // Force ADJUST_RESIZE while this screen is open so the docked keyboard's IME inset shrinks the
    // content (consumed by the activity's edge-to-edge safeDrawingPadding) instead of panning the
    // whole window up and carrying the sliders off the top. Restore the previous mode on leave.
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(
            ((previousMode ?: 0) and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        onDispose {
            if (previousMode != null) window?.setSoftInputMode(previousMode)
        }
    }

    // Auto-track the bucket the docked keyboard is currently showing.
    val (lastStateName, _) = useDataStore(LastUsedSizeStateSetting, blocking = true)
    val kind = remember(lastStateName) {
        try { KeyboardSizeSettingKind.valueOf(lastStateName) } catch (e: Exception) { KeyboardSizeSettingKind.Portrait }
    }

    // kxkb: settings are per-(active subtype × geometry). Track the active subtype reactively so the
    // sliders rebind to the right blob when the language/layout is switched while this screen is open.
    val (activeSub, _) = useDataStore(ActiveSubtype, blocking = true)
    val comboKey = remember(activeSub, kind) { perComboSizingKey(context, kind) }
    // What we read/write: the per-combo key when a subtype resolves, else the geometry-level key
    // (BFU / direct-boot). useDataStore reads live from the cache by key, so changing comboKey on a
    // subtype switch rebinds correctly.
    val writeKey = comboKey ?: KeyboardSettings[kind]!!

    val comboItem = useDataStore(writeKey, blocking = true)
    // The geometry-level blob this combo inherits from until its first edit.
    val (geomBlob, _) = useDataStore(KeyboardSettings[kind]!!, blocking = true)

    // Effective current settings: per-combo if already forked, else the inherited geometry baseline,
    // else the kind default. Writes always target writeKey -> forks this combo on its first edit.
    fun currentSettings(): SavedKeyboardSizingSettings =
        SavedKeyboardSizingSettings.fromJsonString(comboItem.value)
            ?: SavedKeyboardSizingSettings.fromJsonString(geomBlob)
            ?: getDefaultSettingForKind(kind, context)

    val parsed = remember(comboItem.value, geomBlob) { currentSettings() }

    fun floatItem(
        get: (SavedKeyboardSizingSettings) -> Float,
        set: (SavedKeyboardSizingSettings, Float) -> SavedKeyboardSizingSettings
    ) = DataStoreItem(get(parsed)) { v ->
        comboItem.setValue(set(currentSettings(), v).toJsonString())
    }

    // Whole-blob writer for the colour overrides (parallels floatItem; colours are packed ARGB Ints).
    fun writeColor(set: (SavedKeyboardSizingSettings) -> SavedKeyboardSizingSettings) {
        comboItem.setValue(set(currentSettings()).toJsonString())
    }

    // Active theme scheme — supplies each colour's inherited (default) value, shown in the swatch
    // and used to seed the channel sliders until the user sets an explicit override.
    val scheme = LocalKeyboardScheme.current

    // Geometry default, used as the neutral value for the overall "Keyboard height" slider
    // (heightMultiplier has no fixed neutral — it is device/geometry dependent).
    val defaultSizing = remember(kind) { getDefaultSettingForKind(kind, context) }

    // kxkb: human-readable "language · layout" for the combo currently being edited, so the per-combo
    // scope is visible (settings silently target the active subtype × geometry).
    val comboLabel = remember(activeSub) {
        try {
            val st = Subtypes.convertToSubtype(activeSub)
            "${Subtypes.getName(st)} · ${Subtypes.getLayoutName(context, SubtypeLocaleUtils.getKeyboardLayoutSetName(st))}"
        } catch (e: Exception) { "" }
    }

    Box {
        ScrollableList {
            ScreenTitle(stringResource(R.string.kxkb_sizing_title), showBack = true, navController)

            // kxkb: jump straight back to the keyboard. This page is usually opened from the
            // action-bar "Live sizing" icon while typing; finishing the activity returns to the app
            // (and its keyboard) in one tap, instead of several Back presses up the settings stack.
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

            Text(
                stringResource(R.string.kxkb_sizing_geometry_note),
                style = Typography.SmallMl,
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.padding(12.dp, 4.dp)
            )

            if(comboLabel.isNotEmpty()) {
                Text(
                    comboLabel,
                    style = Typography.Body.MediumMl,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp, 8.dp, 12.dp, 0.dp)
                )
            }

            Text(
                stringResource(
                    R.string.kxkb_sizing_active_geometry,
                    stringResource(
                        when (kind) {
                            KeyboardSizeSettingKind.Portrait -> R.string.kxkb_sizing_geometry_portrait
                            KeyboardSizeSettingKind.Landscape -> R.string.kxkb_sizing_geometry_landscape
                            KeyboardSizeSettingKind.FoldableInnerDisplay -> R.string.kxkb_sizing_geometry_fold
                        }
                    )
                ),
                style = Typography.Body.MediumMl,
                color = LocalContentColor.current,
                modifier = Modifier.padding(12.dp, 8.dp)
            )

            // All per-combo controls live inside key(kind, activeSub) so each slider's internal seed
            // resyncs on a geometry or language/layout switch. Grouped Jami-style: KxkbSection (big
            // header + full-width rule) > KxkbSubgroup (accent header + short underline, controls
            // indented one step deeper) > controls, with each colour beside the element it affects.
            key(kind, activeSub) {
                KxkbSection("Size")

                KxkbSubgroup("Height") {
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_keyboard_height),
                        item = floatItem({ it.heightMultiplier }, { s, v -> s.copy(heightMultiplier = v) }),
                        default = defaultSizing.heightMultiplier, range = 0.5f..2.5f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_top_row_height),
                        item = floatItem({ it.topRowHeightFactor }, { s, v -> s.copy(topRowHeightFactor = v) }),
                        default = 1f, range = 0.5f..2.0f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_bottom_row_height),
                        item = floatItem({ it.bottomRowHeightFactor }, { s, v -> s.copy(bottomRowHeightFactor = v) }),
                        default = 1f, range = 0.2f..2.0f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                }

                KxkbSubgroup("Spacing") {
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_horizontal_gap),
                        item = floatItem({ it.horizontalGapAddDp }, { s, v -> s.copy(horizontalGapAddDp = v) }),
                        default = 0f, range = 0f..10f, transform = { it }, indicator = { "%.1f dp".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_vertical_gap),
                        item = floatItem({ it.verticalGapAddDp }, { s, v -> s.copy(verticalGapAddDp = v) }),
                        default = 0f, range = 0f..10f, transform = { it }, indicator = { "%.1f dp".format(it) }
                    )
                }

                KxkbSubgroup("Split mode") {
                    // Split-mode half width as a fraction of the display width (only affects split mode,
                    // i.e. landscape / inner display). Higher = wider halves and a smaller centre gap;
                    // the size calc hard-caps the effective value at 90%, so the halves never fully meet.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_split_width),
                        item = floatItem({ it.splitWidthFraction }, { s, v -> s.copy(splitWidthFraction = v) }),
                        default = defaultSizing.splitWidthFraction, range = 0.4f..1.0f, transform = { it }, indicator = { "%.0f%%".format(it * 100) }
                    )
                }

                KxkbSection("Keys")

                KxkbSubgroup("Text") {
                    // Key label font scale. Stored -1 = "inherit theme"; the slider shows the effective
                    // value (HighContrastYellow's keyLetterScale, 1.4x, until set) and writes an absolute
                    // scale once dragged. Feeds keyLetterScale via withPerKindLook.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_key_font_size),
                        item = floatItem(
                            { if (it.fontSizeMultiplier >= 0f) it.fontSizeMultiplier else 1.4f },
                            { s, v -> s.copy(fontSizeMultiplier = v) }
                        ),
                        default = 1.4f, range = 0.5f..3.0f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_font),
                        current = parsed.fontColor,
                        inherited = scheme.onKeyboardContainer.toArgb(),
                        onChange = { v -> writeColor { it.copy(fontColor = v) } }
                    )
                }

                KxkbSubgroup("Shape & background") {
                    // Key corner roundness (0 = square, 1 = theme max). Stored -1 = inherit theme; the
                    // slider shows the effective value (HighContrastYellow uses 0 = square until set)
                    // and writes an absolute value. Feeds keyRoundness via withPerKindLook.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_key_roundness),
                        item = floatItem(
                            { if (it.keyRoundness >= 0f) it.keyRoundness else 0f },
                            { s, v -> s.copy(keyRoundness = v) }
                        ),
                        default = 0f, range = 0f..5f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_key_bg),
                        current = parsed.keyBackgroundColor,
                        inherited = scheme.keyboardContainer.toArgb(),
                        onChange = { v -> writeColor { it.copy(keyBackgroundColor = v) } }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_functional_bg),
                        current = parsed.functionalKeyBackgroundColor,
                        inherited = scheme.keyboardContainerVariant.toArgb(),
                        onChange = { v -> writeColor { it.copy(functionalKeyBackgroundColor = v) } }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_border),
                        current = parsed.keyBorderColor,
                        inherited = scheme.outline.toArgb(),
                        onChange = { v -> writeColor { it.copy(keyBorderColor = v) } }
                    )
                }

                KxkbSubgroup("Hint labels") {
                    // Secondary (hint) character scale. Stored -1 = inherit theme (keyHintScale 1.0x
                    // until set); writes an absolute multiplier. Feeds keyHintScale via withPerKindLook,
                    // applied at the on-key hint draw sites in KeyboardView.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_secondary_text_size),
                        item = floatItem(
                            { if (it.hintSizeMultiplier >= 0f) it.hintSizeMultiplier else 1.0f },
                            { s, v -> s.copy(hintSizeMultiplier = v) }
                        ),
                        default = 1.0f, range = 0.5f..3.0f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_secondary),
                        current = parsed.secondaryFontColor,
                        inherited = (scheme.hintColor ?: scheme.onSurfaceVariant).toArgb(),
                        onChange = { v -> writeColor { it.copy(secondaryFontColor = v) } }
                    )
                }

                KxkbSection("Sliding labels")

                KxkbSubgroup("Flick positions") {
                    // kxkb 4D: at-rest directional-label grid positions, as fractions of the key from
                    // centre. Top/bottom and left/right independent. Only visible while key sliding is on.
                    // Fed to KeyboardView via withPerKindLook -> setFlickLabelOffsets.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_flick_top),
                        item = floatItem({ it.flickLabelTopOffset }, { s, v -> s.copy(flickLabelTopOffset = v) }),
                        default = 0.30f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_flick_bottom),
                        item = floatItem({ it.flickLabelBottomOffset }, { s, v -> s.copy(flickLabelBottomOffset = v) }),
                        default = 0.40f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_flick_left),
                        item = floatItem({ it.flickLabelLeftOffset }, { s, v -> s.copy(flickLabelLeftOffset = v) }),
                        default = 0.34f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_flick_right),
                        item = floatItem({ it.flickLabelRightOffset }, { s, v -> s.copy(flickLabelRightOffset = v) }),
                        default = 0.34f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                }

                KxkbSubgroup("Cluster positions") {
                    // kxkb cluster: how far the left / right outer mains of a cluster (predictive multi-key)
                    // sit from the centre, as a fraction of key width per column-step. 0.333 = evenly tiled.
                    // Display only - does not affect prediction. Fed via withPerKindLook -> setClusterMainOffsets.
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_cluster_left),
                        item = floatItem({ it.clusterLeftOffset }, { s, v -> s.copy(clusterLeftOffset = v) }),
                        default = 0.333f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_cluster_right),
                        item = floatItem({ it.clusterRightOffset }, { s, v -> s.copy(clusterRightOffset = v) }),
                        default = 0.333f, range = 0.0f..0.6f, transform = { it }, indicator = { "%.2f".format(it) }
                    )
                }

                KxkbSection("Surfaces & suggestion bar")

                KxkbSubgroup("Keyboard background") {
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_keyboard_bg),
                        current = parsed.keyboardBackgroundColor,
                        inherited = scheme.keyboardSurface.toArgb(),
                        onChange = { v -> writeColor { it.copy(keyboardBackgroundColor = v) } }
                    )
                }

                KxkbSubgroup("Suggestion bar") {
                    SettingSliderForDataStoreItem(
                        title = stringResource(R.string.kxkb_sizing_suggestion_bar_height),
                        item = floatItem({ it.suggestionBarHeightFactor }, { s, v -> s.copy(suggestionBarHeightFactor = v) }),
                        default = 1f, range = 0.5f..2.0f, transform = { it }, indicator = { "%.2fx".format(it) }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_suggestion_bg),
                        current = parsed.suggestionBarColor,
                        inherited = scheme.keyboardSurface.toArgb(),
                        onChange = { v -> writeColor { it.copy(suggestionBarColor = v) } }
                    )
                    ColorSetting(
                        title = stringResource(R.string.kxkb_color_suggestion_text),
                        current = parsed.suggestionTextColor,
                        inherited = scheme.onSurface.toArgb(),
                        onChange = { v -> writeColor { it.copy(suggestionTextColor = v) } }
                    )
                }
            }

            KxkbSection("Layout switcher")
            Column(Modifier.fillMaxWidth().padding(start = 32.dp)) {
                LayoutSwitcherShortcutCatalog.forEach { shortcut ->
                    SettingToggleSharedPrefs(
                        title = shortcut.label,
                        key = layoutSwitcherShortcutPrefKey(shortcut.id),
                        default = shortcut.defaultOn
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            KxkbSizingResetMenu.render(showTitle = false)

            // kxkb: second close button at the bottom, so a one-tap return to the keyboard is
            // reachable whether the user is at the top of the page or has scrolled down to Reset.
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

            AndroidTextInput(allowPredictions = false, autoshow = false)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DraggableSettingItem(idx: Int, item: LongPressKey, moveItem: (LongPressKey, Int) -> Unit, disable: (LongPressKey) -> Unit, dragIcon: @Composable () -> Unit, limits: IntRange) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val talkBackOn = remember {
        AccessibilityUtils.init(context)
        AccessibilityUtils.getInstance().isAccessibilityEnabled
    }

    val customActions = remember(idx, limits, item, resources) {
        buildList {
            if (idx > limits.first) {
                add(
                    CustomAccessibilityAction(
                        resources.getString(R.string.morekey_settings_move_kind_up)
                    ) {
                        moveItem(item, -1)
                        true
                    }
                )

                add(
                    CustomAccessibilityAction(
                        resources.getString(R.string.morekey_settings_move_kind_up_to_top)
                    ) {
                        moveItem(item, -100)
                        true
                    }
                )
            }
            if (idx < limits.last) {
                add(
                    CustomAccessibilityAction(
                        resources.getString(R.string.morekey_settings_move_kind_down)
                    ) {
                        moveItem(item, 1)
                        true
                    }
                )
                add(
                    CustomAccessibilityAction(
                        resources.getString(R.string.morekey_settings_move_kind_down_to_bottom)
                    ) {
                        moveItem(item, 100)
                        true
                    }
                )
            }
            add(
                CustomAccessibilityAction(
                    resources.getString(R.string.morekey_settings_disable)
                ) {
                    disable(item)
                    true
                }
            )
        }
    }

    val semantics = Modifier.clearAndSetSemantics {
        contentDescription = item.name(resources)
        stateDescription = resources.getString(
            R.string.morekey_settings_kind_position,
            (idx + 1).toString(),
            (limits.last + 1).toString()
        )

        if (talkBackOn) {
            this.customActions = customActions
        }
    }

    val dragging = remember { mutableStateOf(false) }
    val offset = remember { mutableFloatStateOf(0.0f) }
    val height = remember { mutableIntStateOf(1) }

    val pendingOffsetDiff = remember { mutableFloatStateOf(0.0f) }
    LaunchedEffect(idx, pendingOffsetDiff.floatValue) {
        if(pendingOffsetDiff.floatValue != 0.0f) {
            offset.floatValue += pendingOffsetDiff.floatValue
            pendingOffsetDiff.floatValue = 0.0f
        }
    }

    val shouldClampLower = (idx - 1) < limits.first
    val shouldClampUpper = (idx + 1) > limits.last

    SettingItem(
        title = "${idx+1}. " + item.name(resources),
        subtitle = item.description(resources),
        icon = {
            if(talkBackOn) {
                Column {
                    IconButton(onClick = { moveItem(item, -1) }) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.morekey_settings_move_kind_up)
                        )
                    }
                    IconButton(onClick = { moveItem(item, 1) }) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.morekey_settings_move_kind_down)
                        )
                    }
                }
            } else {
                Box(Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            dragging.value = true
                            offset.floatValue = 0.0f
                        },
                        onDragEnd = {
                            dragging.value = false
                            offset.floatValue = 0.0f
                        },
                        onDragCancel = {
                            dragging.value = false
                            offset.floatValue = 0.0f
                        },
                        onDrag = { change, dragAmount ->
                            offset.floatValue += dragAmount.y

                            if ((offset.floatValue + pendingOffsetDiff.floatValue).absoluteValue > height.intValue) {
                                val direction = offset.floatValue.sign.toInt()
                                moveItem(
                                    item,
                                    direction
                                )
                                pendingOffsetDiff.floatValue -= height.intValue * direction
                            }

                        }
                    )
                }) {
                    dragIcon()
                }
            }
        },
        modifier = semantics
            .onSizeChanged { size -> height.intValue = size.height }
            .let { modifier ->
                if (!dragging.value) {
                    modifier
                        .background(LocalKeyboardScheme.current.surfaceTint.copy(alpha = if(idx % 2 == 0) 0.02f else 0.06f))
                } else {
                    modifier
                        .zIndex(10.0f)
                        .graphicsLayer {
                            clip = false
                            translationX = 0.0f
                            translationY = offset.floatValue.let {
                                if (shouldClampLower && it < 0.0f) 0.0f
                                else if (shouldClampUpper && it > 0.0f) 0.0f
                                else it
                            }
                        }
                        .background(
                            LocalKeyboardScheme.current.surfaceTint.copy(alpha = 0.2f)
                                .compositeOver(LocalKeyboardScheme.current.background)
                        )
                }
            }
    ) {
        IconButton(onClick = { disable(item) }) {
            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.morekey_settings_disable))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LongPressKeyLayoutEditor(context: Context, setting: DataStoreItem<String>) {
    val resources = LocalResources.current
    Row(Modifier.padding(16.dp)) {
        Text(stringResource(R.string.morekey_settings_layout), style = Typography.Heading.Medium, modifier = Modifier
            .align(CenterVertically)
            .weight(1.0f))

        Spacer(Modifier.width(4.dp))

        Button(onClick = {
            setting.setValue(LongPressKeyLayoutSetting.default)
        }) {
            Text(stringResource(R.string.morekey_settings_reset))
        }
    }


    val dragIcon: @Composable () -> Unit = {
        Icon(Icons.Default.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
    }

    val items = setting.value.toLongPressKeyLayoutItems()

    val moveItem: (item: LongPressKey, direction: Int) -> Unit = { item, direction ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()
        val oldIdx = oldItems.indexOf(item)

        val insertIdx = (oldIdx + direction).coerceAtLeast(0).coerceAtMost(oldItems.size - 1)

        val newItems = oldItems.filter { it != item }.toMutableList().apply {
            add(insertIdx, item)
        }.toEncodedString()

        setting.setValue(newItems)
    }

    val disable: (item : LongPressKey) -> Unit = { item ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()

        val newItems = oldItems.filter { it != item }.toEncodedString()

        setting.setValue(newItems)

    }

    val enable: (item : LongPressKey) -> Unit = { item ->
        val oldItems = context.getSettingBlocking(LongPressKeyLayoutSetting).toLongPressKeyLayoutItems()

        val newItems = oldItems.filter { it != item }.toMutableList().apply {
            add(item)
        }.toEncodedString()

        setting.setValue(newItems)
    }

    if(items.isNotEmpty()) {
        Text(
            stringResource(R.string.morekey_settings_active),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Column(Modifier.semantics {
            collectionInfo = CollectionInfo(
                rowCount = items.size,
                columnCount = 1
            )
            contentDescription = resources.getString(R.string.morekey_settings_active)
        }) {
            items.forEachIndexed { i, v ->
                key(v.ordinal) {
                    DraggableSettingItem(
                        idx = i,
                        item = v,
                        moveItem = moveItem,
                        disable = disable,
                        dragIcon = dragIcon,
                        limits = items.indices
                    )
                }
            }
        }
    }

    val inactiveEntries = LongPressKey.entries.filter { !items.contains(it) }
    if(inactiveEntries.isNotEmpty()) {
        Text(
            stringResource(R.string.morekey_settings_inactive),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Column(Modifier.semantics {
            collectionInfo = CollectionInfo(
                rowCount = inactiveEntries.size,
                columnCount = 1
            )
            contentDescription = resources.getString(R.string.morekey_settings_inactive)
        }) {
            inactiveEntries.forEach {
                SettingItem(
                    title = it.name(resources),
                    subtitle = it.description(resources),
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = it.name(resources)

                        onClick(label = resources.getString(R.string.morekey_settings_reactivate)) {
                            enable(it)
                            true
                        }
                    }
                ) {
                    IconButton(onClick = { enable(it) }) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.morekey_settings_reactivate)
                        )
                    }
                }
            }
        }
    }
}

val LongPressMenu = UserSettingsMenu(
    title = R.string.morekey_settings_keys,
    navPath = "longPress", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.morekey_settings_show_hints,
            subtitle = R.string.morekey_settings_show_hints_subtitle,
            setting = KeyHintsSetting
        ).copy(searchTags = R.string.morekey_settings_show_hints_tags),

        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.morekey_settings_backspace_title))
        },

        UserSetting(name = R.string.morekey_settings_backspace_hold_delete_words) {
            val oldSetting = useSharedPrefsInt(
                key = Settings.PREF_BACKSPACE_MODE,
                default = Settings.BACKSPACE_MODE_CHARACTERS
            )

            val setting = useSharedPrefsInt(
                key = Settings.PREF_BACKSPACE_MODE_HOLD,
                default = oldSetting.value
            )

            SettingToggleRaw(
                title = stringResource(R.string.morekey_settings_backspace_hold_delete_words),
                enabled = setting.value == Settings.BACKSPACE_MODE_WORDS,
                setValue = { to ->
                    setting.setValue(if(to) Settings.BACKSPACE_MODE_WORDS else Settings.BACKSPACE_MODE_CHARACTERS)
                }
            )
        },

        UserSetting(name = R.string.morekey_settings_backspace_swipe_to_delete) {
            val setting = useSharedPrefsInt(
                key = Settings.PREF_BACKSPACE_MODE,
                default = Settings.BACKSPACE_MODE_CHARACTERS
            )

            val deleteModes = mapOf(
                Settings.BACKSPACE_MODE_OFF to stringResource(R.string.morekey_settings_backspace_swipe_to_delete_off),
                Settings.BACKSPACE_MODE_CHARACTERS to stringResource(R.string.morekey_settings_backspace_swipe_to_delete_characters),
                Settings.BACKSPACE_MODE_WORDS to stringResource(R.string.morekey_settings_backspace_swipe_to_delete_words),
            )

            DropDownPickerSettingItem(
                label = stringResource(R.string.morekey_settings_backspace_swipe_to_delete),
                options = deleteModes.keys.toList(),
                selection = setting.value,
                onSet = { setting.setValue(it) },
                getDisplayName = { deleteModes[it] ?: "?" },
            )
        },


        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.morekey_settings_spacebar_title))
        },

        UserSetting(name = R.string.morekey_settings_spacebar_swipe_shortcut) {
            val setting = useSharedPrefsInt(
                key = Settings.PREF_SPACEBAR_SWIPE_MODE,
                default = remember { Settings.getInstance().current.mSpacebarSwipeMode }
            )

            val modes = mapOf(
                Settings.SPACEBAR_MODE_OFF to stringResource(R.string.morekey_settings_spacebar_swipe_shortcut_off),
                Settings.SPACEBAR_MODE_CURSOR to stringResource(R.string.morekey_settings_spacebar_swipe_shortcut_cursor),
                Settings.SPACEBAR_MODE_LANGUAGE to stringResource(R.string.morekey_settings_spacebar_swipe_shortcut_language),
                Settings.SPACEBAR_MODE_LAYOUT_MENU to stringResource(R.string.morekey_settings_spacebar_swipe_shortcut_layout_menu), // kxkb
            )

            DropDownPickerSettingItem(
                label = stringResource(R.string.morekey_settings_spacebar_swipe_shortcut),
                options = modes.keys.toList(),
                selection = setting.value,
                onSet = { setting.setValue(it) },
                getDisplayName = { modes[it] ?: "?" },
            )
        },

        UserSetting(name = R.string.morekey_settings_spacebar_hold_shortcut) {
            val setting = useSharedPrefsInt(
                key = Settings.PREF_SPACEBAR_HOLD_MODE,
                default = remember { Settings.getInstance().current.mSpacebarHoldMode }
            )

            val modes = mapOf(
                Settings.SPACEBAR_MODE_CURSOR to stringResource(R.string.morekey_settings_spacebar_hold_shortcut_cursor),
                Settings.SPACEBAR_MODE_LANGUAGE to stringResource(R.string.morekey_settings_spacebar_hold_shortcut_language),
            )

            DropDownPickerSettingItem(
                label = stringResource(R.string.morekey_settings_spacebar_hold_shortcut),
                options = modes.keys.toList(),
                selection = setting.value,
                onSet = { setting.setValue(it) },
                getDisplayName = { modes[it] ?: "?" },
            )
        },

        // TODO: Might not work well for showing up in search
        UserSetting(name = R.string.morekey_settings_layout) {
            val context = LocalContext.current
            val setting = useDataStore(LongPressKeyLayoutSetting)
            LongPressKeyLayoutEditor(
                context = context,
                setting = setting,
            )
        },

        UserSetting(
            name = R.string.morekey_settings_duration,
            subtitle = R.string.morekey_settings_duration_subtitle,
        ) {
            val resources = LocalResources.current
            SettingSliderSharedPrefsInt(
                title = stringResource(R.string.morekey_settings_duration),
                subtitle = stringResource(R.string.morekey_settings_duration_subtitle),
                key = Settings.PREF_KEY_LONGPRESS_TIMEOUT,
                default = 300,
                range = 100.0f..700.0f,
                hardRange = 25.0f..1200.0f,
                transform = { it.roundToInt() },
                indicator = { resources.getString(R.string.abbreviation_unit_milliseconds, "$it") },
                steps = 23
            )
        },
    )
)

@Composable
private fun AutoSpacesSetting() {
    val altSpacesMode = useSharedPrefsInt(Settings.PREF_ALT_SPACES_MODE, Settings.DEFAULT_ALT_SPACES_MODE)
    val autoSpaceModes = mapOf(
        Settings.SPACES_MODE_ALL to stringResource(R.string.typing_settings_auto_space_mode_auto2),
        Settings.SPACES_MODE_SUGGESTIONS to stringResource(R.string.typing_settings_auto_space_mode_suggestions2),
        Settings.SPACES_MODE_LEGACY to stringResource(R.string.typing_settings_auto_space_mode_legacy2),
        Settings.SPACES_MODE_NONE to stringResource(R.string.typing_settings_auto_space_mode_none2),
    )
    DropDownPickerSettingItem(
        label = stringResource(R.string.typing_settings_auto_space_mode),
        options = autoSpaceModes.keys.toList(),
        selection = altSpacesMode.value,
        onSet = {
            altSpacesMode.setValue(it)
        },
        getDisplayName = {
            autoSpaceModes[it] ?: "?"
        },
        icon = {
            Icon(painterResource(R.drawable.space), contentDescription = null)
        }
    )
}

val NumberRowSettingMenu = UserSettingsMenu(
    title = R.string.keyboard_settings_number_row_title,
    navPath = "numberRow", registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            PrimarySettingToggleDataStoreItem(
                stringResource(R.string.keyboard_settings_show_number_row),
                useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false)
            )
        },

        userSettingToggleSharedPrefs(
            R.string.keyboard_settings_number_row_dont_use_script_digits,
            default = {false},
            key = Settings.PREF_USE_WESTERN_NUMERALS,
        ).copy(visibilityCheck = {
            useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false).value
        }),

        UserSetting(name = R.string.keyboard_settings_number_row_style, visibilityCheck = {
            useSharedPrefsBool(Settings.PREF_ENABLE_NUMBER_ROW, false).value
        }) {
            val context = LocalContext.current
            val scheme = LocalKeyboardScheme.current
            val provider = remember(scheme) {
                BasicThemeProvider(context, scheme)
            }
            val keySize = with(LocalDensity.current) {
                32.dp.toPx() to 48.dp.toPx()
            }
            val background = remember(provider) {
                provider.keyBackground.toBitmap(
                    width = keySize.first.toInt(),
                    height = keySize.second.toInt()
                ).asImageBitmap()
            }

            val measurer = rememberTextMeasurer()
            val textSizePx = background.height / 2f
            val textSizeSp = with(LocalDensity.current) { textSizePx.toSp() }
            val color = LocalKeyboardScheme.current.onKeyboardContainer

            val textLayoutResult = measurer.measure(
                text = "1",
                style = TextStyle(
                    fontSize = textSizeSp,
                    color = color,
                    textAlign = TextAlign.Center
                )
            )

            SettingRadio(
                title = stringResource(R.string.keyboard_settings_number_row_style),
                options = listOf(
                    Settings.NUMBER_ROW_MODE_DEFAULT,
                    Settings.NUMBER_ROW_MODE_CLASSIC
                ),
                optionNames = listOf(
                    stringResource(R.string.keyboard_settings_number_row_style_default),
                    stringResource(R.string.keyboard_settings_number_row_style_classic),
                ),
                setting = useSharedPrefsInt(
                    key = Settings.PREF_NUMBER_ROW_MODE,
                    default = Settings.NUMBER_ROW_MODE_DEFAULT
                ),
                hints = listOf(
                    {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.size(32.dp, 48.dp)
                        ) {
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    x = background.width / 2.0f - textLayoutResult.size.width / 2.0f,
                                    y = background.height / 2.0f - textLayoutResult.size.height / 2.0f,
                                )
                            )
                        }
                    },
                    {
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.size(32.dp, 48.dp)
                        ) {
                            drawImage(background)
                            drawText(
                                textLayoutResult = textLayoutResult,
                                topLeft = Offset(
                                    x = background.width / 2.0f - textLayoutResult.size.width / 2.0f,
                                    y = background.height / 2.0f - textLayoutResult.size.height / 2.0f,
                                )
                            )
                        }
                    },
                )
            )
        }
    )
)

val KeyboardSettingsMenu = UserSettingsMenu(
    title = R.string.keyboard_settings_title,
    navPath = "keyboard", registerNavPath = true,
    settings = listOf(
        userSettingNavigationItem(
            title = R.string.size_settings_title,
            subtitle = R.string.size_settings_subtitle2,
            style = NavigationItemStyle.Misc,
            navigateTo = "resize",
            icon = R.drawable.maximize
        ),
        userSettingNavigationItem(
            title = R.string.kxkb_sizing_title,
            subtitle = R.string.kxkb_sizing_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "kxkbSizing",
            icon = R.drawable.maximize
        ),
        userSettingNavigationItem(
            title = R.string.special_keys_title,
            subtitle = R.string.special_keys_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "specialKeys",
            icon = R.drawable.book
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_show_number_row,
            subtitle = R.string.keyboard_settings_show_number_row_subtitle,
            key = Settings.PREF_ENABLE_NUMBER_ROW,
            default = {false},
            icon = { Text("123", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.clearAndSetSemantics{}) },
            submenu = NumberRowSettingMenu.navPath
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_show_arrow_row,
            subtitle = R.string.keyboard_settings_show_arrow_row_subtitle,
            key = Settings.PREF_ENABLE_ARROW_ROW,
            default = {false},
            icon = {
                Icon(painterResource(id = R.drawable.direction_arrows), contentDescription = null)
            }
        ),
        userSettingNavigationItem(
            title = R.string.morekey_settings_title,
            subtitle = R.string.morekey_settings_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "longPress",
            icon = R.drawable.arrow_up
        ),
        userSettingToggleDataStore(
            title = R.string.keyboard_settings_show_suggestion_row,
            subtitle = R.string.keyboard_settings_show_suggestion_row_subtitle,
            setting = ActionBarDisplayedSetting,
            icon = {
                Icon(painterResource(id = R.drawable.more_horizontal), contentDescription = null)
            }
        ),
        userSettingToggleDataStore(
            title = R.string.keyboard_settings_inline_autofill,
            subtitle = R.string.keyboard_settings_inline_autofill_subtitle,
            setting = InlineAutofillSetting
        ),
        userSettingToggleSharedPrefs(
            title = R.string.keyboard_settings_period_key,
            subtitle = R.string.keyboard_settings_period_key_subtitle2,
            key = Settings.PREF_ENABLE_ALT_PERIOD_KEY,
            default = {false},
        ),
        userSettingToggleDataStore(
            title = R.string.keyboard_settings_hide_when_hardware_keyboard_is_connected,
            setting = HideKeyboardWhenHardKeyboardConnected
        )
    )
)

val TypingSettingsMenu = UserSettingsMenu(
    title = R.string.typing_settings_title,
    navPath = "typing", registerNavPath = true,
    settings = listOf(
        UserSetting(
            name = R.string.typing_settings_auto_space_mode,
            component = {
                AutoSpacesSetting()
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_swipe,
            subtitle = R.string.typing_settings_swipe_subtitle,
            disabledSubtitle = R.string.typing_settings_swipe_disabled_by_key_sliding,
            disabled = { useSharedPrefsBool(Settings.PREF_KEY_SLIDING, false).value },
            key = Settings.PREF_GESTURE_INPUT,
            default = {true},
            icon = {
                Icon(painterResource(id = R.drawable.swipe_icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.key_sliding_title,
            subtitle = R.string.key_sliding_subtitle,
            key = Settings.PREF_KEY_SLIDING,
            default = {false},
            icon = {
                Icon(painterResource(id = R.drawable.swipe_icon), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleDataStore(
            title = R.string.typing_settings_suggest_emojis,
            subtitle = R.string.typing_settings_suggest_emojis_subtitle,
            setting = SHOW_EMOJI_SUGGESTIONS,
            icon = {
                Icon(painterResource(id = R.drawable.smile), contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.auto_cap,
            subtitle = R.string.auto_cap_summary,
            key = Settings.PREF_AUTO_CAP,
            default = {true},
            icon = {
                Text("Aa", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.use_double_space_period,
            subtitle = R.string.use_double_space_period_summary,
            key = Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
            default = {true},
            icon = {
                Text(".", style = Typography.Body.MediumMl, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_delete_pasted_text_on_backspace,
            key = Settings.PREF_BACKSPACE_DELETE_INSERTED_TEXT,
            default = {true}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.typing_settings_revert_correction_on_backspace,
            key = Settings.PREF_BACKSPACE_UNDO_AUTOCORRECT,
            default = {true}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.popup_on_keypress,
            key = Settings.PREF_POPUP_ON,
            default = {booleanResource(R.bool.config_default_key_preview_popup)}
        ),
        userSettingToggleSharedPrefs(
            title = R.string.vibrate_on_keypress,
            key = Settings.PREF_VIBRATE_ON,
            default = {booleanResource(R.bool.config_default_vibration_enabled)}
        ),
        UserSetting(
            name = R.string.typing_settings_vibration_strength,
            visibilityCheck = {
                LocalSharedPrefsCache.current!!.currSharedPrefs.getBoolean(
                    Settings.PREF_VIBRATE_ON,
                    booleanResource(R.bool.config_default_vibration_enabled)
                )
            },
            component = {
                val context = LocalContext.current
                val resources = LocalResources.current
                SyncDataStoreToPreferencesInt(vibrationDurationSetting, PREF_VIBRATION_DURATION_SETTINGS)

                SettingSlider(
                    title = stringResource(R.string.typing_settings_vibration_strength),
                    setting = vibrationDurationSetting,
                    range = -1.0f .. 100.0f,
                    hardRange = -1.0f .. 2000.0f,
                    transform = { it.roundToInt() },
                    indicator = {
                        if(it == -1) {
                            resources.getString(R.string.typing_settings_vibration_strength_default)
                        } else {
                            resources.getString(R.string.abbreviation_unit_milliseconds, "$it")
                        }
                    }
                )
            }
        ),
        userSettingToggleSharedPrefs(
            title = R.string.sound_on_keypress,
            key = Settings.PREF_SOUND_ON,
            default = {booleanResource(R.bool.config_default_sound_enabled)}
        ),
        UserSetting(
            name = R.string.typing_settings_keypress_sound_volume,
            visibilityCheck = {
                LocalSharedPrefsCache.current!!.currSharedPrefs.getBoolean(
                    Settings.PREF_SOUND_ON,
                    booleanResource(R.bool.config_default_sound_enabled)
                )
            },
            component = {
                val context = LocalContext.current
                val resources = LocalResources.current
                SyncDataStoreToPreferencesFloat(keySoundVolumeSetting, PREF_KEYPRESS_SOUND_VOLUME)

                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val value = remember { mutableFloatStateOf(0.0f) }
                val ringerMode = remember { mutableStateOf(audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) }
                val firstPlayback = remember { mutableStateOf(false) }

                LaunchedEffect(value.floatValue) {
                    delay(100L) // debounce
                    if(firstPlayback.value == false) {
                        firstPlayback.value = true
                        return@LaunchedEffect
                    }
                    val volume = value.floatValue.let {
                        if(it == -1.0f) {
                            Settings.readDefaultKeypressSoundVolume(resources)
                        } else {
                            it
                        }
                    }

                    val shouldPlay = audioManager.getRingerMode() == AudioManager.RINGER_MODE_NORMAL
                    ringerMode.value = shouldPlay

                    if(shouldPlay) {
                        audioManager.playSoundEffect(
                            AudioManager.FX_KEYPRESS_STANDARD,
                            volume
                        )
                    }
                }

                if(!ringerMode.value) {
                    Tip(stringResource(R.string.typing_settings_keypress_sound_volume_ringer_mode_warning))
                }

                Tip(stringResource(R.string.typing_settings_keypress_sound_volume_vendor_warning))

                SettingSlider(
                    title = stringResource(R.string.typing_settings_keypress_sound_volume),
                    setting = keySoundVolumeSetting,
                    range = 0.0f .. 1.0f,
                    hardRange = 0.0f .. 1.0f,
                    transform = {
                        value.floatValue = it
                        if(it == 0.0f) {
                            -1.0f
                        } else {
                            it
                        }
                    },
                    indicator = {
                        if(it <= 0.0f) {
                            resources.getString(R.string.typing_settings_keypress_sound_volume_default)
                        } else {
                            "${(it * 100.0f).roundToInt()}%"
                        }
                    }
                )
            }
        ),
    )
)

@Preview(showBackground = true)
@Composable
fun KeyboardAndTypingScreen(navController: NavHostController = rememberNavController()) {
    ScrollableList {
        if(IMESettingsMenu.visibilityCheck!!()) {
            ScreenTitle("", showBack = true, navController)
            IMESettingsMenu.render(showBack = false)
            ScreenTitle(
                stringResource(
                    KeyboardSettingsMenu.title
                ),
                showBack = false,
                navController
            )
        } else {
            ScreenTitle(
                stringResource(
                    KeyboardSettingsMenu.title
                ),
                showBack = true,
                navController
            )
        }

        KeyboardSettingsMenu.render(showBack = false, showTitle = false)
        TypingSettingsMenu.render(showBack = false)

        BottomSpacer()
    }
}