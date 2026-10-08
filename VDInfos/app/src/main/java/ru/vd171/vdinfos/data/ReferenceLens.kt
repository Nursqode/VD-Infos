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
import ru.vd171.vdinfos.core.model.Lens

/**
 * The path the divergence marks are measured against.
 *
 * `null` (the default) keeps the probe's own majority: the most repeated reading wins and
 * everything that disagrees with it is marked. Picking a lens instead means "trust this
 * path" - on a device where one read is known to come back unspoofed (an out-of-process
 * `getprop`, say), the spoofed paths are the ones marked. A probe that does not read
 * through the chosen lens falls back to the majority.
 */
object ReferenceLens {

    private const val PREFS = "vdinfos_prefs"
    private const val KEY = "reference_lens"

    fun load(ctx: Context): Lens? =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { name -> Lens.entries.firstOrNull { it.name == name } }

    fun save(ctx: Context, lens: Lens?) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (lens == null) remove(KEY) else putString(KEY, lens.name)
        }.apply()
    }
}
