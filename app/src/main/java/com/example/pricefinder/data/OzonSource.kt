package com.example.pricefinder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class OzonSource(
    private val client: OkHttpClient,
    private val json: Json
) : PriceSource {

    override val source = Source.OZON

    override suspend fun search(query: String): List<PriceItem> {
        val path = URLEncoder.encode("/search/?text=$query&from_global=true", "UTF-8")
        val url = "https://api.ozon.ru/composer-api.bx/page/json/v2?url=$path"

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json")
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (resp.code in 300..399) return emptyList()
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
        val sku = obj["sku"]?.jsonPrimitive?.contentOrNull
            ?: obj["id"]?.jsonPrimitive?.contentOrNull
            ?: return null

        val title = findText(obj, listOf("title", "name", "text")) ?: return null

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
