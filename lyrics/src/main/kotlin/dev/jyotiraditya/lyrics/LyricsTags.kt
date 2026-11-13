package dev.jyotiraditya.lyrics

object LyricsTags {

    private val LYRICS_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "ELRC", "LRC")

    private const val LYRICS_KEY_PREFIX = "LYRICS-"

    /** The custom field the app writes its rich lyrics into, alongside the standard LRC. */
    private const val RICH_KEY = "CUSTOM1"

    private const val TTML_PREFIX = "DMT-TTML:"

    private const val LRC_PREFIX = "DMT-LRC:"

    /** The richest lyrics among [tags], keyed by upper-case name, or null if none hold any. */
    fun bestOf(tags: Map<String, List<String>>): String? = tags
        .asSequence()
        .filter { (key, _) ->
            val name = key.uppercase()
            name in LYRICS_KEYS || name.startsWith(LYRICS_KEY_PREFIX) || name == RICH_KEY
        }
        .flatMap { (key, values) ->
            if (key.uppercase() == RICH_KEY) {
                values.mapNotNull { value ->
                    when {
                        value.startsWith(TTML_PREFIX) -> value.removePrefix(TTML_PREFIX)
                        value.startsWith(LRC_PREFIX) -> value.removePrefix(LRC_PREFIX)
                        else -> null
                    }
                }
            } else {
                values
            }
        }
        .map(String::trim)
        .filter(String::isNotEmpty)
        // Formats are declared richest first.
        .minByOrNull { LyricsFormat.of(it).ordinal }
}
