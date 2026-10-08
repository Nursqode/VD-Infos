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

@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package ru.vd171.vdinfos.core.model

import androidx.annotation.StringRes
import ru.vd171.vdinfos.R
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

enum class Category(@StringRes val labelRes: Int) {
    IDENTITY(R.string.cat_identity),
    FINGERPRINT(R.string.cat_fingerprint),
    TELEPHONY(R.string.cat_telephony),
    NETWORK(R.string.cat_network),
    LOCALE(R.string.cat_locale),
    BOOT(R.string.cat_boot),
    BUILD(R.string.cat_build),
    HARDWARE(R.string.cat_hardware),
    DISPLAY(R.string.cat_display),
    SENSORS(R.string.cat_sensors),
    MEDIA(R.string.cat_media),
    STORAGE(R.string.cat_storage),
    PROCESS(R.string.cat_process),
    PACKAGES(R.string.cat_packages),
    ACCOUNTS(R.string.cat_accounts),
    WEBVIEW(R.string.cat_webview),
    SECURITY(R.string.cat_security),
    INTEGRITY(R.string.cat_integrity),
    EMULATOR(R.string.cat_emulator),
    SYSTEM(R.string.cat_system),
}

enum class Lens(val label: String, val short: String) {
    JAVA("Java / SDK", "JVM"),
    NATIVE("Native / JNI", "JNI"),
    SHELL("Shell via JVM", "JVM+SH"),
    SHELL_NATIVE("Shell via JNI", "JNI+SH"),
    ATTEST("TEE / Attestation", "TEE"),
}

