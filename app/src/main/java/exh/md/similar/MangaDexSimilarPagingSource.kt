package exh.md.similar

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.MetadataMangasPage
import exh.md.handlers.SimilarHandler
import exh.md.service.MangaDexService
import exh.md.service.SimilarService
import exh.md.utils.MdLang
import exh.recs.sources.RecommendationPagingSource
import exh.recs.sources.SourceCatalogue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.sy.SYMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * MangaDexSimilarPagingSource inherited from the general Pager.
 */
internal class MangaDexSimilarPagingSource(
    manga: Manga,
    // KMK -->
    private val sourceCatalogue: SourceCatalogue,
    // KMK <--
) : RecommendationPagingSource(
    // KMK -->
    sourceCatalogue.source,
    // KMK <--
    manga,
) {

    override val name: String
        get() = "MangaDex"

    override val category: StringResource
        get() = SYMR.strings.similar_titles

    override val associatedSourceId: Long
        // KMK -->
        get() = sourceCatalogue.sourceId

    private val client by lazy { Injekt.get<NetworkHelper>().client }

    private val mdLang by lazy {
        sourceCatalogue.source?.lang?.let { lang ->
            MdLang.fromExt(lang)
        } ?: MdLang.ENGLISH
    }

    private val mangadexService by lazy {
        MangaDexService(client)
    }
    private val similarService by lazy {
        SimilarService(client)
    }
    private val similarHandler by lazy {
        SimilarHandler(mdLang.lang, mangadexService, similarService)
    }
    // KMK <--

    override suspend fun requestNextPage(currentPage: Int): MangasPage {
        val mangasPage = coroutineScope {
            val similarPageDef = async {
                // KMK -->
                similarHandler.getSimilar(manga.toSManga())
                // KMK <--
            }
            val relatedPageDef = async {
                // KMK -->
                similarHandler.getRelated(manga.toSManga())
                // KMK <--
            }
            val similarPage = similarPageDef.await()
            val relatedPage = relatedPageDef.await()

            MetadataMangasPage(
                relatedPage.mangas + similarPage.mangas,
                false,
                relatedPage.mangasMetadata + similarPage.mangasMetadata,
            )
        }

        return mangasPage.takeIf { it.mangas.isNotEmpty() } ?: throw NoResultsException()
    }
}
