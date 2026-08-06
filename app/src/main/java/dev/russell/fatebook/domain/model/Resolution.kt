package dev.russell.fatebook.domain.model

enum class Resolution(val apiValue: String) {
    YES("YES"),
    NO("NO"),
    AMBIGUOUS("AMBIGUOUS");

    companion object {
        fun fromApi(value: String): Resolution? =
            entries.find { it.apiValue.equals(value, ignoreCase = true) }
    }
}

/**
 * Special resolution values the server accepts for a multiple-choice question
 * besides a winning option's text.
 */
object McResolution {
    const val OTHER = "OTHER"
    const val AMBIGUOUS = "AMBIGUOUS"
}
