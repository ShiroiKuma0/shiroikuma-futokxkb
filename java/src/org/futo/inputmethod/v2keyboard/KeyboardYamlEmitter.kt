package org.futo.inputmethod.v2keyboard

// kxkb: YAML emitter for the layout editor. The codebase only PARSES yaml (kaml decode); kotlinx
// serialization cannot encode our model (CompassSlotSerializer / TapsSerializer / SpacedListSerializer
// all throw or TODO on serialize), so this is a hand-written emitter that walks the parsed [Keyboard]
// model and prints YAML.
//
// Strategy for fidelity:
//   - emit every key in the explicit `{ type: … }` object form (never the throwing shorthand paths),
//     EXCEPT a pure single-spec BaseKey, which round-trips as a bare scalar, and any key structurally
//     equal to a TemplateKeys value, which round-trips as its `$name` scalar.
//   - emit only NON-default fields (an omitted field re-parses to its default → model equality holds).
//   - emit list-form for row key-lists / moreKeys / languages (the YamlList branch sidesteps the
//     space-split scalar form and is always safe).
//
// Round-trip contract (verified by the editor spike): parseKeyboardYamlString(emitKeyboardYaml(kb)) == kb.

// ---- tiny YAML node model + block-style renderer ----

private sealed interface Y
private data class YScalar(val raw: String, val quote: Boolean = true) : Y
private data class YSeq(val items: List<Y>) : Y
private data class YMap(val entries: List<Pair<String, Y>>) : Y

private fun yStr(s: String): Y = YScalar(s, quote = true)
private fun yRaw(s: String): Y = YScalar(s, quote = false)
private fun yBool(b: Boolean): Y = YScalar(b.toString(), quote = false)
private fun yInt(i: Int): Y = YScalar(i.toString(), quote = false)
private fun yFloat(f: Float): Y = YScalar(f.toString(), quote = false)

private fun scalarText(s: YScalar): String {
    if (!s.quote) return s.raw
    val sb = StringBuilder("\"")
    for (c in s.raw) {
        when (c) {
            '\\' -> sb.append("\\\\")
            '"'  -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            '\r' -> sb.append("\\r")
            else -> sb.append(c)
        }
    }
    return sb.append("\"").toString()
}

private fun renderMap(map: YMap, indent: String, sb: StringBuilder) {
    for ((k, v) in map.entries) {
        when (v) {
            is YScalar -> sb.append(indent).append(k).append(": ").append(scalarText(v)).append("\n")
            is YSeq -> {
                if (v.items.isEmpty()) sb.append(indent).append(k).append(": []\n")
                else { sb.append(indent).append(k).append(":\n"); renderSeq(v, indent + "  ", sb) }
            }
            is YMap -> {
                if (v.entries.isEmpty()) sb.append(indent).append(k).append(": {}\n")
                else { sb.append(indent).append(k).append(":\n"); renderMap(v, indent + "  ", sb) }
            }
        }
    }
}

private fun renderSeq(seq: YSeq, indent: String, sb: StringBuilder) {
    for (item in seq.items) {
        when (item) {
            is YScalar -> sb.append(indent).append("- ").append(scalarText(item)).append("\n")
            is YSeq -> {
                if (item.items.isEmpty()) sb.append(indent).append("- []\n")
                else { sb.append(indent).append("-\n"); renderSeq(item, indent + "  ", sb) }
            }
            is YMap -> {
                if (item.entries.isEmpty()) sb.append(indent).append("- {}\n")
                else { sb.append(indent).append("-\n"); renderMap(item, indent + "  ", sb) }
            }
        }
    }
}

// ---- template reverse-map: a key equal to a TemplateKeys value emits as "$name" ----

private val templateReverse: List<Pair<AbstractKey, String>> by lazy {
    TemplateKeys.entries.map { it.value to it.key }
}

private fun templateName(key: AbstractKey): String? =
    templateReverse.firstOrNull { it.first == key }?.second

// ---- attributes ----

