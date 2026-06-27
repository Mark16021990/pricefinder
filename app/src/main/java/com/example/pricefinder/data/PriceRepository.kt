package com.example.pricefinder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PriceRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val sources: List<PriceSource> = listOf(
        WildberriesSource(client, json),
        OzonSource(client, json)
    )

    /**
     * Builds a query from name + article + model, then searches every source
     * in parallel. Sources that fail are reported in sourceErrors but do not
     * abort the others.
     */
    suspend fun search(name: String, article: String, model: String): SearchResult =
        searchByQuery(
            listOf(name, article, model)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        )

    suspend fun searchByQuery(query: String): SearchResult = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Введите запрос или сделайте фото" }

        val errors = mutableListOf<String>()
        val all = coroutineScope {
            sources.map { src ->
                async {
                    runCatching { src.search(query) }
                        .getOrElse {
                            errors += "${src.source.label}: ${it.message ?: "ошибка"}"
                            emptyList()
                        }
                }
            }.flatMap { it.await() }
        }.sortedBy { it.price }

        if (all.isEmpty()) {
            return@withContext SearchResult(emptyList(), 0.0, 0.0, 0.0, errors)
        }

        val prices = all.map { it.price }
        SearchResult(
            items = all,
            averagePrice = prices.average(),
            minPrice = prices.min(),
            maxPrice = prices.max(),
            sourceErrors = errors
        )
    }
}
