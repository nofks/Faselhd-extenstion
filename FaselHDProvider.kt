package com.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://www.faselhds.care"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime)

    private fun Element.toSearchResponse(): SearchResponse? {
        val title = select("div.h1").text() ?: return null
        val url = select("a").attr("href") ?: return null
        val posterUrl = select("img").attr("src")
        val quality = select("span.quality").text()
        val year = select("span.year").text().toIntOrNull()
        
        return MovieSearchResponse(
            title,
            url,
            this@FaselHD.name,
            if (url.contains("/episode/") || url.contains("/series/")) TvType.TvSeries else TvType.Movie,
            posterUrl,
            year,
            null,
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/series/" to "Series",
        "$mainUrl/anime/" to "Anime"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + if (page > 1) "page/$page/" else "").document
        val list = doc.select("div.poster").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("div.poster").mapNotNull {
            it.toSearchResponse()
        }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toEpisode(): Episode {
        val url = select("a").attr("href")
        val title = select("h3").text()
        val thumbUrl = select("img").attr("src")
        val episodeNum = title.getIntFromText()
        
        return newEpisode(url) {
            name = title
            episode = episodeNum
            posterUrl = thumbUrl
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("div.title h1").text()
        val posterUrl = doc.select("div.poster img").attr("src")
        
        val year = doc.select("span.year").text().toIntOrNull()
        val duration = doc.select("span.runtime").text().getIntFromText()
        val synopsis = doc.select("div.description").text()
        val rating = doc.select("span.rating").text().toRatingInt()
        
        val tags = doc.select("div.genres a").map { it.text() }
        
        val actors = doc.select("div.cast div.person").mapNotNull {
            val name = it.select("div.name").text() ?: return@mapNotNull null
            val image = it.select("img").attr("src") ?: return@mapNotNull null
            Actor(name, image)
        }

        val recommendations = doc.select("div.related div.poster").mapNotNull {
            it.toSearchResponse()
        }

        val isMovie = !url.contains("/series/") && !url.contains("/episode/")

        return if (isMovie) {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.rating = rating
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        } else {
            val episodes = doc.select("div.episodes div.episode").map {
                it.toEpisode()
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags
                this.rating = rating
                this.year = year
                this.plot = synopsis
                this.recommendations = recommendations
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        
        // Get watch servers
        doc.select("div.servers-list li").forEach { server ->
            val serverUrl = server.attr("data-link")
            val quality = when {
                server.text().contains("1080") -> Qualities.P1080
                server.text().contains("720") -> Qualities.P720
                server.text().contains("480") -> Qualities.P480
                server.text().contains("360") -> Qualities.P360
                else -> Qualities.Unknown
            }

            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    serverUrl,
                    this.mainUrl,
                    quality.value
                )
            )
        }
        
        return true
    }
}
