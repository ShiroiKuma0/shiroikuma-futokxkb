<div align="center">

<img src="java/ic_launcher-playstore.png" width="120" alt="白い熊 不倒 kxkb app icon" />

# 白い熊 不倒 kxkb

## The name is a play on words - “不倒” is read FUTŌ and means indomitable, indestructible,
in Japanese - a very proper name for this powerful keyboard! :@)

**An offline, privacy-respecting keyboard — supercharged for power users.**

A fork of [FUTO Keyboard](https://keyboard.futo.org/) with **major additions**: a full in-app
layout editor, new kinds of keys, cluster-word prediction in any language, live resizing &
theming, an instant layout switcher, and deep terminal / Emacs support.

Installs **side-by-side** with the official FUTO Keyboard. *(不倒 — “indomitable - does not fall!”.)*

</div>

<!--
  README VIDEOS — playback on GitHub
  ----------------------------------
  The <video> tags below stream the clips committed under videos/ via their raw URLs, which
  GitHub plays inline on the rendered README. If a clip ever fails to load inline, the most
  reliable fallback is to open this file in GitHub's web editor, delete the <video> line, then
  drag-and-drop the .mp4 from videos/ into the editor — GitHub uploads it and inserts an
  official attachment URL that always plays. (Note: GitHub rejects any single file over 100 MB
  on push, so keep the clips compressed.)
-->

---

## 🛠 A full keyboard editor, built right in

Design and reshape any layout **without leaving the keyboard**. Tap a key to edit it; add, move,
duplicate or delete keys and whole rows; edit the symbol and AltGr pages; tune per-key colour,
size and spacing. A **real-size preview updates as you type**, and you can apply your changes
live or export the layout as YAML.

<video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_201147_1.mp4" controls muted width="360"></video>

---

## ⌨️ New kinds of keys

Far more than one letter per button:

- **Cluster keys** pack several letters into a single key — type whole words with fewer taps.
- **Compass / 4D keys** flick in eight directions for extra characters.
- **Modifier-chord keys** fire Emacs-style `Ctrl-`, `Meta-` and `Shift-` combinations straight
  from the keyboard — ideal for a real shell or editor on your phone.

<video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_195916_1.mp4" controls muted width="360"></video>

---

## 🔮 Word prediction that understands clusters — in any language

Tap the multi-letter cluster keys and let the keyboard **figure out the word you meant**. A
dictionary trie-walk with accent-folding makes prediction and autocorrect work even for
languages that have **no neural model and lots of diacritics** (Czech, Russian, …) — and it
keeps working in terminal fields where keyboards normally go silent.

<video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_200100_1.mp4" controls muted width="360"></video>

---

## 📐 Resize and reshape it live

**Standard, Split, One-handed or Floating — in any orientation.** Drag sliders for height, lift
off the bottom edge, width, key gaps, cluster spacing and key roundedness, and watch the
keyboard change **underneath your finger** in real time.

<video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_200300_1.mp4" controls muted width="360"></video>

---

## 🎨 Theme every pixel

Recolour the **keyboard, keys, suggestion bar and fonts**, set the caps-lock indicator colour,
adjust roundedness and transparency — all with a **live preview**, all saved per language and
layout.

<video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_200616_1.mp4" controls muted width="360"></video>

---

## 🌀 One swipe to anything

Swipe the spacebar for an **instant menu**: jump between languages and your own custom layouts,
or open the editor, resize, special keys and full settings — without digging through menus.

<table>
<tr>
<td align="center"><b>Quick switcher</b></td>
<td align="center"><b>Your custom layouts</b></td>
</tr>
<tr>
<td><video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_200010_1.mp4" controls muted width="360"></video></td>
<td><video src="https://raw.githubusercontent.com/ShiroiKuma0/shiroikuma-futokxkb/custom/videos/SVID_20260607_200954_1.mp4" controls muted width="360"></video></td>
</tr>
</table>

---

## ✍️ Power-user & developer touches

- **Built for terminals & Emacs** — force prediction on in raw-input (Termux) fields, send a
  real `Tab` key for shell / minibuffer completion, force auto-capitalization (even after
  newlines), and use dedicated code layouts with automatic spacing turned off.
- **Software dead keys** for diacritic composition (`ˇ` + `r` → `ř`).
- **Caps-lock by tap-cycling Shift**, with a coloured Shift-glyph indicator so the locked state
  is obvious.
- **Period-style spacing** for `:` `;` and `…`, plus an **expandable suggestions panel** with
  your own pinnable top-bar shortcuts (snippets, bracket-pairs, paste, date stamps).
- **Personal-dictionary shortcut** and **hold-to-repeat backspace** in every layout.

---

## Built on FUTO Keyboard

This project is a fork of [FUTO Keyboard](https://keyboard.futo.org/) (package
`shiroikuma.futokxkb`, so it coexists with the official build). FUTO Keyboard is itself a fork of
[LatinIME, the Android Open-Source Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME),
built to be a good modern keyboard that **stays offline and doesn’t spy on you**.

All upstream work and the project’s mission belong to the FUTO team — see the
[upstream repository](https://github.com/futo-org/android-keyboard/) for issues, contributing and
the canonical source. The code remains under the [FUTO Source First License 1.1](LICENSE.md).

## Building

Clone recursively to fetch all submodules:

```
git clone --recursive https://github.com/ShiroiKuma0/shiroikuma-futokxkb.git
```

If you forgot `--recursive`:

```
git submodule update --init --recursive
```

Then open the project in Android Studio, or build from the command line:

```
./gradlew assembleUnstableDebug
./gradlew assembleStableRelease
```