private fun labelFlagsNode(lf: LabelFlags): Y {
    val e = ArrayList<Pair<String, Y>>()
    if (lf.alignHintLabelToBottom) e.add("alignHintLabelToBottom" to yBool(true))
    if (lf.alignIconToBottom) e.add("alignIconToBottom" to yBool(true))
    if (lf.alignLabelOffCenter) e.add("alignLabelOffCenter" to yBool(true))
    if (lf.hasHintLabel) e.add("hasHintLabel" to yBool(true))
    if (lf.followKeyLabelRatio) e.add("followKeyLabelRatio" to yBool(true))
    if (lf.followKeyLetterRatio) e.add("followKeyLetterRatio" to yBool(true))
    if (lf.followKeyHintLabelRatio) e.add("followKeyHintLabelRatio" to yBool(true))
    if (lf.followKeyLargeLetterRatio) e.add("followKeyLargeLetterRatio" to yBool(true))
    if (lf.autoXScale) e.add("autoXScale" to yBool(true))
    return YMap(e) // empty map (all-false) round-trips to LabelFlags() all-false
}

// Returns null when there is nothing to emit (all fields null) so the caller omits the key.
private fun attrsNode(a: KeyAttributes?): Y? {
    if (a == null) return null
    val e = ArrayList<Pair<String, Y>>()
    a.width?.let { e.add("width" to yRaw(it.name)) }
    a.style?.let { e.add("style" to yRaw(it.name)) }
    a.anchored?.let { e.add("anchored" to yBool(it)) }
    a.showPopup?.let { e.add("showPopup" to yBool(it)) }
    a.moreKeyMode?.let { e.add("moreKeyMode" to yRaw(it.name)) }
    a.useKeySpecShortcut?.let { e.add("useKeySpecShortcut" to yBool(it)) }
    a.longPressEnabled?.let { e.add("longPressEnabled" to yBool(it)) }
    a.labelFlags?.let { e.add("labelFlags" to labelFlagsNode(it)) }
    a.repeatableEnabled?.let { e.add("repeatableEnabled" to yBool(it)) }
    a.shiftable?.let { e.add("shiftable" to yBool(it)) }
    a.fastMoreKeys?.let { e.add("fastMoreKeys" to yBool(it)) }
    a.heightRows?.let { e.add("heightRows" to yInt(it)) }
    a.color?.let { e.add("color" to yInt(it)) }
    a.fontScale?.let { e.add("fontScale" to yFloat(it)) }
    a.hintScale?.let { e.add("hintScale" to yFloat(it)) }
    a.backgroundColor?.let { e.add("backgroundColor" to yInt(it)) }
    a.borderColor?.let { e.add("borderColor" to yInt(it)) }
    return if (e.isEmpty()) null else YMap(e)
}

// ---- slots (compass / cluster / column directions) ----

private fun slotNode(slot: CompassSlot): Y = when (slot) {
    is KeySlot -> keyNode(slot.key)
    is ModSlot -> {
        val e = ArrayList<Pair<String, Y>>()
        e.add("mod" to yStr(slot.mod))
        slot.on?.let { e.add("on" to yStr(it)) }
        slot.label?.let { e.add("label" to yStr(it)) }
        YMap(e)
    }
}

// ---- keys ----

