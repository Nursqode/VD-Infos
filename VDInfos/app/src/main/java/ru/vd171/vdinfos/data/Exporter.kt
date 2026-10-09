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

package ru.vd171.vdinfos.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ru.vd171.vdinfos.core.model.Lens
import ru.vd171.vdinfos.core.model.LensValue
import ru.vd171.vdinfos.core.model.ProbeResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A report holding only the divergences: one entry per probe, only the offending readings. */
@Serializable
data class DivergenceReport(
    val takenAt: Long,
    val appVersion: String,
    val referenceLens: String? = null,
    val divergences: List<Divergence>,
)

@Serializable
data class Divergence(
    val probe: String,
    val title: String,
    val category: String,
    val verdict: String,
    val readings: List<LensValue>,
)

object Exporter {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    /** ASCII markers around a divergent reading, so it can be grepped in the report. */
    private const val MARK_BEFORE = ">>> DIVERGENT >>>"
    private const val MARK_AFTER = "<<< DIVERGENT <<<"

    private const val REPORT_FILE = "vdinfos-report.json"
    private const val DIVERGENCES_FILE = "vdinfos-divergences.json"

    fun toJson(snapshot: Snapshot, reference: Lens? = null): String =
        json.encodeToString(Snapshot.serializer(), marked(snapshot, reference))

    /** The short report: only the readings that diverge, with their markers. */
    fun toDivergencesJson(snapshot: Snapshot, reference: Lens? = null): String {
        val divergences = snapshot.results.mapNotNull { probe ->
            val flags = divergentFlags(probe, reference)
            val rows = probe.values.filterIndexed { i, _ -> flags[i] }.map(::marked)
            if (rows.isEmpty()) null
            else Divergence(
                probe = probe.spec.id,
                title = probe.spec.title,
                category = probe.spec.category.name,
                verdict = probe.verdict.name,
                readings = rows,
            )
        }
        return json.encodeToString(
            DivergenceReport.serializer(),
            DivergenceReport(
                takenAt = snapshot.takenAt,
                appVersion = snapshot.appVersion,
                referenceLens = reference?.name,
                divergences = divergences,
            ),
        )
    }

    fun toText(snapshot: Snapshot, reference: Lens? = null): String = buildString {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(snapshot.takenAt))
        appendLine("VD Infos ${snapshot.appVersion} - $stamp")
        // The same divergence test the app and the divergences report use, so the number here
        // follows the reference lens instead of the verdict the scan froze.
        val mismatches = snapshot.results.filter { it.divergesUnder(reference) }
        appendLine("Probes: ${snapshot.results.size} | Divergences: ${mismatches.size}")
        appendLine("=".repeat(48))
        if (mismatches.isNotEmpty()) {
            appendLine("\n## DIVERGENCES (Java × Native)")
            mismatches.forEach { r ->
                appendLine("• ${r.spec.title}")
                r.values.forEach { v ->
                    appendLine("    ${v.lens.short}: ${v.value ?: " - "}")
                    v.detail?.takeIf { it.isNotBlank() }?.let { appendLine("        ($it)") }
                }
            }
        }
    }

    fun shareIntent(context: Context, snapshot: Snapshot, reference: Lens? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, fileUri(context, REPORT_FILE, toJson(snapshot, reference)))
            putExtra(Intent.EXTRA_TEXT, toText(snapshot, reference))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun shareDivergencesIntent(context: Context, snapshot: Snapshot, reference: Lens? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(
                Intent.EXTRA_STREAM,
                fileUri(context, DIVERGENCES_FILE, toDivergencesJson(snapshot, reference)),
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    private fun divergentFlags(probe: ProbeResult, reference: Lens?): List<Boolean> =
        ProbeResult.divergentFlagsOf(probe.values, reference)

    private fun marked(v: LensValue): LensValue =
        v.copy(markBefore = MARK_BEFORE, markAfter = MARK_AFTER, divergent = true)

    /**
     * Copies [snapshot] tagging every divergent reading with the markers above. The marks
     * live in the exported copy only: the in-app snapshot and its cache stay raw.
     */
    private fun marked(snapshot: Snapshot, reference: Lens?): Snapshot = snapshot.copy(
        results = snapshot.results.map { probe ->
            val flags = divergentFlags(probe, reference)
            if (flags.none { it }) probe
            else probe.copy(values = probe.values.mapIndexed { i, v -> if (flags[i]) marked(v) else v })
        }
    )

    private fun fileUri(context: Context, name: String, content: String): Uri {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val f = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
    }
}
