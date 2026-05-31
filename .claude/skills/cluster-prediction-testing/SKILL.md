---
name: cluster-prediction-testing
description: Test and preserve the prediction quality of the shiroikuma.futokxkb fork's CLUSTER layouts (Multiling-style multi-letter-per-key keyboards) across upstream FUTO syncs. Use this skill whenever cluster/column-key prediction quality must be verified or has regressed — after rebasing onto a new FUTO release (run as part of /upstream-new-version), when the user reports that suggestions on a cluster layout got worse, when investigating why a cluster layout shows wrong/raw candidates, or when working on the cluster-prediction pipeline (LanguageModelFacilitator.processAndMergeSuggestions / maybeApplyClusterConstraint / makePredictionInputValues, clusterAllowedSets / clusterTrieWalk / clusterBaseFold, or getValidNextCodePoints). Covers a PRE-BUILD static audit of the prediction pipeline's integration points AND an ON-DEVICE staged tracer (a default-off dev toggle, ClusterPredictionDebug) read via adb. Companion to futo-keyboard-build (pipeline internals) and upstream-new-version (the sync flow this plugs into).
---

# Preserving cluster-layout prediction quality across upstream syncs

The fork's headline feature is **Multiling-style cluster prediction**: on a `cluster`/`column` layout each key holds a SET of letters, so each tap commits to a set rather than one letter, and a bespoke pipeline constrains dict + LM suggestions to prefixes that stay inside the tapped sets and injects valid in-set words via a dictionary trie-walk. It works for English (transformer LM) and for no-transformer languages (Czech, Russian) via diacritic-folded matching on the legacy dict path. Full internals: **futo-keyboard-build → "Implemented features" → "Multiling-style cluster prediction" + "Cluster prediction for accented / no-transformer languages".**

This quality is **fragile across upstream syncs**: our pipeline hooks into FUTO's suggestion flow at a handful of integration points, and FUTO actively reworks that flow (their swipe/prediction push). A clean rebase replays OUR commits intact, but if upstream changed the *surrounding* semantics, our hooks can be silently bypassed and every cluster layout collapses to the raw dictionary tap-decoder (accent-blind, ignores the tapped sets) — looking exactly like the feature was never there.

> **The canonical example (0.1.29-rc1).** Upstream added a gate in `makePredictionInputValues`: when composing a word and the new `AllowTransformerOnNonQWERTYLayouts` setting is off (default), it returns `null` for any layout that isn't QWERTY-shaped. Cluster layouts aren't QWERTY, so this `null` skipped the transformer path (English) AND made `maybeApplyClusterConstraint`'s `makePredictionInputValues(ignorePassthrough=true)` call return null (cz/ru) — the whole cluster pipeline never ran. The rebase was clean; the feature still "broke." **Fix:** the gate now also accepts any layout carrying a cluster key (`kb.sortedKeys.any { !it.clusterMains.isNullOrEmpty() }`), reusing the same signal `clusterAllowedSets` keys on. This is exactly the class of regression the audit + tracer below exist to catch.

So testing has **two layers**: a **pre-build static audit** (catches integration-point drift before you build) and an **on-device staged tracer** (proves actual candidate quality after you build — the only faithful test, since cluster prediction needs the native arm64 dict decoder, the transformer model, the dict/model assets and the coordinate→key runtime, none of which run on a host JVM).

---

## The integration points (what cluster prediction depends on)

Memorise these — the audit checks each, and a trace tells you which one broke.

