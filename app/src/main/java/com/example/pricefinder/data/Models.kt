package com.example.pricefinder.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============ Wildberries (internal, undocumented) ============
@Serializable
data class WbSearchResponse(val data: WbData? = null)

@Serializable
data class WbData(val products: List<WbProduct> = emptyList())

@Serializable
data class WbProduct(
    val id: Long = 0,
    val name: String = "",
    val brand: String = "",
    val rating: Double? = null,
    val reviewRating: Double? = null,
    val feedbacks: Int? = null,
    val sizes: List<WbSize> = emptyList(),
    @SerialName("salePriceU") val salePriceU: Long? = null,
    @SerialName("priceU") val priceU: Long? = null
) {
    fun resolvePrice(): Double? {
        val nested = sizes.firstNotNullOfOrNull { it.price?.product ?: it.price?.total }
        val raw = nested ?: salePriceU ?: priceU
        return raw?.let { it / 100.0 }
    }
}

@Serializable
data class WbSize(val price: WbPrice? = null)

@Serializable
data class WbPrice(
    val basic: Long? = null,
    val product: Long? = null,
    val total: Long? = null
)

// ============ Ozon (internal composer API, undocumented) ============
@Serializable
data class OzonResponse(
    val widgetStates: Map<String, String> = emptyMap()
)

// ============ UI-facing models ============
enum class Source(val label: String) {
    WILDBERRIES("Wildberries"),
    OZON("Ozon"),
    YANDEX_MARKET("Яндекс Маркет"),
    MEGAMARKET("Мегамаркет")
}

data class PriceItem(
    val id: String,
    val name: String,
    val brand: String,
    val price: Double,
    val rating: Double?,
    val feedbacks: Int?,
    val url: String,
    val source: Source
)

data class SearchResult(
    val items: List<PriceItem>,
    val averagePrice: Double,
    val minPrice: Double,
    val maxPrice: Double,
    val sourceErrors: List<String> = emptyList()
)
