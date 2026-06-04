package ule.jescuj00.fridgey.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything an Open Food Facts lookup can pre-fill in AddProducto. The brand
 * is already folded into [nombre] (e.g. "Leche entera (Hacendado)") — there is
 * no separate brand field in the domain by design. [categoria] is always
 * [Categoria.OTROS] (OFF's taxonomy isn't mapped to our enum); the user
 * adjusts it afterwards.
 *
 * `@Serializable` so the scanner can hand the resolved fields back to
 * AddProducto across the navigation back-stack as a single JSON string
 * (see [toJson] / [fromJsonOrNull]). Keeping (de)serialization in the shared
 * module means the Compose layer needs no kotlinx-serialization dependency.
 */
@Serializable
data class ProductAutoFill(
    val codigoBarras: String,
    val nombre: String,
    val cantidad: Double,
    val unidad: UnidadMedida,
    val imagenUrl: String?,
    val categoria: Categoria,
) {
    fun toJson(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun fromJsonOrNull(raw: String?): ProductAutoFill? =
            raw?.let { runCatching { JSON.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/**
 * Outcome of a barcode → Open Food Facts lookup. The scanner shows immediate
 * feedback per case and always keeps "introducir a mano" available.
 */
sealed interface ProductLookupResult {
    data class Found(val product: ProductAutoFill) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data object NetworkError : ProductLookupResult
}
