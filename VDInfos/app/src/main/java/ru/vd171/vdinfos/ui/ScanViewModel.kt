/*
 * ======================================================================
 *  VD INFOS  ::  method debugger
 *  Read every device info by every method, then compare. A divergence is a hook.
 *
 *  Copyright (C) 2026  VD171
 *  SPDX-License-Identifier: AGPL-3.0-or-later
 *
 *  Free software under the GNU AGPL v3 or later. NETWORK COPYLEFT: run a
 *  modified version, even as a service, and you MUST offer its source.
 *
 *  Site           : https://vd171.ru
 *  Site           : https://vd.priv8.ru
 *  Source         : https://github.com/VD171/VD-Infos
 *  GitHub         : @VD171 https://github.com/VD171
 *  XDA-Developers : @VD171 https://xdaforums.com/m/vd171.4699873/
 *  Telegram       : @VD_Priv8 https://t.me/VD_Priv8
 *  Discord        : @VD.Priv8 https://discord.com/users/1296831918989639721
 *  E-mail         : vd.priv8@pm.me
 * ======================================================================
 */

package ru.vd171.vdinfos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ru.vd171.vdinfos.BuildConfig
import ru.vd171.vdinfos.core.model.Category
import ru.vd171.vdinfos.core.model.Lens
import ru.vd171.vdinfos.core.model.ProbeResult
import ru.vd171.vdinfos.core.model.Verdict
import ru.vd171.vdinfos.data.Exporter
import ru.vd171.vdinfos.data.LocaleManager
import ru.vd171.vdinfos.data.ReferenceLens
import ru.vd171.vdinfos.data.Snapshot
import ru.vd171.vdinfos.data.SnapshotStore
import ru.vd171.vdinfos.engine.ProbeEngine
import ru.vd171.vdinfos.probe.NativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The divergence marks already measured, keyed by probe id.
 *
 * Marking a probe normalises every one of its readings, so the measurement is kept instead of
 * being redone: the header, the filters, the sort and every visible card ask for marks on each
 * pass, and repeating that work per read is what used to stall the list. The ViewModel owns
 * this cache and measures each probe once as the scan streams it in - one linear pass per scan
 * instead of a fresh one for every partial result the screen shows. A new scan (the same ids
 * can come back with other values) or a new reference lens (another baseline) drops the whole
 * cache at once.
 *
 * Identity equality is on purpose: the cache travels inside [ScanUiState], and a state
 * comparing it entry by entry would cost more than the marks it saves.
 */
class Marks {

    private val measured = ConcurrentHashMap<String, List<Boolean>>()

    /** The marks of [result], or null when nobody measured them yet. */
    fun of(result: ProbeResult): List<Boolean>? =
        measured[result.spec.id]?.takeIf { it.size == result.values.size }

    fun put(result: ProbeResult, marks: List<Boolean>) {
        measured[result.spec.id] = marks
    }

    /** Measures [result] against [lens], unless it has been measured already. */
    fun measure(result: ProbeResult, lens: Lens?) {
        if (of(result) == null) put(result, ProbeResult.divergentFlagsOf(result.values, lens))
    }
}

