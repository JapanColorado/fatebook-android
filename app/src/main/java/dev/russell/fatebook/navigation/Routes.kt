package dev.russell.fatebook.navigation

import android.net.Uri

object Routes {
    const val FEED = "feed"

    /** Route pattern; prefill is an optional title seed (share-target flow). */
    const val CREATE = "create?prefill={prefill}"
    const val SETTINGS = "settings"
    const val ANALYTICS = "analytics"

    fun create(prefill: String? = null): String =
        if (prefill.isNullOrBlank()) "create" else "create?prefill=${Uri.encode(prefill)}"
}