private fun keyNode(key: AbstractKey): Y {
    // 1. Known template → "$name"
    templateName(key)?.let { return yStr("\$$it") }

    // 2. Pure single-spec BaseKey → bare scalar (unless it would look like a template).
    if (key is BaseKey && key == BaseKey(key.spec)) {
        val looksTemplate = key.spec.startsWith("$") && key.spec != "$"
        if (!looksTemplate) return yStr(key.spec)
    }

    // 3. Explicit object form.
    return when (key) {
        is BaseKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("base"))
            e.add("spec" to yStr(key.spec))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            if (key.moreKeys.isNotEmpty()) e.add("moreKeys" to YSeq(key.moreKeys.map { yStr(it) }))
            key.hint?.let { e.add("hint" to yStr(it)) }
            key.code?.let { e.add("code" to yInt(it)) }
            YMap(e)
        }
        is CaseSelector -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("case"))
            e.add("normal" to keyNode(key.normal))
            if (key.shifted != key.normal) e.add("shifted" to keyNode(key.shifted))
            if (key.shiftedManually != key.shifted) e.add("shiftedManually" to keyNode(key.shiftedManually))
            if (key.shiftLocked != key.shiftedManually) e.add("shiftLocked" to keyNode(key.shiftLocked))
            if (key.symbols != key.normal) e.add("symbols" to keyNode(key.symbols))
            if (key.symbolsShifted != key.normal) e.add("symbolsShifted" to keyNode(key.symbolsShifted))
            YMap(e)
        }
        is GapKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("gap"))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is CompassKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("compass"))
            e.add("primary" to keyNode(key.primary))
            key.slide?.let { e.add("slide" to yStr(it)) }
            key.up?.let { e.add("up" to slotNode(it)) }
            key.down?.let { e.add("down" to slotNode(it)) }
            key.left?.let { e.add("left" to slotNode(it)) }
            key.right?.let { e.add("right" to slotNode(it)) }
            key.upLeft?.let { e.add("upLeft" to slotNode(it)) }
            key.upRight?.let { e.add("upRight" to slotNode(it)) }
            key.downLeft?.let { e.add("downLeft" to slotNode(it)) }
            key.downRight?.let { e.add("downRight" to slotNode(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.label?.let { e.add("label" to yStr(it)) }
            key.icon?.let { e.add("icon" to yStr(it)) }
            YMap(e)
        }
        is ClusterKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("cluster"))
            e.add("main" to yStr(key.main))
            key.up?.let { e.add("up" to slotNode(it)) }
            key.down?.let { e.add("down" to slotNode(it)) }
            key.upLeft?.let { e.add("upLeft" to slotNode(it)) }
            key.upRight?.let { e.add("upRight" to slotNode(it)) }
            key.downLeft?.let { e.add("downLeft" to slotNode(it)) }
            key.downRight?.let { e.add("downRight" to slotNode(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.label?.let { e.add("label" to yStr(it)) }
            key.icon?.let { e.add("icon" to yStr(it)) }
            YMap(e)
        }
        is ColumnKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("column"))
            e.add("main" to yStr(key.main))
            key.left?.let { e.add("left" to slotNode(it)) }
            key.right?.let { e.add("right" to slotNode(it)) }
            key.upLeft?.let { e.add("upLeft" to slotNode(it)) }
            key.upRight?.let { e.add("upRight" to slotNode(it)) }
            key.downLeft?.let { e.add("downLeft" to slotNode(it)) }
            key.downRight?.let { e.add("downRight" to slotNode(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.label?.let { e.add("label" to yStr(it)) }
            key.icon?.let { e.add("icon" to yStr(it)) }
            YMap(e)
        }
        is MacroKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("macro"))
            e.add("text" to yStr(key.text))
            key.label?.let { e.add("label" to yStr(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is ChordKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("chord"))
            e.add("keys" to yStr(key.keys))
            key.label?.let { e.add("label" to yStr(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is CycleKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("cycle"))
            e.add("taps" to YSeq(key.taps.items.map { yStr(it) }))
            key.label?.let { e.add("label" to yStr(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is FlickKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("flick"))
            e.add("primary" to keyNode(key.primary))
            key.up?.let { e.add("up" to keyNode(it)) }
            key.down?.let { e.add("down" to keyNode(it)) }
            key.left?.let { e.add("left" to keyNode(it)) }
            key.right?.let { e.add("right" to keyNode(it)) }
            key.upLeft?.let { e.add("upLeft" to keyNode(it)) }
            key.upRight?.let { e.add("upRight" to keyNode(it)) }
            key.downLeft?.let { e.add("downLeft" to keyNode(it)) }
            key.downRight?.let { e.add("downRight" to keyNode(it)) }
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.label?.let { e.add("label" to yStr(it)) }
            key.icon?.let { e.add("icon" to yStr(it)) }
            YMap(e)
        }
        is EnterKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("enter"))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is ActionKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("action"))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            YMap(e)
        }
        is ContextualKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("contextual"))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.fallbackKey?.let { e.add("fallbackKey" to keyNode(it)) }
            YMap(e)
        }
        is OptionalZWNJKey -> {
            val e = ArrayList<Pair<String, Y>>()
            e.add("type" to yRaw("optionalzwnj"))
            attrsNode(key.attributes)?.let { e.add("attributes" to it) }
            key.fallbackKey?.let { e.add("fallbackKey" to keyNode(it)) }
            YMap(e)
        }
        else -> // PeriodKey / TemplateCurrencyKey are only reachable via templates (handled above);
                // anything else is unknown — emit a visible marker rather than crashing.
            YMap(listOf("type" to yRaw("base"), "spec" to yStr("?UNSUPPORTED:${key::class.simpleName}")))
    }
}

// ---- rows ----

private fun rowNode(row: Row): Y {
    val e = ArrayList<Pair<String, Y>>()
    val keys = when {
        row.numbers != null -> { e.add("numbers" to YSeq(row.numbers!!.map { keyNode(it) })); "numbers" }
        row.bottom != null  -> { e.add("bottom" to YSeq(row.bottom!!.map { keyNode(it) })); "bottom" }
        else                -> { e.add("letters" to YSeq(row.letters!!.map { keyNode(it) })); "letters" }
    }
    val defaultRowHeight = if (row.numbers != null) NumberRowHeight else 1.0
    if (row.rowHeight != defaultRowHeight) e.add("rowHeight" to yRaw(row.rowHeight.toString()))
    val defaultSplittable = row.letters != null
    if (row.splittable != defaultSplittable) e.add("splittable" to yBool(row.splittable))
    if (row.numRowMode != RowNumberRowMode.Default) e.add("numRowMode" to yRaw(row.numRowMode.name))
    attrsNode(row.attributes)?.let { e.add("attributes" to it) }
    return YMap(e)
}

// ---- keyboard ----

/** Emit a [Keyboard] model back to a YAML string that re-parses to an equal model. */
fun emitKeyboardYaml(kb: Keyboard): String {
    val e = ArrayList<Pair<String, Y>>()
    e.add("name" to yStr(kb.name))
    if (kb.languages.isNotEmpty()) e.add("languages" to YSeq(kb.languages.map { yStr(it) }))
    if (kb.description.isNotEmpty()) e.add("description" to yStr(kb.description))

    val defOverrides = LayoutSetOverrides()
    if (kb.layoutSetOverrides != defOverrides) {
        kb.layoutSetOverrides.let { o ->
            e.add("layoutSetOverrides" to YMap(listOf(
                "symbols" to yStr(o.symbols),
                "symbolsShifted" to yStr(o.symbolsShifted),
                "number" to yStr(o.number),
                "phone" to yStr(o.phone),
                "phoneShifted" to yStr(o.phoneShifted),
            )))
        }
    }

    if (kb.numberRowMode != NumberRowMode.UserConfigurable) e.add("numberRowMode" to yRaw(kb.numberRowMode.name))
    if (kb.bottomRowHeightMode != BottomRowHeightMode.Fixed) e.add("bottomRowHeightMode" to yRaw(kb.bottomRowHeightMode.name))
    if (kb.bottomRowWidthMode != BottomRowWidthMode.SeparateFunctional) e.add("bottomRowWidthMode" to yRaw(kb.bottomRowWidthMode.name))
    attrsNode(kb.attributes)?.let { e.add("attributes" to it) }
    if (kb.overrideWidths.isNotEmpty()) {
        e.add("overrideWidths" to YMap(kb.overrideWidths.entries.map { (k, v) -> k.name to yFloat(v) }))
    }
    if (kb.rowHeightMode != RowHeightMode.ClampHeight) e.add("rowHeightMode" to yRaw(kb.rowHeightMode.name))
    if (kb.useZWNJKey) e.add("useZWNJKey" to yBool(true))
    if (kb.minimumFunctionalKeyWidth != 0.125f) e.add("minimumFunctionalKeyWidth" to yFloat(kb.minimumFunctionalKeyWidth))
    if (kb.minimumBottomRowFunctionalKeyWidth != 0.15f) e.add("minimumBottomRowFunctionalKeyWidth" to yFloat(kb.minimumBottomRowFunctionalKeyWidth))

    e.add("rows" to YSeq(kb.rows.map { rowNode(it) }))

    if (kb.altPages.isNotEmpty()) {
        e.add("altPages" to YSeq(kb.altPages.map { page -> YSeq(page.map { rowNode(it) }) }))
    }
    if (kb.combiners != listOf(CombinerKind.DeadKey)) {
        e.add("combiners" to YSeq(kb.combiners.map { yRaw(it.name) }))
    }
    if (kb.topBar.isNotEmpty()) e.add("topBar" to YSeq(kb.topBar.map { yStr(it) }))
    if (!kb.autoShift) e.add("autoShift" to yBool(false))
    kb.imeHint?.let { e.add("imeHint" to yStr(it)) }
    // subKeyboards intentionally not emitted (no kxkb layout uses it); flagged if present.
    if (kb.subKeyboards.isNotEmpty()) {
        e.add("_unsupported_subKeyboards" to yStr("present (${kb.subKeyboards.keys.joinToString()}) — not emitted"))
    }

    val sb = StringBuilder()
    renderMap(YMap(e), "", sb)
    return sb.toString()
}
