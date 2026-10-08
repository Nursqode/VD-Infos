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

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.vd171.vdinfos.R
import ru.vd171.vdinfos.core.model.Category
import ru.vd171.vdinfos.core.model.Verdict
import ru.vd171.vdinfos.data.Exporter
import ru.vd171.vdinfos.ui.components.AboutDialog
import ru.vd171.vdinfos.ui.components.CategoryHeader
import ru.vd171.vdinfos.ui.components.ProbeCard
import ru.vd171.vdinfos.ui.components.ReferenceLensDialog
import ru.vd171.vdinfos.ui.theme.verdictColor

@Composable
fun HomeScreen(vm: ScanViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showRefLens by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(vm.reportJson().toByteArray())
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                ctx,
                ctx.getString(if (ok) R.string.save_ok else R.string.save_fail),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val saveDivergencesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                ctx.contentResolver.openOutputStream(uri)?.use {
                    it.write(vm.divergencesJson().toByteArray())
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                ctx,
                ctx.getString(if (ok) R.string.save_ok else R.string.save_fail),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Scaffold(
        topBar = {
            CompactTopBar(
                reveal = state.reveal,
                onReveal = { vm.toggleReveal() },
                focusOnly = state.focusOnly,
                onFocusOnly = { vm.toggleFocusOnly() },
                onSave = { saveLauncher.launch(vm.suggestedFileName()) },
                onShare = {
                    val intent = Exporter.shareIntent(ctx, vm.currentSnapshot(), state.refLens)
                    ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.action_export)))
                },
                onSaveDivergences = { saveDivergencesLauncher.launch(vm.suggestedDivergencesFileName()) },
                onShareDivergences = {
                    val intent = Exporter.shareDivergencesIntent(ctx, vm.currentSnapshot(), state.refLens)
                    ctx.startActivity(
                        Intent.createChooser(intent, ctx.getString(R.string.action_share_divergences))
                    )
                },
                onRefLens = { showRefLens = true },
                onAbout = { showAbout = true },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.scan() }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_rescan))
            }
        },
    ) { pad ->
        if (showAbout) AboutDialog { showAbout = false }
        if (showRefLens) {
            ReferenceLensDialog(state.refLens) { lens ->
                vm.setRefLens(lens)
                showRefLens = false
            }
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
            SummaryHeader(state)
            SearchAndFilters(state, vm)
            val grouped = remember(state.filtered) { state.filtered.groupBy { it.spec.category } }
            val expanded = remember { mutableStateMapOf<Category, Boolean>() }
            LaunchedEffect(state.query) { expanded.clear() }
            val searching = state.query.isNotBlank()
            val singleCategory = grouped.size == 1
            val singleItem = state.filtered.size == 1
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp, end = 12.dp, bottom = 96.dp, top = 2.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                grouped.forEach { (cat, rows) ->
                    val mm = rows.count { it.isDivergent }
                    // Collapsed by default: the header count already says what is inside.
                    val isOpen = expanded[cat] ?: (searching || singleCategory)
                    item(key = "hdr:${cat.name}") {
                        CategoryHeader(
                            title = stringResource(cat.labelRes),
                            total = rows.size, mismatches = mm, expanded = isOpen,
                        ) { expanded[cat] = !isOpen }
                    }
                    if (isOpen) {
                        items(rows, key = { it.spec.id }) { r ->
                            ProbeCard(
                                result = r,
                                reveal = state.reveal,
                                refLens = state.refLens,
                                focusOnly = state.focusOnly,
                                startExpanded = singleItem,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One compact line for the app title, version and tagline, plus a single overflow
 * button. The bar wraps its content instead of forcing a fixed height, so a large
 * system font scale can no longer push the header over the probe list.
 */
@Composable
private fun CompactTopBar(
    reveal: Boolean,
    onReveal: () -> Unit,
    focusOnly: Boolean,
    onFocusOnly: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onSaveDivergences: () -> Unit,
    onShareDivergences: () -> Unit,
    onRefLens: () -> Unit,
    onAbout: () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "v" + ru.vd171.vdinfos.BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(
                    stringResource(R.string.app_tagline) +
                        " \u00b7 SDK " + ctx.applicationInfo.targetSdkVersion,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_reveal)) },
                        leadingIcon = {
                            Icon(
                                if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { menu = false; onReveal() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_focus_only)) },
                        leadingIcon = {
                            // A quiet checkbox that stays in place: an empty box when the
                            // filter is off, a checked one when it is on.
                            Icon(
                                if (focusOnly) Icons.Outlined.CheckBox
                                else Icons.Outlined.CheckBoxOutlineBlank,
                                contentDescription = null,
                                tint = if (focusOnly) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = { menu = false; onFocusOnly() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save)) },
                        leadingIcon = { Icon(Icons.Filled.SaveAlt, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onSave() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export)) },
                        leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_save_divergences)) },
                        leadingIcon = { Icon(Icons.Filled.SaveAlt, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onSaveDivergences() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share_divergences)) },
                        leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onShareDivergences() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_ref_lens)) },
                        leadingIcon = { Icon(Icons.Filled.Adjust, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onRefLens() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_about)) },
                        leadingIcon = { Icon(Icons.Filled.Info, null, Modifier.size(18.dp)) },
                        onClick = { menu = false; onAbout() },
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(state: ScanUiState) {
    val allAgreed = !state.scanning && state.total > 0 &&
        state.done == state.total && state.mismatches == 0
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (allAgreed) stringResource(R.string.summary_all_agreed_mark) else "") +
                        pluralStringResource(
                            R.plurals.summary_divergences, state.mismatches, state.mismatches,
                        ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        state.mismatches > 0 -> verdictColor(Verdict.MISMATCH)
                        allAgreed -> verdictColor(Verdict.MATCH)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    stringResource(R.string.summary_line, state.done, state.total, state.matches) +
                        if (!state.nativeAvailable) stringResource(R.string.summary_native_off) else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.scanning) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        if (state.scanning) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            )
        }
    }
}

/** Compact, icon-free search box: one thin line instead of a 56dp text field. */
@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(9.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val stroke = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interaction,
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp)
                    .clip(shape)
                    .border(if (focused) 1.5.dp else 1.dp, stroke, shape)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(15.dp)
                            .clickable { onValueChange("") },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFilters(state: ScanUiState, vm: ScanViewModel) {
    Column {
        CompactSearchField(
            value = state.query,
            onValueChange = { vm.setQuery(it) },
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.onlyDivergent,
                onClick = { vm.toggleDivergent() },
                label = {
                    Text(stringResource(R.string.filter_divergent), style = MaterialTheme.typography.labelSmall)
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = verdictColor(Verdict.MISMATCH),
                ),
            )
            FilterChip(
                selected = state.category == null,
                onClick = { vm.setCategory(null) },
                label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) },
            )
            state.categories.forEach { c ->
                FilterChip(
                    selected = state.category == c,
                    onClick = { vm.setCategory(if (state.category == c) null else c) },
                    label = { Text(stringResource(c.labelRes), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}
