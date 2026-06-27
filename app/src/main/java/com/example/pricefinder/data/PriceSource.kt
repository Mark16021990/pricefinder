package com.example.pricefinder.data

/** A searchable price source (one marketplace). */
interface PriceSource {
    val source: Source
    suspend fun search(query: String): List<PriceItem>
}
