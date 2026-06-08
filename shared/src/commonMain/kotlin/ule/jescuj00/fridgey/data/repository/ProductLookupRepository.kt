package ule.jescuj00.fridgey.data.repository

import kotlinx.coroutines.CancellationException
import ule.jescuj00.fridgey.data.remote.OffRateLimitException
import ule.jescuj00.fridgey.data.remote.OpenFoodFactsApi
import ule.jescuj00.fridgey.domain.model.Categoria
import ule.jescuj00.fridgey.domain.model.ProductAutoFill
import ule.jescuj00.fridgey.domain.model.ProductLookupResult
import ule.jescuj00.fridgey.domain.usecase.MapOffCategoryUseCase
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
    private val mapCategory: MapOffCategoryUseCase,
) : ProductLookupSource {
    override suspend fun lookup(barcode: String): ProductLookupResult {
        val response = try {
            api.getProduct(barcode)
        } catch (e: CancellationException) {
            throw e            // never swallow coroutine cancellation
        } catch (e: OffRateLimitException) {
            return ProductLookupResult.RateLimited   // HTTP 429 — temporary, distinct from NotFound
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
        // Infer the category FIRST so the quantity parser can fall back to the
        // INFERRED category's default unit (not OTROS) when OFF gives no usable
        // quantity — e.g. a dairy with no `quantity` lands on LITROS, not UNIDADES.
        val categoria = mapCategory(product.categoriesTags)
        val parsed = parseQuantity(product.quantity, fallbackUnit = categoria.unidadDefault)

        return ProductLookupResult.Found(
            ProductAutoFill(
                codigoBarras = barcode,
                nombre = appendBrand(baseName, product.brands),
                cantidad = parsed.cantidad,
                unidad = parsed.unidad,
                imagenUrl = product.imageUrl?.takeIf { it.isNotBlank() },
                // Inferred from OFF's `categories_tags`; OTROS when no keyword
                // matches. Only a SUGGESTION — the user can change it.
                categoria = categoria,
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
