---
name: multiling-futo-conversion
description: Convert the user's Multiling O Android-keyboard layouts (their "kxkb" layouts) into FUTO Keyboard v2 YAML layouts for their shiroikuma.futokxkb fork. Use this skill ANY TIME the user uploads a Multiling layout (a text file full of bracket codes like [4D:…], [MC:…], [3+2:…], [SYM], [ALTGR], [Lock]) and asks to convert/port/translate it to FUTO, or mentions "Multiling", "kxkb layout", "convert my layout", "4D keys to compass", "port my keyboard layout", or references the sym/altGr/main/shifted sections of a Multiling layout. Also use when iterating on a previously converted layout. The deliverable is a single `.yaml` file the user imports via FUTO's Dev/Custom Layouts — NOT a fork code change. Default to assuming this skill applies whenever a Multiling-format keyboard layout is the input and a FUTO YAML layout is the wanted output. This skill is about AUTHORING the layout YAML; the fork's build/key-engine internals live in `futo-keyboard-build`.
---

# Multiling O → FUTO YAML layout conversion

The user is porting their hand-built **Multiling O** keyboard layouts to their FUTO Keyboard fork
(`shiroikuma.futokxkb`). They upload one Multiling layout at a time, give you the `name:`, and you
emit one FUTO v2 YAML layout file that reproduces it **as closely as the FUTO key model allows**,
focusing on the rich keys (compass / chord / cluster). The user imports the `.yaml` via **Dev/Custom
Layouts** in the app — this is layout authoring, not a code change, so there is **no patch and no
build block**: just create the file and present it.

## The single most important rule: ASK when anything is unclear

Multiling layouts are dense and idiosyncratic, and small misreads cascade across every key. **When a
bracket code, a slot order, an intended behaviour, or a mapping choice is genuinely ambiguous, STOP
and ask the user** rather than guessing. The user explicitly wants this. Good things to ask about are
listed at the end. Conversely, don't re-ask things this skill already settles (slot order, the
sym→alt0 / altGr→alt1 rule, etc.) — just apply them and note any caveats.

## Scope (what to convert, what to ignore)

Convert these Multiling sections:
- **`main`** → the base letter rows.
- **`shifted`** → folded into the base via per-key shift (see "Shift", below) — NOT a separate file.
- **`sym`** → **alt0** (`altPages` page 0).
- **`altGr`** → **alt1** (`altPages` page 1).
- **`topBar`** → the top-level **`topBar:`** list (static suggestions shown when nothing is typed — see "topBar", below). Near 1:1; the fork now supports it (used to be ignored).

