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

package ru.vd171.vdinfos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.vd171.vdinfos.R
import ru.vd171.vdinfos.core.compare.Compare
import ru.vd171.vdinfos.core.compare.ComparisonProbe
import ru.vd171.vdinfos.core.model.Category
import ru.vd171.vdinfos.core.model.ProbeResult
import ru.vd171.vdinfos.core.model.Verdict
import ru.vd171.vdinfos.data.Snapshot
import ru.vd171.vdinfos.ui.theme.verdictColor

/**
 * Compares the current device against a loaded report: for every probe a two-column
 * table ("This device" on the left, "Loaded report" on the right) with per-reading rows.
 * Rows whose value differs are highlighted red.
 */
@Composable
fun CompareScreen(
    currentResults: List<ProbeResult>,
    compared: Snapshot,
    reveal: Boolean,
    onClose: () -> Unit,
) {
    val probes = remember(currentResults, compared) {
        Compare.probes(Snapshot(0L, "", currentResults), compared)
    }
    val differing = probes.count { it.differs }

    Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
        // Header: count + identities + close button.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.compare_differs, differing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (differing > 0) verdictColor(Verdict.MISMATCH)
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.compare_this_device) + " v" +
                        ru.vd171.vdinfos.BuildConfig.VERSION_NAME + "  \u2194  " +
                        stringResource(R.string.compare_loaded_report) + " v" + compared.appVersion,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_remove_compare), Modifier.size(20.dp))
            }
        }
        // Two-column header for the reading rows.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
        ) {
            Text(
                stringResource(R.string.compare_this_device),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.compare_loaded_report),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (probes.isEmpty()) {
            Text(
                stringResource(R.string.compare_empty),
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        val grouped = probes.groupBy { it.category }
        val expanded = remember { mutableStateMapOf<Category, Boolean>() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            grouped.forEach { (cat, rows) ->
                val mm = rows.count { it.differs }
                val isOpen = expanded[cat] ?: (mm > 0 || grouped.size == 1)
                item(key = "c:$cat") {
                    CategoryHeader(
                        title = stringResource(cat.labelRes),
                        total = rows.size,
                        mismatches = mm,
                        expanded = isOpen,
                        onClick = { expanded[cat] = !isOpen },
                    )
                }
                if (isOpen) {
                    items(rows, key = { it.id }) { p ->
                        CompareProbeCard(p, reveal)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareProbeCard(p: ComparisonProbe, reveal: Boolean) {
    val differs = p.differs
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (differs) {
                Text(
                    when {
                        p.onlyInA -> stringResource(R.string.compare_only_in_a)
                        p.onlyInB -> stringResource(R.string.compare_only_in_b)
                        else -> "\u2716"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = verdictColor(Verdict.MISMATCH),
                    maxLines = 1,
                )
            }
        }
        if (p.rows.isEmpty()) {
            Text(
                "\u2014",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            p.rows.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 0.5.dp,
                )
                val rowBg = if (r.different) Color(0x1AFF5A5A) else Color.Transparent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(rowBg, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(52.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompareCell(r.valueA, r.presentA, r.different, p.sensitive, reveal, Modifier.weight(1f))
                    Spacer(Modifier.width(4.dp))
                    CompareCell(r.valueB, r.presentB, r.different, p.sensitive, reveal, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompareCell(
    value: String?,
    present: Boolean,
    different: Boolean,
    sensitive: Boolean,
    reveal: Boolean,
    modifier: Modifier = Modifier,
) {
    // Mask the current device's sensitive readings unless revealed, like the cards do.
    val shown = if (value != null && present && sensitive && !reveal) mask(value) else value
    Text(
        shown ?: "\u2014",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = when {
            different -> verdictColor(Verdict.MISMATCH)
            present -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun mask(value: String): String {
    if (value.length <= 4) return "\u2022\u2022\u2022\u2022"
    return value.take(2) + "\u2022".repeat((value.length - 4).coerceAtMost(12)) + value.takeLast(2)
}