| # | Point | File | What our feature needs |
|---|-------|------|------------------------|
| 1 | `makePredictionInputValues(inputStyle, ignorePassthrough)` | `LanguageModelFacilitator.kt` | Returns non-null for cluster layouts. `ignorePassthrough=true` (our param) skips the legacy-passthrough guard; **any new gate that can return null must also exempt cluster layouts.** |
| 2 | The QWERTY/layout gate inside #1 | `LanguageModelFacilitator.kt` | Must accept `kb.sortedKeys.any { !it.clusterMains.isNullOrEmpty() }`. This is OUR added clause. |
| 3 | `maybeApplyClusterConstraint(inputStyle, dictResult)` | `LanguageModelFacilitator.kt` | Still **called from GeneralIME's `dictResult != null` (legacy) branch** in `updateSuggestionsDictionaryInternal`. |
| 4 | `processAndMergeSuggestions(values, dict, lm)` | `LanguageModelFacilitator.kt` | The merge point; holds `clusterAllowedSets` filter + `clusterTrieWalk`/`clusterEnumerateValid` injection + `clusterConfidentAutocorrect`. Still called from BOTH the transformer-merge branch and (with empty LM) from #3. |
| 5 | `getValidNextCodePoints(ComposedData)` | `DictionaryFacilitator.java` (iface) + `DictionaryFacilitatorImpl.java` + native `dictionary.cpp` `getNextValidCodePoints` | Signature + behaviour stable — the trie-walk enumeration (`clusterTrieWalk`) calls it for valid continuations. |
| 6 | `Key.clusterMains: List<ClusterMain>?` | `Key.kt` | The cluster-key signal used by #2 and `clusterAllowedSets`. |
| 7 | `ClusterKey`/`ColumnKey.computeData` emit `clusterMains` | `CompassKey.kt` | The runtime keys that carry the sets. |

---

## Layer A — pre-build static audit

Run after the rebase, **before building**. `$base_tag` = the tag custom was previously on, `$latest_tag` = the new one (both set by upstream-new-version Step 1).

### The tripwire: `audit.sh`

A script in this skill dir does the whole audit as one PASS/REVIEW/FAIL command — run it instead of assembling greps from memory:

```bash
bash .claude/skills/cluster-prediction-testing/audit.sh "$base_tag" "$latest_tag"
# new_tag is auto-detected from the current base if you omit the 2nd arg.
```

It (1) confirms our hooks survived the rebase in the current tree and (2) flags when upstream changed the suggestion-DECISION logic. **Exit codes:**
- **0 CLEAN** — hooks intact, no decision-logic drift → build, then Layer B.
- **1 REVIEW** — upstream touched the decision logic (it prints the exact added lines, e.g. a new gate in `makePredictionInputValues` or changed GeneralIME branching) → read them, confirm cluster layouts (`clusterMains`) still reach `processAndMergeSuggestions`, patch if needed, then build.
- **2 HARD FAIL** — a hook is missing (rebase mangled our code) or `getValidNextCodePoints` changed → patch before building.

It's a **tripwire, not a proof** — a clean exit still REQUIRES Layer B (the on-device tracer), because a semantic shift can pass a textual audit. (Run against `0.1.28 0.1.29-rc1` it correctly exits 1, surfacing the exact non-QWERTY gate that caused the original regression.)

### What it checks (run any of these by hand for deeper inspection)

```bash
cd ~/git/shiroikuma-futokxkb

# What upstream changed across the whole pipeline between the two bases:
git diff --stat "$base_tag" "$latest_tag" -- \
  java/src/org/futo/inputmethod/engine/general/GeneralIME.kt \
  java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt \
  java/src/org/futo/inputmethod/latin/Suggest.java \
  java/src/org/futo/inputmethod/latin/DictionaryFacilitator.java \
  java/src/org/futo/inputmethod/latin/DictionaryFacilitatorImpl.java \
  java/src/org/futo/inputmethod/latin/inputlogic/InputLogic.java \
  native/jni/src/suggest/core/dictionary/dictionary.cpp \
  native/jni/src/suggest/core/dictionary/dictionary.h

# RED FLAG: new early-returns / layout gates in makePredictionInputValues (point 1/2) —
# read the upstream diff of this method end-to-end; this is where 0.1.29-rc1's gate appeared:
git diff "$base_tag" "$latest_tag" -- java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt \
  | grep -E '^\+' | grep -E 'return null|mKeyboardLayoutSetName|qwerty|getSetting|isComposingWord|INPUT_STYLE_'

# getValidNextCodePoints must be unchanged (point 5):
git diff "$base_tag" "$latest_tag" -- \
  java/src/org/futo/inputmethod/latin/DictionaryFacilitator.java \
  java/src/org/futo/inputmethod/latin/DictionaryFacilitatorImpl.java \
  native/jni/src/suggest/core/dictionary/dictionary.cpp | grep -E 'getValidNextCodePoints|NextValidCodePoints'

# OUR hooks must still be present (each must print a hit):
grep -n 'clusterMains.isNullOrEmpty'  java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt   # point 2
grep -n 'maybeApplyClusterConstraint' java/src/org/futo/inputmethod/engine/general/GeneralIME.kt            # point 3
grep -n 'fun processAndMergeSuggestions\|fun clusterTrieWalk\|fun clusterAllowedSets' \
        java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt                                 # point 4
grep -n 'val clusterMains' java/src/org/futo/inputmethod/keyboard/Key.kt                                    # point 6
```

