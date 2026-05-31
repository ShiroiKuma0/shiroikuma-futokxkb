#!/usr/bin/env bash
# cluster-prediction static audit (Layer A) for the shiroikuma.futokxkb fork.
#
# A pre-build TRIPWIRE, not a proof: it (1) confirms our cluster-prediction hooks
# survived the rebase intact in the current tree, and (2) flags when upstream
# changed the suggestion-DECISION logic our hooks plug into (e.g. the 0.1.29-rc1
# non-QWERTY gate in makePredictionInputValues). It cannot prove prediction works
# -- only Layer B (on-device tracer) can -- so a clean run still REQUIRES Layer B.
#
# Usage:  bash .claude/skills/cluster-prediction-testing/audit.sh <base_tag> <new_tag>
#   base_tag = the upstream tag custom was PREVIOUSLY on (before this sync)
#   new_tag  = the upstream tag custom was just rebased onto (auto-detected if omitted)
#
# Exit: 0 = clean   1 = REVIEW required (upstream touched decision logic)   2 = HARD FAIL
# Run from the repo root.

set -u
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "not in a git repo"; exit 2; }

base_tag="${1:-}"
new_tag="${2:-}"
[ -z "$new_tag" ] && new_tag="$(git describe --tags --exact-match "$(git merge-base HEAD upstream/master 2>/dev/null)" 2>/dev/null || true)"

if [ -z "$base_tag" ] || [ -z "$new_tag" ]; then
  echo "usage: audit.sh <base_tag> <new_tag>"
  echo "  base_tag required; new_tag auto-detected as: ${new_tag:-<none, pass it>}"
  exit 2
fi
git rev-parse --verify -q "${base_tag}^{commit}" >/dev/null || { echo "unknown base_tag: $base_tag"; exit 2; }
git rev-parse --verify -q "${new_tag}^{commit}"  >/dev/null || { echo "unknown new_tag: $new_tag"; exit 2; }

# --- pipeline files -------------------------------------------------------------
LMF=java/src/org/futo/inputmethod/latin/xlm/LanguageModelFacilitator.kt
GIME=java/src/org/futo/inputmethod/engine/general/GeneralIME.kt
DF=java/src/org/futo/inputmethod/latin/DictionaryFacilitator.java
DFI=java/src/org/futo/inputmethod/latin/DictionaryFacilitatorImpl.java
SUG=java/src/org/futo/inputmethod/latin/Suggest.java
KEY=java/src/org/futo/inputmethod/keyboard/Key.kt
COMPASS=java/src/org/futo/inputmethod/v2keyboard/CompassKey.kt
DICTCPP=native/jni/src/suggest/core/dictionary/dictionary.cpp
DICTH=native/jni/src/suggest/core/dictionary/dictionary.h

hard=0; review=0
ok()     { printf '  [OK]    %s\n' "$1"; }
fail()   { printf '  [FAIL]  %s\n' "$1"; hard=1; }
warn()   { printf '  [REVIEW] %s\n' "$1"; review=1; }

added()   { git diff "$base_tag" "$new_tag" -- "$1" | grep -E '^\+' | grep -vE '^\+\+\+'; }
removed() { git diff "$base_tag" "$new_tag" -- "$1" | grep -E '^\-' | grep -vE '^\-\-\-'; }

echo "=== cluster-prediction static audit: $base_tag -> $new_tag ==="
echo

# --- 1. Our hooks must still be present in the CURRENT tree (post-rebase) -------
echo "1. Hook presence (current tree = our rebased code):"
grep -q 'clusterMains.isNullOrEmpty'        "$LMF"  && ok "point 2: non-QWERTY-gate cluster bypass present in makePredictionInputValues" \
                                                     || fail "point 2: cluster bypass (clusterMains.isNullOrEmpty) MISSING from $LMF -- the fix was lost in the rebase"
grep -q 'maybeApplyClusterConstraint'       "$GIME" && ok "point 3: maybeApplyClusterConstraint still wired in GeneralIME legacy branch" \
                                                     || fail "point 3: maybeApplyClusterConstraint call MISSING from $GIME"