@Serializable
data class LensValue(
    val lens: Lens,
    val source: String,
    /** Report only: marker written before the value of a divergent reading (null otherwise). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("mark_before")
    val markBefore: String? = null,
    val value: String?,
    /** Report only: marker written after the value of a divergent reading (null otherwise). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("mark_after")
    val markAfter: String? = null,
    /** Report only: true where this reading disagrees with its probe's reference reading. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val divergent: Boolean = false,
    val error: String? = null,
    val elapsedMicros: Long = 0L,
    val compare: Boolean = true,
    val detail: String? = null,
    val tag: String? = null,
    @Transient val reveal: String? = null,
) {
    val ok: Boolean get() = error == null
    val present: Boolean get() = ok && !value.isNullOrEmpty()
}

enum class Verdict(@StringRes val labelRes: Int) {
    MATCH(R.string.verdict_match),
    MISMATCH(R.string.verdict_mismatch),
    SINGLE(R.string.verdict_single),
    EMPTY(R.string.verdict_empty),
    ERROR(R.string.verdict_error),
    INFO(R.string.verdict_info),
}

@Serializable
data class ProbeSpec(
    val id: String,
    val title: String,
    val category: Category,
    val lenses: Set<Lens>,
    val note: String? = null,
    val sensitive: Boolean = false,
    val solution: String? = null,
)

@Serializable
data class ProbeResult(
    val spec: ProbeSpec,
    val values: List<LensValue>,
    val verdict: Verdict,
    val detail: String? = null,
) {
    fun value(lens: Lens): String? = values.firstOrNull { it.lens == lens }?.value
    val primary: String? get() = values.firstOrNull { it.present }?.value
    val isDivergent: Boolean get() = verdict == Verdict.MISMATCH

    companion object {
        /**
         * Flags aligned with [values] marking the readings the verdict votes on: present,
         * comparable, not an instrument failure and, where a real value exists, not a
         * shell refusal.
         */
        private fun votingFlags(values: List<LensValue>): List<Boolean> {
            val base = values.map { it.present && it.compare && !isInstrumentFailure(it.value!!) }
            if (base.none { it }) return base
            val hasNonShellValue = values.indices.any { i ->
                base[i] && !values[i].lens.isShell && !isRefusal(values[i].value!!)
            }
            return values.indices.map { i ->
                base[i] && !(hasNonShellValue && values[i].lens.isShell && isRefusal(values[i].value!!))
            }
        }

        fun verdictOf(values: List<LensValue>): Verdict {
            val present = values.filter { it.present }
            val errored = values.filter { !it.ok }
            if (present.isEmpty()) return if (errored.isNotEmpty()) Verdict.ERROR else Verdict.EMPTY
            val voting = votingFlags(values)
            val comparable = values.filterIndexed { i, _ -> voting[i] }
            if (comparable.isEmpty()) return Verdict.INFO
            if (comparable.size == 1) return Verdict.SINGLE
            val norm = comparable.map { normalise(it.value!!) }
            val polarity = norm.map { BOOLEAN_WORDS[it] }
            if (polarity.all { it != null }) {
                return if (polarity.toSet().size == 1) Verdict.MATCH else Verdict.MISMATCH
            }
            return if (norm.toSet().size == 1) Verdict.MATCH else Verdict.MISMATCH
        }

        /**
         * The readings that vote on a probe plus the one they are measured against.
         *
         * [pivot] is the index of the reading the marks are measured against: the
         * [reference] lens when it voted, otherwise the first reading carrying the most
         * repeated value. [tie] says two or more values share that top count, so without a
         * chosen lens no reading is trusted over the others.
         */
        private class Vote(
            val voting: List<Boolean>,
            val indices: List<Int>,
            val pivot: Int?,
            val tie: Boolean,
        )

        private fun voteOf(values: List<LensValue>, reference: Lens?): Vote {
            val voting = votingFlags(values)
            val indices = values.indices.filter { voting[it] }
            if (indices.size < 2) return Vote(voting, indices, indices.firstOrNull(), false)
            reference?.let { ref -> indices.firstOrNull { values[it].lens == ref } }
                ?.let { return Vote(voting, indices, it, false) }
            val norm = indices.map { normalise(values[it].value!!) }
            val counts = norm.groupingBy { it }.eachCount()
            val top = counts.values.max()
            val leaders = counts.filterValues { it == top }.keys
            if (leaders.size > 1) return Vote(voting, indices, indices.first(), true)
            val consensus = leaders.first()
            return Vote(voting, indices, indices.first { normalise(values[it].value!!) == consensus }, false)
        }

        /**
         * Flags aligned with [values]: `true` where a reading disagrees with the probe's
         * reference reading, so the UI can point at the exact method a divergence came from.
         *
         * The reference is the [reference] lens whenever that lens voted on this probe -
         * handy when one path is known to be unspoofed (an out-of-process getprop, say),
         * so the spoofed paths are the ones marked. Without it, the consensus is the most
         * repeated normalised value among the voting readings; when two or more values tie
         * for the most repeated, no reading is trusted over the others and every voting
         * reading is flagged.
         *
         * Readings outside the vote (not comparable, refused shell calls, instrument
         * failures, absent values) are never flagged, and a probe whose readings agree
         * flags nothing at all.
         */
        fun divergentFlagsOf(values: List<LensValue>, reference: Lens? = null): List<Boolean> {
            val vote = voteOf(values, reference)
            if (vote.indices.size < 2) return List(values.size) { false }
            if (vote.tie) return vote.voting
            val pivot = vote.pivot ?: return List(values.size) { false }
            val base = normalise(values[pivot].value!!)
            return values.indices.map { i -> vote.voting[i] && normalise(values[i].value!!) != base }
        }

        /**
         * Index of the one reading the marks are measured against ([divergentFlagsOf]), so a
         * focus view can keep exactly that row and drop the others of the same lens. Null
         * when the probe has nothing to compare.
         */
        fun referenceIndexOf(values: List<LensValue>, reference: Lens? = null): Int? =
            voteOf(values, reference).pivot

        private val BOOLEAN_WORDS = mapOf(
            "true" to true, "1" to true, "yes" to true, "on" to true, "enabled" to true,
            "false" to false, "0" to false, "no" to false, "off" to false, "disabled" to false,
        )

        private val Lens.isShell get() = this == Lens.SHELL || this == Lens.SHELL_NATIVE

        private fun isRefusal(v: String): Boolean = v.trim().uppercase() in Sentinels.REFUSALS

        fun isInstrumentFailure(v: String): Boolean {
            val t = v.trim()
            return t in Sentinels.FAILURES || Sentinels.FAILURE_PREFIXES.any { t.startsWith(it) }
        }

        private fun normalise(v: String): String =
            v.trim().trim('"').replace(Regex("\\s+"), " ").lowercase()
    }
}
