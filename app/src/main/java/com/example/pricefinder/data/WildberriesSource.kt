package com.example.pricefinder.data

import kotlinx.coroutines.delay
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
            "?query=$q&resultset=catalog&limit=100&sort=popular" +
            "&dest=-1257786&lang=ru&curr=rub&spp=30"

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .header("Origin", "https://www.wildberries.ru")
            .header("Referer", "https://www.wildberries.ru/")
            .build()

        var body = ""
        var attempt = 0
        while (true) {
            attempt++
            val code = client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    body = resp.body?.string().orEmpty()
                    0
                } else {
                    resp.code
                }
            }
            if (code == 0) break
            if (code == 429 && attempt < 3) {
                delay(1200L * attempt)
                continue
            }
            error("HTTP $code")
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
