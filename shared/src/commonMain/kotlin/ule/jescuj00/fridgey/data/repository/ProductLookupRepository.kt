package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.CancellationException
import ule.jescuj00.fridgey.data.remote.OpenFoodFactsApi
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.usecase.ParseQuantityUseCase

/**
 * Resolves a scanned barcode against Open Food Facts and maps the response to
 * a [ProductAutoFill]. Network/transport failures become
 * [ProductLookupResult.NetworkError]; a `status != 1` (or missing product)
 * becomes [ProductLookupResult.NotFound]. The scanner decides the UX per case.
 */
class ProductLookupRepository(
    private val api: OpenFoodFactsApi,
    private val parseQuantity: ParseQuantityUseCase,
) {
    suspend fun lookup(barcode: String): ProductLookupResult {
        val response = try {
            api.getProduct(barcode)
        } catch (e: CancellationException) {
            throw e            // never swallow coroutine cancellation
        } catch (e: Exception) {
            return ProductLookupResult.NetworkError
        }

        val product = response.product
        if (response.status != 1 || product == null) {
            return ProductLookupResult.NotFound
        }

        val baseName = product.productNameEs?.takeIf { it.isNotBlank() }
            ?: product.productName?.takeIf { it.isNotBlank() }
            ?: ""
        val parsed = parseQuantity(product.quantity, fallbackUnit = Categoria.OTROS.unidadDefault)

        return ProductLookupResult.Found(
            ProductAutoFill(
                codigoBarras = barcode,
                nombre = appendBrand(baseName, product.brands),
                cantidad = parsed.cantidad,
                unidad = parsed.unidad,
                imagenUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                // OFF's category taxonomy doesn't map cleanly to our enum;
                // default to OTROS and let the user pick in AddProducto.
                categoria = Categoria.OTROS,
            )
        )
    }

    /**
     * Folds the brand into the product name: "Leche entera" + "Hacendado" →
     * "Leche entera (Hacendado)". Uses the first brand when several are
     * comma-separated. No separate brand field exists in the domain.
     */
    private fun appendBrand(name: String, brands: String?): String {
        val firstBrand = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
        return when {
            name.isBlank() -> firstBrand.orEmpty()
            firstBrand != null -> "$name ($firstBrand)"
            else -> name
        }
    }
}
