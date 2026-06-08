package ule.jescuj00.fridgey.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import ule.jescuj00.fridgey.data.remote.dto.OffProductResponse

/**
 * Thrown by [OpenFoodFactsApi] when OFF answers HTTP 429 (rate limit), so the
 * repository can map it to a distinct domain result instead of letting the
 * rate-limit body be (mis)deserialised into a product response.
 */
class OffRateLimitException : Exception("Open Food Facts rate limit (HTTP 429)")

/**
 * Thin Ktor wrapper over the Open Food Facts product-by-barcode endpoint
 * (API v2). Only requests the fields we map to the domain. Throws on
 * transport/parse failure — the repository turns those into a domain result.
 *
 * OFF requires a descriptive `User-Agent`; an empty/generic one can be
 * throttled or rejected.
 */
class OpenFoodFactsApi(private val client: HttpClient) {

    suspend fun getProduct(barcode: String): OffProductResponse {
        val response = client.get("$BASE_URL/api/v2/product/$barcode.json") {
            header(HttpHeaders.UserAgent, USER_AGENT)
            parameter("fields", FIELDS)
        }
        // Inspect the HTTP status BEFORE deserialising. A 429 body is not a
        // product JSON, so letting it reach `.body<OffProductResponse>()` would
        // disguise the rate limit as NotFound (all-default fields) or
        // NetworkError (parse failure). Distinguish it explicitly here.
        if (response.status == HttpStatusCode.TooManyRequests) {
            throw OffRateLimitException()
        }
        return response.body()
    }

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val USER_AGENT = "Fridgey/1.0 (jescuj00@estudiantes.unileon.es)"
        const val FIELDS =
            "product_name,product_name_es,brands,quantity,image_url,categories_tags"
    }
}
