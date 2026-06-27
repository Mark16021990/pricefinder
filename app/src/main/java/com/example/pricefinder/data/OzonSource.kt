package com.example.pricefinder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * Ozon has no public search API. This uses the internal "composer-api"
 * endpoint that powers the website. It returns a map of widget states,
 * each a JSON string that must be parsed separately. The structure is
 * undocumented and changes often, so parsing is fully defensive: any
 * failure yields an empty list rather than crashing the whole search.
 */
class OzonSource(
    private val client: OkHttpClient,
    private val json: Json
) : PriceSource {

    override val source = Source.OZON

    override suspend fun search(query: String): List<PriceItem> {
        val q = URLEncoder.encode(query, "UTF-8")
        val path = URLEncoder.encode("/search/?text=$query&from_global=true", "UTF-8")
        val url = "https://api.ozon.ru/composer-api.bx/page/json/v2?url=$path"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) PriceFinder/2.0")
            .header("Accept", "application/json")
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrNull() ?: return emptyList()
        val states = root["widgetStates"]?.jsonObject ?: return emptyList()

        val items = mutableListOf<PriceItem>()
        for ((key, value) in states) {
            if (!key.contains("searchResults", ignoreCase = true) &&
                !key.contains("tileGrid", ignoreCase = true)) continue
            val raw = (value as? JsonPrimitive)?.contentOrNull ?: continue
            val widget = runCatching { json.parseToJsonElement(raw).jsonObject }
                .getOrNull() ?: continue
            val list = widget["items"] as? JsonArray ?: continue
            for (el in list) {
                parseItem(el as? JsonObject ?: continue)?.let { items += it }
            }
        }
        return items
    }

    private fun parseItem(obj: JsonObject): PriceItem? {
        // Ozon item shape varies; pull fields opportunistically.
        val sku = obj["sku"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
            ?: return null

        // Title is often nested in mainState text atoms.
        val title = findText(obj, listOf("title", "name", "text")) ?: return null

        // Price strings look like "1 299 ₽" — strip to digits.
        val priceText = findText(obj, listOf("price", "finalPrice", "cardPrice"))
        val price = priceText
            ?.filter { it.isDigit() }
            ?.toDoubleOrNull()
            ?: return null

        return PriceItem(
            id = "ozon_$sku",
            name = title,
            brand = "",
            price = price,
            rating = null,
            feedbacks = null,
            url = "https://www.ozon.ru/product/$sku",
            source = Source.OZON
        )
    }

    /** Shallow recursive search for the first string under any of the keys. */
    private fun findText(obj: JsonObject, keys: List<String>, depth: Int = 0): String? {
        if (depth > 4) return null
        for ((k, v) in obj) {
            if (k in keys) {
                (v as? JsonPrimitive)?.contentOrNull
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
            when (v) {
                is JsonObject -> findText(v, keys, depth + 1)?.let { return it }
                is JsonArray -> v.forEach { el ->
                    (el as? JsonObject)?.let { findText(it, keys, depth + 1)?.let { r -> return r } }
                }
                else -> {}
            }
        }
        return null
    }
}
