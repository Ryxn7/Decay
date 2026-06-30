package com.decay.app.whitelist

import android.content.Context

/**
 * The set of app packages Decay is allowed to act inside. The camera pipeline
 * only spins up while one of these is in the foreground.
 *
 * Backed by SharedPreferences; defaults to the three flagship short-form feeds.
 */
object AppWhitelist {

    private const val PREFS = "decay_prefs"
    private const val KEY = "whitelist"

    val DEFAULTS: Set<String> = setOf(
        "com.instagram.android",        // Instagram
        "com.zhiliaoapp.musically",     // TikTok (global)
        "com.ss.android.ugc.trill",     // TikTok (alt region package)
        "com.google.android.youtube",   // YouTube (Shorts)
    )

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY, DEFAULTS) ?: DEFAULTS

    fun set(ctx: Context, packages: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY, packages).apply()
    }

    fun isWhitelisted(ctx: Context, pkg: String): Boolean = get(ctx).contains(pkg)
}
