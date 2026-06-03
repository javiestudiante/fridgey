package ule.jescuj00.fridgey.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import ule.jescuj00.fridgey.data.repository.ProductLookupRepository
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.model.UnidadMedida
import ule.jescuj00.fridgey.domain.usecase.ParseQuantityUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HITO 1 verification: hits the REAL Open Food Facts API to prove the Ktor
 * call + JSON parsing + domain mapping work end-to-end. Network-dependent by
 * design. Run: ./gradlew :shared:testDebugUnitTest --tests "*OpenFoodFactsLookupTest*"
 */
class OpenFoodFactsLookupTest {

    private fun repo(): ProductLookupRepository {
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        return ProductLookupRepository(OpenFoodFactsApi(client), ParseQuantityUseCase())
    }

    @Test
    fun nutella_found_with_brand_appended_to_name() = runBlocking {
        val result = repo().lookup("3017620422003")
        println("[OFF] Nutella -> $result")
        assertTrue(result is ProductLookupResult.Found, "expected Found, got $result")
        val p = (result as ProductLookupResult.Found).product
        assertEquals("3017620422003", p.codigoBarras)
        assertTrue(p.nombre.contains("Nutella"), "name should contain Nutella: ${p.nombre}")
        assertTrue(p.nombre.contains("("), "brand should be appended in parens: ${p.nombre}")
        assertTrue(p.imagenUrl != null, "image url should be present")
    }

    @Test
    fun cocacola_quantity_33cl_parsed_to_330ml() = runBlocking {
        val result = repo().lookup("5449000000996")
        println("[OFF] Coca-Cola -> $result")
        assertTrue(result is ProductLookupResult.Found, "expected Found, got $result")
        val p = (result as ProductLookupResult.Found).product
        println("[OFF] Coca-Cola cantidad=${p.cantidad} unidad=${p.unidad} nombre=${p.nombre}")
        // "33 cl" -> 330 ml
        assertEquals(UnidadMedida.MILILITROS, p.unidad)
        assertEquals(330.0, p.cantidad)
    }

    @Test
    fun unknown_barcode_is_not_found() = runBlocking {
        val result = repo().lookup("0000000000000")
        println("[OFF] unknown -> $result")
        assertTrue(result is ProductLookupResult.NotFound, "expected NotFound, got $result")
    }
}
