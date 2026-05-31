---
name: upstream-new-version
description: Check whether FUTO Keyboard upstream (futo-org/android-keyboard) has a newer release — stable OR release-candidate (-rcN) — and, if so, sync the shiroikuma.futokxkb fork to it — fast-forward `master`, rebase the `custom` commit stack onto the new tag (reconciling conflicts when they're small, or stopping to plan with the user when they're significant), then build the new signed APK per the futo-keyboard-build skill. Use this skill whenever the user runs /upstream-new-version, or asks to "check for a new FUTO version", "sync to the latest FUTO release", "update to the new upstream", "rebase onto the new tag", "pull the new FUTO version/rc", or otherwise wants the fork brought up to a newer upstream release or release candidate. This is the orchestration layer on top of futo-keyboard-build; read that skill for all the build/rebase detail this one references.
---

# Sync the FUTO Keyboard fork to a new upstream release

One-command upstream sync for the user's `shiroikuma.futokxkb` fork: **check → (if new) rebase → build → test → push**. The user invokes it as `/upstream-new-version` with no further prompt, so this skill must carry the whole decision flow itself.

This is the **orchestration layer**. Every concrete fact (remotes, keystore, version scheme, the build block, conflict watch-points, the commit-1/commit-2 anchors) lives in the **`futo-keyboard-build`** skill — **read it before running this**, especially its "Sync to a new upstream version" and "Build + sign + deploy pipeline" sections. This skill sequences those pieces and adds the decision points the user asked for: *only continue automatically when conflicts are small; stop and plan when they're significant.*

## The one discipline that overrides everything: don't push until the user says "Push"

Same rule as the rest of this fork (see CLAUDE.md / futo-keyboard-build). The entire rebase + build happens on the **local** working tree as your scratchpad. **No `git push` — not `master`, not `custom` — until the user explicitly says "Push".** A rebase rewrites local `custom` history and is freely re-runnable (`git rebase --abort`, or reset to `origin/custom`) right up until that point. Build and let the user test on-device first.

## Step 0 — Preconditions

- cwd is the repo (`~/git/shiroikuma-futokxkb`); remotes are `origin` (SSH, fork) and `upstream` (HTTPS, futo-org, fetch-only) — confirm with `git remote -v`.
- Working tree clean (`git status --short` empty). If dirty, surface it and ask before proceeding — uncommitted scratch work would be caught in the rebase.
- Currently on (or able to check out) `custom`.

## Step 1 — Check upstream for a new release (stable OR release-candidate)

The user tracks release candidates too, so the sync target is the **highest upstream tag including `-rcN` pre-releases** — not stable-only.

```bash
cd ~/git/shiroikuma-futokxkb
git fetch upstream --tags
git fetch origin            # so origin/master / origin/custom are current for later

# current base tag of the custom stack = the upstream commit custom was rebased onto
base_tag=$(git describe --tags --exact-match "$(git merge-base custom upstream/master)")

# latest upstream tag, INCLUDING release candidates.
#   grep:  v? + bare semver + optional -rcN   (keeps releases AND rc's, drops junk)
#   sed:   strip a leading v, and rewrite  -rc -> ~rc  so sort -V orders it correctly —
#          GNU sort -V treats `~` as a pre-release marker (0.1.29-rc1 < 0.1.29). WITHOUT
#          this, sort -V wrongly ranks 0.1.28-rc1 ABOVE 0.1.28 (suffix read as additive).
#   final sed restores the real tag name.
latest_tag=$(git tag -l \
  | grep -E '^v?[0-9]+(\.[0-9]+)+(-rc[0-9]+)?$' \
  | sed -E 's/^v//; s/-rc/~rc/' \
  | sort -V | tail -1 \
  | sed -E 's/~rc/-rc/')

# latest STABLE (no -rc), for context only
latest_stable=$(git tag -l | grep -E '^[0-9]+(\.[0-9]+)+$' | sort -V | tail -1)

echo "custom is based on:             $base_tag"
echo "latest upstream tag (incl. rc): $latest_tag"
echo "latest upstream stable:         $latest_stable"
```

`latest_tag` is the max over a candidate pool that **includes `base_tag` itself**, so it is always ≥ `base_tag` — a plain string equality check is therefore enough:

