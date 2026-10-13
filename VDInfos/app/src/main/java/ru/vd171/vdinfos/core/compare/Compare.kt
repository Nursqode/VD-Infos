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
 * ======================================================================
 */

package ru.vd171.vdinfos.core.compare

import ru.vd171.vdinfos.core.model.Category
import ru.vd171.vdinfos.core.model.LensValue
import ru.vd171.vdinfos.core.model.ProbeResult
import ru.vd171.vdinfos.core.model.Verdict
import ru.vd171.vdinfos.data.Snapshot

/** One reading slot of a compared probe: this device vs the loaded report. */
data class ComparisonRow(
    val label: String,
    val valueA: String?,
    val valueB: String?,
    val presentA: Boolean,
    val presentB: Boolean,
    val different: Boolean,
)

/** One probe, its readings aligned between the two devices. */
data class ComparisonProbe(
    val id: String,
    val title: String,
    val category: Category,
    val sensitive: Boolean,
    val verdictA: Verdict?,
    val verdictB: Verdict?,
    val rows: List<ComparisonRow>,
    val onlyInA: Boolean,
    val onlyInB: Boolean,
) {
    /** The probe is a divergence when any row differs or it exists on just one device. */
    val differs: Boolean get() = rows.any { it.different } || onlyInA || onlyInB
}

/**
 * Aligns two device reports probe by probe. The reading key is the same label the card
 * shows (`tag` when present, else the lens short name), and readings that share a label
 * are paired positionally, so two `JVM`/`JNI` rows line up in order. Values are compared
 * with the app's own normalisation, so a row is highlighted only when the two devices
 * really disagree (whitespace, quotes and case ignored).
 */
object Compare {

    fun probes(a: Snapshot, b: Snapshot): List<ComparisonProbe> {
        val byB = b.results.associateBy { it.spec.id }
        val ids = (a.results.map { it.spec.id } + b.results.map { it.spec.id }).distinct()
        return ids.mapNotNull { id ->
            val pa = a.results.firstOrNull { it.spec.id == id }
            val pb = byB[id]
            if (pa == null && pb == null) null
            else {
                val host = pa ?: pb!!
                ComparisonProbe(
                    id = id,
                    title = host.spec.title,
                    category = host.spec.category,
                    sensitive = host.spec.sensitive,
                    verdictA = pa?.verdict,
                    verdictB = pb?.verdict,
                    rows = rows(pa, pb),
                    onlyInA = pb == null,
                    onlyInB = pa == null,
                )
            }
        }
    }

    private fun rows(pa: ProbeResult?, pb: ProbeResult?): List<ComparisonRow> {
        fun labelled(p: ProbeResult?) =
            p?.values?.map { v -> (v.tag ?: v.lens.short) to v } ?: emptyList()
        val a = labelled(pa)
        val b = labelled(pb)
        val labels = (a.map { it.first } + b.map { it.first }).distinct()
        val out = mutableListOf<ComparisonRow>()
        for (lbl in labels) {
            val aL = a.filter { it.first == lbl }.map { it.second }
            val bL = b.filter { it.first == lbl }.map { it.second }
            val n = maxOf(aL.size, bL.size)
            for (i in 0 until n) {
                val av = aL.getOrNull(i)
                val bv = bL.getOrNull(i)
                val presentA = av?.present == true
                val presentB = bv?.present == true
                val different = presentA != presentB ||
                    (presentA && presentB && !ProbeResult.sameReadingValue(av!!.value, bv!!.value))
                out += ComparisonRow(
                    label = lbl,
                    valueA = display(av),
                    valueB = display(bv),
                    presentA = presentA,
                    presentB = presentB,
                    different = different,
                )
            }
        }
        return out
    }

    private fun display(v: LensValue?): String? =
        v?.value?.takeIf { it.isNotBlank() } ?: v?.error
}