data class ScanUiState(
    val results: List<ProbeResult> = emptyList(),
    val scanning: Boolean = false,
    val total: Int = 0,
    val nativeAvailable: Boolean = NativeBridge.available,
    val query: String = "",
    val onlyDivergent: Boolean = false,
    val category: Category? = null,
    val reveal: Boolean = false,
    /** The path the divergence marks are measured against; null keeps the probe majority. */
    val refLens: Lens? = null,
    /** Keep only the divergences and the reading they were compared against. */
    val focusOnly: Boolean = false,
    /**
     * The marks measured under [refLens]: filled by the ViewModel as the scan arrives, shared
     * by every state of that scan, and replaced whenever the lens or the results change.
     */
    val marks: Marks = Marks(),
) {
    val done: Int get() = results.size
    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total

    /**
     * Marks of [result] under this state's [refLens]: the ones already measured, or a fresh
     * measurement for whatever the ViewModel has not reached yet (a probe read through a new
     * lens, or one asked about before the scan got to it).
     */
    fun marksOf(result: ProbeResult): List<Boolean> =
        marks.of(result) ?: ProbeResult.divergentFlagsOf(result.values, refLens).also {
            marks.put(result, it)
        }

    /**
     * Whether [result] counts as a divergence here: with a reference lens the marks decide, as
     * the chosen path is trusted and whatever disagrees with it is the divergence - even where
     * the majority vote found nothing wrong. Without one the probe's own verdict decides, as
     * always, which keeps the default reading free of any mark work at all.
     */
    fun diverges(result: ProbeResult): Boolean =
        if (refLens == null) result.isDivergent else marksOf(result).any { it }

    private val divergentCount: Int by lazy { results.count { diverges(it) } }
    private val matchCount: Int by lazy { results.count { it.verdict == Verdict.MATCH && !diverges(it) } }

    /** Probes that count as divergences under the chosen [refLens]. */
    val mismatches: Int get() = divergentCount

    /** Probes that still agree once the chosen lens is the baseline. */
    val matches: Int get() = matchCount

    val categories: List<Category> by lazy {
        Category.entries.filter { c -> results.any { it.spec.category == c } }
    }

    /**
     * The list the screen scrolls: filtered, ordered with the divergences first. Built once per
     * state - the screen reads it several times per pass, and every read used to redo the sort,
     * whose comparator re-measured its probes on every single comparison.
     */
    val filtered: List<ProbeResult> by lazy {
        results.asSequence()
            .filter { !onlyDivergent || diverges(it) }
            .filter { category == null || it.spec.category == category }
            .filter {
                query.isBlank() ||
                    it.spec.title.contains(query, true) ||
                    it.spec.id.contains(query, true) ||
                    it.values.any { v -> v.value?.contains(query, true) == true }
            }
            .filter { !focusOnly || marksOf(it).any { f -> f } }
            .sortedWith(compareBy<ProbeResult> { it.spec.category.ordinal }
                .thenByDescending { diverges(it) }
                .thenBy { it.spec.title })
            .toList()
    }
}

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ProbeEngine(LocaleManager.wrap(app))
    private val store = SnapshotStore(app)

    private val _state = MutableStateFlow(
        ScanUiState(total = engine.count, refLens = ReferenceLens.load(app))
    )
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init { scan() }

    fun scan() {
        if (_state.value.scanning) return
        // A new scan reads every probe again and the values can come back different: no mark
        // measured for the previous run survives it, so the state starts with a fresh cache.
        _state.update { it.copy(scanning = true, results = emptyList(), marks = Marks()) }
        viewModelScope.launch {
            val acc = ArrayList<ProbeResult>(engine.count)
            var i = 0
            engine.scan().collect { r ->
                acc.add(r); i++
                // Measured as it arrives, into whichever cache the state holds now: linear over
                // the whole scan, and the partial results shown while scanning cost nothing.
                val current = _state.value
                current.marks.measure(r, current.refLens)
                if (i % PROGRESS_STEP == 0) _state.update { it.copy(results = ArrayList(acc)) }
            }
            _state.update { it.copy(results = ArrayList(acc), scanning = false) }
            persist(acc)
        }
    }

    private fun persist(results: List<ProbeResult>) {
        store.save(Snapshot(System.currentTimeMillis(), BuildConfig.VERSION_NAME, results))
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun toggleDivergent() = _state.update { it.copy(onlyDivergent = !it.onlyDivergent) }
    fun setCategory(c: Category?) = _state.update { it.copy(category = c) }
    fun toggleReveal() = _state.update { it.copy(reveal = !it.reveal) }

    fun toggleFocusOnly() = _state.update { it.copy(focusOnly = !it.focusOnly) }

    fun setRefLens(lens: Lens?) {
        ReferenceLens.save(getApplication<Application>(), lens)
        // Same baseline as before: nothing to measure again (the dialog commits on dismiss too).
        if (_state.value.refLens == lens) return
        // Every mark was measured against the old baseline: measure the probes again.
        _state.update { it.copy(refLens = lens, marks = Marks()) }
    }

    fun currentSnapshot(): Snapshot =
        Snapshot(System.currentTimeMillis(), BuildConfig.VERSION_NAME, _state.value.results)

    fun reportJson(): String = Exporter.toJson(currentSnapshot(), _state.value.refLens)

    /** The short report: only the readings that diverge from the reference path. */
    fun divergencesJson(): String = Exporter.toDivergencesJson(currentSnapshot(), _state.value.refLens)

    fun suggestedFileName(): String = "vdinfos-${stamp()}.json"

    fun suggestedDivergencesFileName(): String = "vdinfos-divergences-${stamp()}.json"

    private fun stamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(java.util.Date())

    private companion object {
        /**
         * How often a running scan shows what it has found: the screen gets a partial list
         * every this many probes. Each one rebuilds the filtered list, so a larger step trades
         * a little less live feedback for less work on the main thread.
         */
        const val PROGRESS_STEP = 50
    }
}
