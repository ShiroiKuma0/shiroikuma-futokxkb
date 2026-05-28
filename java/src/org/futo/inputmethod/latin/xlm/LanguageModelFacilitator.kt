package org.futo.inputmethod.latin.xlm;

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.general.OnGetSuggestedWordsCallbackWithInputStyle
import org.futo.inputmethod.keyboard.Keyboard
import org.futo.inputmethod.keyboard.KeyboardSwitcher
import org.futo.inputmethod.latin.BinaryDictionary
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.Dictionary
import org.futo.inputmethod.latin.DictionaryFacilitator
import org.futo.inputmethod.latin.NgramContext
import org.futo.inputmethod.latin.Suggest
import org.futo.inputmethod.latin.SuggestedWords
import org.futo.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.futo.inputmethod.latin.SuggestionBlacklist
import org.futo.inputmethod.latin.common.ComposedData
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_TRANSFORMER_FINETUNING
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.utils.SuggestionResults
import kotlin.math.ceil


val AutocorrectThresholdSetting = SettingsKey(
    floatPreferencesKey("lm_autocorrect_threshold"),
    4.0f
)

val BinaryDictTransformerWeightSetting = SettingsKey(
    floatPreferencesKey("binary_dict_result_weight"),
    3.4f
)

val AllowTransformerOnNonQWERTYLayouts = SettingsKey(
    booleanPreferencesKey("allow_transformer_lm_on_non_qwerty"),
    false
)

internal fun SuggestedWordInfo.add(other: SuggestedWordInfo): SuggestedWordInfo {
    assert(mWord == other.mWord)

    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        (mScore.coerceAtLeast(0).toLong() + other.mScore.coerceAtLeast(0).toLong())
            .coerceAtMost(
                Int.MAX_VALUE.toLong()
            ).toInt(),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        mSourceDict ?: other.mSourceDict,
        0,
        0
    )

    result.mOriginatesFromTransformerLM = mOriginatesFromTransformerLM || other.mOriginatesFromTransformerLM

    return result
}


internal fun SuggestedWordInfo.scoreAtLeast(other: SuggestedWordInfo): SuggestedWordInfo {
    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        mScore.coerceAtLeast(other.mScore + 1),
        SuggestedWordInfo.KIND_WHITELIST or SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        mSourceDict,
        0,
        0
    )

    return result
}

internal fun SuggestedWordInfo.scoreAtMost(other: SuggestedWordInfo): SuggestedWordInfo {
    val result = SuggestedWordInfo(
        mWord,
        mPrevWordsContext,
        mScore.coerceAtMost(other.mScore - 1),
        mKindAndFlags,
        mSourceDict,
        0,
        0
    )

    return result
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length

    val dist = Array(len1 + 1) { IntArray(len2 + 1) }

    for (i in 0..len1) {
        dist[i][0] = i
    }
    for (j in 0..len2) {
        dist[0][j] = j
    }

    for (j in 1..len2) {
        for (i in 1..len1) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dist[i][j] = minOf(
                dist[i - 1][j] + 1,
                dist[i][j - 1] + 1,
                dist[i - 1][j - 1] + cost
            )
        }
    }

    return dist[len1][len2]
}

