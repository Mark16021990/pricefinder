package com.example.pricefinder.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MegamarketSource(
    private val client: OkHttpClient,
    private val json: Json
) : PriceSource {

    override val source = Source.MEGAMARKET

    override suspend fun search(query: String): List<PriceItem> {
        val url = "https://megamarket.ru/api/mobile/v1/catalogService/search/get"

        val payload = """
            {"requestVersion":10,"query":"$query","limit":100,"offset":0,
            "isMultiCategorySearch":true,"searchByOriginalQuery":false,
            "selectedSuggestParams":[],"sortType":1}
        """.trimIndent()

        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val body = client.newCall(request).execute().use { resp ->
            if (resp.code in 300..399) return emptyList()
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.string().orEmpty()
        }

        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrNull() ?: return emptyList()
        val items = findItemsArray(root) ?: return emptyList()

        val result = mutableListOf<PriceItem>()
        for (el in items) {
            val obj = el as? JsonObject ?: continue
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: obj["goodsName"]?.jsonPrimitive?.contentOrNull
                ?: continue
            val priceObj = obj["price"]
            val price = when (priceObj) {
                is JsonPrimitive -> priceObj.doubleOrNull
                is JsonObject -> priceObj["price"]?.jsonPrimitive?.doubleOrNull
                else -> null
            } ?: obj["finalPrice"]?.jsonPrimitive?.doubleOrNull ?: continue
            val id = obj["goodsId"]?.jsonPrimitive?.contentOrNull
                ?: obj["id"]?.jsonPrimitive?.contentOrNull ?: "$title"

            result += PriceItem(
                id = "mm_$id",
                name = title,
                brand = "",
                price = price,
                rating = null,
                feedbacks = null,
                url = "https://megamarket.ru/catalog/details/$id/",
                source = Source.MEGAMARKET
            )
        }
        return result
    }

    private fun findItemsArray(obj: JsonObject, depth: Int = 0): JsonArray? {
        if (depth > 6) return null
        for ((key, v) in obj) {
            if ((key == "items" || key == "products" || key == "goods") && v is JsonArray && v.isNotEmpty()) {
                return v
            }
            when (v) {
                is JsonObject -> findItemsArray(v, depth + 1)?.let { return it }
                is JsonArray -> v.forEach { el ->
                    (el as? JsonObject)?.let { findItemsArray(it, depth + 1)?.let { r -> return r } }
                }
                else -> {}
            }
        }
        return null
    }
}
