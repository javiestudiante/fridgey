package ule.jescuj00.fridgey.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open Food Facts API v2 product response. We only model the handful of
 * fields requested via `?fields=...`; everything else is ignored by the
 * lenient Json config (`ignoreUnknownKeys = true`).
 *
 * `status` is 1 when the product was found, 0 otherwise (in which case
 * [product] is absent).
 */
@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val code: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_es") val productNameEs: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
)
