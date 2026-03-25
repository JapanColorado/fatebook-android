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
