package org.futo.inputmethod.latin.xlm

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * kxkb: a default-off, dev-section toggle that writes a STAGED trace of the cluster-prediction
 * pipeline to a file in getExternalFilesDir, so prediction quality can be inspected via
 *   adb pull /sdcard/Android/data/shiroikuma.futokxkb/files/cluster_pred_debug.log
 * (the file-based pattern that works on devices where app logcat is suppressed, e.g. Huawei).
 *
 * WHY: cluster prediction depends on the native arm64 dict decoder, the transformer model, the
 * coordinate->key runtime and the dict/model assets, so it cannot be exercised on a host JVM --
 * the only faithful test is on-device. This tracer makes EVERY build a test build (toggle off by
 * default): after an upstream sync, build, flip the toggle on, type the language test corpus, pull
 * the log, and the per-stage candidate dump pinpoints exactly where (if anywhere) quality is lost.
 *
 * The trace records, per non-batch suggestion update on a cluster layout:
 *   - makePredictionInputValues gate decision (cluster-layout detected? path values produced?)
 *   - the per-tap folded cluster sets (so the letter-set mapping can be verified)
 *   - the raw dictionary candidates, the transformer LM candidates (if any),
 *   - the trie-walk enumeration and the selected in-set injections,
 *   - the final merged candidates the user actually sees.
 *
 * See the cluster-prediction-testing skill for the corpus and the read procedure.
 */
object ClusterPredictionDebug {
    // Master toggle: write the staged prediction trace to the log file.
    val ClusterPredDebugTrace = SettingsKey(
        booleanPreferencesKey("cluster_pred_debug_trace"),
        false
    )

    // While tracing, suppress next-word predictions + topBar static defaults in the idle/neutral
    // bar state, so the on-screen bar only ever shows pure word-prediction/correction candidates.
    // (While COMPOSING a word the bar is already pure corrections, so this only affects the idle bar.)
    val ClusterPredDebugIsolate = SettingsKey(
        booleanPreferencesKey("cluster_pred_debug_isolate"),
        false
    )

    private const val LOG_NAME = "cluster_pred_debug.log"
    private const val MAX_BYTES = 1L * 1024 * 1024 // rotate at ~1 MB so a forgotten toggle can't fill storage

    fun isTraceEnabled(context: Context): Boolean = context.getSetting(ClusterPredDebugTrace)
    fun isIsolateEnabled(context: Context): Boolean = context.getSetting(ClusterPredDebugIsolate)

    private fun logFile(context: Context): File? =
        context.getExternalFilesDir(null)?.let { File(it, LOG_NAME) }

    fun clear(context: Context) {
        try { logFile(context)?.let { if (it.exists()) it.delete() } } catch (_: Exception) {}
    }

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Append a block to the trace file. [block] is only evaluated when tracing is enabled. */
    fun trace(context: Context, block: () -> String) {
        if (!isTraceEnabled(context)) return
        try {
            val f = logFile(context) ?: return
            if (f.exists() && f.length() > MAX_BYTES) f.delete()
            f.appendText("[${timeFmt.format(Date())}] ${block()}\n")
        } catch (_: Exception) {
            // diagnostics must never crash typing
        }
    }

    // --- compact formatters (top-N, score-annotated) -------------------------------------------

    fun fmtInfos(list: List<SuggestedWordInfo>?, n: Int = 12): String {
        if (list.isNullOrEmpty()) return "(none)"
        return list.take(n).joinToString(" ") { "${it.mWord}(${it.mScore})" } +
            if (list.size > n) " …+${list.size - n}" else ""
    }

    fun fmtWords(words: SuggestedWords?, n: Int = 12): String {
        if (words == null) return "(null)"
        return fmtInfos(words.mSuggestedWordInfoList, n)
    }

    fun fmtPairs(list: List<Pair<String, Int>>?, n: Int = 12): String {
        if (list.isNullOrEmpty()) return "(none)"
        return list.take(n).joinToString(" ") { "${it.first}(${it.second})" } +
            if (list.size > n) " …+${list.size - n}" else ""
    }

    fun fmtSets(sets: List<Set<Int>>?): String {
        if (sets == null) return "(not a cluster context)"
        return sets.joinToString("") { s ->
            "[" + s.sorted().joinToString("") { cp -> String(Character.toChars(cp)) } + "]"
        }
    }
}
