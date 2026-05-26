package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem

val MiscMenu = UserSettingsMenu(
    title = R.string.misc_settings_title,
    navPath = "misc", registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_export_configuration_title))
        },

        userSettingNavigationItem(
            title = (R.string.settings_export_configuration),
            subtitle = (R.string.settings_export_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportingcfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_import_configuration),
            subtitle = (R.string.settings_import_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),

        // kxkb: a slim, non-destructive backup of just the keyboard configuration — all the per-combo
        // Keyboard UI settings (colours, sizes for every layout · language · geometry), toggles, layout
        // setup and themes — without the GB-scale voice/transformer models or dictionaries. Import keeps
        // those untouched (restore them by hand). Import auto-detects the slim file via its marker.
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_kb_backup_title))
        },
        userSettingNavigationItem(
            title = (R.string.settings_kb_export),
            subtitle = (R.string.settings_kb_export_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportingkbcfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_kb_import),
            subtitle = (R.string.settings_kb_import_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),

        // kxkb: learned data — personal dictionary, clipboard, learned-word/typing history (incl.
        // mozc/rime). Usually a few MB. Import is non-destructive (won't touch settings or models).
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_learned_backup_title))
        },
        userSettingNavigationItem(
            title = (R.string.settings_learned_export),
            subtitle = (R.string.settings_learned_export_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportinglearnedcfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_learned_import),
            subtitle = (R.string.settings_learned_import_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),

        // kxkb: the heavy resources — transformer & voice models + dictionaries (can be GBs). Import is
        // non-destructive (won't touch settings or learned data).
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_models_backup_title))
        },
        userSettingNavigationItem(
            title = (R.string.settings_models_export),
            subtitle = (R.string.settings_models_export_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportingmodelscfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_models_import),
            subtitle = (R.string.settings_models_import_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),
    )
)