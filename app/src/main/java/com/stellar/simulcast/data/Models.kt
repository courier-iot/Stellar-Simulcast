package com.stellar.simulcast.data

import java.time.LocalDate

data class Anime(
    val id: Int,
    val english: String?,
    val romaji: String?,
    val services: List<String>,
    val cover: String?,
    val format: String?,
    val episodes: Int?,
    val status: String?,
    val score: Int?,
    val genres: List<String>,
    val nextEpisode: Int?,
    val nextAiringAt: Long?,
    val siteUrl: String?,
    val isAdult: Boolean,
) {
    fun name(lang: TitleLang): String = when (lang) {
        TitleLang.ENGLISH -> english ?: romaji
        TitleLang.ROMAJI -> romaji ?: english
    } ?: "Untitled"

    /** The other-language title, if it's different. */
    fun altName(lang: TitleLang): String? {
        val other = if (lang == TitleLang.ENGLISH) romaji else english
        return other?.takeIf { it != name(lang) }
    }
}

data class AiringEp(val id: Int, val episode: Int, val airingAt: Long, val anime: Anime)

data class StreamLink(val site: String, val url: String)

data class AnimeDetail(
    val anime: Anime,
    val description: String,
    val banner: String?,
    val studios: List<String>,
    val season: String?,
    val seasonYear: Int?,
    val duration: Int?,
    val source: String?,
    val links: List<StreamLink>,
    val upcoming: List<Pair<Int, Long>>,
)

data class Paged<T>(val items: List<T>, val hasMore: Boolean)

enum class Season { WINTER, SPRING, SUMMER, FALL }

data class SeasonKey(val season: Season, val year: Int) {
    val label: String
        get() = season.name.lowercase().replaceFirstChar { it.uppercase() } + " " + year

    fun next(): SeasonKey =
        if (season == Season.FALL) SeasonKey(Season.WINTER, year + 1)
        else SeasonKey(Season.entries[season.ordinal + 1], year)

    fun prev(): SeasonKey =
        if (season == Season.WINTER) SeasonKey(Season.FALL, year - 1)
        else SeasonKey(Season.entries[season.ordinal - 1], year)

    companion object {
        fun current(): SeasonKey {
            val d = LocalDate.now()
            val s = when (d.monthValue) {
                in 1..3 -> Season.WINTER
                in 4..6 -> Season.SPRING
                in 7..9 -> Season.SUMMER
                else -> Season.FALL
            }
            return SeasonKey(s, d.year)
        }
    }
}
