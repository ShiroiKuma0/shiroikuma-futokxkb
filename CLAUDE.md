# shiroikuma-futokxkb

Fork of [FUTO Keyboard](https://github.com/futo-org/futo-keyboard-android) — package `shiroikuma.futokxkb`, label "白い熊 FUTO kxkb", installable side-by-side with the official FUTO Keyboard from F-Droid.

The `custom` branch carries ~56 customisation commits (all prefixed `kxkb:`) over the FUTO `0.1.28` release tag, rebased onto each new upstream release.

## Skills

Two skills in `.claude/skills/` document this project's workflow, conventions, and architecture in dense reference prose. **Read the relevant SKILL.md before starting work** — they encode hard-won detail (build env quirks, the kxkb commit stack's architecture, gotchas hit during development) that you won't find by reading the source. Each is named by trigger; the descriptions are tuned for automatic invocation.

- **`futo-keyboard-build`** (primary, ~664 lines) — project identity, branch/remote model, commits 1–2 documented in full, build + sign + deploy pipeline, versioning (local counter + env injection), keystore convention, per-change delivery workflow, upstream-rebase procedure, and the **Implemented features** reference covering every kxkb feature commit in dense paragraphs. Read this any time the user asks to apply a change, rebuild, sync to a new FUTO release, or work on anything in the kxkb stack.
- **`multiling-futo-conversion`** (companion, ~271 lines) — porting the user's Multiling O `kxkb` layouts to FUTO v2 YAML. Deliverable is a `.yaml` for FUTO's Dev/Custom Layouts import, NOT fork code. Read this any time a Multiling layout file (with `[4D:…]`, `[MC:…]`, `[3+2:…]`, `[SYM]`, `[ALTGR]`, `[Lock]` codes) is the input.

The `futo-keyboard-build` skill body has incidental references to two further skills — `shell-block-formatting` and `patch-naming` — that are NOT included here because they're obsolete in Claude Code (see workflow adaptations below). Just ignore those references; the rest of the skill is fully applicable.

## Claude Code vs claude.ai — workflow adaptations

The skills were originally written for claude.ai chat where Claude can't touch the user's filesystem. In Claude Code you can — adapt as follows:

- **Patch delivery is obsolete.** The "deliver patch + apply-and-build block, STOP" workflow (futo-keyboard-build §"Per-change delivery workflow", and any mention of `.patch` files or `patch-naming`) does not apply. Instead: edit files directly with `Edit`/`Write`, run the build with `Bash`, report results to the user. The user still tests each change on-device before committing.
- **Shell-block formatting is obsolete.** The `r()` helper, cyan `>>>` echo prefixes, ANSI-C-quoted stderr recolouring, and y/n pause gates (futo-keyboard-build §"Build + sign + deploy pipeline" intro and the `shell-block-formatting` skill it references) are for shell blocks the user copy-pastes into a terminal. In Claude Code you run commands directly via `Bash`; the user sees clean output. Just invoke gradle / adb / git plainly. The technical content (gradle invocation, NOISE filter, env-var versioning, keystore.properties heredoc, sideload steps) still applies — only the chat-formatting wrapper around it doesn't.
- **The one discipline that stays: do not commit or push until the user says "Push".** Treat the working tree as your scratchpad between user "Push" commands — multiple uncommitted fixes can stack. This is the same rule as in claude.ai, just without the patch-as-checkpoint mechanism.
- **`/mnt/skills/user/...` and `/home/claude/...` paths** in skill bodies are claude.ai sandbox paths — ignore them. Skills live in `.claude/skills/` here; the project clone IS your cwd.
- **Sandbox re-sync** on user "Done" (futo-keyboard-build §"Per-change delivery workflow" step 3) doesn't apply — there's no sandbox-to-resync. After a push, you're already at the new tip.
- **The `sandbox-environment` skill** referenced in futo-keyboard-build §"Companion skills" is claude.ai-only and not included here.
- **`skill-export`** referenced in user preferences is a zip-naming convention for sharing skills back to claude.ai; not used inside Claude Code (you edit `.claude/skills/` directly).
- **Skill edits take effect immediately** for subsequent invocations. Commit skill edits as their own `kxkb: ` commit, or bundle into a feature commit when the skill change documents that feature (which is the usual case — see how the cluster-prediction commit and its skill bullet were paired).

## Quick reference

- Branch: `custom`, rebased onto each upstream release tag
- Commit subject prefix: `kxkb:`
- Build (release / stable / arm64):
  ```
  BRANCH_NAME='shiroikuma' VERSION_NAME=<name> VERSION_CODE=<code> \
    ./gradlew :assembleStableRelease --console=plain
  ```
  See futo-keyboard-build §"Build + sign + deploy pipeline" for the full block.
