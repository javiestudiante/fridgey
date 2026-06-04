package ule.jescuj00.fridgey.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import ule.jescuj00.fridgey.data.remote.dto.OffProductResponse

/**
 * Thin Ktor wrapper over the Open Food Facts product-by-barcode endpoint
 * (API v2). Only requests the fields we map to the domain. Throws on
 * transport/parse failure — the repository turns those into a domain result.
 *
 * OFF requires a descriptive `User-Agent`; an empty/generic one can be
 * throttled or rejected.
 */
class OpenFoodFactsApi(private val client: HttpClient) {

    suspend fun getProduct(barcode: String): OffProductResponse =
        client.get("$BASE_URL/api/v2/product/$barcode.json") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("fields", FIELDS)
        }.body()

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val USER_AGENT = "Fridgey/1.0 (jescuj00@estudiantes.unileon.es)"
        const val FIELDS =
            "product_name,product_name_es,brands,quantity,image_url,categories_tags"
    }
}
