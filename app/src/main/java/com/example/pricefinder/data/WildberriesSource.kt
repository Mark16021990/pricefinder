package com.example.pricefinder.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class WildberriesSource(
    private val client: OkHttpClient,
    private val json: Json
) : PriceSource {

    override val source = Source.WILDBERRIES

    override suspend fun search(query: String): List<PriceItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://search.wb.ru/exactmatch/ru/common/v9/search" +
            "?query=$q&resultset=catalog&limit=50&sort=popular" +
            "&dest=-1257786&lang=ru&curr=rub&spp=30"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) PriceFinder/2.0")
            .header("Accept", "*/*")
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        val parsed = json.decodeFromString<WbSearchResponse>(body)
        return parsed.data?.products.orEmpty().mapNotNull { p ->
            val price = p.resolvePrice() ?: return@mapNotNull null
            PriceItem(
                id = "wb_${p.id}",
                name = p.name.ifBlank { "Без названия" },
                brand = p.brand,
                price = price,
                rating = p.reviewRating ?: p.rating,
                feedbacks = p.feedbacks,
                url = "https://www.wildberries.ru/catalog/${p.id}/detail.aspx",
                source = Source.WILDBERRIES
            )
        }
    }
}
