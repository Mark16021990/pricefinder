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

    private val ozonClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val sources: List<PriceSource> = listOf(
        WildberriesSource(client, json),
        OzonSource(ozonClient, json),
        YandexMarketSource(client, json),
        MegamarketSource(client, json),
        AvitoSource(client, json),
        AliexpressSource(client, json)
    )

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
                            errors += "${src.source.label}: ${friendlyError(it)}"
                            emptyList()
                        }
                }
            }.flatMap { it.await() }
        }.filter { isRealProduct(it) }.sortedBy { it.price }

        val prices = all.map { it.price }
        SearchResult(
            items = all,
            averagePrice = if (prices.isEmpty()) 0.0 else prices.average(),
            minPrice = prices.minOrNull() ?: 0.0,
            maxPrice = prices.maxOrNull() ?: 0.0,
            sourceErrors = errors
        )
    }

    private val junkWords = listOf(
        "marketfront", "speculation", "velocity", "distributionbanner",
        "header", "footer", "banner", "способ доставки", "способы оплаты",
        "со страховкой", "способ оплаты", "доставка", "оплата", "гарантия",
        "бренд", "магазин", "оригинал", "спешл", "отправка", "самовывоз",
        "корзина", "избранное", "каталог", "войти", "регистрация"
    )

    private fun isRealProduct(item: PriceItem): Boolean {
        val name = item.name.trim()
        if (name.length < 5) return false
        if (name.startsWith("@") || name.contains("/")) return false
        val lower = name.lowercase()
        if (name.length < 25 && junkWords.any { lower == it || lower.startsWith("$it ") }) return false
        if (Regex("^[A-Za-z@/]+$").matches(name)) return false
        return true
    }

    private fun friendlyError(e: Throwable): String {
        val msg = e.message ?: "ошибка"
        return when {
            msg.contains("429") -> "слишком много запросов, повторите позже"
            msg.contains("redirect", ignoreCase = true) ||
                msg.contains("follow-up", ignoreCase = true) -> "источник недоступен"
            msg.contains("timeout", ignoreCase = true) -> "превышено время ожидания"
            else -> msg
        }
    }
}
