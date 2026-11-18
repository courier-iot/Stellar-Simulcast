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
    /** "ANIME" or "MANGA" (light novels are MANGA with format NOVEL). */
    val type: String = "ANIME",
    val volumes: Int? = null,
    val chapters: Int? = null,
) {
    val isNovel: Boolean get() = format == "NOVEL"

    fun name(lang: TitleLang): String = when (lang) {
        TitleLang.ENGLISH -> english ?: romaji
        TitleLang.ROMAJI -> romaji ?: english
    } ?: "Untitled"

    /** The other-language title, if it's different. */
    fun altName(lang: TitleLang): String? {
        val other = if (lang == TitleLang.ENGLISH) romaji else english
        return other?.takeIf { it != name(lang) }
    }

    /** Episodes that have already aired, based on the next one coming up. */
    val airedCount: Int?
        get() = when {
            nextEpisode != null -> nextEpisode - 1
            status == "FINISHED" -> episodes
            else -> null
        }

    /** A releasing show whose next episode is over two weeks out is on a break (works for weekly and every-other-week shows). */
    fun onBreak(now: Long): Boolean {
        val at = nextAiringAt ?: return false
        return status == "RELEASING" && (nextEpisode ?: 0) > 1 && at - now > 15 * 86400
    }
}

data class AiringEp(val id: Int, val episode: Int, val airingAt: Long, val anime: Anime)

data class StreamLink(val site: String, val url: String, val language: String?)

data class CastMember(val character: String, val characterImage: String?, val role: String?, val voiceActor: String?)

data class Related(val anime: Anime, val relation: String)

data class EpisodeInfo(val number: Int, val airingAt: Long?, val title: String?, val url: String?)

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
    /** Every episode AniList has a date or title for, in order. */
    val episodeList: List<EpisodeInfo>,
    val trailerUrl: String?,
    val cast: List<CastMember>,
    val related: List<Related>,
    /** Change in average score over roughly the last month, if known. */
    val scoreChange: Int?,
    val recommendations: List<Anime> = emptyList(),
    val studioId: Int? = null,
)

data class StudioPage(val id: Int, val name: String, val shows: List<Anime>, val hasMore: Boolean)

enum class NovelBrowse(val label: String) { POPULAR("Popular novels"), TRENDING("Trending novels"), RECENT("New novels") }

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

/** What the Browse tab is showing. */
sealed interface BrowseMode {
    val label: String

    data object Trending : BrowseMode {
        override val label = "Trending"
    }

    data object BingeReady : BrowseMode {
        override val label = "Binge-ready"
    }

    data class OfSeason(val key: SeasonKey) : BrowseMode {
        override val label: String get() = key.label
    }
}

data class SequelNews(val parentTitle: String, val sequel: Anime, val startDate: LocalDate?)