grep -q 'fun processAndMergeSuggestions'     "$LMF" && \
grep -q 'fun clusterTrieWalk'                "$LMF" && \
grep -q 'fun clusterAllowedSets'             "$LMF" && ok "point 4: processAndMergeSuggestions + clusterTrieWalk + clusterAllowedSets present" \
                                                     || fail "point 4: a cluster pipeline function is MISSING from $LMF"
grep -q 'val clusterMains'                    "$KEY" && ok "point 6: Key.clusterMains property present" \
                                                     || fail "point 6: Key.clusterMains MISSING from $KEY"
[ "$(grep -c 'clusterMains = mains' "$COMPASS")" -ge 2 ] && ok "point 7: ClusterKey + ColumnKey still emit clusterMains" \
                                                     || fail "point 7: ClusterKey/ColumnKey clusterMains emission changed in $COMPASS"
grep -q 'ClusterPredictionDebug'             "$LMF" && ok "tracer: ClusterPredictionDebug instrumentation present" \
                                                     || warn "tracer: ClusterPredictionDebug not found in $LMF (Layer B trace may be gone)"
echo

# --- 2. Did upstream change the suggestion-DECISION logic? (base..new diff) -----
echo "2. Upstream changes to the decision logic ($base_tag..$new_tag):"
gate_hits="$(added "$LMF" | grep -nE 'return null|return@|mKeyboardLayoutSetName|qwerty|sortedKeys|getSetting\(|isComposingWord|INPUT_STYLE_' || true)"
if [ -n "$gate_hits" ]; then
  warn "upstream added decision-logic lines in makePredictionInputValues' file -- READ the method and confirm cluster layouts (clusterMains) still reach the pipeline:"
  echo "$gate_hits" | sed 's/^/        /'
else
  ok "no new gate/early-return/layout-shape lines added to $LMF"
fi
branch_hits="$(added "$GIME" | grep -nE 'when\s*\{|predictionInputValues|dictResult|maybeApplyClusterConstraint|setNeutralSuggestionStrip|INPUT_STYLE_' || true)"
if [ -n "$branch_hits" ]; then
  warn "upstream changed GeneralIME suggestion branching -- confirm the dictResult!=null branch still calls maybeApplyClusterConstraint:"
  echo "$branch_hits" | sed 's/^/        /'
else
  ok "no changes to GeneralIME suggestion branching"
fi
echo

# --- 3. getValidNextCodePoints / native getNextValidCodePoints signature --------
echo "3. getValidNextCodePoints stability (the trie-walk depends on it):"
sig_removed="$( { removed "$DF"; removed "$DFI"; removed "$SUG"; } | grep -E 'getValidNextCodePoints' || true)"
nat_removed="$( { removed "$DICTCPP"; removed "$DICTH"; } | grep -E 'getNextValidCodePoints|NextValidCodePoints' || true)"
if [ -n "$sig_removed" ] || [ -n "$nat_removed" ]; then
  fail "getValidNextCodePoints / getNextValidCodePoints lines were REMOVED/CHANGED upstream -- the enumeration may break:"
  { [ -n "$sig_removed" ] && echo "$sig_removed"; [ -n "$nat_removed" ] && echo "$nat_removed"; } | sed 's/^/        /'
else
  ok "no getValidNextCodePoints/getNextValidCodePoints removals between the tags"
fi
echo

# --- verdict --------------------------------------------------------------------
echo "=== verdict ==="
if [ "$hard" = 1 ]; then
  echo "HARD FAIL: a hook is missing or getValidNextCodePoints changed. Patch BEFORE building."
  echo "(Then still run Layer B on-device to confirm quality.)"
  exit 2
elif [ "$review" = 1 ]; then
  echo "REVIEW REQUIRED: upstream touched the decision logic. Read the flagged lines, confirm cluster"
  echo "layouts still reach processAndMergeSuggestions (the bypass keys on Key.clusterMains), patch if"
  echo "needed, then build. Layer B on-device verification is MANDATORY regardless."
  exit 1
else
  echo "CLEAN: hooks intact, no decision-logic drift. Build, then run Layer B on-device (still required)."
  exit 0
fi
