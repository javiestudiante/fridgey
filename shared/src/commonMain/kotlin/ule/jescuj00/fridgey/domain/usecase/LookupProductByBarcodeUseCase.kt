package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.data.repository.ProductLookupSource
import ule.jescuj00.fridgey.domain.model.ProductLookupResult

/**
 * Scanner-facing entry point for the barcode → Open Food Facts lookup.
 * Thin delegate over a [ProductLookupSource] — Koin injects the caching
 * decorator, so repeated scans of the same product are served from cache
 * without the use case (or the ViewModel) knowing anything about it.
 */
class LookupProductByBarcodeUseCase(
    private val source: ProductLookupSource,
) {
    suspend operator fun invoke(barcode: String): ProductLookupResult =
        source.lookup(barcode)
}