- **`latest_tag == base_tag`** → already on the newest upstream tag. Report it ("custom is on `0.1.28`, the newest upstream tag — nothing to sync") and **stop**.
- **`latest_tag != base_tag`** → it is strictly newer → continue to Step 2. *(Currently: base `0.1.28`, latest `0.1.29-rc1` → sync.)* First show the user what's coming: `git log --oneline "$base_tag".."$latest_tag" | head -50` and the replay count `git rev-list --count "$base_tag"..custom` (was 95 at `0.1.28`).

**Flag pre-releases.** When the target is a release candidate (`case "$latest_tag" in *-rc*)`), say so explicitly before proceeding — e.g. "syncing onto `0.1.29-rc1`, a **release candidate** (no stable `0.1.29` yet)". The user opted into tracking rc's by running this, and every later step is gated + reversible, so proceed — but make the rc status clear so they can decline.

Notes on detection:
- `git merge-base custom upstream/master` is the base-tag commit because `custom` is rebased onto a tag on upstream's first-parent history; `--exact-match` names it — works whether the base is a stable tag or an rc. Cross-check against `~/tmp/.shiroikuma_futokxkb_build`'s first field if it exists.
- The **`-rc → ~rc` rewrite is essential**: GNU `sort -V` otherwise places `0.1.28-rc1` *after* `0.1.28`, which would make the skill try to "sync" backwards from a release to its own rc. With `~`, ordering is correct semver — `…-rc1 < … < (next)-rc1 < (next)` — so a newer version's rc beats an older stable, and a stable beats its own rc. Verified empirically. (When upstream's stable `X` later releases while you're on `X-rcN`, `latest_tag` becomes `X` and the skill rolls you forward off the rc onto the stable.)
- FUTO tags: bare semver for releases (`0.1.28`, `0.1.26.2`), `<version>-rcN` for candidates (`0.1.29-rc1`); only the ancient `v0.1.20..v0.1.22` carried a `v`. If a tag's nature is unclear, check the GitHub releases page or ask.

## Step 2 — Fast-forward `master` (local only; push deferred to "Push")

`master` is a pure mirror of upstream, fast-forward only, never carries our changes.

```bash
git checkout master
git merge --ff-only upstream/master
git checkout custom
```

Do **not** `git push origin master` here — defer it to the "Push" step with the `custom` push.

## Step 3 — Rebase the `custom` stack onto the new tag

```bash
git rebase "$latest_tag"
```

Then triage the outcome.

### Clean rebase → continue to Step 4.

### Conflicts → decide: "not huge" vs "significant"

The user's instruction: **reconcile and continue automatically only when the conflicts are not huge; if they're significant, stop and plan with me.** Apply this judgement honestly — when in doubt, treat it as significant and ask.

**"Not huge" — resolve in place, `git add`, `git rebase --continue`:**
- Only **commit 1** (`Customize for shiroikuma side-by-side install`) and/or **commit 2** (`Quieter build logs`) conflict. These are the documented rebase-sensitive base commits; their exact anchors and the resolution rule live in futo-keyboard-build (see *Reference* below). Rule: **keep our customization values, take upstream's changes around them.**
- Pure context-line shifts where our hunk obviously slots into the moved-but-equivalent surrounding code.
- A commit goes **empty** because upstream did the same thing (e.g. upstream silenced a warning commit 2 also silenced) → let git auto-skip / `git rebase --skip`.
- A small handful (≈1–3) of mechanical conflicts in feature commits where our change clearly maps onto the new code and the intent is unambiguous.

**"Significant" — STOP, do not guess, plan with the user:**
- Upstream **refactored / moved / renamed** a subsystem one of our feature commits depends on (e.g. `KeyboardState`, `LayoutEngine`/`mergeDuplicates`, the `v2keyboard` model + `KeyAttributes`/`ComputedKeyData`, `KeyboardView` draw path, `SavedKeyboardSizingSettings`/`KeyboardSizingCalculator`, `InputLogic`/`DeadKeyCombiner`, the suggestion/cluster pipeline) so the patch no longer maps cleanly.
- Many commits conflict, or the **same file conflicts repeatedly** across several replayed commits.
- A **semantic** conflict: the hunks merge textually but an API signature or behaviour upstream changed, so you can't be confident the feature still works without analysis.
- Any conflict whose correct resolution isn't obvious from the futo-keyboard-build documentation.