**Interpretation.** A change to GeneralIME's `updateSuggestionsDictionaryInternal` branching, a new gate/early-return in `makePredictionInputValues`, a `getValidNextCodePoints` signature change, or a rename of any point above = **plan a patch before building** (mirror the 0.1.29-rc1 fix: keep our hook working against the new code). If the diffs only touch swipe/gesture/batch paths (`INPUT_STYLE_TAIL_BATCH`/`UPDATE_BATCH`, `SwipeDecoderDictionary`, the native `ITrie`) and none of the seven points moved, the audit is **green** — but still run Layer B, because a semantic shift can pass a textual audit. **Keep `audit.sh`'s checks in sync** with this skill's integration-point table if you add or move a hook.

---

## Layer B — on-device staged tracer (`ClusterPredictionDebug`)

A permanent, **default-off** dev toggle ships in every build, so any build is a test build — no separate debug APK. It writes a staged trace of the cluster pipeline to a file (the file-based pattern that works where app logcat is suppressed, e.g. Huawei).

### Turning it on

**Settings → Developer → "Cluster prediction debug"** (the dev section; enable Developer mode first if hidden):
- **Trace cluster prediction to file** — writes `cluster_pred_debug.log` to the app's external files dir.
- **Isolate prediction (hide next-word + topBar)** — blanks the idle/next-word bar so ONLY the while-composing cluster candidates appear on screen. (While composing a word the bar is already pure corrections; this just removes next-word/topBar noise from the idle state so manual reading is unambiguous. The trace itself is always clean regardless.)
- **Clear cluster debug log** — deletes the file (clear before each test run).

### The cycle per upstream sync

**AGENT ACTION — right after the APK is on the device (immediately after the `adb push`), post the reminder block below to the user and WAIT for their "done".** The user can't be expected to remember the toggle/corpus steps; the on-device typing is the one part only they can do, so you must prompt for it explicitly every time rather than silently waiting. Then pull the log yourself.

> **📲 Cluster-prediction test — please do this on the phone, then tell me "done":**
> 1. Install the pushed APK (file manager → `/sdcard/tmp/<apk>`), open it over your current build.
> 2. **Settings → Developer → "Cluster prediction debug"** → tap **Clear cluster debug log**, turn **Trace cluster prediction to file** ON, and **Isolate prediction** ON.
> 3. On an **English** cluster layout type a few words (e.g. `simple`, `people`, `keyboard`); switch to **Czech** and type `těžko`, `daleko`, `jednoduché`; switch to **Russian** and type a few words.
> 4. Reply **"done"** — I'll pull and read the trace and confirm the quality, then tell you to switch the toggles back off.

Then:
1. Pull and read the trace:
   ```bash
   adb pull /sdcard/Android/data/shiroikuma.futokxkb/files/cluster_pred_debug.log /tmp/cluster_pred_debug.log
   ```