Ignore: **`num`**, **`symShifted`**, **`altGrShifted`**, and any other special layers,
unless the user says otherwise. (`symShifted`/`altGrShifted` exist because Multiling's [SYM]/[ALTGR]
keys were shift-sensitive; FUTO alt pages aren't, so they collapse away.)

"As close as possible, preserving the visible layout" — for keys that can't be reproduced but can't be
dropped without breaking the grid, use a sensible placeholder (a plain base key, a `$gap`, or a rough
equivalent) and flag it.

## Reading the Multiling format

- A bracketed token `[...]` is **one key**. Bare consecutive characters are **individual keys** — so
  `($)` is three keys `(`, `$`, `)`, and `||` is two `|` keys.
- A leading `+` on a row line is just Multiling's row marker / a literal `+` key per the layout —
  check context (in the kxkb 5r15c layout the row-1 leading `+` is a literal `+` key).
- Rows can have different key counts; Multiling auto-sizes. The user's layout name (e.g. "5r15c") is
  their label, not a hard grid — count keys per row as written.

### Bracket-code reference (Multiling → FUTO)

| Multiling | Meaning | FUTO mapping |
|---|---|---|
| `[4D:p L U R D TL TR BR BL]` | 4-direction key: primary + 8 slides | **`compass`** (slot order below) |
| `[3+2:abc t b]` | band of 3 mains + top + bottom small | **`cluster`** `{ main: "abc", up: t, down: b }` |
| `[MC:[Mod]base:LABEL]` | modifier chord, sends Mod+base, key shows LABEL | **`chord`** `{ chord: "X-base", label: "LABEL" }` |
| `[MC:text]` (no `[Mod]`) | macro — types literal text | **`macro`** `{ macro: "text" }` (slot) / `{ type: macro, text: "text" }` (standalone) |
| `[XK:…]` | extra/long-press keys | `moreKeys` |
| `[MT:…]` | multitap cycle | **`cycle`** `{ type: cycle, taps: … }` |
| `[SYM]` | switch to sym layer | alt0 switch: `…|!code/key_to_alt_0_layout` |
| `[ALTGR]` | switch to altGr layer | alt1 switch: `…|!code/key_to_alt_1_layout` |
| `[SYM:x]` / `[ALTGR:x]` | tap = x, slide = open that pad | combined key: primary = x, page-switch on **up** slide |
| `[Lock]` | lock the special layer on | return-to-letters key: `← ABC|!code/key_to_alpha_0_layout` |
| `[Esc]` | escape | `Esc\|!code/key_escape` |
| `[Ctrl]` | one-shot Ctrl modifier | `Ctrl\|!code/key_ctrl` |
| `[Tab]` `[Enter]` `[Del]` `[Space]` `[Shift]` | functional keys | `!icon/tab_key\|!code/key_tab`, `…\|!code/key_enter`, `$delete`, spacebar (below), `$shift` |
| `[HIDE]` | hide keyboard | `!icon/action_hide_keyboard\|!code/action_hide_keyboard` |
| `[Up] [Down] [Left] [Right]` | cursor arrows | `!icon/action_up\|!code/action_up` (…down/left/right) |
| `[Undo]` | undo | `!icon/action_undo\|!code/action_undo` |
| `[Tool]` | gear / settings | `!icon/settings_key\|!code/key_settings` |
| `[VOICE]` | voice input | `!icon/action_voice_input\|!code/action_voice_input` |
| `[Paste]` | paste | on a key: `!icon/action_paste\|!code/action_paste`. In `topBar`: keep the literal `[Paste]` entry — the topBar engine handles it as paste (see "topBar"). |
| `[LB]` `[RB]` | literal `[` and `]` | `"["` / `"]"` |
| `[]` | empty spacer slot | `$gap` (or normalise — see Bottom row) |

If you meet a bracket code not in this table (`[123:…]`, `[EDIT:…]`, `[LANG:…]`, `[EMJ:…]`, etc.),
it's a layer/feature out of scope — take just its tap character if it has one, drop the rest, and
**ask** if you're unsure what the user wants.

## The compass slot order (memorise — it's the #1 source of error)

Multiling's `[4D:…]` lists slots in this order after the primary:

```
main, left, up, right, down, top-left, top-right, bottom-right, bottom-left
```

→ FUTO compass fields:

```
primary, left, up, right, down, upLeft, upRight, downRight, downLeft
```

A space in the Multiling token = an empty/skipped slot. A short token just leaves later slots empty.
Because the Multiling order differs from FUTO's compact `slide:` string order, **always write explicit
named slots** (`left:`, `up:`, …), never the compact `slide:` string.

Worked example — `[4D:o[MC:[Alt]o:M-o]Oó[MC:[Ctrl]o:C-o]ŌÓöō]`:

```yaml
- type: compass
  primary: o
  left:  { chord: "M-o", label: "M-o" }   # [MC:[Alt]o:M-o]
  up:    O
  right: ó
  down:  { chord: "C-o", label: "C-o" }    # [MC:[Ctrl]o:C-o]
  upLeft: Ō
  upRight: Ó
  downRight: ö
  downLeft: ō
```

## Chords

`[MC:[Ctrl]c:C-c]` — the part between the **first and second colon** (`[Ctrl]c`) is what's sent
(Ctrl + c); the trailing part (`C-c`) is the on-key **label**. → `{ chord: "C-c", label: "C-c" }`.

Notation: `C-` = Ctrl, `M-` = Alt/Meta, `S-` = Shift, `s-` = Super. **Shift must be explicit**:
`[Ctrl][Shift]x` → `C-S-x` (label often written `C-X`). Named bases: TAB, RET/ENTER, SPC/SPACE, ESC,
DEL, UP/DOWN/LEFT/RIGHT, HOME, END, PGUP/PGDN, F1–F12. So `[MC:[Shift][Tab]:S-TAB]` → `{ chord:
"S-TAB", label: "S-TAB" }`, `[MC:[Ctrl][Enter]:C-RET]` → `{ chord: "C-RET", label: "C-RET" }`.

**Caveat to flag:** the chord engine's base map only covers `a–z`, `0–9`, and the *unshifted* terminal
symbols `[ ] \ / - = ; ' , . ``. A chord whose base is a *shifted* symbol (`C->`, `C-<`, `C-:`) may
not emit. Convert it faithfully but note it for the user to test.

A `[MC:…]` with **no `[Mod]`** is a **macro**, not a chord (e.g. `[MC:1ˢᵗ]` types "1ˢᵗ"). In a compass
slot use `{ macro: "1ˢᵗ" }`; as a standalone key use `{ type: macro, text: "1ˢᵗ" }`.

## Clusters

`[3+2:abc t b]` = 3 mains (`abc`, the centre commits on tap, left/right reachable by slide) + a top
extra + a bottom extra → `{ type: cluster, main: "abc", up: t, down: b }`. The token's chars after the
3 mains fill top then bottom; a missing one is just omitted. Non-letter mains (`;`, `.`, `:`, digits)
commit fine but don't get prediction — that's expected.

## Shift — top row explicit, letter rows automatic

FUTO has **no shifted-layer block**; shift is **per-key**. So:
- **Letter rows:** plain keys auto-capitalise the **primary** on Shift (FUTO native, `shiftable`
  defaults true). Do nothing. Caveat to state: only the primary changes case — the slide slots stay
  as authored (they won't swap like Multiling's main-vs-shifted did). The user has accepted this
  ("others automatic"); if they want a specific letter key's shifted slots exact, wrap just that one
  in a CaseSelector.
- **Top (number/symbol) row:** reproduce the exact shifted symbols by wrapping each differing key in a
  CaseSelector, `normal` = the `main`-layer key, `shiftedManually` = the `shifted`-layer key (use
  `shiftedManually`, not `shifted`, so auto-caps at sentence start doesn't swap digits for symbols):

```yaml
- type: case
  normal:          { type: compass, primary: "1", left: { macro: "1ˢᵗ" }, up: ⓪, right: ①, down: "@" }
  shiftedManually: { type: compass, primary: "@", left: { macro: "1ˢᵗ" }, up: ⓪, right: ①, down: "1" }
```

Keys identical in `main` and `shifted` need no CaseSelector.

## Alt pages (sym → alt0, altGr → alt1)

Author each as a page under top-level `altPages:` (a list of pages; each page is a list of rows, same
`letters:` / `bottom:` shape as the base). Reach them with switch keys; FUTO alt pages are sticky
tap-toggles that **auto-revert to base after one printable keystroke** (fine — matches a momentary
feel).

- `[SYM]` → a key/slot `sym|!code/key_to_alt_0_layout`; `[ALTGR]` → `altGr|!code/key_to_alt_1_layout`.
- Put the page-switch on the **up** slide by default; if up is occupied, use the next free direction.
- Combined keys `[ALTGR:[Esc]]` / `[SYM:[Del]]`: primary = the tap char, page-switch on up:
  `{ type: compass, primary: "Esc|!code/key_escape", up: "altGr|!code/key_to_alt_1_layout" }`.
- `[Lock]` (lives on the sym/altGr pages) → the **return-to-letters** key
  `{ type: base, spec: "← ABC|!code/key_to_alpha_0_layout", attributes: { style: Functional, showPopup: false } }`.
  `key_to_alpha_0_layout` jumps to base from any page. **Do not** use the `$alphabet` template (it goes
  to the symbols layer). The label is free text — "← ABC" reads as "back to letters"; confirm the
  user's preference.

## Special / functional key specs

- Spacebar: `{ type: base, attributes: { width: Custom1, style: Spacebar }, spec: "!icon/space_key|!code/key_space" }`
  with a top-level `overrideWidths: { Custom1: 0.2 }`.
- `$shift`, `$delete`, `$gap` templates exist. `key_delete` is also a real code if you need it on a
  compass primary (or use the `$delete` template scalar, which resolves in a compass `primary`).
- Compass `primary` accepts a full key, a template scalar (`$delete`), or a `label|!code/…` spec.

## Auto long-press popups (`moreKeyMode`) and the `$` literal — two separate levers

Easy to conflate; they are not the same setting.

**`moreKeyMode` — the auto long-press popups.** FUTO derives number/symbol long-press keys from a key's
grid coordinate; a Regular-width letter/bottom-row key defaults to `MoreKeyMode.All` (auto num + sym).
Multiling layouts don't want those (their extras live in the slides), so suppress them with
`moreKeyMode: OnlyExplicit`. **This is also crash-avoidance, not just cosmetic:** a letter row whose
keys are all *non-counting* (clusters / compass) currently crashes FUTO's auto-moreKeys builder
(`MoreKeysBuilder.getNumForCoordinate`/`symsForCoord` index `numColumnsByRow[regularRow]` out of
bounds) unless those keys are `OnlyExplicit`. Compass keys already default to `OnlyExplicit`
(`CompassKey.kt`); set it explicitly on any cluster / other non-counting letter-row key you emit — or,
since converted layouts generally want no auto popups at all, set it once at top level so it propagates
to every key:

```yaml
attributes: { moreKeyMode: OnlyExplicit, useKeySpecShortcut: false }
```

*(A source fix is pending in the fork — `MoreKeysBuilder` should use `numColumnsByRow.getOrNull(...)`
and handle null; see the `futo-keyboard-build` skill. Once it lands the `OnlyExplicit` workaround is no
longer needed to avoid the crash, though you may still want it to silence the popups.)*

**`useKeySpecShortcut: false` — the keyspec shortcut table only.** Governs the keyspec shortcut
substitution, chiefly `$`→locale-currency, so a bare `$` stays a literal dollar (set it as above). It
does **not** touch the auto long-press popups (that is `moreKeyMode`), letter capitalisation,
`!icon/`/`!code/` resolution, or `$template` references.

## Bottom row

Multiling bottom rows often carry many `[]` spacer gaps and doubled keys. Default to **normalising**
to a clean functional row (e.g. Shift · space · Tab · ?,! · gear · `:` · `|` · Del · Enter · space ·
Shift), matching the user's own earlier hand-made test layout, and **say so** — offer the literal
gap-for-gap version if they'd rather. Render `||` as a single `|` key (the user's preference) unless
told otherwise.

## topBar (static suggestions when nothing is typed)

Multiling's `topBar` list maps **near 1:1** to the fork's top-level **`topBar:`** field (a list of
strings). These show in the suggestion bar whenever nothing is being composed (empty field, between
words, after Enter) and insert on tap. The fork's engine parses each entry — so **keep the Multiling
strings as-is**; don't expand them:

- plain text / emoji (`+`, `:@)`, `❤`, `☺`) → inserted literally.
- **`A…B`** (contains `…`, U+2026) → inserts `AB` with the caret **between** A and B (`(…)`→`()`,
  `“…”`, `[…]`, `{…}`).
- **`[Paste]`** → pastes the clipboard (keep the literal `[Paste]`).
- **`{{<pattern>`** → current date/time via Java `SimpleDateFormat` (`{{yyyy-MM-dd ` → `2026-05-29 `).
  Multiling used the same `{{` convention with the same pattern letters, so copy verbatim.

So the user's `["+","-","*","#","“…”","\"…\"","(…)","[Paste]",":@)","[…]","{{yyyy-MM-dd ","☺","❤","♡ ","{…}","{{yyyy-MM-dd_HH-mm-ss"]`
becomes simply:

```yaml
topBar: [ "+", "-", "*", "#", "“…”", "\"…\"", "(…)", "[Paste]", ":@)", "[…]", "{{yyyy-MM-dd ", "☺", "❤", "♡ ", "{…}", "{{yyyy-MM-dd_HH-mm-ss" ]
```

A layout's `topBar:` **overrides** the user's global default list (Settings → Typing → "Suggestion bar
candidates"); omit `topBar:` to fall back to the global list. Only emit `topBar:` if the Multiling
source has a topBar section (or the user asks). Engine details live in `futo-keyboard-build`.

## File structure (target)

```yaml
name: "<exactly what the user gives you>"
attributes: { moreKeyMode: OnlyExplicit, useKeySpecShortcut: false }
numberRowMode: AlwaysDisabled        # the layout carries its own top row
overrideWidths: { Custom1: 0.2 }
topBar: [ … ]                        # optional; only if the Multiling source had a topBar
rows:
  - letters: [ … row 1 … ]
  - letters: [ … ]
  - bottom:  [ … ]
altPages:
  - [ … alt0 (sym) rows … ]
  - [ … alt1 (altGr) rows … ]
```

Open each converted file with a comment block documenting the mapping decisions and every flagged
caveat (odd chords, placeholders, normalised bottom row, etc.) so the user can scan what to test.

## Naming

- **Filename:** the uploaded layout's filename with the extension changed to `.yaml`, otherwise
  byte-for-byte (e.g. `kxkb_gnu-emacs_5r15c_1_3_4.txt` → `kxkb_gnu-emacs_5r15c_1_3_4.yaml`).
- **`name:` field:** whatever the user tells you (they always provide it; e.g. "kxkb 5r15c 1.3.4").
  This is independent of the filename and is what shows in FUTO's layout picker.

## Validate before delivering

Write the file in `/home/claude`, then sanity-check it parses and the row/key counts look right:

```bash
python3 -c "import yaml,sys; d=yaml.safe_load(open('FILE.yaml')); print([ (list(r)[0], len(list(r.values())[0])) for r in d['rows'] ], 'alt:', [len(p) for p in d.get('altPages',[])])"
```

(pyyaml only catches YAML syntax, not FUTO/kaml semantics — but it catches the common mistakes.) Then
copy to `/mnt/user-data/outputs/<filename>` and `present_files`. **No patch, no build block** — it's a
Dev-import layout.

## Things to ASK about (don't guess)

- Any bracket code not in the reference table, or a `[4D:…]`/`[3+2:…]` token whose slot count or
  intended targets you can't cleanly parse.
- A `[MC:…]` whose payload is malformed or whose mod/base is unclear (e.g. an odd `[Ctrl]is`).
- Whether a specific letter-row key needs its shifted *slots* exact (vs primary-only auto-caps).
- Whether to normalise or literally reproduce a gappy bottom row, and what the return-key label should
  be, if the user hasn't already said.
- Anything where reproducing Multiling exactly conflicts with how FUTO can behave — surface the
  trade-off and let the user choose.

## Related skills

- `futo-keyboard-build` — the fork's build pipeline and the authoritative descriptions of the FUTO
  key-engine vocabulary (compass / macro / chord / cycle / cluster, alt pages, the Special Keys page).
  Read it when a mapping depends on engine behaviour, or before bundling a finished layout into the
  fork.
- `skill-export` — zip/naming convention for delivering this skill itself.
