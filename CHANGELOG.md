# Changelog

All notable changes to **白い熊 不倒 kxkb** — a power-user fork of [FUTO Keyboard](https://keyboard.futo.org/) — are recorded here, newest first. Built-and-signed APKs live on the [Releases page](https://github.com/ShiroiKuma0/shiroikuma-futokxkb/releases); the app installs side-by-side with the official FUTO Keyboard (package `shiroikuma.futokxkb`).

Versions are `<FUTO base>+<build>`. The fork tracks upstream FUTO releases and rebases its additions on top of each one.

## [0.1.29+10] — 2026-06-07

First public release, based on **FUTO Keyboard 0.1.29**. Everything below is added on top of upstream.

### New key types & key behaviour
- **Cluster keys** — several letters share one key; tap once and prediction resolves the word (Multiling-style).
- **Compass / 4D keys** — a centre letter plus directional flicks, with at-rest direction labels and a state-aware slide icon.
- **Column keys** — a vertical predictive cluster.
- **Cycle (multitap) keys** — tap repeatedly to cycle through a set of characters.
- **Chord keys** — fire a combination from a single key.
- **Macro keys** — emit a whole string / snippet.
- **Case keys** — per-shift-state keys that change with Shift / caps-lock.
- **Software dead keys** — accent keys compose with the next letter (`´ ˇ ¨ ¯ ˚` …; e.g. `ˇ`+`r` → `ř`).
- **Row-spanning keys** — a key can be taller than one row.
- **Hold-to-repeat** — Backspace and the arrow keys auto-repeat when held, in any layout.
- **Cluster / column auto-capitalise** — the whole predictive band upper-cases when the layout is shifted.
- **Adjacent identical keys render separately** — duplicate neighbours no longer merge into one wide key.
- **Long-press study popup** — preview a complex key's full set of characters.
- **Hide-keyboard key** and **alt-page keys** (jump to symbols / AltGr and back).

### Word prediction & the suggestion bar
- **Cluster word-prediction** — type whole words on multi-letter keys; the right word is chosen from the dictionary.
- **Works in any language** — a dictionary trie-walk with accent-folding brings prediction & autocorrect to languages with no neural model and heavy diacritics (Czech, Russian, …).
- **Autocorrect on space** for cluster layouts, keeping the literal typed form as a selectable 2nd candidate.
- **Expandable suggestions panel** (on by default), showing up to **128 candidates** in a scrollable / overlay list.
- **Static "suggestion bar candidates"** when nothing is typed — emoji / snippets, `A…B` bracket-pairs (cursor between), `[Paste]`, `{{date}}` stamps — now **combined with** next-word predictions.
- **Per-layout candidate lists** — each layout can carry its own suggestion-bar set, editable in the keyboard editor.

### In-app visual keyboard editor (Settings → Keyboard UI → "Keyboard editor")
- **Tap any key to edit it** on a real-size live preview built from the layout itself.
- **Change a key's type** (base / cluster / compass / column / cycle / chord / macro / case / gap) and all its fields.
- **Structural editing** — add, move, insert, delete and **duplicate** keys and whole rows; **swap keys up / down** between rows.
- **Alt-page editing** — edit the symbol / AltGr pages; reorder / add / delete pages; **copy a page from another layout**.
- **Per-key appearance** — text colour, font & hint size, background & border colour, cluster spacing, character position and secondary-label offsets — each shown on a single-key live crop preview.
- **Save & share** — apply live, **export the layout as YAML**, or **save as a new layout**.
- **Conveniences** — per-layout candidate editor, preselects the layout you came from, a bottom "Close" button, and a configurable font for the layout-name labels.

### Live appearance, sizing & layout modes (saved per language · layout · orientation)
- **Keyboard modes** — Standard, Split, One-handed and Floating, selectable in **any** orientation.
- **Live size knobs** — keyboard / row heights, horizontal & vertical gaps, key roundness, primary & secondary text sizes, suggestion-bar height — all with a live preview.
- **Width & lift** — narrow + centre the whole keyboard, set split width, and lift the keyboard off the bottom edge; narrowed / lifted / split keyboards become **see-through** so the app shows through the gaps.
- **Full colour theming** — recolour the keyboard, keys (background + border), suggestion bar and fonts, with transparency; high-contrast **yellow-on-black** default look.
- **Caps-lock indicator** — tap-cycle Shift to caps-lock, shown by a recoloured Shift glyph (colour configurable).

### Pinnable quick-actions & the spacebar switcher
- **Swipe-the-spacebar switcher** — an instant menu with a languages column (most-recent first), a custom-layouts column, and a configurable shortcut header (editor, resize, special keys, settings).
- **Pinnable action buttons** — Key-sliding (4D) on/off, Terminal prediction on/off, Force auto-capitalization on/off, Live sizing, Special keys, Custom layouts, Personal dictionary, plus a **Special Keys reference page**.
- **Layout-switch action** — "Next layout (same language)".

### Typing, spacing & punctuation
- **Force auto-capitalization** (optional, pinnable) — capitalises after `.` `?` `!` and **after newlines**, even in apps that don't request it.
- **Smart spacing for `:` `;` and `…`** — they now space like ordinary sentence punctuation.

### Terminal, shell & Emacs
- **Real Escape key** (`KEYCODE_ESCAPE`) and a **one-shot Ctrl modifier** that sends genuine `Ctrl+<key>` events (highlighted while armed) — for vim / Emacs / shells.
- **Tab sends a real Tab key** (shell / Emacs-minibuffer completion) instead of a literal tab character.
- **Terminal prediction toggle** — forces composing & prediction on in raw-input fields (Termux / `TYPE_NULL`), so you get suggestions and space-committed words over SSH.
- **GNU code layouts** — a dedicated `zxx` "GNU" language with code-friendly layouts and automatic spacing / prediction switched off.

### Languages, layouts, backup & app identity
- **Installs side-by-side** with the official FUTO Keyboard (`shiroikuma.futokxkb`).
- **Bundled kxkb layouts**, including a default usable **before first unlock** (taller, full-width).
- **GNU (`zxx`) language** registered for code input.
- **App-language setting** — choose the in-app UI language independently of the system (offers English (US) and persists across restarts).
- **Scoped settings backup** — export / import config in three sets: Settings, Learned data, Models.
- **App identity** — black / yellow **不倒** launcher mark (Minchō Bold wordmark) and label **白い熊 不倒 kxkb**.

**Build:** versionCode `116900010` · arm64-v8a · base FUTO `0.1.29` · APK SHA-256 `72ec5de413ed9fcbea7da6a18de8e6ed4d98e37e41ff035d9dc9c6f4f6eafd2c`

[0.1.29+10]: https://github.com/ShiroiKuma0/shiroikuma-futokxkb/releases/tag/0.1.29%2B10
