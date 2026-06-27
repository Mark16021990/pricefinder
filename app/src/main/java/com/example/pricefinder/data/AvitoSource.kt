package com.example.pricefinder.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class AvitoSource(
    private val client: OkHttpClient,
    private val json: Json
) : PriceSource {

    override val source = Source.AVITO

    override suspend fun search(query: String): List<PriceItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.avito.ru/all?q=$q"

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .build()

        val html = client.newCall(request).execute().use { resp ->
            if (resp.code in 300..399) return emptyList()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        val items = mutableListOf<PriceItem>()
        val titleRegex = Regex("\"title\"\\s*:\\s*\"([^\"]{3,120})\"")
        val priceRegex = Regex("\"price(?:Detailed)?\"\\s*:\\s*\\{?[^}]*?\"?(?:value|price)\"?\\s*:\\s*\"?(\\d{2,8})")

        val titles = titleRegex.findAll(html).map { it.groupValues[1] }.toList()
        val prices = priceRegex.findAll(html).mapNotNull { it.groupValues[1].toDoubleOrNull() }
            .filter { it in 10.0..5000000.0 }
            .toList()

        val count = minOf(titles.size, prices.size, 30)
        for (i in 0 until count) {
            items += PriceItem(
                id = "av_$i",
                name = titles[i],
                brand = "",
                price = prices[i],
                rating = null,
                feedbacks = null,
                url = url,
                source = Source.AVITO
            )
        }
        return items
    }
}