public class LanguageModelFacilitator(
    val context: Context,
    val inputLogic: InputLogic,
    val dictionaryFacilitator: DictionaryFacilitator,
    val settings: Settings,
    val keyboardSwitcher: KeyboardSwitcher,
    val lifecycleScope: LifecycleCoroutineScope,
    val suggestionBlacklist: SuggestionBlacklist,
    val suggestedWordsCallback: OnGetSuggestedWordsCallbackWithInputStyle
) {
    private val TAG = "LanguageModelFacilitator"
    private val userDictionary = UserDictionaryObserver(context)

    private var languageModel: LanguageModel? = null
    data class PredictionInputValues(
        val composedData: ComposedData,
        val ngramContext: NgramContext,
        val inputStyle: Int,
        val sequenceId: Int
    )
    private val sharedFlow = MutableSharedFlow<PredictionInputValues>(replay = 0, extraBufferCapacity = 1)

    private var currentSequenceId = 0
    private val sequenceIdFinishedFlow = MutableSharedFlow<Pair<PredictionInputValues, SuggestedWords?>>(replay = 1, extraBufferCapacity = 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    val languageModelScope = LanguageModelScope

    private var numConsecutiveTimeouts = 0
    private var transformerDisabled = false

    fun reportTimeout() {
        if(shouldPassThroughToLegacy()) return

        if(BuildConfig.DEBUG) Log.d(TAG, "Failed to complete prediction within the time!")
        numConsecutiveTimeouts += 1
        if(numConsecutiveTimeouts > 5) {
            transformerDisabled = true
            if(BuildConfig.DEBUG) Log.w(TAG, "Temporarily disabling transformer due to continuous timeouts")
        }
    }

    private var skipLanguage: String? = null
    private suspend fun runLanguageModel(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(transformerDisabled) return null

        val locale = dictionaryFacilitator.primaryLocale ?: return null
        if ((languageModel == null && locale.language != skipLanguage) || (languageModel != null && languageModel?.locale?.language != locale.language)) {
            skipLanguage = null
            if(BuildConfig.DEBUG) Log.d(TAG, "Calling closeInternalLocked on model due to seeming locale change")
            languageModel?.closeInternalLocked()
            languageModel = null

            // TODO: Cache value so we're not hitting this repeatedly
            val options = ModelPaths.getModelOptions(context)
            val model = options[locale.language]
            if (model != null) {
                languageModel = LanguageModel(context, lifecycleScope, model, locale)
            } else {
                if(BuildConfig.DEBUG) Log.d(TAG, "no model for ${locale.language}")
                skipLanguage = locale.language
                return null
            }
        }

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return null

        val keyboard = keyboardSwitcher.keyboard ?: return null

        val proximityInfoHandle = keyboard.proximityInfo.nativeProximityInfo

        val autocorrectThreshold = context.getSetting(AutocorrectThresholdSetting)

        try {
            return languageModel?.getSuggestions(
                values.composedData,
                values.ngramContext,
                proximityInfoHandle,
                autocorrectThreshold,
                userDictionary.getWords(listOf(locale)).map { it.word },
                suggestionBlacklist.currentBlacklist.toTypedArray<String>()
            )
        }catch (e: ModelLoadingException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Unable to load Transformer model for ${locale.getDisplayLanguage(locale)}, it may be corrupted or unsupported.",
                    Toast.LENGTH_LONG
                ).show()
                transformerDisabled = true
                e.printStackTrace()
            }
            return null
        }
    }

    suspend fun getLanguageModelSuggestions(values: PredictionInputValues): ArrayList<SuggestedWordInfo>? {
        if(values.composedData.mTypedWord.length > BinaryDictionary.DICTIONARY_MAX_WORD_LENGTH-1)
            return null

        val lmSuggestions = runLanguageModel(values)
        return lmSuggestions
    }

    // kxkb: for cluster-style (tap, non-swipe) input, each tap commits to a SET of letters (the
    // cluster's mains). Returns the per-tap allowed lowercase codepoints, or null when the constraint
    // must NOT apply: gesture/swipe input, no current keyboard, or a layout with no cluster keys at
    // all. That null path is what keeps ordinary/qwerty layouts and all swipe typing untouched -- on
    // a swipe keyboard off-path candidates are desirable, so the filter is bypassed entirely there.
    private fun clusterAllowedSets(values: PredictionInputValues): List<Set<Int>>? {
        if (values.composedData.mIsBatchMode) return null
        val keyboard = keyboardSwitcher.keyboard ?: return null
        val pointers = values.composedData.mInputPointers
        val n = pointers.pointerSize
        if (n <= 0) return null
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        val sets = ArrayList<Set<Int>>(n)
        var anyCluster = false
        for (i in 0 until n) {
            val nearest = keyboard.getNearestKeys(xs[i], ys[i])
            val key = nearest.firstOrNull { it.isOnKey(xs[i], ys[i]) } ?: nearest.firstOrNull()
            val mains = key?.clusterMains
            if (mains != null && mains.isNotEmpty()) {
                anyCluster = true
                sets.add(mains.map { Character.toLowerCase(it.codePoint) }.toHashSet())
            } else {
                // A non-cluster key mixed into a cluster layout pins its own letter; an unresolved
                // tap (empty set) is left unconstrained so we never over-filter on bad coordinates.
                val c = key?.code ?: -1
                sets.add(if (c > 0) hashSetOf(Character.toLowerCase(c)) else emptySet())
            }
        }
        return if (anyCluster) sets else null
    }

    // kxkb: the word's typed prefix (its first sets.size chars) must each fall in that tap's allowed
    // set; anything beyond the tap count is unconstrained, so "simp" still completes to "simple",
    // "simply", "simplistic". Words shorter than the tap count don't cover the taps and are dropped.
    private fun prefixInClusterSets(word: String, sets: List<Set<Int>>): Boolean {
        if (word.length < sets.size) return false
        for (i in sets.indices) {
            val allowed = sets[i]
            if (allowed.isEmpty()) continue
            if (!allowed.contains(Character.toLowerCase(word[i].code))) return false
        }
        return true
    }

    // kxkb: exact-length matches get a 2x boost while completions get an exponential penalty. The
    // tug-of-war on the penalty alone was unwinnable -- too aggressive killed completions like
    // "simp"->"simple", too soft let long content words like "cable"/"dehydration" through because
    // their LM raw scores are huge. Boosting exact-length directly makes the comparison decisive:
    // an exact-length match wins easily over any completion, while completions still appear in the
    // alternative slots when no exact-length match dominates. Words below the tap count are dropped
    // by the prefix filter.
    private fun clusterLengthBias(len: Int, tapCount: Int): Float {
        if (tapCount <= 0 || len < tapCount) return 1.0f
        if (len == tapCount) return 2.0f
        // kxkb: for 4-tap prefixes like "simp" -> "simple"/"simply", use a gentler completion
        // penalty so the obvious completions stay visible in the alternative slots. Other lengths
        // keep the steeper bias -- shorter inputs (3-tap "cat") get exact-length matches injected
        // by enumeration so don't need completion help; longer inputs (>=5 tap) tend to be full-word
        // typing where completions are usually noise rather than the intent.
        val baseFactor = if (tapCount == 4) 0.7 else 0.55
        return Math.pow(baseFactor, (len - tapCount).toDouble()).toFloat()
    }

    // kxkb: Multiling's actual mechanism. The LM beam and the dict's incremental tap-decoder both
    // miss many common in-set exact-length words for ambiguous cluster input (people, simple, ...).
    // Enumerate the cartesian product of per-tap cluster main letters (symbols filtered out, since
    // a typed word can't contain them), dictionary-check each combination, and return the valid
    // ones with their dictionary frequency. Capped at 2000 combinations so the cost per keystroke
    // stays bounded; very long/wide inputs fall back to the existing engines.
    private fun clusterEnumerateValid(clusterSets: List<Set<Int>>): List<Pair<String, Int>> {
        val n = clusterSets.size
        if (n == 0) return emptyList()
        val letterSets = clusterSets.map { set ->
            set.filter { Character.isLetter(it) }.toList()
        }
        if (letterSets.any { it.isEmpty() }) return emptyList()
        var total = 1L
        for (lst in letterSets) {
            total *= lst.size
            if (total > 2000) return emptyList()
        }
        val results = mutableListOf<Pair<String, Int>>()
        val sb = StringBuilder(n)
        fun recurse(pos: Int) {
            if (pos == n) {
                val word = sb.toString()
                if (dictionaryFacilitator.isValidSuggestionWord(word)) {
                    val freq = dictionaryFacilitator.getFrequency(word)
                    results.add(word to freq.coerceAtLeast(0))
                }
                return
            }
            for (codePoint in letterSets[pos]) {
                sb.append(codePoint.toChar())
                recurse(pos + 1)
                sb.deleteCharAt(sb.length - 1)
            }
        }
        recurse(0)
        return results
    }

    fun processAndMergeSuggestions(
        values: PredictionInputValues,
        suggestedWordsDict: SuggestedWords,
        lmSuggestions: ArrayList<SuggestedWordInfo>
    ): SuggestedWords? {
        var transformerWeight = context.getSetting(BinaryDictTransformerWeightSetting)
        if(dictionaryFacilitator.locales.size > 1) transformerWeight = 1.0f

        // kxkb: on cluster-style layouts each tap commits to a SET of letters (the key's mains), so
        // constrain suggestions to words whose typed prefix stays within the tapped sets (Multiling-
        // style). clusterSets is null for swipe/gesture input and for layouts with no cluster keys,
        // leaving ordinary and gesture typing completely unaffected. The typed word is always exempt.
        val clusterSets = clusterAllowedSets(values)
        val lmSuggestions = if(clusterSets != null) {
            ArrayList(lmSuggestions.filter { prefixInClusterSets(it.mWord, clusterSets) })
        } else lmSuggestions

        val suggestionResults = SuggestionResults(
            14, values.ngramContext.isBeginningOfSentenceContext, false)


        val reweightedSuggestions = lmSuggestions.mapIndexedNotNull { i, it ->
            if(transformerWeight == Float.NEGATIVE_INFINITY) { null } else {
                val lengthFactor = if (clusterSets != null) clusterLengthBias(it.mWord.length, clusterSets.size) else 1.0f
                SuggestedWordInfo(
                    it.mWord,
                    it.mPrevWordsContext,
                    (it.mScore.toFloat() * transformerWeight * lengthFactor).toLong().coerceAtMost(Int.MAX_VALUE.toLong() - lmSuggestions.size)
                        .toInt() - i + (lmSuggestions.size - 1),
                    it.mKindAndFlags,
                    it.mSourceDict,
                    it.mIndexOfTouchPointOfSecondWord,
                    it.mAutoCommitFirstWordConfidence
                ).apply {
                    this.mOriginatesFromTransformerLM = true
                }
            }
        }

        val maxWord = reweightedSuggestions.maxByOrNull { it.mScore }

        val suggestedWordsDictList = suggestedWordsDict.mSuggestedWordInfoList.mapNotNull { sw ->
            if (!suggestionBlacklist.isSuggestedWordOk(sw)) return@mapNotNull null
            val isTyped = sw === suggestedWordsDict.typedWordInfo
            if (clusterSets != null && !isTyped && !prefixInClusterSets(sw.mWord, clusterSets)) {
                return@mapNotNull null
            }
            if (clusterSets != null && !isTyped) {
                val factor = clusterLengthBias(sw.mWord.length, clusterSets.size)
                if (factor != 1.0f) {
                    SuggestedWordInfo(sw.mWord, sw.mPrevWordsContext,
                        (sw.mScore.toFloat() * factor).toInt(),
                        sw.mKindAndFlags, sw.mSourceDict,
                        sw.mIndexOfTouchPointOfSecondWord,
                        sw.mAutoCommitFirstWordConfidence)
                } else sw
            } else sw
        }.toMutableList()

        // kxkb: Multiling-style injection. Anything missing from the dict's tap-decoder output that
        // is nonetheless a real in-set exact-length word gets injected here so it always reaches the
        // bar. Score is a high base plus a frequency tiebreak (so common words like "people"/"simple"
        // rank above rare in-set siblings), with the 2x exact-length boost pre-applied since these
        // bypass the bias mapNotNull above.
        if (clusterSets != null) {
            // kxkb: cross-reference enumeration with the LM. Words the language model also generated
            // are contextually plausible -- freq >= 30 is enough to admit them. Words the LM did NOT
            // generate are likely proper nouns or abbreviations the dictionary happens to know about
            // (Diu, diu, yip, ...), so require a much higher freq floor (>= 100) to keep them out of
            // the alternative slots. When the LM is silent (still warming up, or it really had
            // nothing for this input), fall back to the single moderate floor used before.
            val enumerated = if (lmSuggestions.isNotEmpty()) {
                val lmWords = lmSuggestions.mapTo(HashSet()) { it.mWord.lowercase() }
                clusterEnumerateValid(clusterSets).filter { (word, freq) ->
                    if (word.lowercase() in lmWords) freq >= 30 else freq >= 100
                }
            } else {
                clusterEnumerateValid(clusterSets).filter { (_, freq) -> freq >= 50 }
            }
            if (enumerated.isNotEmpty()) {
                val existing = suggestedWordsDictList.mapTo(HashSet()) { it.mWord.lowercase() }
                for ((word, freq) in enumerated) {
                    if (word.lowercase() !in existing) {
                        val score = (100_000_000 + freq * 1_000_000) * 2
                        suggestedWordsDictList.add(SuggestedWordInfo(
                            word, "", score,
                            SuggestedWordInfo.KIND_CORRECTION,
                            null,
                            SuggestedWordInfo.NOT_AN_INDEX,
                            SuggestedWordInfo.NOT_A_CONFIDENCE
                        ))
                    }
                }
            }
        }

        var maxWordDict = suggestedWordsDictList.maxByOrNull {
            if(it == suggestedWordsDict.typedWordInfo
                || it.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)) Int.MIN_VALUE else it.mScore
        }

        val maxNonWhitelistWordDict = suggestedWordsDictList.maxByOrNull {
            if(it == suggestedWordsDict.typedWordInfo
                || it.isKindOf(SuggestedWordInfo.KIND_EMOJI_SUGGESTION)
                || (it.isKindOf(SuggestedWordInfo.KIND_WHITELIST) && it.mSourceDict?.mDictType == "main")) Int.MIN_VALUE else it.mScore
        }

        // English language has some shortcuts, e.g. "bot" -> "not",    "hid" -> "his"
        // These are common misspellings and usually useful corrections, but if the language model
        // believes the original word fits better in the context we should skip the shortcut,
        // else it's impossible to type these words without manually skipping correction in
        // suggestion bar
        if(maxWordDict != null && maxNonWhitelistWordDict != null && maxNonWhitelistWordDict != maxWordDict && maxNonWhitelistWordDict.mWord == maxWord?.mWord) {
            val idx = suggestedWordsDictList.indexOf(maxWordDict)
            suggestedWordsDictList.remove(maxWordDict)
            suggestedWordsDictList.add(idx, maxWordDict.scoreAtMost(maxNonWhitelistWordDict))
            maxWordDict = maxNonWhitelistWordDict
        }

        val bothAlgorithmsCameToSameConclusion = maxWordDict?.mWord == maxWord?.mWord

        var autocorrectWord: SuggestedWordInfo? = null
        val filtered = mutableListOf<SuggestedWordInfo>()
        if(bothAlgorithmsCameToSameConclusion && maxWord != null && maxWordDict != null){
            if(BuildConfig.DEBUG) Log.d(TAG, "both algorithms came to same conclusion, autocorrect to ${maxWord.mWord}")
            // We can be pretty confident about autocorrecting this
            val clone = maxWord.add(maxWordDict)
            autocorrectWord = clone
            suggestionResults.add(clone)
            filtered.add(maxWordDict)
            filtered.add(maxWord)
        }
        // In some cases the LM will predict an uppercased word but dictionary predicts lowercased,
        // we should prefer the lowercase version to reduce automatically capitalizing which can be
        // annoying
        val bothAlgorithmsCameToSameConclusionButLowerCased = maxWordDict?.mWord == maxWord?.mWord?.lowercase()
        if(bothAlgorithmsCameToSameConclusionButLowerCased && maxWord != null && maxWordDict != null) {
            if(BuildConfig.DEBUG) Log.d(TAG, "both algorithms came to same conclusion but lowercased, autocorrect to ${maxWord.mWord}")
            val clone = maxWordDict.scoreAtLeast(maxWord)
            autocorrectWord = clone
            suggestionResults.add(clone)
            filtered.add(maxWordDict)
        }

        if(transformerWeight <= 0.0f) {
            if(suggestedWordsDictList.isEmpty()) {
                transformerWeight = 1.0f
            }
        }

        // Add reweightedSuggestions, with space replacement logic. It can replace one of the LM
        // suggestions if the top dictionary result has a space, based on heuristics about the
        // relative quality of the LM suggestion
        val spaceReplacementPossible = maxWordDict != null && maxWordDict.word.count { it == ' ' } == 1
        var spaceReplacementPerformed = false
        for(i in 0 until reweightedSuggestions.size) {
            val word = reweightedSuggestions[i]
            if(filtered.contains(word)) continue

            if(!spaceReplacementPerformed && spaceReplacementPossible && (
                        // If the dict score is high enough, allow the space suggestion
                        ((maxWordDict.mScore) > (word.mScore / 3))
                                // Most LM-generated dashed suggestions are distractions, so accept the space suggestion
                                || (word.word.contains('-'))
                                // If the typed word is much longer than the transformer word, just accept the space suggestion
                                || (values.composedData.mTypedWord.length > ceil(word.word.length * 3.0 / 2.0))
                        )
            ) {
                val clone = maxWordDict.scoreAtLeast(word)
                suggestionResults.add(clone)
                spaceReplacementPerformed = true
                continue
            }

            suggestionResults.add(word)
        }

        if(maxWordDict?.mSourceDict?.mDictType == Dictionary.TYPE_USER_HISTORY
            && maxWordDict.mScore > 100
            && maxWord != null
            && (maxWordDict.mWord == values.composedData.mTypedWord || maxWordDict.mWord.length > 1)
        ) {
            if(BuildConfig.DEBUG) Log.d(TAG, "type user history found")
            val clone = maxWordDict.scoreAtLeast(maxWord)
            suggestionResults.add(clone)
        }

        suggestionResults.mRawSuggestions?.addAll(reweightedSuggestions.filter { !filtered.contains(it) })
        if(transformerWeight != Float.POSITIVE_INFINITY) {
            suggestedWordsDictList?.let { words ->
                suggestionResults.addAll(words.filter {
                    it != suggestedWordsDict.typedWordInfo && !filtered.contains(
                        it
                    )
                }.take(10))
            }
        }

        val settingsValues = settings.current ?: return null
        val locale = dictionaryFacilitator.primaryLocale ?: return null
        val wordComposer = inputLogic.mWordComposer ?: return null

        val suggestedWords = Suggest.obtainNonBatchedInputSuggestedWords(
            wordComposer,
            values.inputStyle,
            settingsValues.mAutoCorrectionEnabledPerUserSettings,
            -1,
            locale,
            suggestionResults,
            settingsValues.mAutoCorrectionThreshold,
            settingsValues.mIsNumberRowEnabled
        )


        if(BuildConfig.DEBUG) {
            val dmpw: (SuggestedWordInfo?) -> String = { v -> v?.let { "${it.mWord}:${it.mScore}:${it.mKindAndFlags}:${it.mSourceDict?.mDictType}" } ?: "[null]" }
            val dmp: (List<SuggestedWordInfo>?) -> String = { v -> v?.joinToString { dmpw(it) } ?: "[null]" }
            Log.d(TAG, "process update suggestion strip:\n" +
                    "raw lm results: ${dmp(lmSuggestions)}\n" +
                    "reweighted lm results: ${dmp(reweightedSuggestions)}\n" +
                    "raw dict results: ${dmp(suggestedWordsDict?.mSuggestedWordInfoList)}}\n" +
                    "filtered dict results: ${dmp(suggestedWordsDictList)}}\n" +
                    "------------\n" +
                    "max word lm: ${dmpw(maxWord)}}\n" +
                    "max word dict: ${dmpw(maxWordDict)}}\n"
            )
        }


        return suggestedWords
    }

    public suspend fun destroyModel() {
        if(BuildConfig.DEBUG) Log.d(TAG, "destroyModel called")
        languageModel?.closeInternalLocked()
        languageModel = null
    }

    public fun close() {
        userDictionary.unregister()
    }

    private var trainingEnabled = false

    public fun launchProcessor() = lifecycleScope.launch {
        if(BuildConfig.DEBUG) Log.d(TAG, "Starting processor")
        launch {
            withContext(Dispatchers.Default) {
                TrainingWorkerStatus.lmRequest.collect {
                    if (it == LanguageModelFacilitatorRequest.ResetModel) {
                        if(BuildConfig.DEBUG) Log.d(TAG, "ResetModel event received, destroying model")
                        destroyModel()
                    }else if(it == LanguageModelFacilitatorRequest.ClearTrainingLog) {
                        historyLog.clear()
                        saveHistoryLog()
                    }
                }
            }
        }

        launch {
            withContext(Dispatchers.Default) {
                ModelPaths.modelOptionsUpdated.collect {
                    if(BuildConfig.DEBUG) Log.d(TAG, "ModelPaths options updated, destroying model")
                    skipLanguage = null
                    destroyModel()
                }
            }
        }
    }

    public fun shouldPassThroughToLegacy(): Boolean = when {
        (!settings.current.mTransformerPredictionEnabled) -> true
        (dictionaryFacilitator.primaryLocale.language == skipLanguage) -> true
        (transformerDisabled) -> true
        else -> false
    }

    // This method should return null if transformer is disabled by settings or locale
    var prevAcceptedLayout: Keyboard? = null
    var prevAcceptedLayoutResult = false
    fun makePredictionInputValues(inputStyle: Int): PredictionInputValues? {
        if(shouldPassThroughToLegacy()) return null

        if(inputStyle == SuggestedWords.INPUT_STYLE_TAIL_BATCH
            || inputStyle == SuggestedWords.INPUT_STYLE_UPDATE_BATCH) return null

        val settingsValues = settings.current
        if (!settingsValues.needsToLookupSuggestions()) {
            return null
        }

        if(settingsValues.mInputAttributes.mIsEmailField) return null

        if(!inputLogic.mConnection.isConnected) return null

        try {
            val wordComposer = inputLogic.mWordComposer
            val ngramContext = inputLogic.getNgramContextFromNthPreviousWordForSuggestion(
                settingsValues.mSpacingAndPunctuations,
                2
            )

            if(wordComposer.isComposingWord
                && !context.getSetting(AllowTransformerOnNonQWERTYLayouts)
            ) {
                // Check we are on a supported layout
                if(prevAcceptedLayout != keyboardSwitcher.keyboard) {
                    if(keyboardSwitcher.keyboard?.mId?.mKeyboardLayoutSetName == "qwerty") {
                        prevAcceptedLayoutResult = true
                    } else {
                        val letters = keyboardSwitcher.keyboard?.sortedKeys?.filter {
                            settingsValues.isWordCodePoint(it.code) && it.code != '\''.code
                        }?.sortedBy {
                            it.x + 10000 * it.y
                        }?.joinToString(separator = "") { it.label.lowercase() } ?: ""

                        prevAcceptedLayoutResult = letters == "qwertyuiopasdfghjklzxcvbnm"
                    }
                }

                if(prevAcceptedLayoutResult == false)
                    return null
            }

            val values = PredictionInputValues(
                wordComposer.composedDataSnapshot,
                ngramContext,
                inputStyle,
                ++currentSequenceId
            )

            return values
        } catch(e: Exception) {
            if(e is CancellationException) throw e

            if(BuildConfig.DEBUG) Log.d(TAG, "Failed to get context, composed data snapshot, etc: $e")
            e.printStackTrace()
        }

        return null
    }

    private val historyLog: MutableList<HistoryLogForTraining> = mutableListOf()

    public fun addToHistory(
        word: String,
        wasAutoCapitalized: Boolean,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        blockPotentiallyOffensive: Boolean,
        importance: Int
    ) {
        if(shouldPassThroughToLegacy()) return
        if(!trainingEnabled) return
        if(settings.current?.mInputAttributes?.mNoLearning != false) return

        if(dictionaryFacilitator.mostConfidentLocale != languageModel?.locale) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val lastIdx = wordCtx.lastIndexOf(committedNgramCtx)
        if(lastIdx == -1) {
            //println("addToHistory: extraction failed, couldn't find ngram ctx in full ctx")
            return
        }

        val misspelledWord = wordCtx.substring(
            lastIdx + committedNgramCtx.length
        )
        if(misspelledWord.isNotBlank() && (!(misspelledWord.startsWith(" ") || committedNgramCtx == " ") || misspelledWord.endsWith(" ") || misspelledWord.trim().contains(" "))) {
            //println("addToHistory: extraction failed bad context. wordCtx=[$wordCtx]  --   committedNgramCtx=[$committedNgramCtx]  --  word=[$word]  --  fullNgram=[$ngramContext]")
            return
        }

        val ctxBeforeMisspelledWord = wordCtx.dropLast(misspelledWord.length)

        val key = committedNgramCtx.trim() + " " + word.trim()
        val logToAdd = if(misspelledWord.isNotBlank()) {
            // Correcting (ctx) misspelled -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                misspelledWord.trim(),
                word,
                importance,
                dictionaryFacilitator.primaryLocale.language,
                timeStampInSeconds
            )
        } else {
            // Predicted (ctx) -> word
            HistoryLogForTraining(
                key,
                ctxBeforeMisspelledWord,
                committedNgramCtx,
                null,
                word,
                importance,
                dictionaryFacilitator.primaryLocale.language,
                timeStampInSeconds
            )
        }

        historyLog.add(logToAdd)
        //println("addToHistory: Adding $logToAdd")
    }

    public fun unlearnFromHistory(
        word: String,
        ngramContext: NgramContext,
        timeStampInSeconds: Long,
        eventType: Int
    ) {
        if(shouldPassThroughToLegacy()) return
        if(!trainingEnabled) return

        val wordCtx = ngramContext.fullContext.trim().lines().last()
        var committedNgramCtx = ngramContext.extractPrevWordsContext().replace(NgramContext.BEGINNING_OF_SENTENCE_TAG, " ").trim();
        if(committedNgramCtx.isEmpty()) {
            committedNgramCtx = " "
        }
        
        val keyToSearch = committedNgramCtx.trim() + " " + word.trim()

        val logToRemove = historyLog.indexOfLast {
            it.key.startsWith(keyToSearch) || it.key == keyToSearch
        }

        if(logToRemove == -1) {
            //println("addToHistory: UNLEARN Couldn't find key $keyToSearch")
        } else {
            //println("addToHistory: Unlearning ${historyLog[logToRemove]}")
            historyLog.removeAt(logToRemove)
        }
    }

    public fun saveHistoryLog() {
        if(!context.getSetting(USE_TRANSFORMER_FINETUNING)) historyLog.clear()
        saveHistoryLogBackup(context, historyLog)
    }

    public fun loadHistoryLog() {
        assert(historyLog.isEmpty())
        loadHistoryLogBackup(context, historyLog)
    }

    public fun onStartInput() {
        transformerDisabled = false
        numConsecutiveTimeouts = 0
    }

    public fun isTransformerDisabled(): Boolean = transformerDisabled
}