- Versioning: per-build counter at `$HOME/tmp/.shiroikuma_futokxkb_build`, format `<tag>+N`; versionCode `tag_first_parent_count * 10000 + N`. Counter increments only on a successful build.
- Sideload: `adb push <apk> /sdcard/tmp/<name>.apk`, install via on-device file manager.
- Keystore: `keystore.properties` regenerated at build time from a heredoc — never committed.
- Submodules: `git submodule update --init --recursive` before first build; the `java/assets/layouts` and `java/assets/themes` submodules live on gitlab.futo.org (may be unreachable) but builds work without them as long as they were initialised once.

## Repo layout notes

- `java/src/` — Java/Kotlin sources (Android app)
- `native/jni/` — native C++ (dict decoder, transformer LM JNI bridge)
- `java/assets-custom/` — kxkb assets that ride alongside the upstream `java/assets/` submodule (used by the GNU-language registration, custom layouts, etc.)
- `common/`, `libs/` — shared infrastructure
- `build.gradle` — flavors (stable / unstable / playstore), signing, version env reads

## Active recent work

Last shipped: `kxkb: backspace auto-repeats when held in any layout` (commit `bfe244fbb`) — `BaseKey.computeData` now treats `CODE_DELETE` as repeatable (alongside the arrow actions), so longhand delete keys repeat when held without needing `repeatableEnabled` in the layout (the `$delete` template already did). 1 file.

Prior: `kxkb: append topBar defaults after next-word predictions (combined bar)` (commit `f25c887eb`), on top of the Multiling-style "topBar" feature (`432f70487`). The topBar static entries (emoji/snippets, `A…B` bracket-pairs with caret-between, `[Paste]`, `{{date` stamps) show in the suggestion bar's idle/next-word state; now they're **appended after any next-word predictions** (so both are selectable) instead of only when the bar is empty. While composing a word the bar stays pure (no defaults). Global list editable in Settings → Typing → "Suggestion bar candidates"; a layout's own `topBar:` overrides it. Engine: `GeneralIME.showPredictionsWithTopBar()` + `engine/general/TopBar.kt` parser + identity-matched pick interception in `onEventInternal`. No native change. Also confirmed this session: next-word prediction for cs/ru is **user-history-only by design** (no transformer model covers them; bundled dicts are unigram-only) — not a bug. Documented in full in futo-keyboard-build §"Implemented features".

Prior: `kxkb: expandable suggestions panel (Multiling-style) default-on + 128 candidates` (commit `2cfffdc45`). FUTO already ships an expandable candidates UI (`ActionBarWithExpandableCandidates` — scrollable inline row + a ▲ toggle that overlays the keyboard with the full list) but used it only for CJK, gated behind a default-off dev setting (`UseExpandableSuggestionsForGeneralIME`). Defaulted that to true, surfaced it as a Settings → Typing toggle ("Expandable suggestions panel"), and raised the merge candidate pool from 14 to 128 (`SuggestionResults` capacity + the dict `take`) so cluster layouts can flip through many candidates. 4 files, no native change. Documented in full in futo-keyboard-build §"Implemented features".

Prior: `kxkb: software dead keys for diacritic composition` (engine commit `45bba2f80`, cz/gnu 1.3.5 layouts `bfb0a429c`). Diacritic accent keys (`´ ˇ ¨ ¯ ˚` …) now compose with the next letter (`ˇ`+`r`→`ř`) instead of typing literally. FUTO's `DeadKeyCombiner` is already in every layout's combiner chain but only fires on `FLAG_DEAD` events (which AOSP only set for hardware dead keys); the fix re-dispatches an accent keypress as a dead event in `InputLogic.onCodeInput`, gated by `DeadKeyCombiner.isDeadAccentCodePoint` (dedicated accent display-forms `≥ U+0080` only, so literal ASCII `` ` `` `^` `~` `'` stay literal). 2 engine files, no native change. The cz/gnu layouts were bumped to 1.3.5 with macron/diaeresis/ring dead-key slots. Documented in full in futo-keyboard-build §"Implemented features".

Prior: `kxkb: cluster prediction for accented / no-LM languages (Czech, Russian)` (commit `bc1cd9e7c`). Made the Multiling-style cluster prediction work beyond English. Two root causes fixed, Kotlin-only (2 files), language-agnostic: (1) the cluster pipeline was transformer-gated — Czech/Russian have a dict but no transformer model, so they fell through to the legacy path with no cluster handling; now `maybeApplyClusterConstraint` runs it on the legacy dict path too (`makePredictionInputValues(ignorePassthrough=true)`). (2) accent-blindness — `clusterBaseFold` (NFD) folds accented dictionary letters onto the base-Latin/Cyrillic cluster sets. The cartesian enumeration was also replaced by a **dictionary trie-walk** (`clusterTrieWalk` via the existing `getValidNextCodePoints`) that handles any length (incl. `jednoduché`) and reads accents straight from the trie; ≥4-tap clusters now auto-commit the predicted word. Verified on Czech + Russian. Documented in full in futo-keyboard-build §"Implemented features". Prior five-layer base feature was `kxkb: Multiling-style cluster prediction` (commit `3bb85d1a1`).