2. Verify quality (below). If degraded, the trace pinpoints the broken stage → patch the integration point → rebuild → re-verify (post the reminder again).
3. When quality is confirmed, tell the user to turn **Trace**/**Isolate** back off. (No rebuild needed — they're default-off; the clean release behaves normally with them off.)

### Reading the trace

Each suggestion update emits two related blocks. From `GeneralIME` (the path decision):

```
[12:00:01.234] update style=0 clusterLayout=true path=transformer-merge predValues=yes lm=18 dict=12
```

and from `processAndMergeSuggestions` (the staged candidates):

```
[12:00:01.240] processAndMerge composing="simpl" clusterSets=[s][i][m][p][l] lm=18 confAuto=true
    dict.raw      = simply(...) simple(...) ...
    lm(filtered)  = simple(...) simply(...) ...
    enum.trieWalk = simple(120) simply(95) simplest(40) ...
    enum.selected = simple(120) simply(95) ...
    FINAL (shown) = simple(...) simply(...) simplest(...) ...
```

For a no-transformer language (Czech/Russian) you instead see `path=legacy(+maybeCluster)` and a `maybeApplyClusterConstraint: applying cluster pipeline on legacy (no-transformer) path` line, then the same `processAndMerge` block with `lm=0`.

**What each field tells you:**
- `clusterLayout=true` + `path=...` — the layout was recognised as cluster and which branch ran. If `clusterLayout=true` but `path=legacy` for a language that HAS a transformer (English), the transformer was skipped — suspect a gate in `makePredictionInputValues` (point 1/2). If you see a `makePredictionInputValues REFUSED by QWERTY gate (... clusterLayout=true ...)` line, that gate has regressed our fix — **that is the 0.1.29-rc1 bug returning.**
- `clusterSets=[s][i][m]...` — the per-tap folded letter sets. If these are wrong/empty, coordinate→key mapping or `clusterMains` is broken (points 6/7), not the LM.
- `dict.raw` vs `FINAL` — the raw tap-decoder output vs what the user sees. The cluster value is in the *difference*: out-of-set words filtered out, valid in-set words injected.
- `enum.trieWalk` — the dictionary trie-walk results. Empty here while `enum.selected` falls back means `getValidNextCodePoints` returned nothing (point 5) — check that integration.

### Quality bar (what "super-high" means)

- The intended word appears in `FINAL` and as a **top** candidate; for ≥4-tap unambiguous words it becomes the **autocorrect** (`confAuto=true`).
- `FINAL` contains **only in-set words** (no out-of-set fuzzy neighbours like `těžce` for a `těžko` tap).
- Accented spellings appear for cz/ru (folded matching working): typing base letters yields `daleko`, `těžko`, `jednoduché`.
- English shows `path=transformer-merge` (LM contributing context ranking), cz/ru show the legacy cluster path with `lm=0` but still correct in-set words.

### Test corpus

Type these on the matching cluster layout; expect each to surface as a top candidate / autocorrect with a clean in-set `FINAL`.

- **English** (transformer; e.g. `kxkb_en_3+2_5r9c`): `simple`, `simply`, `people`, `keyboard`, and a short ambiguous one (`simp` → `simple`/`simply`/`simplistic` as suggestions, not autocorrected).
- **Czech** (no transformer): `těžko`, `daleko`, `jednoduché` (a long word — exercises the trie-walk's no-length-cap path).
- **Russian** (no transformer): a few common words spanning several taps.

(Adjust the exact words to the layouts in play; the structural checks — pipeline fired, sets correct, FINAL in-set — matter more than the specific word.)

---

## When you find a regression

Map the broken stage (from the trace) to its integration point, then patch surgically — keep the hook working against upstream's new code rather than reverting upstream. Re-derive from futo-keyboard-build's "Implemented features" if the cluster code itself was mangled by the rebase. Rebuild with the tracer still on, re-verify, then ship clean. Record the reconciliation in futo-keyboard-build's "Sync to a new upstream version" section (as the 0.1.29-rc1 gate fix is recorded).

## Related skills

- **`futo-keyboard-build`** — cluster-pipeline internals (the five-layer feature + the accented/no-transformer work), the build pipeline, and the per-sync doc-maintenance rule. Read it for *how the pipeline works*.
- **`upstream-new-version`** — the sync flow. This skill is its verification step: run Layer A after the rebase (before build), Layer B after the build (before declaring the sync done).
- **`multiling-futo-conversion`** — authors the cluster layout YAML that this prediction serves.