**When significant**, don't `--abort` silently and don't force a resolution. Instead:
1. Gather the picture without changing anything: `git status` (which commit is replaying — git names it), the conflicted hunks (`git diff`), and what upstream changed in each conflicted file (`git log --oneline "$base_tag".."$latest_tag" -- <file>` / `git show`).
2. Identify **which of our feature commits** is conflicting and **why** (map it to its entry in futo-keyboard-build's *Implemented features*).
3. **Present a plan and ask the user how to proceed** (use AskUserQuestion, or EnterPlanMode for a multi-commit mess). Typical options to offer:
   - **Resolve together** — walk the hunks with the user, since the right call needs their intent.
   - **Re-derive the affected commit** from futo-keyboard-build (each kxkb commit is small and documented) instead of fighting the merge.
   - **Drop / defer** the conflicting feature commit (note it for re-application later).
   - **Abort the whole sync** (`git rebase --abort`) and revisit — the tree returns to exactly where it was; nothing is lost.
   You may leave the rebase paused while discussing (state is preserved), or abort and resume later with the same `git rebase "$latest_tag"`. Make clear to the user that aborting is safe and re-runnable.

Don't push through a significant rebase to "get it building" — a silently mis-resolved feature is worse than a paused sync.

## Step 4 — Submodules

After a successful rebase:

```bash
git submodule update --init --recursive
```

(The `java/assets/layouts` / `java/assets/themes` submodules live on gitlab.futo.org and may be unreachable, but builds work as long as they were initialised once. `libs/` is required.)

## Step 5 — Build the new APK (apply the futo-keyboard-build pipeline)

Build directly with Bash (Claude Code — no copy-paste shell-block formatting, no `r()`/echo/pause-gate wrappers; those are obsolete here per CLAUDE.md). The version counter **resets to N=1** automatically because the base tag changed → versionName `<new-tag>+1`. This mirrors futo-keyboard-build's "Build + sign + deploy pipeline"; consult it if anything below needs detail.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
# Claude Code's non-interactive shell does NOT inherit the user's profile, so ANDROID_HOME is unset
# and gradle fails at configuration with "SDK location not found". Export it explicitly (the SDK is
# at ~/android-sdk). Verified needed during the 0.1.29-rc1 sync.
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
cd ~/git/shiroikuma-futokxkb

target_tag="$latest_tag"   # the new tag we just rebased onto (may be an -rcN)

# keystore.properties — regenerated each build, never committed
cat > keystore.properties <<'EOF'
keyAlias=futokxkb
keyPassword=futokxkb123
storeFile=/home/shiroikuma/.android-keystores/futokxkb.jks
storePassword=futokxkb123
EOF

# version: base name = tag; base code = first-parent commit count of the TAG; N from local counter (resets when base changes)
base_name="${target_tag#v}"
base_code=$(git rev-list --first-parent --count "$target_tag")
counter="$HOME/tmp/.shiroikuma_futokxkb_build"
stored_name=""; stored_n=0
[ -f "$counter" ] && read stored_name stored_n < "$counter"
if [ "$stored_name" = "$base_name" ]; then N=$((stored_n + 1)); else N=1; fi
our_name="${base_name}+${N}"
our_code=$(( base_code * 10000 + N ))
apk_name="shiroikuma-futokxkb_${our_name}_arm64-v8a.apk"
echo "Will produce: $apk_name (versionCode $our_code)"

# build stable/release/arm64; drop ONLY the three benign tool-emitted warnings (no source toggle);
# PIPESTATUS[0] preserves gradle's real exit code so errors / BUILD FAILED still surface.
build_ok=0
NOISE='CXX5304|AndroidManifest\.xml Warning:|was tagged at AndroidManifest\.xml:0 to (remove|replace) other declarations but no other declaration present|^warn: removing resource .* without required default value'
BRANCH_NAME='shiroikuma' VERSION_NAME="$our_name" VERSION_CODE="$our_code" \
  ./gradlew :assembleStableRelease --console=plain 2>&1 | sed -E "/$NOISE/d"
[ "${PIPESTATUS[0]}" = 0 ] && build_ok=1

if [ "$build_ok" = 1 ]; then
  echo "$base_name $N" > "$counter"            # consume the build number only on success
  built=$(ls -t build/outputs/apk/stable/release/*.apk | head -1)
  mkdir -p ~/tmp && cp "$built" ~/tmp/"$apk_name"   # local backup, unconditional
  ls -lh ~/tmp/"$apk_name"
else
  echo "BUILD FAILED — no APK. Diagnose the What went wrong / Caused by lines; do NOT install any leftover APK."
fi
```

- First build after a fresh checkout pulls a large dependency set + compiles native CMake (10–20+ min); later builds use the cache.
- An rc base flows through verbatim: `base_name="${target_tag#v}"` → `0.1.29-rc1`, so versionName = `0.1.29-rc1+1` and APK = `shiroikuma-futokxkb_0.1.29-rc1+1_arm64-v8a.apk` (the `-`/`+` are both legal in Android versionName + on disk). versionCode stays a plain integer (`base_code*10000 + N`).
- If the build **fails on the rebase**, that's a sign the conflict resolution was wrong (or upstream needs a toolchain bump) — treat it like a significant conflict: diagnose, and if it stems from the rebase, replan with the user rather than patching blindly.
- Toolchain reminders: JDK 21 (`./gradlew --stop` if a stale daemon picked the wrong JVM), Android platform-35 / build-tools 35.0.0, NDK 28.2.13676358, python3 for `updateLocales`. See futo-keyboard-build *Toolchain prerequisites*.

### adb push (per the standing rule)

On a successful build, the APK is already in `~/tmp/`. **Ask via the AskUserQuestion UI** whether to push to the phone — never auto-push, never plain prose (this is a saved user preference, `ask-before-adb-push`). On "push":

```bash
adb devices
adb push "$built" /sdcard/tmp/"$apk_name"   # user installs via the on-device file manager
```

Never `adb install` / `adb uninstall` — the user sideloads from the device file manager. If the cable's absent, the `~/tmp/` copy is the fallback (KDE Connect / Bluetooth).

## Step 6 — User tests on-device

Hand the build over and **wait**. The user installs the new APK over the previous fork build (same signing key → in-place update) and verifies the customisations still work on the new base. They may report regressions from the upstream bump; iterate locally (more edits, rebuild) — still no push.

## Step 7 — Only when the user says "Push"

Then, in one flow:

```bash
git push origin master                       # the deferred ff from Step 2
git push --force-with-lease origin custom     # the rebased stack (history rewritten)
```

`--force-with-lease` (never bare `--force`) so a surprise update to `origin/custom` aborts the push instead of clobbering it.

Then honour futo-keyboard-build's **Maintenance standing rule** ("after each pull/rebase"): update the docs to the new base — at minimum the `target_tag` / version examples in *Project identity* + *Versioning* + the build block, the base-tag and commit count and any moved commit hashes/line numbers in *Customization commits* / *Implemented features*, plus any rebase-conflict learning worth recording in *Sync to a new upstream version* — in **both** `futo-keyboard-build`'s SKILL.md and the top-level `CLAUDE.md` (its "Active recent work" + quick-reference tag). Commit those doc updates on `custom` (plain subject, no prefix) and push (force-with-lease). Treat the sync as incomplete until the docs reflect the new base. (The claude.ai-era "deliver a skill zip" step is obsolete here — you edit `.claude/skills/` directly.)

## Reference — conflict watch-points (condensed from futo-keyboard-build)

- **Commit 1** (`Customize for shiroikuma side-by-side install`): `build.gradle` `defaultConfig` — the `applicationId 'shiroikuma.futokxkb'` line and the `ndk { abiFilters 'arm64-v8a' }` line — and `java/res/values/strings-appname.xml` (`english_ime_name` → `白い熊 FUTO kxkb`). Leave `namespace`, `testApplicationId`, flavor suffixes untouched. If upstream restructures `defaultConfig`, re-anchor; keep our values, take upstream's surrounding changes.
- **Commit 2** (`Quieter build logs`): the `defaultConfig` `externalNativeBuild { cmake { cppFlags/cFlags '-w' } }`, `kotlinOptions { suppressWarnings = true }` (property form, not the method-call form), the top-level `tasks.withType(JavaCompile)` block (`-Xlint:none -nowarn -XDsuppressNotes`, `deprecation = false`), and the two `gradle.properties` lines. If upstream itself silences these or changes the `VERSION_CODE`/`VERSION_NAME` env reads, the commit may go empty → let git skip it. The `NOISE` filter in the build block must stay tight — do not broaden it.
- **General rule:** if conflicts feel non-trivial, re-derive each small commit from futo-keyboard-build rather than fighting the merge — which for feature commits means the "significant → plan with the user" path in Step 3.

## Related skills

- **`futo-keyboard-build`** — the canonical skill this one orchestrates: project identity, remotes/branches, the full build+sign+deploy pipeline, versioning, the keystore convention, the complete "Sync to a new upstream version" block, and the *Implemented features* reference for every kxkb commit (essential for judging which feature a conflict touches). Read it first.
- **`multiling-futo-conversion`** — unrelated to syncing; authors layout YAML.
